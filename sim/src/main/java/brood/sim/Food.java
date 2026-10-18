package brood.sim;

/** Immutable food position in fixed-point world coordinates. */
public record Food(long id, long x, long y) {
    public Food {
        if (id <= 0) throw new IllegalArgumentException("Food ID must be positive");
        if (x < 0 || x > SimulationEngine.COORDINATE_SCALE
                || y < 0 || y > SimulationEngine.COORDINATE_SCALE) {
            throw new IllegalArgumentException("Food coordinate is outside the world");
        }
    }
}
