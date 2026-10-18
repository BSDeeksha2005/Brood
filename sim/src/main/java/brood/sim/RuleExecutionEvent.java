package brood.sim;

import java.util.Objects;

/** A rule that actually matched during a tick, with the decision inputs used by the engine. */
public record RuleExecutionEvent(long tick, long creatureId, String ruleId,
                                 CreatureRule.Condition condition, CreatureRule.Action action,
                                 long targetFoodId, long targetDistanceSquared, int energyAtDecision) {
    public RuleExecutionEvent {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(action, "action");
        if (!ruleId.matches("[a-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Event rule IDs must use lowercase letters, digits, and hyphens");
        }
        if (tick <= 0 || creatureId <= 0) throw new IllegalArgumentException("Event tick and creature ID must be positive");
        if (targetFoodId < 0) throw new IllegalArgumentException("Target food ID cannot be negative");
        if (targetDistanceSquared < -1) throw new IllegalArgumentException("Target distance sentinel is invalid");
        if ((targetFoodId == 0) != (targetDistanceSquared == -1)) {
            throw new IllegalArgumentException("Target food ID and distance must use matching no-target sentinels");
        }
        long maximumDistanceSquared = 2 * SimulationEngine.COORDINATE_SCALE * SimulationEngine.COORDINATE_SCALE;
        if (targetDistanceSquared > maximumDistanceSquared) {
            throw new IllegalArgumentException("Target distance exceeds the world bounds");
        }
        if (energyAtDecision < 0 || energyAtDecision > SimulationEngine.MAX_ENERGY) {
            throw new IllegalArgumentException("Event energy is outside the supported range");
        }
    }
}
