package brood.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import brood.lang.CompileException;
import brood.sim.WorldSnapshot;
import org.junit.jupiter.api.Test;

class SimulationSessionForkTest {
    @Test
    void restoredBranchesStartEquivalentAndRemainIndependent() {
        SimulationSession session = new SimulationSession();
        SimulationSession.ForkView fork = session.createFork();
        String originalStart = hash(fork.original());
        assertEquals(originalStart, hash(fork.alternate()));
        assertEquals(fork.original(), fork.alternate());
        assertNotSame(fork.original(), fork.alternate());
        for (int index = 0; index < fork.original().creatures().size(); index++) {
            assertNotSame(fork.original().creatures().get(index), fork.alternate().creatures().get(index));
        }

        SimulationSession.ForkView oneAdvanced = session.advanceForkBranch(fork.id(), "alternate", 1);
        assertEquals(originalStart, hash(oneAdvanced.original()));
        assertNotEquals(originalStart, hash(oneAdvanced.alternate()));

        SimulationSession.ForkView bothAdvanced = session.advanceFork(fork.id(), 1);
        assertEquals(1, bothAdvanced.original().tick());
        assertEquals(2, bothAdvanced.alternate().tick());
        assertNotEquals(hash(bothAdvanced.original()), hash(bothAdvanced.alternate()));
    }

    @Test
    void alternateProgramIsIsolatedInvalidProgramsAreAtomicAndDifferenceIsEngineDerived() {
        SimulationSession session = new SimulationSession();
        SimulationSession.ForkView fork = session.createFork();
        var changed = session.applyAlternateProgram(fork.id(), 1, "otherwise: rest");
        String originalHash = hash(changed.original());
        String editedAlternateHash = hash(changed.alternate());

        assertEquals(hash(session.snapshot()), originalHash);
        assertNotEquals(originalHash, editedAlternateHash);
        assertThrows(CompileException.class,
                () -> session.applyAlternateProgram(fork.id(), 1, "otherwise: jump"));
        SimulationSession.ForkView afterRejected = session.fork(fork.id());
        assertEquals(originalHash, hash(afterRejected.original()));
        assertEquals(editedAlternateHash, hash(afterRejected.alternate()));

        SimulationSession.ForkView advanced = session.advanceFork(fork.id(), 1);
        assertNotEquals(advanced.original().creatures().getFirst().energy(),
                advanced.alternate().creatures().getFirst().energy());
        assertEquals(originalHash, hash(session.snapshot()));
    }

    @Test
    void identicalInputsKeepBranchesEquivalentAndMainRestartDoesNotEraseExperiment() {
        SimulationSession session = new SimulationSession();
        SimulationSession.ForkView fork = session.createFork();
        SimulationSession.ForkView advanced = session.advanceFork(fork.id(), 12);
        assertEquals(hash(advanced.original()), hash(advanced.alternate()));

        session.restart();
        assertEquals(0, session.snapshot().tick());
        assertEquals(hash(advanced.original()), hash(session.fork(fork.id()).original()));
    }

    private static String hash(brood.sim.WorldState state) {
        return WorldSnapshot.capture(state).stateHash();
    }
}
