package brood.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brood.lang.CreatureLanguage;
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
        WorldState first = engine.createWorld(2026);
        WorldState second = engine.createWorld(2026);
        assertEquals(first, second);
        assertEquals(WorldSnapshot.capture(first).stateHash(), WorldSnapshot.capture(second).stateHash());
        assertNotEquals(engine.createWorld(2026), engine.createWorld(2027));
    }

    @Test
    void initialWorldContainsTheSixStableCreaturesAndPrograms() {
        WorldState world = engine.createWorld(SimulationEngine.DEFAULT_WORLD_SEED);
        assertEquals(List.of("Mallow", "Pip", "Lumen", "Nori", "Peach", "Thimble"),
                world.creatures().stream().map(Creature::name).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L),
                world.creatures().stream().map(Creature::id).toList());
        assertTrue(world.creatures().stream().allMatch(creature -> creature.program() != null));
        assertEquals(6, world.creatures().stream().map(Creature::programSource).distinct().count());
        assertTrue(world.creatures().stream().allMatch(creature -> creature.energy() > 0));
        assertEquals(List.of(7L, 8L, 9L), world.food().stream().map(Food::id).toList());
        assertEquals(10L, world.nextEntityId());
    }

    @Test
    void applyingProgramChangesOnlyTheSelectedCreatureAndSnapshotKeepsAllSix() {
        WorldState initial = engine.createWorld(SimulationEngine.DEFAULT_WORLD_SEED);
        var alternate = new CreatureLanguage().compile("otherwise: rest");
        WorldState edited = engine.applyProgram(initial, 3, alternate);

        assertEquals(initial.creatures().get(0), edited.creatures().get(0));
        assertEquals(alternate.source(), edited.creatures().get(2).programSource());
        for (int index : List.of(0, 1, 3, 4, 5)) {
            assertEquals(initial.creatures().get(index).programSource(), edited.creatures().get(index).programSource());
        }
        WorldState restored = WorldSnapshot.deserialize(WorldSnapshot.capture(edited).serialize()).restore();
        assertEquals(6, restored.creatures().size());
        assertEquals(WorldSnapshot.capture(edited).stateHash(), WorldSnapshot.capture(restored).stateHash());
    }

    @Test
    void creaturesKeepIndependentEnergyPositionAndProgramState() {
        WorldState initial = engine.createWorld(SimulationEngine.DEFAULT_WORLD_SEED);
        WorldState withoutFood = new WorldState(initial.seed(), initial.randomState(), initial.tick(),
                initial.nextEntityId(), initial.creatures(), List.of(), initial.events());
        var restProgram = new CreatureLanguage().compile("otherwise: rest");
        WorldState edited = engine.applyProgram(withoutFood, 3, restProgram);
        WorldState stepped = engine.step(edited);

        assertEquals(91_250, stepped.creatures().get(2).energy());
        assertEquals(initial.creatures().get(2).x(), stepped.creatures().get(2).x());
        assertEquals(initial.creatures().get(2).y(), stepped.creatures().get(2).y());
        List<Integer> expectedEnergy = List.of(81_750, 66_750, 91_250, 74_250, 57_750, 88_250);
        for (int index : List.of(0, 1, 3, 4, 5)) {
            assertEquals(expectedEnergy.get(index), stepped.creatures().get(index).energy());
            assertEquals(initial.creatures().get(index).programSource(), stepped.creatures().get(index).programSource());
        }
    }

    @Test
    void allSixInitialProgramsExecuteIndependentlyInStableCreatureOrder() {
        WorldState stepped = engine.step(engine.createWorld(SimulationEngine.DEFAULT_WORLD_SEED));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L),
                stepped.events().stream().map(RuleExecutionEvent::creatureId).toList());
        assertEquals(6, stepped.creatures().size());
        assertTrue(stepped.events().stream().map(RuleExecutionEvent::action).distinct().count() >= 2);
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
    void deterministicFoodRegrowthRestoresOneSnackAndAllocatesStableId() {
        WorldState initial = engine.createWorld(SimulationEngine.DEFAULT_WORLD_SEED);
        WorldState empty = new WorldState(initial.seed(), initial.randomState(), 0, initial.nextEntityId(),
                initial.creatures(), List.of(), initial.events());
        WorldState state = empty;
        for (int tick = 0; tick < SimulationEngine.FOOD_REGROW_INTERVAL_TICKS; tick++) {
            state = engine.step(state);
        }
        assertEquals(1, state.food().size());
        assertEquals(10, state.food().getFirst().id());
        assertEquals(11, state.nextEntityId());
        assertTrue(state.food().getFirst().x() >= 100_000 && state.food().getFirst().x() <= 900_000);
        assertTrue(state.food().getFirst().y() >= 100_000 && state.food().getFirst().y() <= 900_000);
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
    void versionOneSnapshotsRestoreAndUpgradeToTheCurrentCanonicalFormat() {
        WorldState original = world(creature(1, 80_000, engine.defaultRules()),
                List.of(new Food(2, 700_000, 700_000)), 3);
        String[] rows = WorldSnapshot.capture(original).serialize().split("\\n", -1);
        rows[0] = "BROOD_SNAPSHOT\t1";
        for (int index = 0; index < rows.length; index++) {
            if (rows[index].startsWith("C\t")) {
                rows[index] = rows[index].substring(0, rows[index].lastIndexOf('\t'));
            }
        }
        WorldState restored = WorldSnapshot.deserialize(String.join("\n", rows)).restore();
        assertEquals(original, restored);
        assertEquals(WorldSnapshot.CURRENT_VERSION,
                Integer.parseInt(WorldSnapshot.capture(restored).serialize().split("\\n", 2)[0].split("\\t")[1]));
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
        WorldSnapshot snapshot = WorldSnapshot.capture(world(creature(1, 80_000, engine.defaultRules()),
                List.of(new Food(2, 700_000, 700_000)), 3));
        String serialized = snapshot.serialize();
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize("BROOD_SNAPSHOT\t999\n"));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize(
                serialized.replace("\t" + WorldSnapshot.CURRENT_VERSION + "\nWORLD", "\t999\nWORLD")));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize(serialized.substring(0, serialized.length() - 8)));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.deserialize(serialized.replaceFirst("F\\t2\\t", "F\t1\t")));
    }

    @Test
    void programmedCreatureExecutesRulesAndSnapshotReplayIsDeterministic() {
        var program = new CreatureLanguage().compile("when energy < 25000: rest\nwhen nearest_food <= 300000: seek nearest_food\notherwise: wander");
        Creature creature = Creature.programmed(1, "Mossling", 500_000, 500_000, 30_000, 1, program);
        WorldState initial = world(creature, List.of(new Food(2, 525_000, 500_000)), 3);
        WorldState expected = engine.step(initial);
        assertEquals("rule-2", expected.events().getFirst().ruleId());
        assertEquals(CreatureRule.Action.SEEK_FOOD, expected.events().getFirst().action());
        assertEquals(44_750, expected.creatures().getFirst().energy());
        WorldState restored = WorldSnapshot.deserialize(WorldSnapshot.capture(expected).serialize()).restore();
        assertEquals(expected, restored);
        for (int tick = 0; tick < 30; tick++) {
            expected = engine.step(expected);
            restored = engine.step(restored);
            assertEquals(WorldSnapshot.capture(expected).stateHash(), WorldSnapshot.capture(restored).stateHash());
        }
    }

    @Test
    void programmedEatConsumesOnlyTheNearbyFoodAndWanderUsesSerializableRandomness() {
        var language = new CreatureLanguage();
        var eatProgram = language.compile("when nearest_food <= 10000: eat\notherwise: rest");
        Creature eater = Creature.programmed(1, "Mossling", 500_000, 500_000, 40_000, 1, eatProgram);
        WorldState eaten = engine.step(world(eater, List.of(new Food(2, 505_000, 500_000)), 3));
        assertTrue(eaten.food().isEmpty());
        assertEquals(CreatureRule.Action.EAT, eaten.events().getFirst().action());
        assertEquals(54_750, eaten.creatures().getFirst().energy());

        var wanderProgram = language.compile("otherwise: wander");
        Creature wanderer = Creature.programmed(1, "Mossling", 500_000, 500_000, 40_000, 1, wanderProgram);
        WorldState first = world(wanderer, List.of(), 2);
        WorldState second = world(wanderer, List.of(), 2);
        for (int tick = 0; tick < 100; tick++) {
            first = engine.step(first);
            second = engine.step(second);
            assertEquals(WorldSnapshot.capture(first).stateHash(), WorldSnapshot.capture(second).stateHash());
        }
    }

    @Test
    void applyingProgramBetweenTicksPreservesHistoryAndSnapshotReplay() {
        Creature initialCreature = creature(1, 40_000, engine.defaultRules());
        WorldState initial = world(initialCreature, List.of(new Food(2, 520_000, 500_000)), 3);
        WorldState beforeApply = engine.step(initial);
        var program = new CreatureLanguage().compile("otherwise: rest");
        WorldState edited = engine.applyProgram(beforeApply, 1, program);

        assertEquals(beforeApply.tick(), edited.tick());
        assertEquals(beforeApply.events(), edited.events());
        assertEquals(program.source(), edited.creatures().getFirst().programSource());

        WorldState afterApply = engine.step(edited);
        assertEquals("rule-1", afterApply.events().getLast().ruleId());
        assertEquals(program.source(), afterApply.events().getLast().programSource());
        WorldState restored = WorldSnapshot.deserialize(WorldSnapshot.capture(afterApply).serialize()).restore();
        assertEquals(afterApply, restored);
        assertEquals(WorldSnapshot.capture(afterApply).stateHash(), WorldSnapshot.capture(restored).stateHash());
    }

    private Creature creature(long id, int energy, List<CreatureRule> rules) {
        return new Creature(id, "Mossling", 500_000, 500_000, energy, 1, rules);
    }

    private WorldState world(Creature creature, List<Food> food, long nextId) {
        return new WorldState(1, 99, 0, nextId, List.of(creature), food, List.of());
    }
}
