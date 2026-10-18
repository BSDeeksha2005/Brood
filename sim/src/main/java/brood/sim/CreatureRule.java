package brood.sim;

import java.util.Objects;

/** One data-driven condition/action pair understood by the first simulation slice. */
public record CreatureRule(String id, Condition condition, Action action) {
    public CreatureRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(action, "action");
        if (!id.matches("[a-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Rule IDs must use lowercase letters, digits, and hyphens");
        }
    }

    public enum Condition {
        LOW_ENERGY,
        FOOD_NEARBY
    }

    public enum Action {
        SEEK_FOOD,
        REST
    }
}
