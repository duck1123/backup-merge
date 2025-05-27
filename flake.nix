{
  description =
    "A Nix flake that builds a Clojure project and packages it into a Docker image";

  inputs = {
    clj-nix = {
      inputs = {
        nix-fetcher-data.follows = "nix-fetcher-data";
        nixpkgs.follows = "nixpkgs";
      };
      url = "github:jlesquembre/clj-nix";
    };

    flake-parts.url = "github:hercules-ci/flake-parts";

    flake-utils.url = "github:numtide/flake-utils";

    make-shell.url = "github:nicknovitski/make-shell";

    nix-fetcher-data = {
      inputs = {
        flake-parts.follows = "flake-parts";
        nixpkgs.follows = "nixpkgs";
      };
      url = "github:jlesquembre/nix-fetcher-data";
    };

    nixpkgs.url = "nixpkgs/nixos-unstable";
  };

  outputs = inputs@{ clj-nix, flake-parts, make-shell, nixpkgs, self, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      inherit (clj-nix.lib) mkCljApp;
      inherit (clj-nix.packages.${system}) mk-deps-cache;
      inherit (pkgs.dockerTools) buildImage;
      projectSrc = ./.;

      deps-cache = mk-deps-cache { lockfile = ./deps-lock.json; };

      backup-merge = mkCljApp {
        inherit pkgs;
        modules = [{
          inherit projectSrc;
          name = "net.kronkltd/backup-merge";
          version = "0.1.0";
          main-ns = "backup-merge.core";
        }];
      };

      bm = pkgs.runCommand "bm" {
        nativeBuildInputs = [ deps-cache pkgs.babashka pkgs.git pkgs.tree ];
      } ''
        mkdir -p $TMPDIR/cpcache
        mkdir -p $out/bin
        export CLJ_CACHE=$TMPDIR/cpcache
        export HOME=${deps-cache}
        export JAVA_TOOL_OPTIONS="-Duser.home=${deps-cache}"
        cd ${projectSrc}
        substitute ./bm $out/bin/bm \
          --replace-fail "#!/usr/bin/env bb" "#!${pkgs.babashka}/bin/bb"
        chmod +x $out/bin/bm
        cp -r src $out/bin/src
      '';

      dockerImage = buildImage {
        name = "backup-merge";
        tag = "latest";
        copyToRoot = clj-nix.pkgs.buildEnv {
          name = "clojure-app-env";
          paths = with pkgs; [ bash coreutils nushell openjdk backup-merge ];
        };
        config = {
          Cmd = [ "${backup-merge}/bin/backup-merge" ];
          Env = [ "USER=backup-merge" ];
          WorkinDir = "/app";
        };
      };
    in flake-parts.lib.mkFlake { inherit inputs; } (_: {
      imports = [ make-shell.flakeModules.default ];
      systems = [ system ];

      perSystem = { pkgs, ... }: {
        make-shells.default = { ... }: {
          packages = with pkgs; [
            babashka
            bbin
            clojure
            gnumake
            postgresql
            runme
            tree
            yarn
          ];
        };

        packages = {
          inherit bm deps-cache dockerImage;
          default = bm;
        };
      };
    });
}
