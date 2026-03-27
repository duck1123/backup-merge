# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Backup Merge is a Clojure-based tool for merging, parsing, and exploring Nostr backup data. It provides a CLI (`bm`), an interactive Clerk notebook UI, and XTDB-backed data storage.

## Common Commands

### Development Tasks (via Babashka)

```sh
bb server          # Start Clerk notebook UI (http://localhost:7777/, nREPL on 7000)
bb lint            # Run cljfmt check + eastwood linter
bb format          # Auto-fix formatting (cljfmt)
bb check           # Check formatting only
bb validate        # Run eastwood linter only
bb test            # Run tests
bb ci-local        # Local CI: lint + test
bb ci-all          # Full CI pipeline: lint, test, eastwood, build, update-deps
bb clean           # Remove generated resources
bb update-deps-lock  # Update Nix deps lock file
```

### Build

```sh
nix build .#dockerImage    # Build Docker image to ./result
docker load < result       # Load into Docker
nix run github:jlesquembre/clj-nix#deps-lock  # Regenerate deps lock for Nix
```

### CLI (`bm` script)

```sh
./bm clerk start                     # Start notebook server
./bm convert -0 <filename>           # Convert JS backup to JSONL
./bm merge js                        # Merge JavaScript backup files
./bm merge jsonl                     # Merge JSONL backup files
./bm nostr list-backups              # List available backups
./bm nostr parse --file <file>       # Parse a backup file
./bm org fetch --date <date>         # Fetch daily org file
./bm org parse --date <date>         # Parse org file by date
```

## Architecture

### Multi-Runtime Structure

The project runs across three runtimes:

- **JVM/Clojure** (`src/main/`) — Core application: XTDB database operations, org-mode parsing, data transformation, Clerk notebook definitions
- **Babashka** (`src/babashka/`) — CLI orchestration: command routing, process management, bridges to JVM and Node.js code
- **Node.js/ClojureScript via nbb** (`src/nbb/`) — File operations: reading JS-format backups, merging, format conversion

### Key Components

**`src/babashka/backup_merge/core.clj`** — CLI entry point. Defines all `bm` subcommands using `babashka.cli`. Routes commands to JVM clojure or nbb processes.

**`src/main/backup_merge/core.clj`** — Service logic: XTDB node startup/shutdown (via `mount`), Nostr event ingestion, org-mode file parsing and querying.

**`src/main/backup_merge/state.clj`** — Shared Clerk notebook state as a reactive atom (`!state`). Tracks selected files, pagination, active filters (pubkey, event-id), and XTDB connection.

**`src/main/backup_merge/helpers.clj`** — Clerk UI components and custom viewers for notebook rendering.

**`src/nbb/backup_merge/example.cljs`** — ClojureScript logic for reading JavaScript-object-format backup files and merging/deduplicating Nostr events.

**`src/notebooks/backup_merge/`** — Clerk notebook files (`core_notebook.clj`, `org_notebook.clj`) for interactive data exploration.

### Database

XTDB 2.0 is used for Nostr event storage. Default config uses local file storage (`./storage`, `./tx-log`). Remote config (`xtdb.edn`) uses PostgreSQL. The `mount` library manages the node lifecycle.

### Nostr Event Shape

```clojure
{:id "...", :pubkey "...", :kind 42, :content "...",
 :tags [["p" "..."] ...], :sig "...", :created_at 1234567890}
```

## Dependencies

- `deps.edn` — JVM Clojure deps (xtdb, clerk, next.jdbc, cheshire, mount, timbre, orgmode)
- `bb.edn` — Babashka tasks and deps
- `package.json` — Node.js deps (nbb, zx, csv-parse)
- `flake.nix` — Nix build, Docker image, and `bm` CLI packaging
- `flake.lock` — Locked Nix inputs (update with `nix flake update`)

## Formatting

Custom indentation rules are in `indentation.clj`. cljfmt reads this for project-specific style. Run `bb format` to apply.
