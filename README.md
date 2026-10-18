# Brood

Brood is a small artificial-life laboratory. Observe six named creatures in a living terrarium, edit their rule programs, and compare two deterministic futures from a saved moment.

**Live demo:** <https://brood-3581.onrender.com>

## What works

- Six authoritative Java creatures: Mallow, Pip, Lumen, Nori, Peach, and Thimble. Each starts with its own deterministic rule program.
- A fixed-tick simulation with seeded randomness, stable entity IDs, food sensing and consumption, energy, and recorded rule executions.
- A small custom language for energy and nearest-food conditions and the actions `seek nearest_food`, `eat`, `rest`, and `wander`.
- Per-creature program editing through the field journal. Invalid programs return source-position diagnostics and leave the world unchanged.
- Canonical, versioned world snapshots with restoration and SHA-256 state hashes.
- Fork Time: capture a moment, create independent original and alternate branches, edit a creature in the alternate, advance either branch or both by a chosen number of ticks, and compare engine-derived outcomes.
- A Canvas 2D terrarium. Cosmetic animation and smoothed display positions are renderer-only; they never modify simulation state.

## Architecture and data flow

```text
React field journal / Fork Time
              │ same-origin /api requests
              ▼
Vite proxy ──► local Java HTTP adapter (127.0.0.1:8080)
              ├── lang: lexer, parser, validator, interpreter
              └── sim: immutable world state, fixed-tick engine, snapshots, hashes
```

The server owns the current world and up to eight in-memory Fork Time experiments. Each experiment is created by canonical snapshot serialization and two independent restores. Branch identifiers increase monotonically for the lifetime of the server process. Branch programs and tick histories are isolated from the main world and from the other branch. Restart resets the main world; it does not rewrite existing experiments. Experiments are held in memory and are lost when the API process stops.

The production frontend reads and updates the Java world through `web/src/simulation/EngineStore.ts`. The older visual fixture remains in its own module for isolated visual development and is not a production fallback.

## Creature program example

```text
when energy <= 25000: rest
when nearest_food <= 300000: seek nearest_food
otherwise: wander
```

Rules run in source order; the optional `otherwise` rule is last. Conditions support `<`, `<=`, `>`, and `>=` with integer constants. `#` and `//` begin comments. Programs have no loops, functions, external I/O, or reflection, and the interpreter enforces a step budget. Energy constants use milli-energy units (25,000 means 25%). See [`lang/`](lang/) for the grammar and compiler implementation.

## Determinism and snapshots

The default world seed is `2026`. Creatures are initialized in stable ID order at documented integer coordinates; the seeded SplitMix64 generator initializes food positions and supplies `wander` draws. A `step()` call advances one integer tick, processes creatures and food in stable ID order, and does not read wall-clock time. UI speed controls schedule more fixed ticks per display interval; they do not change tick semantics.

Snapshots include the tick, seed and RNG state, next entity ID, all creatures and their programs, available food, and rule-execution history. Canonical serialization uses explicit field order and versioning; SHA-256 hashes cover the canonical snapshot. Fork Time reports those state hashes and computes comparisons from the branch worlds. This is snapshot branching and forward simulation, not historical event replay.

For the full rules and initialization contract, read [`sim/SEMANTICS.md`](sim/SEMANTICS.md).

## Requirements

- Java 21 (the Gradle projects target Java 21)
- Node.js 20 or newer
- npm

The wrapper uses Gradle 9.8.1 and verifies the downloaded distribution checksum. No extra Java or frontend frameworks are needed.

## Run locally

Start the local API in one terminal:

```sh
cd server
../sim/gradlew run
```

The API binds only to `127.0.0.1:8080`. Start the frontend in a second terminal:

```sh
cd web
npm install
npm run dev
```

Open the Vite URL, usually <http://localhost:5173>. The dev server proxies `/api` to the local Java process. Use Ctrl-C in each terminal to stop its process.

## Deploy on Render

The live demo is deployed on Render from the `main` branch of [`BSDeeksha2005/Brood`](https://github.com/BSDeeksha2005/Brood). The repository includes a Render Blueprint and a multi-stage Dockerfile. Render builds the React app, packages it into the Java server, and serves the UI and API from one origin. Local development still uses the separate Vite and Java processes above. The deployed server binds to Render's `PORT` on `0.0.0.0`; outside Render it defaults to loopback for local safety.

Render deploys new commits pushed to `main`. To set up another instance, open the Render dashboard, choose **New → Blueprint**, connect `BSDeeksha2005/Brood`, and apply the `render.yaml` blueprint. The free web-service plan can sleep after inactivity, so the first visit after a quiet period may take 50 seconds or more to wake. Simulation worlds and Fork Time experiments are in memory and reset when the service restarts.

## Test and build

Simulation and language tests/build:

```sh
cd sim
./gradlew test build
```

API integration tests/build:

```sh
cd server
../sim/gradlew test build
```

Frontend typecheck and production build:

```sh
cd web
npm run typecheck
npm run build
```

## Repository layout

- `lang/` — standalone Java creature language.
- `sim/` — deterministic Java simulation and snapshot semantics.
- `server/` — local-only API and isolated branch sessions.
- `web/` — React, TypeScript, Vite, and Canvas renderer.
- `lab/` — reserved for future experiment tooling.

## Demo

Screenshot or short terrarium recording can be added here when one is prepared.

## Current limitations

- There is no reproduction, evolution, collision model, or species interaction.
- Food replenishes deterministically at most once every 8 ticks while fewer than three snacks remain.
- Fork experiments are in-memory, limited to eight at once, and disappear when the API process exits. The main world also resets on server restart.
- Fork Time supports snapshot branching and deterministic forward execution, but does not retain historical world snapshots for scrubbing or replay.
- Rule-execution history is included in snapshots and currently has no retention bound.
- The public Render demo is intentionally a bounded, single shared world: there are no user accounts or persistent sessions, and anyone with the URL can edit programs and advance the world. Tick counts, program sizes, rule counts, fork count, and branch advances have explicit limits.
- No cross-platform bit-for-bit guarantee is claimed.
