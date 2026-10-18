# Brood Simulation Semantics — Milestones 1–4

This document defines the behaviour of the first Java simulation core. The core is framework-independent and does not read the clock, render graphics, or depend on the web fixture.

## State model

A `WorldState` is an immutable value containing:

- The original `seed` and current SplitMix64 `randomState`.
- The integer logical `tick` and next global `entityId` to allocate.
- Creatures and available food, each stored in strictly increasing entity-ID order.
- The ordered history of actual rule-execution events.

A `Creature` contains an ID, species/name, fixed-point position, energy, generation, and its ordered rules. It may also contain a canonical creature-language program source. Programmed creatures execute the program in place of the legacy starter-rule dispatcher. A `Food` contains an ID and fixed-point position. Rules and events are typed records. Constructors defensively copy collections and reject invalid ranges, duplicate IDs, or unstable entity order.

Coordinates use an integer scale of 1,000,000 units per world width/height. Energy uses milli-energy units from 0 through 100,000 (100 energy). Distances are squared integer distances; no floating-point arithmetic is used in authoritative state transitions.

## Tick execution order

`SimulationEngine.step(state)` performs exactly one logical tick and returns a new state. It has no elapsed-time or speed parameter.

1. Set the new tick to the old tick plus one.
2. Copy the available food in ascending ID order.
3. Process creatures in ascending ID order. Earlier creatures in a tick may consume food before later creatures select a target.
4. Deduct 250 energy units, clamped at zero.
5. Find the nearest available food whose squared distance is within the 300,000-unit sensing radius. Equal distances choose the lower food ID.
6. Evaluate program rules in source order using post-drain energy and nearest sensed food, or apply the legacy rule precedence below.
7. If a rule matches, append its event for the new tick and execute its action.
8. On ticks divisible by 8, if fewer than three food particles remain, draw one deterministic food position inside the 100,000–900,000 coordinate bounds and allocate the next global entity ID. Food regrowth runs after all creatures have acted, so new food cannot be eaten on its spawn tick.
9. Store the updated creature, food list, RNG state, ID counter, event history, and tick in a new `WorldState`.

`SimulationEngine.DEFAULT_WORLD_SEED` and the local server default are 2026. `createWorld(seed)` initializes six creatures in stable order with IDs 1 through 6: Mallow at (180,000, 300,000) with 82,000 energy; Pip at (420,000, 620,000) with 67,000; Lumen at (690,000, 310,000) with 91,000; Nori at (820,000, 700,000) with 74,000; Peach at (310,000, 820,000) with 58,000; and Thimble at (570,000, 460,000) with 88,000. Each starts at generation 1 with its own validated program: Mallow rests below 25,000 energy, seeks food within 300,000 units, otherwise wanders; Pip rests below 40,000 energy, otherwise wanders; Lumen seeks food within 300,000 units, otherwise wanders; Nori rests below 75,000 energy, then seeks food within 300,000 units, otherwise wanders; Peach seeks food within 150,000 units, otherwise wanders; Thimble rests below 50,000 energy, seeks food within 300,000 units, otherwise rests. Rule source and per-creature state are snapshot/hash state. Food receives IDs 7, 8, and 9; its positions come from the seeded generator. `nextEntityId` is 10. Creature placement does not consume random draws. IDs are global across entity kinds and are never derived from object identity.

Names, positions, and energy are simulation state. The existing UI adapter maps these six stable names to its three established visual kinds (Mallow/Nori moss, Pip/Peach coral, Lumen/Thimble lilac); visual kind is not a species or behavioral mechanic in the Java core.

## Rules and precedence

The legacy starter creature has two rules:

- `low-energy-seek`: condition `LOW_ENERGY`, action `SEEK_FOOD`.
- `nearby-food-seek`: condition `FOOD_NEARBY`, action `SEEK_FOOD`.

Precedence is semantic, not based on the order of the rule list: `LOW_ENERGY` is checked first when energy is at or below 25,000; `FOOD_NEARBY` is checked second when a food target is within sensing radius. Within one condition, the first matching rule in the creature's stored rule order wins. This guarantees low energy cannot be masked by a food rule.

A matched legacy rule produces a `RuleExecutionEvent` with its rule ID, tick, creature ID, condition/action, selected food ID (zero means no target), pre-movement squared distance (minus one means no target), and energy at decision time. Program rule IDs are `rule-1`, `rule-2`, … in source order; their events contain the selected action, nearest sensed food data, post-drain energy, and the canonical source of the program that executed. No event is produced when no rule matches.

