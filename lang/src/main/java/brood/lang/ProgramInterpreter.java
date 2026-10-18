package brood.lang;

import brood.lang.CreatureProgram.Action;
import brood.lang.CreatureProgram.Always;
import brood.lang.CreatureProgram.Comparison;
import brood.lang.CreatureProgram.Rule;
import brood.lang.CreatureProgram.Sensor;
import java.util.Objects;

/** Deterministic bounded interpreter. Conditions and the selected action each cost one step. */
public final class ProgramInterpreter {
    public static final int DEFAULT_STEP_BUDGET = 128;

    public Execution execute(CreatureProgram program, int energy, Long nearestFoodDistance, int stepBudget) {
        Objects.requireNonNull(program, "program");
        if (energy < 0 || energy > 100_000) throw new IllegalArgumentException("Energy is outside the supported range");
        if (nearestFoodDistance != null && nearestFoodDistance < 0) throw new IllegalArgumentException("Food distance must not be negative");
        if (stepBudget < 1) throw new IllegalArgumentException("Step budget must be positive");
        int steps = 0;
        for (Rule rule : program.rules()) {
            if (++steps > stepBudget) throw new StepBudgetExceededException(stepBudget);
            if (!matches(rule, energy, nearestFoodDistance)) continue;
            if (++steps > stepBudget) throw new StepBudgetExceededException(stepBudget);
            return new Execution(rule.id(), rule.condition(), rule.action(), steps);
        }
        return new Execution(null, null, null, steps);
    }

    private static boolean matches(Rule rule, int energy, Long distance) {
        if (rule.condition() instanceof Always) return true;
        Comparison comparison = (Comparison) rule.condition();
        long actual;
        if (comparison.sensor() == Sensor.ENERGY) actual = energy;
        else {
            if (distance == null) return false;
            actual = distance;
        }
        return switch (comparison.operator()) {
            case LESS -> actual < comparison.value();
            case LESS_EQUAL -> actual <= comparison.value();
            case GREATER -> actual > comparison.value();
            case GREATER_EQUAL -> actual >= comparison.value();
        };
    }

    public record Execution(String ruleId, CreatureProgram.Condition condition, Action action, int steps) {
        public boolean matched() { return ruleId != null; }
    }

    public static final class StepBudgetExceededException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private StepBudgetExceededException(int budget) { super("Program exceeded its " + budget + " step budget"); }
    }
}
