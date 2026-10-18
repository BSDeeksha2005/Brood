package brood.lang;

import java.util.List;

/** Compilation failure with source-positioned diagnostics. */
public final class CompileException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final transient List<Diagnostic> diagnostics;

    public CompileException(List<Diagnostic> diagnostics) {
        super(diagnostics.isEmpty() ? "Program compilation failed" : diagnostics.getFirst().toString());
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<Diagnostic> diagnostics() { return diagnostics; }
}
