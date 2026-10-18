# Brood Simulation Semantics — Milestone 1

This document defines the behaviour of the first Java simulation core. The core is framework-independent and does not read the clock, render graphics, or depend on the web fixture.

## State model

A `WorldState` is an immutable value containing:

- The original `seed` and current SplitMix64 `randomState`.
- The integer logical `tick` and next global `entityId` to allocate.
- Creatures and available food, each stored in strictly increasing entity-ID order.
- The ordered history of actual rule-execution events.

A `Creature` contains an ID, species/name, fixed-point position, energy, generation, and its ordered rule list. A `Food` contains an ID and fixed-point position. Rules and events are typed records. Constructors defensively copy collections and reject invalid ranges, duplicate IDs, or unstable entity order.

Coordinates use an integer scale of 1,000,000 units per world width/height. Energy uses milli-energy units from 0 through 100,000 (100 energy). Distances are squared integer distances; no floating-point arithmetic is used in authoritative state transitions.

## Tick execution order

`SimulationEngine.step(state)` performs exactly one logical tick and returns a new state. It has no elapsed-time or speed parameter.

1. Set the new tick to the old tick plus one.
2. Copy the available food in ascending ID order.
3. Process creatures in ascending ID order. Earlier creatures in a tick may consume food before later creatures select a target.
4. Deduct 250 energy units, clamped at zero.
5. Find the nearest available food whose squared distance is within the 300,000-unit sensing radius. Equal distances choose the lower food ID.
6. Evaluate rule conditions using the post-drain energy and nearest sensed food.
7. If a rule matches, append its event for the new tick and execute its action.
8. Store the updated creature, food list, event history, and tick in a new `WorldState`.

The first creature has ID 1; the initial foods receive IDs 2, 3, and 4. `nextEntityId` is 5. IDs are global across entity kinds and are never derived from object identity.

## Rules and precedence

The starter creature has two rules:

- `low-energy-seek`: condition `LOW_ENERGY`, action `SEEK_FOOD`.
- `nearby-food-seek`: condition `FOOD_NEARBY`, action `SEEK_FOOD`.

Precedence is semantic, not based on the order of the rule list: `LOW_ENERGY` is checked first when energy is at or below 25,000; `FOOD_NEARBY` is checked second when a food target is within sensing radius. Within one condition, the first matching rule in the creature's stored rule order wins. This guarantees low energy cannot be masked by a food rule.

A matched rule produces a `RuleExecutionEvent` with its rule ID, tick, creature ID, condition/action, selected food ID (zero means no target), pre-movement squared distance (minus one means no target), and energy at decision time. No event is produced when no rule matches.

The supported actions are `SEEK_FOOD` and `REST`. `SEEK_FOOD` follows the nearest sensed food. `REST` holds position and adds 500 energy units after the per-tick drain. If no rule matches, the creature holds position and only loses energy.

## Movement and food consumption

A seeking creature moves at most 15,000 coordinate units per tick toward its target. Integer square root and integer division determine movement. If the target is within the 10,000-unit consumption radius after movement, the creature snaps to its target, that food ID is removed exactly once, and energy increases by 15,000 units, capped at 100,000. A consumed food is unavailable to later creatures in the same tick.

## Randomness

Initial food positions use an immutable SplitMix64 generator. Its `randomState` is the input state for the next draw and is included in `WorldState` and snapshots. Bounded draws use unsigned remainder; this is deterministic but has a small modulo bias. No random draws occur during the current tick transition after initial-world creation. Rendering effects and the frontend fixture are outside this engine and cannot change the RNG state.

## Snapshot and versioning

`WorldSnapshot.capture` captures the complete immutable `WorldState`; `restore` returns that same value safely because all nested state is immutable. Snapshot version 1 is a newline-terminated tab-separated format with explicit record tags and field order. Text fields are URL-safe Base64 without padding. Entity and event list order is validated, malformed counts/fields/ranges are rejected, unsupported versions fail, and non-canonical encodings are rejected.

The snapshot includes seed, RNG state, tick, next ID, all creature fields and ordered rules, available food, and the complete rule event history. It excludes renderer time, visual offsets, selection, playback state, and wall-clock time.

## Canonical hashing

`WorldSnapshot.stateHash()` computes lowercase hexadecimal SHA-256 over the UTF-8 bytes of the canonical versioned serialization. It does not use `Object.hashCode`, collection hash order, pointer identity, or rendered pixels. Changing any serialized simulation field changes the hash.

## Known limitations

- This milestone has one species and a small condition/action dispatcher, not the custom rule language or a general interpreter.
- Initial population and food count are fixed; there is no reproduction, food regrowth, collision, or multi-species interaction.
- The engine API has no entity-spawn operation yet, although the next global ID is stored for future deterministic allocation.
- Rule events are retained without a bounded history policy.
- Bounded SplitMix64 draws use modulo reduction and are reproducible but not perfectly uniform.
- No cross-platform bit-for-bit claim is made; tests in this milestone verify deterministic replay on the configured Java toolchain.
