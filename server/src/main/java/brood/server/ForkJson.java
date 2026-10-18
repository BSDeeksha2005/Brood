package brood.server;

import brood.sim.WorldSnapshot;

/** Serializes a fork experiment and the canonical hash of each independent branch. */
final class ForkJson {
    private ForkJson() { }

    static String encode(SimulationSession.ForkView fork) {
        StringBuilder json = new StringBuilder(8192);
        json.append("{\"id\":").append(WorldJson.quote(fork.id()))
                .append(",\"snapshotTick\":").append(fork.snapshotTick())
                .append(",\"snapshotHash\":").append(WorldJson.quote(fork.snapshotHash()))
                .append(",\"original\":");
        appendBranch(json, fork.id() + "-original", fork.original());
        json.append(",\"alternate\":");
        appendBranch(json, fork.id() + "-alternate", fork.alternate());
        return json.append('}').toString();
    }

    private static void appendBranch(StringBuilder json, String id, brood.sim.WorldState world) {
        json.append("{\"id\":").append(WorldJson.quote(id))
                .append(",\"stateHash\":").append(WorldJson.quote(WorldSnapshot.capture(world).stateHash()))
                .append(",\"world\":").append(WorldJson.encode(world)).append('}');
    }
}
