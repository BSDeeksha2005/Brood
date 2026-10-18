package brood.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Versioned canonical serialization and restore boundary for complete world state. */
public final class WorldSnapshot {
    public static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 1_000_000;
    private final WorldState state;

    private WorldSnapshot(WorldState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public static WorldSnapshot capture(WorldState state) {
        return new WorldSnapshot(state);
    }

    public WorldState restore() {
        return state;
    }

    /** Canonical, newline-terminated UTF-8 text with explicit field and entity order. */
    public String serialize() {
        StringBuilder output = new StringBuilder();
        line(output, "BROOD_SNAPSHOT", Integer.toString(CURRENT_VERSION));
        line(output, "WORLD", Long.toString(state.seed()), Long.toString(state.randomState()),
                Long.toString(state.tick()), Long.toString(state.nextEntityId()));
        line(output, "CREATURES", Integer.toString(state.creatures().size()));
        for (Creature creature : state.creatures()) {
            line(output, "C", Long.toString(creature.id()), encode(creature.name()), Long.toString(creature.x()),
                    Long.toString(creature.y()), Integer.toString(creature.energy()), Integer.toString(creature.generation()),
                    Integer.toString(creature.rules().size()));
            for (CreatureRule rule : creature.rules()) {
                line(output, "R", encode(rule.id()), rule.condition().name(), rule.action().name());
            }
        }
        line(output, "FOOD", Integer.toString(state.food().size()));
        for (Food food : state.food()) {
            line(output, "F", Long.toString(food.id()), Long.toString(food.x()), Long.toString(food.y()));
        }
        line(output, "EVENTS", Integer.toString(state.events().size()));
        for (RuleExecutionEvent event : state.events()) {
            line(output, "E", Long.toString(event.tick()), Long.toString(event.creatureId()), encode(event.ruleId()),
                    event.condition().name(), event.action().name(), Long.toString(event.targetFoodId()),
                    Long.toString(event.targetDistanceSquared()), Integer.toString(event.energyAtDecision()));
        }
        line(output, "END");
        return output.toString();
    }

    public String stateHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialize().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static WorldSnapshot deserialize(String serialized) {
        if (serialized == null) throw new IllegalArgumentException("Invalid Brood snapshot: input must not be null");
        try {
            Cursor cursor = new Cursor(serialized);
            String[] header = cursor.read("BROOD_SNAPSHOT", 2);
            int version = integer(header[1]);
            if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported snapshot version: " + version);

            String[] worldRow = cursor.read("WORLD", 5);
            long seed = number(worldRow[1]);
            long randomState = number(worldRow[2]);
            long tick = number(worldRow[3]);
            long nextEntityId = number(worldRow[4]);

            int creatureCount = count(cursor.read("CREATURES", 2)[1]);
            List<Creature> creatures = new ArrayList<>(creatureCount);
            for (int index = 0; index < creatureCount; index++) {
                String[] row = cursor.read("C", 8);
                long id = number(row[1]);
                String name = decode(row[2]);
                long x = number(row[3]);
                long y = number(row[4]);
                int energy = integer(row[5]);
                int generation = integer(row[6]);
                int ruleCount = count(row[7]);
                List<CreatureRule> rules = new ArrayList<>(ruleCount);
                for (int ruleIndex = 0; ruleIndex < ruleCount; ruleIndex++) {
                    String[] ruleRow = cursor.read("R", 4);
                    rules.add(new CreatureRule(decode(ruleRow[1]), enumValue(CreatureRule.Condition.class, ruleRow[2]),
                            enumValue(CreatureRule.Action.class, ruleRow[3])));
                }
                creatures.add(new Creature(id, name, x, y, energy, generation, rules));
            }

            int foodCount = count(cursor.read("FOOD", 2)[1]);
            List<Food> food = new ArrayList<>(foodCount);
            for (int index = 0; index < foodCount; index++) {
                String[] row = cursor.read("F", 4);
                food.add(new Food(number(row[1]), number(row[2]), number(row[3])));
            }

            int eventCount = count(cursor.read("EVENTS", 2)[1]);
            List<RuleExecutionEvent> events = new ArrayList<>(eventCount);
            for (int index = 0; index < eventCount; index++) {
                String[] row = cursor.read("E", 9);
                events.add(new RuleExecutionEvent(number(row[1]), number(row[2]), decode(row[3]),
                        enumValue(CreatureRule.Condition.class, row[4]), enumValue(CreatureRule.Action.class, row[5]),
                        number(row[6]), number(row[7]), integer(row[8])));
            }
            cursor.read("END", 1);
            cursor.requireEnd();

            WorldSnapshot snapshot = new WorldSnapshot(new WorldState(seed, randomState, tick, nextEntityId,
                    creatures, food, events));
            if (!snapshot.serialize().equals(serialized)) {
                throw new IllegalArgumentException("Snapshot is not in canonical format");
            }
            return snapshot;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Brood snapshot: " + exception.getMessage(), exception);
        }
    }

    private static void line(StringBuilder output, String... fields) {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) output.append('\t');
            output.append(fields[index]);
        }
        output.append('\n');
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        if (!encode(decoded).equals(value)) throw new IllegalArgumentException("Non-canonical text encoding");
        return decoded;
    }

    private static int count(String value) {
        int count = integer(value);
        if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException("Record count is outside the supported range");
        return count;
    }

    private static long number(String value) { return Long.parseLong(value); }
    private static int integer(String value) { return Integer.parseInt(value); }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value);
    }

    private static final class Cursor {
        private final String[] lines;
        private final int lineCount;
        private int index;

        private Cursor(String serialized) {
            lines = serialized.split("\n", -1);
            lineCount = lines.length > 0 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        }

        private String[] read(String tag, int fieldCount) {
            if (index >= lineCount) throw new IllegalArgumentException("Unexpected end of snapshot");
            String[] fields = lines[index++].split("\t", -1);
            if (fields.length != fieldCount || !fields[0].equals(tag)) {
                throw new IllegalArgumentException("Expected " + tag + " record at line " + index);
            }
            return fields;
        }

        private void requireEnd() {
            if (index != lineCount) throw new IllegalArgumentException("Unexpected records after END");
        }
    }
}
