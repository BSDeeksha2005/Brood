package brood.sim;

import brood.lang.CreatureLanguage;
import brood.lang.CreatureProgram;
import brood.lang.ProgramInterpreter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure fixed-tick transition for Brood's deterministic starter ecosystem. */
public final class SimulationEngine {
    public static final long DEFAULT_WORLD_SEED = 2026L;
    public static final long COORDINATE_SCALE = 1_000_000L;
    public static final int MAX_ENERGY = 100_000;
    public static final int LOW_ENERGY_THRESHOLD = 25_000;
    public static final int ENERGY_LOSS_PER_TICK = 250;
    public static final int ENERGY_GAIN_PER_FOOD = 15_000;
    public static final int REST_GAIN_PER_TICK = 500;
    public static final int MAX_WORLD_FOOD = 3;
    public static final int FOOD_REGROW_INTERVAL_TICKS = 8;
    public static final long SENSING_RADIUS = 300_000L;
    public static final long CONSUMPTION_RADIUS = 10_000L;
    public static final long MOVEMENT_PER_TICK = 15_000L;
    private static final long SENSING_RADIUS_SQUARED = SENSING_RADIUS * SENSING_RADIUS;
    private static final long CONSUMPTION_RADIUS_SQUARED = CONSUMPTION_RADIUS * CONSUMPTION_RADIUS;
    private static final ProgramInterpreter INTERPRETER = new ProgramInterpreter();
    private static final InitialCreature[] INITIAL_CREATURES = {
            new InitialCreature("Mallow", 180_000, 300_000, 82_000,
                    "when energy <= 25000: rest\nwhen nearest_food <= 300000: seek nearest_food\notherwise: wander"),
            new InitialCreature("Pip", 420_000, 620_000, 67_000,
                    "when energy <= 40000: rest\notherwise: wander"),
            new InitialCreature("Lumen", 690_000, 310_000, 91_000,
                    "when nearest_food <= 300000: seek nearest_food\notherwise: wander"),
            new InitialCreature("Nori", 820_000, 700_000, 74_000,
                    "when energy <= 75000: rest\nwhen nearest_food <= 300000: seek nearest_food\notherwise: wander"),
            new InitialCreature("Peach", 310_000, 820_000, 58_000,
                    "when nearest_food <= 150000: seek nearest_food\notherwise: wander"),
            new InitialCreature("Thimble", 570_000, 460_000, 88_000,
                    "when energy <= 50000: rest\nwhen nearest_food <= 300000: seek nearest_food\notherwise: rest")
    };

