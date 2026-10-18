package brood.lang;

import brood.lang.CreatureProgram.Action;
import brood.lang.CreatureProgram.Always;
import brood.lang.CreatureProgram.Comparison;
import brood.lang.CreatureProgram.Condition;
import brood.lang.CreatureProgram.Operator;
import brood.lang.CreatureProgram.Rule;
import brood.lang.CreatureProgram.Sensor;
import java.util.ArrayList;
import java.util.List;

/** Hand-written lexer and recursive-descent compiler for Brood creature programs. */
public final class CreatureLanguage {
    public static final int MAX_SOURCE_LENGTH = 16_384;
    public static final int MAX_RULES = 64;
    public static final int MAX_CONSTANT = 1_000_000;

    public CreatureProgram compile(String source) {
        if (source == null) throw failure(1, 1, "Program source must not be null");
        if (source.length() > MAX_SOURCE_LENGTH) throw failure(1, 1, "Program exceeds the source length limit");
        return new Parser(source).parse();
    }

    private static CompileException failure(int line, int column, String message) {
        return new CompileException(List.of(new Diagnostic(line, column, message)));
    }

    private enum Kind { WORD, NUMBER, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL, COLON, NEWLINE, END }
    private record Token(Kind kind, String text, int line, int column) { }

    private static final class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int offset;
        private int line = 1;
        private int column = 1;

        private Lexer(String source) { this.source = source; }

        private List<Token> scan() {
            while (offset < source.length()) {
                char current = source.charAt(offset);
                if (current == ' ' || current == '\t' || current == '\r') { advance(); continue; }
                if (current == '\n') { tokens.add(new Token(Kind.NEWLINE, "\\n", line, column)); advance(); line++; column = 1; continue; }
                if (current == '#') { skipComment(); continue; }
                if (current == '/' && peek(1) == '/') { skipComment(); continue; }
                int tokenLine = line;
                int tokenColumn = column;
                if (Character.isLetter(current) || current == '_') {
                    int start = offset;
                    while (Character.isLetterOrDigit(peek(0)) || peek(0) == '_') advance();
                    tokens.add(new Token(Kind.WORD, source.substring(start, offset), tokenLine, tokenColumn));
                    continue;
                }
                if (Character.isDigit(current)) {
                    int start = offset;
                    while (Character.isDigit(peek(0))) advance();
                    tokens.add(new Token(Kind.NUMBER, source.substring(start, offset), tokenLine, tokenColumn));
                    continue;
                }
                if (current == '<' || current == '>') {
                    advance();
                    boolean inclusive = peek(0) == '=';
                    if (inclusive) advance();
                    Kind kind = current == '<' ? (inclusive ? Kind.LESS_EQUAL : Kind.LESS)
                            : (inclusive ? Kind.GREATER_EQUAL : Kind.GREATER);
                    tokens.add(new Token(kind, source.substring(offset - (inclusive ? 2 : 1), offset), tokenLine, tokenColumn));
                    continue;
                }
                if (current == ':') { advance(); tokens.add(new Token(Kind.COLON, ":", tokenLine, tokenColumn)); continue; }
                throw failure(tokenLine, tokenColumn, "Unexpected character '" + current + "'");
            }
            tokens.add(new Token(Kind.END, "end of input", line, column));
            return tokens;
        }

        private char peek(int distance) {
            int index = offset + distance;
            return index >= source.length() ? '\0' : source.charAt(index);
        }
        private void advance() { offset++; column++; }
        private void skipComment() { while (offset < source.length() && peek(0) != '\n') advance(); }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int current;
        private boolean otherwiseSeen;

        private Parser(String source) { tokens = new Lexer(source).scan(); }

        private CreatureProgram parse() {
            List<Rule> rules = new ArrayList<>();
            skipSeparators();
            while (!check(Kind.END)) {
                if (rules.size() == MAX_RULES) throw error(peek(), "Program exceeds the " + MAX_RULES + " rule limit");
                Token start = peek();
                if (matchWord("when")) {
                    if (otherwiseSeen) throw error(start, "A when rule cannot follow otherwise");
                    Condition condition = comparison();
                    consume(Kind.COLON, "Expected ':' before the action");
                    rules.add(new Rule("rule-" + (rules.size() + 1), condition, action(), rules.size() + 1));
                } else if (matchWord("otherwise")) {
                    if (otherwiseSeen) throw error(start, "Only one otherwise rule is allowed");
                    otherwiseSeen = true;
                    consume(Kind.COLON, "Expected ':' before the action");
                    rules.add(new Rule("rule-" + (rules.size() + 1), new Always(), action(), rules.size() + 1));
                } else {
                    throw error(peek(), "Expected 'when' or 'otherwise'");
                }
                if (!check(Kind.END) && !check(Kind.NEWLINE)) throw error(peek(), "Expected a newline between rules");
                skipSeparators();
            }
            if (rules.isEmpty()) throw error(peek(), "Program must contain at least one rule");
            return new CreatureProgram(rebuildSource(rules), rules);
        }

