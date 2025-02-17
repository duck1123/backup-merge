{
  description =
    "A Nix flake that builds a Clojure project and packages it into a Docker image";

  inputs = {
    clj-nix.url = "github:jlesquembre/clj-nix";

    flake-utils.url = "github:numtide/flake-utils";

    nixpkgs.url = "nixpkgs/nixos-unstable";
  };

  outputs = { clj-nix, nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      inherit (clj-nix.lib) mkCljApp;
      inherit (pkgs.dockerTools) buildImage;

      clojureProject = mkCljApp {
        inherit pkgs;
        modules = [{
          projectSrc = ./.;
          name = "net.kronkltd/backup-merge";
          version = "0.1.0";
          main-ns = "backup-merge.core";
        }];
      };

      dockerImage = buildImage {
        name = "backup-merge";
        tag = "latest";
        copyToRoot = pkgs.buildEnv {
          name = "clojure-app-env";
          paths = with pkgs; [
            bash
            coreutils
            nushell
            openjdk
            clojureProject
          ];
        };
        config = {
          Cmd = [ "${clojureProject}/bin/backup-merge" ];
          Env = [
            "USER=backup-merge"
          ];
          WorkinDir = "/app";
        };
      };

    in {
      packages.${system} = {
        inherit clojureProject dockerImage;
        default = dockerImage;
      };

      devShells.${system}.default = pkgs.mkShell {
        buildInputs = with pkgs; [
          babashka
          bbin
          clojure
          gnumake
          postgresql
          runme
          yarn
        ];
      };
    };
}
