package brood.server;

import brood.lang.CreatureLanguage;
import brood.lang.CreatureProgram;
import brood.sim.SimulationEngine;
import brood.sim.WorldSnapshot;
import brood.sim.WorldState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.NoSuchElementException;

/** Owns the one authoritative world for a local Brood server process. */
public final class SimulationSession {
    public static final long INITIAL_SEED = SimulationEngine.DEFAULT_WORLD_SEED;
    public static final int MAX_FORKS = 8;
    public static final int MAX_FORK_ADVANCE = 100;
    private final SimulationEngine engine = new SimulationEngine();
    private final CreatureLanguage language = new CreatureLanguage();
    private final long seed;
    private final Map<String, Fork> forks = new LinkedHashMap<>();
    private WorldState world;
    private long nextForkNumber = 1;

    public SimulationSession(long seed) {
        this.seed = seed;
        restart();
    }

    public SimulationSession() { this(INITIAL_SEED); }

    public synchronized WorldState snapshot() { return world; }

    public synchronized WorldState advance(int ticks) {
        if (ticks < 1 || ticks > 4) throw new IllegalArgumentException("Tick count must be between 1 and 4");
        for (int index = 0; index < ticks; index++) world = engine.step(world);
        return world;
    }

    /** Validates first, installs between ticks, then executes exactly one tick using the new program. */
    public synchronized WorldState applyProgram(long creatureId, String source) {
        CreatureProgram program = language.compile(source);
        WorldState changed = engine.applyProgram(world, creatureId, program);
        world = engine.step(changed);
        return world;
    }

    public synchronized WorldState restart() {
        world = engine.createWorld(seed);
        return world;
    }

    /** Captures a canonical snapshot and restores two separate world graphs from its serialized form. */
    public synchronized ForkView createFork() {
        if (forks.size() >= MAX_FORKS) throw new IllegalArgumentException("At most " + MAX_FORKS + " experiments may be kept at once");
        String serialized = WorldSnapshot.capture(world).serialize();
        WorldState original = WorldSnapshot.deserialize(serialized).restore();
        WorldState alternate = WorldSnapshot.deserialize(serialized).restore();
        String id = "fork-" + nextForkNumber++;
        Fork fork = new Fork(id, world.tick(), WorldSnapshot.capture(world).stateHash(), original, alternate);
        forks.put(id, fork);
        return fork.view();
    }

    public synchronized ForkView fork(String forkId) { return findFork(forkId).view(); }

    /** Installs a compiled program only on the alternate; validation completes before state is replaced. */
    public synchronized ForkView applyAlternateProgram(String forkId, long creatureId, String source) {
        Fork fork = findFork(forkId);
        CreatureProgram program = language.compile(source);
        WorldState changed = engine.applyProgram(fork.alternate, creatureId, program);
        fork.alternate = changed;
        return fork.view();
    }

    public synchronized ForkView advanceFork(String forkId, int ticks) {
        validateForkTicks(ticks);
        Fork fork = findFork(forkId);
        WorldState original = advance(fork.original, ticks);
        WorldState alternate = advance(fork.alternate, ticks);
        fork.original = original;
        fork.alternate = alternate;
        return fork.view();
    }

    public synchronized ForkView advanceForkBranch(String forkId, String branch, int ticks) {
        validateForkTicks(ticks);
        Fork fork = findFork(forkId);
        if (branch.equals("original")) fork.original = advance(fork.original, ticks);
        else if (branch.equals("alternate")) fork.alternate = advance(fork.alternate, ticks);
        else throw new IllegalArgumentException("Branch must be original or alternate");
        return fork.view();
    }

    public synchronized void deleteFork(String forkId) {
        if (forks.remove(forkId) == null) throw new NoSuchElementException("Unknown experiment: " + forkId);
    }

    private WorldState advance(WorldState state, int ticks) {
        WorldState advanced = state;
        for (int index = 0; index < ticks; index++) advanced = engine.step(advanced);
        return advanced;
    }

    private static void validateForkTicks(int ticks) {
        if (ticks < 1 || ticks > MAX_FORK_ADVANCE) {
            throw new IllegalArgumentException("Fork tick count must be between 1 and " + MAX_FORK_ADVANCE);
        }
    }

    private Fork findFork(String forkId) {
        Fork fork = forks.get(forkId);
        if (fork == null) throw new NoSuchElementException("Unknown experiment: " + forkId);
        return fork;
    }

    public CreatureLanguage language() { return Objects.requireNonNull(language); }

    public record ForkView(String id, long snapshotTick, String snapshotHash,
                           WorldState original, WorldState alternate) { }

    private static final class Fork {
        private final String id;
        private final long snapshotTick;
        private final String snapshotHash;
        private WorldState original;
        private WorldState alternate;

        private Fork(String id, long snapshotTick, String snapshotHash, WorldState original, WorldState alternate) {
            this.id = id;
            this.snapshotTick = snapshotTick;
            this.snapshotHash = snapshotHash;
            this.original = original;
            this.alternate = alternate;
        }

        private ForkView view() { return new ForkView(id, snapshotTick, snapshotHash, original, alternate); }
    }
}
