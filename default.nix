{ pkgs ? import <nixpkgs> { } }:
let
  clojureProject = pkgs.stdenv.mkDerivation {
    name = "backup-merge";
    src = ./.;
    nativeBuildInputs = [ pkgs.clojure pkgs.openjdk pkgs.makeWrapper ];
    buildPhase = ''
      mkdir -p target
      clojure -X:uberjar
    '';

    installPhase = ''
      # mkdir -p $out
      ls -al
      ls -al $out
      cp target/*.jar $out/app.jar
    '';
  };

  dockerImage = pkgs.dockerTools.buildImage {
    name = "clojure-app";
    tag = "latest";
    contents = [ pkgs.openjdk clojureProject ];
    config.Cmd = [ "java" "-jar" "/app.jar" ];
  };
in { inherit clojureProject dockerImage; }
