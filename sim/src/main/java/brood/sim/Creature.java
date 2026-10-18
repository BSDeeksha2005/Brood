package brood.sim;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable creature state. Coordinates use SimulationEngine.COORDINATE_SCALE units. */
public record Creature(long id, String name, long x, long y, int energy, int generation,
                       List<CreatureRule> rules) {
    public Creature {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rules, "rules");
        if (id <= 0) throw new IllegalArgumentException("Creature ID must be positive");
        if (name.isBlank()) throw new IllegalArgumentException("Creature name must not be blank");
        validateCoordinate(x, "x");
        validateCoordinate(y, "y");
        if (energy < 0 || energy > SimulationEngine.MAX_ENERGY) {
            throw new IllegalArgumentException("Creature energy is outside the supported range");
        }
        if (generation < 1) throw new IllegalArgumentException("Generation must be positive");
        rules = List.copyOf(rules);
        Set<String> ruleIds = new HashSet<>();
        for (CreatureRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            if (!ruleIds.add(rule.id())) throw new IllegalArgumentException("Duplicate rule ID: " + rule.id());
        }
    }

    private static void validateCoordinate(long value, String name) {
        if (value < 0 || value > SimulationEngine.COORDINATE_SCALE) {
            throw new IllegalArgumentException("Creature " + name + " coordinate is outside the world");
        }
    }
}