        private Condition comparison() {
            Token sensorToken = consume(Kind.WORD, "Expected 'energy' or 'nearest_food'");
            Sensor sensor = switch (sensorToken.text()) {
                case "energy" -> Sensor.ENERGY;
                case "nearest_food" -> Sensor.NEAREST_FOOD;
                default -> throw error(sensorToken, "Unknown condition sensor '" + sensorToken.text() + "'");
            };
            Token operator = peek();
            advanceIfComparison();
            Operator operation = switch (operator.kind()) {
                case LESS -> Operator.LESS;
                case LESS_EQUAL -> Operator.LESS_EQUAL;
                case GREATER -> Operator.GREATER;
                case GREATER_EQUAL -> Operator.GREATER_EQUAL;
                default -> throw error(operator, "Expected one of <, <=, >, >=");
            };
            Token number = consume(Kind.NUMBER, "Expected a non-negative integer constant");
            int value;
            try { value = Integer.parseInt(number.text()); }
            catch (NumberFormatException exception) { throw error(number, "Integer constant is too large"); }
            if (value > MAX_CONSTANT) throw error(number, "Integer constant exceeds " + MAX_CONSTANT);
            if (sensor == Sensor.ENERGY && value > 100_000) throw error(number, "Energy constants must be between 0 and 100000");
            return new Comparison(sensor, operation, value);
        }

        private Action action() {
            Token first = consume(Kind.WORD, "Expected an action: seek, eat, rest, or wander");
            return switch (first.text()) {
                case "seek" -> {
                    Token target = consume(Kind.WORD, "Expected 'nearest_food' after seek");
                    if (!target.text().equals("nearest_food")) throw error(target, "Only 'seek nearest_food' is supported");
                    yield Action.SEEK_NEAREST_FOOD;
                }
                case "eat" -> Action.EAT;
                case "rest" -> Action.REST;
                case "wander" -> Action.WANDER;
                default -> throw error(first, "Unknown action '" + first.text() + "'");
            };
        }

        private void advanceIfComparison() {
            if (check(Kind.LESS) || check(Kind.LESS_EQUAL) || check(Kind.GREATER) || check(Kind.GREATER_EQUAL)) advance();
        }
        private void skipSeparators() { while (match(Kind.NEWLINE)) { } }
        private boolean matchWord(String word) { if (check(Kind.WORD) && peek().text().equals(word)) { advance(); return true; } return false; }
        private boolean match(Kind kind) { if (!check(kind)) return false; advance(); return true; }
        private Token consume(Kind kind, String message) { if (check(kind)) return advance(); throw error(peek(), message); }
        private boolean check(Kind kind) { return peek().kind() == kind; }
        private Token peek() { return tokens.get(current); }
        private Token advance() { if (!check(Kind.END)) current++; return tokens.get(current - 1); }
        private CompileException error(Token token, String message) { return failure(token.line(), token.column(), message); }

        private String rebuildSource(List<Rule> rules) {
            StringBuilder result = new StringBuilder();
            for (Rule rule : rules) {
                if (result.length() > 0) result.append('\n');
                if (rule.condition() instanceof Always) result.append("otherwise");
                else {
                    Comparison condition = (Comparison) rule.condition();
                    result.append("when ").append(condition.sensor() == Sensor.ENERGY ? "energy" : "nearest_food")
                            .append(' ').append(switch (condition.operator()) {
                                case LESS -> "<"; case LESS_EQUAL -> "<="; case GREATER -> ">"; case GREATER_EQUAL -> ">=";
                            }).append(' ').append(condition.value());
                }
                result.append(": ").append(switch (rule.action()) {
                    case SEEK_NEAREST_FOOD -> "seek nearest_food"; case EAT -> "eat"; case REST -> "rest"; case WANDER -> "wander";
                });
            }
            return result.toString();
        }
    }
}
