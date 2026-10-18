package brood.sim;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete immutable authoritative state needed to continue the simulation. */
public record WorldState(long seed, long randomState, long tick, long nextEntityId,
                         List<Creature> creatures, List<Food> food,
                         List<RuleExecutionEvent> events) {
    public WorldState {
        Objects.requireNonNull(creatures, "creatures");
        Objects.requireNonNull(food, "food");
        Objects.requireNonNull(events, "events");
        if (tick < 0) throw new IllegalArgumentException("Tick must not be negative");
        if (nextEntityId <= 0) throw new IllegalArgumentException("Next entity ID must be positive");
        creatures = List.copyOf(creatures);
        food = List.copyOf(food);
        events = List.copyOf(events);

        Set<Long> entityIds = new HashSet<>();
        long maximumId = 0;
        long previousId = 0;
        for (Creature creature : creatures) {
            Objects.requireNonNull(creature, "creature");
            if (creature.id() <= previousId) throw new IllegalArgumentException("Creatures must be ordered by increasing ID");
            previousId = creature.id();
            if (!entityIds.add(creature.id())) throw new IllegalArgumentException("Duplicate entity ID: " + creature.id());
            maximumId = Math.max(maximumId, creature.id());
        }
        previousId = 0;
        for (Food item : food) {
            Objects.requireNonNull(item, "food item");
            if (item.id() <= previousId) throw new IllegalArgumentException("Food must be ordered by increasing ID");
            previousId = item.id();
            if (!entityIds.add(item.id())) throw new IllegalArgumentException("Duplicate entity ID: " + item.id());
            maximumId = Math.max(maximumId, item.id());
        }
        if (nextEntityId <= maximumId) throw new IllegalArgumentException("Next entity ID must exceed all allocated IDs");

        long priorEventTick = 0;
        long priorCreatureId = 0;
        for (RuleExecutionEvent event : events) {
            Objects.requireNonNull(event, "event");
            if (event.tick() > tick) throw new IllegalArgumentException("Event cannot be from a future tick");
            if (event.tick() < priorEventTick
                    || (event.tick() == priorEventTick && event.creatureId() < priorCreatureId)) {
                throw new IllegalArgumentException("Events must be ordered by tick and creature ID");
            }
            priorEventTick = event.tick();
            priorCreatureId = event.creatureId();

            Creature eventCreature = null;
            for (Creature creature : creatures) {
                if (creature.id() == event.creatureId()) {
                    eventCreature = creature;
                    break;
                }
            }
            if (eventCreature == null) throw new IllegalArgumentException("Event references an unknown creature");
            boolean knownRule = false;
            for (CreatureRule rule : eventCreature.rules()) {
                if (rule.id().equals(event.ruleId())) {
                    knownRule = true;
                    break;
                }
            }
            if (!knownRule) throw new IllegalArgumentException("Event references an unknown creature rule");
            if (event.targetFoodId() >= nextEntityId) throw new IllegalArgumentException("Event target exceeds the entity ID counter");
        }
    }
}
