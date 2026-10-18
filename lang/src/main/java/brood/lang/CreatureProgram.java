package brood.lang;

import java.util.List;
import java.util.Objects;

/** Immutable, validated executable program. Only the compiler can construct one. */
public final class CreatureProgram {
    private final String source;
    private final List<Rule> rules;

    CreatureProgram(String source, List<Rule> rules) {
        this.source = Objects.requireNonNull(source, "source");
        this.rules = List.copyOf(rules);
        if (this.rules.isEmpty() || this.rules.size() > CreatureLanguage.MAX_RULES) {
            throw new IllegalArgumentException("Program rule count is outside the supported range");
        }
        boolean otherwiseSeen = false;
        for (int index = 0; index < this.rules.size(); index++) {
            Rule rule = Objects.requireNonNull(this.rules.get(index), "rule");
            if (!rule.id().equals("rule-" + (index + 1))) {
                throw new IllegalArgumentException("Program rule IDs must match source order");
            }
            if (otherwiseSeen) throw new IllegalArgumentException("No rule may follow otherwise");
            if (rule.condition() instanceof Always) otherwiseSeen = true;
            if (rule.condition() instanceof Comparison comparison) {
                if (comparison.value() > CreatureLanguage.MAX_CONSTANT
                        || (comparison.sensor() == Sensor.ENERGY && comparison.value() > 100_000)) {
                    throw new IllegalArgumentException("Program comparison constant is outside the supported range");
                }
            }
        }
    }

    public String source() { return source; }
    public List<Rule> rules() { return rules; }

    public enum Sensor { ENERGY, NEAREST_FOOD }
    public enum Operator { LESS, LESS_EQUAL, GREATER, GREATER_EQUAL }
    public enum Action { SEEK_NEAREST_FOOD, EAT, REST, WANDER }

    public sealed interface Condition permits Comparison, Always { }
    public record Comparison(Sensor sensor, Operator operator, int value) implements Condition {
        public Comparison {
            Objects.requireNonNull(sensor, "sensor");
            Objects.requireNonNull(operator, "operator");
            if (value < 0) throw new IllegalArgumentException("Comparison value must not be negative");
        }
    }
    public record Always() implements Condition { }
    public record Rule(String id, Condition condition, Action action, int line) {
        public Rule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(action, "action");
            if (line < 1) throw new IllegalArgumentException("Rule line must be positive");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CreatureProgram program
                && source.equals(program.source) && rules.equals(program.rules);
    }

    @Override
    public int hashCode() { return Objects.hash(source, rules); }
}