    public WorldState createWorld(long seed) {
        SeededRandom random = new SeededRandom(seed);
        long nextId = 1;
        List<Creature> creatures = new ArrayList<>(INITIAL_CREATURES.length);
        for (InitialCreature initial : INITIAL_CREATURES) {
            var program = new CreatureLanguage().compile(initial.programSource());
            creatures.add(Creature.programmed(nextId++, initial.name(), initial.x(), initial.y(),
                    initial.energy(), 1, program));
        }
        List<Food> food = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            SeededRandom.BoundedDraw xDraw = random.nextInt(500_001);
            random = xDraw.next();
            SeededRandom.BoundedDraw yDraw = random.nextInt(500_001);
            random = yDraw.next();
            food.add(new Food(nextId++, 250_000 + xDraw.value(), 250_000 + yDraw.value()));
        }
        return new WorldState(seed, random.state(), 0, nextId, creatures, food, List.of());
    }

    public List<CreatureRule> defaultRules() {
        return List.of(
                new CreatureRule("low-energy-seek", CreatureRule.Condition.LOW_ENERGY, CreatureRule.Action.SEEK_FOOD),
                new CreatureRule("nearby-food-seek", CreatureRule.Condition.FOOD_NEARBY, CreatureRule.Action.SEEK_FOOD));
    }

    /** Installs a validated program between ticks while preserving the rest of the world and event history. */
    public WorldState applyProgram(WorldState state, long creatureId, CreatureProgram program) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(program, "program");
        List<Creature> creatures = new ArrayList<>(state.creatures());
        boolean found = false;
        for (int index = 0; index < creatures.size(); index++) {
            Creature creature = creatures.get(index);
            if (creature.id() != creatureId) continue;
            creatures.set(index, Creature.programmed(creature.id(), creature.name(), creature.x(), creature.y(),
                    creature.energy(), creature.generation(), program));
            found = true;
            break;
        }
        if (!found) throw new IllegalArgumentException("Unknown creature ID: " + creatureId);
        return new WorldState(state.seed(), state.randomState(), state.tick(), state.nextEntityId(),
                creatures, state.food(), state.events());
    }

    /** Advances exactly one logical tick; no wall clock or rendering values are accepted. */
    public WorldState step(WorldState state) {
        Objects.requireNonNull(state, "state");
        long nextTick = Math.addExact(state.tick(), 1);
        List<Food> availableFood = new ArrayList<>(state.food());
        List<Creature> nextCreatures = new ArrayList<>(state.creatures().size());
        List<RuleExecutionEvent> nextEvents = new ArrayList<>(state.events());
        SeededRandom random = new SeededRandom(state.randomState());

        for (Creature creature : state.creatures()) {
            if (creature.programSource() != null) {
                ProgramStep result = stepProgrammed(creature, availableFood, nextTick, nextEvents, random);
                nextCreatures.add(result.creature());
                random = result.random();
                continue;
            }
            int energyAtDecision = Math.max(0, creature.energy() - ENERGY_LOSS_PER_TICK);
            Target nearest = nearestFood(creature, availableFood);
            CreatureRule rule = matchingRule(creature.rules(), energyAtDecision, nearest != null);
            long targetFoodId = nearest == null ? 0 : nearest.food().id();
            long targetDistanceSquared = nearest == null ? -1 : nearest.distanceSquared();
            long nextX = creature.x();
            long nextY = creature.y();
            int nextEnergy = energyAtDecision;

            if (rule != null) {
                nextEvents.add(new RuleExecutionEvent(nextTick, creature.id(), rule.id(), rule.condition(), rule.action(),
                        targetFoodId, targetDistanceSquared, energyAtDecision));
                if (rule.action() == CreatureRule.Action.REST) {
                    nextEnergy = Math.min(MAX_ENERGY, nextEnergy + REST_GAIN_PER_TICK);
                } else if (rule.action() == CreatureRule.Action.SEEK_FOOD && nearest != null) {
                    long[] position = moveToward(creature.x(), creature.y(), nearest.food().x(), nearest.food().y());
                    nextX = position[0];
                    nextY = position[1];
                    if (distanceSquared(nextX, nextY, nearest.food().x(), nearest.food().y()) <= CONSUMPTION_RADIUS_SQUARED) {
                        nextX = nearest.food().x();
                        nextY = nearest.food().y();
                        nextEnergy = Math.min(MAX_ENERGY, nextEnergy + ENERGY_GAIN_PER_FOOD);
                        availableFood.removeIf(item -> item.id() == nearest.food().id());
                    }
                }
            }
            nextCreatures.add(new Creature(creature.id(), creature.name(), nextX, nextY, nextEnergy,
                    creature.generation(), creature.rules()));
        }

        long nextEntityId = state.nextEntityId();
        if (nextTick % FOOD_REGROW_INTERVAL_TICKS == 0 && availableFood.size() < MAX_WORLD_FOOD) {
            SeededRandom.BoundedDraw xDraw = random.nextInt(800_001);
            random = xDraw.next();
            SeededRandom.BoundedDraw yDraw = random.nextInt(800_001);
            random = yDraw.next();
            availableFood.add(new Food(nextEntityId++, 100_000 + xDraw.value(), 100_000 + yDraw.value()));
        }
        return new WorldState(state.seed(), random.state(), nextTick, nextEntityId,
                nextCreatures, availableFood, nextEvents);
    }

    private static ProgramStep stepProgrammed(Creature creature, List<Food> availableFood, long nextTick,
                                              List<RuleExecutionEvent> events, SeededRandom random) {
        int energy = Math.max(0, creature.energy() - ENERGY_LOSS_PER_TICK);
        Target nearest = nearestFood(creature, availableFood);
        Long distance = nearest == null ? null : integerSquareRoot(nearest.distanceSquared());
        CreatureProgram program = creature.program();
        ProgramInterpreter.Execution execution = INTERPRETER.execute(program, energy, distance,
                ProgramInterpreter.DEFAULT_STEP_BUDGET);
        long x = creature.x();
        long y = creature.y();
        if (execution.matched()) {
            CreatureRule.Action action = switch (execution.action()) {
                case SEEK_NEAREST_FOOD -> CreatureRule.Action.SEEK_FOOD;
                case EAT -> CreatureRule.Action.EAT;
                case REST -> CreatureRule.Action.REST;
                case WANDER -> CreatureRule.Action.WANDER;
            };
            long foodId = nearest == null ? 0 : nearest.food().id();
            long distanceSquared = nearest == null ? -1 : nearest.distanceSquared();
            events.add(new RuleExecutionEvent(nextTick, creature.id(), execution.ruleId(),
                    CreatureRule.Condition.PROGRAM, action, foodId, distanceSquared, energy, program.source()));
            switch (execution.action()) {
                case SEEK_NEAREST_FOOD -> {
                    if (nearest != null) {
                        long[] position = moveToward(x, y, nearest.food().x(), nearest.food().y());
                        x = position[0]; y = position[1];
                        if (distanceSquared(x, y, nearest.food().x(), nearest.food().y()) <= CONSUMPTION_RADIUS_SQUARED) {
                            x = nearest.food().x(); y = nearest.food().y();
                            energy = consume(energy, nearest.food().id(), availableFood);
                        }
                    }
                }
                case EAT -> {
                    if (nearest != null && nearest.distanceSquared() <= CONSUMPTION_RADIUS_SQUARED) {
                        x = nearest.food().x(); y = nearest.food().y();
                        energy = consume(energy, nearest.food().id(), availableFood);
                    }
                }
                case REST -> energy = Math.min(MAX_ENERGY, energy + REST_GAIN_PER_TICK);
                case WANDER -> {
                    SeededRandom.BoundedDraw dx = random.nextInt(2 * Math.toIntExact(MOVEMENT_PER_TICK) + 1);
                    SeededRandom.BoundedDraw dy = dx.next().nextInt(2 * Math.toIntExact(MOVEMENT_PER_TICK) + 1);
                    x = clamp(x + dx.value() - MOVEMENT_PER_TICK);
                    y = clamp(y + dy.value() - MOVEMENT_PER_TICK);
                    random = dy.next();
                }
            }
        }
        return new ProgramStep(new Creature(creature.id(), creature.name(), x, y, energy, creature.generation(),
                creature.rules(), creature.program()), random);
    }

    private static int consume(int energy, long foodId, List<Food> availableFood) {
        boolean removed = availableFood.removeIf(item -> item.id() == foodId);
        return removed ? Math.min(MAX_ENERGY, energy + ENERGY_GAIN_PER_FOOD) : energy;
    }

    private static CreatureRule matchingRule(List<CreatureRule> rules, int energy, boolean foodNearby) {
        // Energy takes precedence even if the food rule appears first in the creature's rule list.
        if (energy <= LOW_ENERGY_THRESHOLD) {
            CreatureRule lowEnergy = firstRule(rules, CreatureRule.Condition.LOW_ENERGY);
            if (lowEnergy != null) return lowEnergy;
        }
        if (foodNearby) return firstRule(rules, CreatureRule.Condition.FOOD_NEARBY);
        return null;
    }

    private static CreatureRule firstRule(List<CreatureRule> rules, CreatureRule.Condition condition) {
        for (CreatureRule rule : rules) {
            if (rule.condition() == condition) return rule;
        }
        return null;
    }

    private static Target nearestFood(Creature creature, List<Food> food) {
        Target nearest = null;
        for (Food item : food) {
            long distanceSquared = distanceSquared(creature.x(), creature.y(), item.x(), item.y());
            if (distanceSquared > SENSING_RADIUS_SQUARED) continue;
            if (nearest == null || distanceSquared < nearest.distanceSquared()
                    || (distanceSquared == nearest.distanceSquared() && item.id() < nearest.food().id())) {
                nearest = new Target(item, distanceSquared);
            }
        }
        return nearest;
    }

    private static long[] moveToward(long x, long y, long targetX, long targetY) {
        long dx = targetX - x;
        long dy = targetY - y;
        long distance = integerSquareRoot(distanceSquared(x, y, targetX, targetY));
        if (distance == 0) return new long[]{x, y};
        long step = Math.min(MOVEMENT_PER_TICK, distance);
        long moveX = dx * step / distance;
        long moveY = dy * step / distance;
        if (moveX == 0 && dx != 0) moveX = Long.signum(dx);
        if (moveY == 0 && dy != 0) moveY = Long.signum(dy);
        return new long[]{clamp(x + moveX), clamp(y + moveY)};
    }

    private static long distanceSquared(long x1, long y1, long x2, long y2) {
        long dx = x2 - x1;
        long dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    private static long integerSquareRoot(long value) {
        if (value < 0) throw new IllegalArgumentException("Square root input must not be negative");
        if (value < 2) return value;
        long estimate = value;
        long next = (estimate + 1) / 2;
        while (next < estimate) {
            estimate = next;
            next = (estimate + value / estimate) / 2;
        }
        return estimate;
    }

    private static long clamp(long coordinate) {
        return Math.max(0, Math.min(COORDINATE_SCALE, coordinate));
    }

    private record Target(Food food, long distanceSquared) { }
    private record ProgramStep(Creature creature, SeededRandom random) { }
    private record InitialCreature(String name, long x, long y, int energy, String programSource) { }
}
