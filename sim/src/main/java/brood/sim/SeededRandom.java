package brood.sim;

/** Immutable SplitMix64 stream; the state is the input to the next draw. */
public record SeededRandom(long state) {
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    public Draw nextLong() {
        long nextState = state + GAMMA;
        long value = mix64(nextState);
        return new Draw(value, new SeededRandom(nextState));
    }

    /** Uses unsigned remainder for a stable bounded draw; modulo bias is acceptable for fixture placement. */
    public BoundedDraw nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        Draw draw = nextLong();
        int value = (int) Long.remainderUnsigned(draw.value(), bound);
        return new BoundedDraw(value, draw.next());
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    public record Draw(long value, SeededRandom next) { }
    public record BoundedDraw(int value, SeededRandom next) { }
}
