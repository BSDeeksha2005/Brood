package brood.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {
    private final SimulationEngine engine = new SimulationEngine();

    @Test
    void oneStepAdvancesExactlyOneTick() {
        WorldState next = engine.step(engine.createWorld(71));
        assertEquals(1, next.tick());
    }

    @Test
    void identicalSeedsProduceIdenticalInitialStates() {
        assertEquals(engine.createWorld(2026), engine.createWorld(2026));
        assertNotEquals(engine.createWorld(2026), engine.createWorld(2027));
    }

    @Test
    void identicalRunsHaveTheSameHashAfterEveryTick() {
        WorldState first = engine.createWorld(404);
        WorldState second = engine.createWorld(404);
        assertEquals(WorldSnapshot.capture(first).stateHash(), WorldSnapshot.capture(second).stateHash());
        for (int tick = 0; tick < 300; tick++) {
            first = engine.step(first);
            second = engine.step(second);
            assertEquals(tick + 1, first.tick());
            assertEquals(WorldSnapshot.capture(first).stateHash(), WorldSnapshot.capture(second).stateHash(),
                    "runs diverged after tick " + first.tick());
        }
    }

    @Test
    void equidistantFoodUsesTheLowerStableId() {
        Creature creature = creature(1, 80_000, engine.defaultRules());
        WorldState state = world(creature, List.of(new Food(2, 600_000, 500_000), new Food(3, 400_000, 500_000)), 4);
        WorldState next = engine.step(state);
        RuleExecutionEvent event = next.events().getFirst();
        assertEquals(2, event.targetFoodId());
        assertTrue(next.creatures().getFirst().x() > creature.x());
    }

    @Test
    void creatureMovesTowardAndConsumesItsTargetOnlyOnce() {
        Creature creature = creature(1, 80_000, engine.defaultRules());
        WorldState initial = world(creature, List.of(new Food(2, 525_000, 500_000)), 3);
        WorldState afterEating = engine.step(initial);
        assertTrue(afterEating.food().isEmpty());
        assertEquals(525_000, afterEating.creatures().getFirst().x());
        assertEquals(94_750, afterEating.creatures().getFirst().energy());
        assertEquals(2, afterEating.events().getFirst().targetFoodId());

        WorldState next = engine.step(afterEating);
        assertTrue(next.food().isEmpty());
        assertEquals(94_500, next.creatures().getFirst().energy());
    }

    @Test
    void energyDrainsEachTickAndFoodAddsDocumentedEnergy() {
        Creature creature = creature(1, 50_000, engine.defaultRules());
        WorldState noFood = world(creature, List.of(), 2);
        assertEquals(49_750, engine.step(noFood).creatures().getFirst().energy());

        WorldState withFood = world(creature, List.of(new Food(2, 525_000, 500_000)), 3);
        assertEquals(64_750, engine.step(withFood).creatures().getFirst().energy());
    }

    @Test
    void lowEnergyRuleWinsEvenWhenFoodRuleAppearsFirst() {
        List<CreatureRule> reversedRules = List.of(
                new CreatureRule("food-first", CreatureRule.Condition.FOOD_NEARBY, CreatureRule.Action.REST),
                new CreatureRule("energy-first", CreatureRule.Condition.LOW_ENERGY, CreatureRule.Action.SEEK_FOOD));
        Creature creature = creature(1, 25_000, reversedRules);
        WorldState state = world(creature, List.of(new Food(2, 520_000, 500_000)), 3);
        WorldState next = engine.step(state);
        RuleExecutionEvent event = next.events().getFirst();

        assertEquals("energy-first", event.ruleId());
        assertEquals(CreatureRule.Condition.LOW_ENERGY, event.condition());
        assertEquals(2, event.targetFoodId());
        assertTrue(next.creatures().getFirst().x() > creature.x());
    }

    @Test
    void snapshotSerializationAndRestorePreserveEveryStateField() {
        WorldState state = engine.createWorld(-904);
        for (int tick = 0; tick < 40; tick++) state = engine.step(state);
        WorldSnapshot snapshot = WorldSnapshot.capture(state);
        WorldSnapshot restored = WorldSnapshot.deserialize(snapshot.serialize());

        assertEquals(state, restored.restore());
        assertEquals(snapshot.serialize(), restored.serialize());
        assertEquals(snapshot.stateHash(), restored.stateHash());
    }

    @Test
    void restoringAtNAndRunningMMatchesAnUninterruptedRun() {
        WorldState uninterrupted = engine.createWorld(818);
        WorldState interrupted = engine.createWorld(818);
        for (int tick = 0; tick < 120; tick++) {
            uninterrupted = engine.step(uninterrupted);
            interrupted = engine.step(interrupted);
        }
        WorldState restored = WorldSnapshot.deserialize(WorldSnapshot.capture(interrupted).serialize()).restore();
        for (int tick = 0; tick < 180; tick++) {
            uninterrupted = engine.step(uninterrupted);
            restored = engine.step(restored);
        }
        assertEquals(300, restored.tick());
        assertEquals(WorldSnapshot.capture(uninterrupted).stateHash(), WorldSnapshot.capture(restored).stateHash());
    }

    @Test
    void malformedOrIncompatibleSnapshotsAreRejected() {
        WorldSnapshot snapshot = WorldSnapshot.capture(engine.createWorld(54));
        String serialized = snapshot.serialize();
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize("BROOD_SNAPSHOT\t999\n"));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize(serialized.replace("\t1\nWORLD", "\t2\nWORLD")));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize(serialized.substring(0, serialized.length() - 8)));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize(serialized.replaceFirst("F\\t2\\t", "F\t1\t")));
    }

    private Creature creature(long id, int energy, List<CreatureRule> rules) {
        return new Creature(id, "Mossling", 500_000, 500_000, energy, 1, rules);
    }

    private WorldState world(Creature creature, List<Food> food, long nextId) {
        return new WorldState(1, 99, 0, nextId, List.of(creature), food, List.of());
    }
}
