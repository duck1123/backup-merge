# Backup Merge

## Start Clerk

[Notebooks](http://localhost:7777/)

```sh {"name": "start-clerk"}
# bb server
./bm clerk start
```

## Deps lock

``` sh {"name":"deps-lock"}
nix run github:jlesquembre/clj-nix#deps-lock
```

## Build

Build the docker image to ./result

``` sh {"name":"build-image"}
nix build .#dockerImage
```

## Load Image

Loads the built image into docker

``` sh {"name":"load-image"}
docker load < result
```

## Run Image

Run the latest image

``` sh {"name":"run-image"}
docker run backup-merge:latest
```

## CI

``` sh {"name":"ci"}
runme run build-image
runme run load-image
runme run run-image
```
