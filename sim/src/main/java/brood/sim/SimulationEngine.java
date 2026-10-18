package brood.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure fixed-tick transition for the first single-species simulation. */
public final class SimulationEngine {
    public static final long COORDINATE_SCALE = 1_000_000L;
    public static final int MAX_ENERGY = 100_000;
    public static final int LOW_ENERGY_THRESHOLD = 25_000;
    public static final int ENERGY_LOSS_PER_TICK = 250;
    public static final int ENERGY_GAIN_PER_FOOD = 15_000;
    public static final int REST_GAIN_PER_TICK = 500;
    public static final long SENSING_RADIUS = 300_000L;
    public static final long CONSUMPTION_RADIUS = 10_000L;
    public static final long MOVEMENT_PER_TICK = 15_000L;
    private static final long SENSING_RADIUS_SQUARED = SENSING_RADIUS * SENSING_RADIUS;
    private static final long CONSUMPTION_RADIUS_SQUARED = CONSUMPTION_RADIUS * CONSUMPTION_RADIUS;

    public WorldState createWorld(long seed) {
        SeededRandom random = new SeededRandom(seed);
        long nextId = 1;
        Creature creature = new Creature(nextId++, "Mossling", 500_000, 500_000, MAX_ENERGY, 1, defaultRules());
        List<Food> food = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            SeededRandom.BoundedDraw xDraw = random.nextInt(500_001);
            random = xDraw.next();
            SeededRandom.BoundedDraw yDraw = random.nextInt(500_001);
            random = yDraw.next();
            food.add(new Food(nextId++, 250_000 + xDraw.value(), 250_000 + yDraw.value()));
        }
        return new WorldState(seed, random.state(), 0, nextId, List.of(creature), food, List.of());
    }

    public List<CreatureRule> defaultRules() {
        return List.of(
                new CreatureRule("low-energy-seek", CreatureRule.Condition.LOW_ENERGY, CreatureRule.Action.SEEK_FOOD),
                new CreatureRule("nearby-food-seek", CreatureRule.Condition.FOOD_NEARBY, CreatureRule.Action.SEEK_FOOD));
    }

    /** Advances exactly one logical tick; no wall clock or rendering values are accepted. */
    public WorldState step(WorldState state) {
        Objects.requireNonNull(state, "state");
        long nextTick = Math.addExact(state.tick(), 1);
        List<Food> availableFood = new ArrayList<>(state.food());
        List<Creature> nextCreatures = new ArrayList<>(state.creatures().size());
        List<RuleExecutionEvent> nextEvents = new ArrayList<>(state.events());

        for (Creature creature : state.creatures()) {
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

        return new WorldState(state.seed(), state.randomState(), nextTick, state.nextEntityId(),
                nextCreatures, availableFood, nextEvents);
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
}
