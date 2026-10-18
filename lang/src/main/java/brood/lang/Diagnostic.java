package brood.lang;

/** A source location and human-readable compiler error. */
public record Diagnostic(int line, int column, String message) {
    @Override
    public String toString() { return line + ":" + column + ": " + message; }
}