`SimulationEngine.applyProgram(state, creatureId, program)` installs a validated immutable program between ticks. It does not advance the clock, alter RNG state, or discard prior events. The new program takes effect on the next `step()`. Events retain the program source that produced them, so later edits do not rewrite historical decisions. A client that wants the result immediately applies the program and then calls `step()` once.

Legacy actions are `SEEK_FOOD` and `REST`. Program actions are `seek nearest_food`, `eat`, `rest`, and `wander`. `seek nearest_food` moves toward the nearest sensed food and consumes it if the post-move position is within consumption radius. `eat` consumes the nearest sensed food only if already within consumption radius; otherwise it holds position. Both add 15,000 energy only when the food ID is successfully removed. `rest` holds position and adds 500 energy after the per-tick drain. `wander` adds independently drawn integer X and Y offsets from [-15,000, 15,000] and clamps position to the world. It consumes exactly two SplitMix64 draws. If no food is sensed, `seek nearest_food` and `eat` hold position. No matching rule means hold position and only lose energy.

## Creature language

The grammar is one rule per line: `when <sensor> <comparison> <integer>: <action>` or `otherwise: <action>`. Sensors are `energy` and `nearest_food`; comparisons are `<`, `<=`, `>`, and `>=`. Actions are `seek nearest_food`, `eat`, `rest`, and `wander`. `#` and `//` start comments through end of line. At least one rule is required; a single optional `otherwise` must be last. The first matching rule wins. Missing nearest food never satisfies a distance condition. Distance is integer square root (floor) of the nearest target's squared distance; only food within sensing radius is available to the condition.

Energy constants use milli-energy units (0–100,000); distance constants use coordinate units. Source is limited to 16,384 Java UTF-16 code units, programs to 64 rules, integer constants to 1,000,000, and energy constants to 100,000. Each tested rule costs one interpreter step and the selected action costs one more; the runtime budget is 128. Programs have no loops, functions, I/O, reflection, or mutable per-creature interpreter variables. The immutable typed AST is compiled before a program is attached to a creature. Diagnostics use one-based line and column positions.

## Movement and food consumption

A seeking creature moves at most 15,000 coordinate units per tick toward its target. Integer square root and integer division determine movement. If the target is within the 10,000-unit consumption radius after movement, the creature snaps to its target, that food ID is removed exactly once, and energy increases by 15,000 units, capped at 100,000. A consumed food is unavailable to later creatures in the same tick.

## Randomness

Initial food positions, program `wander` actions, and food regrowth use an immutable SplitMix64 generator. Regrowth adds at most one food particle every eight ticks and caps available food at three. Its `randomState` is the input state for the next draw and is included in `WorldState` and snapshots. Bounded draws use unsigned remainder; this is deterministic but has a small modulo bias. Rendering effects and the frontend fixture are outside this engine and cannot change the RNG state.

## Snapshot and versioning

`WorldSnapshot.capture` captures the complete immutable `WorldState`; `restore` returns that same value safely because all nested state is immutable. Snapshot version 3 is a newline-terminated tab-separated format with explicit record tags and field order. Text fields are URL-safe Base64 without padding. Each creature record contains its program source (empty means legacy rules) and ordered legacy rules. Program execution events contain their source program, allowing applied programs to change while keeping prior decisions verifiable. Versions 1 and 2 remain readable; version 1 has no program source and version 2 has no event program source. Entity and event list order is validated, malformed counts/fields/ranges are rejected, unsupported versions fail, and non-canonical encodings are rejected. There is no mutable per-creature interpreter state; RNG used by programs is part of world state.

The snapshot includes seed, RNG state, tick, next ID, all creature fields and ordered rules, available food, and the complete rule event history. It excludes renderer time, visual offsets, selection, playback state, and wall-clock time.

## Canonical hashing

`WorldSnapshot.stateHash()` computes lowercase hexadecimal SHA-256 over the UTF-8 bytes of the canonical versioned serialization. It does not use `Object.hashCode`, collection hash order, pointer identity, or rendered pixels. Changing any serialized simulation field changes the hash.

## Known limitations

- The language is intentionally limited to comparisons over energy and nearest-food distance with four actions; there is no general-purpose computation.
- Initial population of six and maximum available food count of three are fixed; there is no reproduction, collision, or multi-species interaction.
- The engine API has no entity-spawn operation yet, although the next global ID is stored for future deterministic allocation.
- Rule events are retained without a bounded history policy.
- Bounded SplitMix64 draws use modulo reduction and are reproducible but not perfectly uniform.
- No cross-platform bit-for-bit claim is made; tests in this milestone verify deterministic replay on the configured Java toolchain.
