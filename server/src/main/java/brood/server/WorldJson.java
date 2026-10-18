package brood.server;

import brood.lang.CreatureProgram;
import brood.sim.Creature;
import brood.sim.CreatureRule;
import brood.sim.Food;
import brood.sim.RuleExecutionEvent;
import brood.sim.SimulationEngine;
import brood.sim.WorldState;
import java.util.List;

/** Converts authoritative Java state into the small DTO consumed by the existing Canvas UI. */
final class WorldJson {
    private WorldJson() { }

    static String encode(WorldState world) {
        StringBuilder json = new StringBuilder(1024);
        json.append("{\"tick\":").append(world.tick()).append(",\"creatures\":[");
        for (int index = 0; index < world.creatures().size(); index++) {
            if (index > 0) json.append(',');
            appendCreature(json, world.creatures().get(index), world);
        }
        json.append("],\"food\":[");
        for (int index = 0; index < world.food().size(); index++) {
            if (index > 0) json.append(',');
            appendFood(json, world.food().get(index));
        }
        return json.append("]}").toString();
    }

    private static void appendCreature(StringBuilder json, Creature creature, WorldState world) {
        RuleExecutionEvent latest = null;
        for (int index = world.events().size() - 1; index >= 0; index--) {
            RuleExecutionEvent event = world.events().get(index);
            if (event.creatureId() == creature.id()) { latest = event; break; }
        }
        String goal = "Holding position";
        String lastRule = "No rule matched yet";
        String observation = "Waiting for its first rule decision.";
        if (latest != null) {
            lastRule = latest.ruleId() + " · " + actionName(latest.action());
            observation = switch (latest.action()) {
                case SEEK_FOOD -> latest.targetFoodId() == 0
                        ? creature.name() + " sensed no nearby snack when " + latest.ruleId() + " ran."
                        : creature.name() + " followed snack " + latest.targetFoodId() + " under " + latest.ruleId() + ".";
                case EAT -> latest.targetFoodId() == 0
                        ? creature.name() + " tried its eat rule, but no snack was in reach."
                        : creature.name() + " had snack " + latest.targetFoodId() + " in reach for its eat rule.";
                case REST -> creature.name() + " had " + (latest.energyAtDecision() / 1000) + "% energy when "
                        + latest.ruleId() + " chose rest.";
                case WANDER -> latest.targetFoodId() == 0
                        ? creature.name() + " sensed no snack nearby; " + latest.ruleId() + " sent them exploring."
                        : creature.name() + " sensed snack " + latest.targetFoodId() + ", but " + latest.ruleId() + " chose to explore.";
            };
            goal = switch (latest.action()) {
                case SEEK_FOOD -> latest.targetFoodId() == 0 ? "No food sensed" : "Seeking snack " + latest.targetFoodId();
                case EAT -> latest.targetFoodId() == 0 ? "No food in reach" : "Eating nearby food";
                case REST -> "Resting in a quiet patch";
                case WANDER -> "Wandering through the habitat";
            };
        }
        json.append("{\"id\":").append(quote(Long.toString(creature.id())))
                .append(",\"name\":").append(quote(creature.name()))
                .append(",\"kind\":").append(quote(kindName(creature.name())))
                .append(",\"x\":").append(decimal(creature.x()))
                .append(",\"y\":").append(decimal(creature.y()))
                .append(",\"phase\":").append(creature.id() * 2.17)
                .append(",\"energy\":").append(creature.energy() / 1000.0)
                .append(",\"generation\":").append(creature.generation())
                .append(",\"goal\":").append(quote(goal))
                .append(",\"lastRule\":").append(quote(lastRule))
                .append(",\"observation\":").append(quote(observation))
                .append(",\"programSource\":").append(quote(creature.programSource() == null ? "" : creature.programSource()))
                .append(",\"rules\":[");
        appendRules(json, creature);
        json.append("]}");
    }

    private static void appendRules(StringBuilder json, Creature creature) {
        if (creature.program() != null) {
            String[] lines = creature.program().source().split("\\n");
            List<CreatureProgram.Rule> rules = creature.program().rules();
            for (int index = 0; index < rules.size(); index++) {
                if (index > 0) json.append(',');
                CreatureProgram.Rule rule = rules.get(index);
                json.append("{\"id\":").append(quote(rule.id()))
                        .append(",\"source\":").append(quote(lines[index]))
                        .append(",\"condition\":").append(quote(conditionName(rule.condition())))
                        .append(",\"action\":").append(quote(actionName(rule.action()))).append('}');
            }
            return;
        }
        for (int index = 0; index < creature.rules().size(); index++) {
            if (index > 0) json.append(',');
            CreatureRule rule = creature.rules().get(index);
            json.append("{\"id\":").append(quote(rule.id()))
                    .append(",\"source\":").append(quote(rule.id() + ": " + actionName(rule.action())))
                    .append(",\"condition\":").append(quote(rule.condition().name().toLowerCase()))
                    .append(",\"action\":").append(quote(actionName(rule.action()))).append('}');
        }
    }

    private static String conditionName(CreatureProgram.Condition condition) {
        if (condition instanceof CreatureProgram.Always) return "otherwise";
        CreatureProgram.Comparison comparison = (CreatureProgram.Comparison) condition;
        String sensor = comparison.sensor() == CreatureProgram.Sensor.ENERGY ? "energy" : "nearest food distance";
        String operator = switch (comparison.operator()) {
            case LESS -> "<"; case LESS_EQUAL -> "≤"; case GREATER -> ">"; case GREATER_EQUAL -> "≥";
        };
        return sensor + " " + operator + " " + comparison.value();
    }

    private static String kindName(String creatureName) {
        return switch (creatureName) {
            case "Pip", "Peach" -> "coral";
            case "Lumen", "Thimble" -> "lilac";
            default -> "moss";
        };
    }

    private static String actionName(CreatureProgram.Action action) {
        return switch (action) {
            case SEEK_NEAREST_FOOD -> "seek nearest food";
            case EAT -> "eat";
            case REST -> "rest";
            case WANDER -> "wander";
        };
    }

    private static String actionName(CreatureRule.Action action) {
        return switch (action) {
            case SEEK_FOOD -> "seek food"; case REST -> "rest"; case EAT -> "eat"; case WANDER -> "wander";
        };
    }

    private static void appendFood(StringBuilder json, Food food) {
        json.append("{\"id\":").append(quote(Long.toString(food.id())))
                .append(",\"x\":").append(decimal(food.x()))
                .append(",\"y\":").append(decimal(food.y()))
                .append(",\"size\":").append(1.4 + food.id() % 4 * .55)
                .append(",\"phase\":").append(food.id() * .73).append('}');
    }

    private static String decimal(long coordinate) {
        return Double.toString((double) coordinate / SimulationEngine.COORDINATE_SCALE);
    }

    static String quote(String text) {
        StringBuilder result = new StringBuilder(text.length() + 2).append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }
}
