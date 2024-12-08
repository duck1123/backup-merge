{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell {
  buildInputs = with pkgs; [
    babashka
    bbin
    clojure
    gnumake
    postgresql
    yarn
  ];
}
