# Brood

Brood is a programmable artificial-life sandbox. This repository contains the Phase 1 foundation: a React, TypeScript, and Vite frontend with a deterministic terrarium fixture.

## Requirements

- Node.js 20 or newer
- npm

## Run the frontend

```sh
cd web
npm install
npm run dev
```

Open the URL printed by Vite (usually <http://localhost:5173>).

## Build

```sh
cd web
npm run build
npm run typecheck
```

## Java simulation core

Java 21 is required for the standalone deterministic core in `sim/`.

```sh
cd sim
./gradlew test build
```

The engine is not connected to the web fixture or server yet. See [`sim/SEMANTICS.md`](sim/SEMANTICS.md) for its tick, rule, snapshot, and hashing contract.

## Repository layout

- `lang/` — future creature language
- `sim/` — standalone Java 21 deterministic simulation core
- `lab/` — future experiment tooling
- `server/` — future Java and Spring Boot API
- `web/` — React and TypeScript frontend

The current fixture in `web/src/simulation/fixture.ts` is a deterministic visual stand-in. Canvas drawing and fixture updates are isolated from React so the renderer can later consume simulation snapshots from the Java API.
