package brood.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BroodServerTest {
    private HttpServer server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() throws Exception {
        server = BroodServer.create(new InetSocketAddress("127.0.0.1", 0), new SimulationSession(2026));
        server.setExecutor(Runnable::run);
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void transfersRealWorldStateAndAppliesProgramBeforeAdvancingOneTick() throws Exception {
        HttpResponse<String> initial = get("/api/world");
        assertEquals(200, initial.statusCode());
        assertTrue(initial.body().contains("\"tick\":0"));
        assertTrue(initial.body().contains("\"programSource\":"));
        assertTrue(initial.body().contains("\"observation\":"));
        assertTrue(initial.body().contains("\"food\":["));
        assertEquals(6, occurrences(initial.body(), "\"programSource\":"));
        assertEquals(6, stringValues(initial.body(), "programSource").stream().distinct().count());
        for (String name : new String[]{"Mallow", "Pip", "Lumen", "Nori", "Peach", "Thimble"}) {
            assertTrue(initial.body().contains("\"name\":\"" + name + "\""));
        }
        assertTrue(initial.body().contains("\"kind\":\"coral\""));
        assertTrue(initial.body().contains("\"kind\":\"lilac\""));

        HttpResponse<String> applied = request("PUT", "/api/creatures/1/program", "otherwise: wander");
        assertEquals(200, applied.statusCode());
        assertTrue(applied.body().contains("\"tick\":1"));
        assertTrue(applied.body().contains("otherwise: wander"));
        assertTrue(applied.body().contains("rule-1 · wander"));

        HttpResponse<String> advanced = request("POST", "/api/step", "2");
        assertEquals(200, advanced.statusCode());
        assertTrue(advanced.body().contains("\"tick\":3"));
    }

    @Test
    void servesBundledFrontendAssetsWithoutRoutingThemThroughTheApi() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.body().contains("Brood static resource test"));
        assertEquals(404, get("/missing.js").statusCode());
        assertEquals(405, request("POST", "/", "ignored").statusCode());
    }

    @Test
    void rejectedProgramReturnsDiagnosticAndDoesNotAdvanceOrReplaceWorld() throws Exception {
        String before = get("/api/world").body();
        HttpResponse<String> rejected = request("PUT", "/api/creatures/1/program", "when energy = 5: rest");
        assertEquals(422, rejected.statusCode());
        assertTrue(rejected.body().startsWith("1:13:"));
        assertEquals(before, get("/api/world").body());
    }

    @Test
    void unknownCreatureAndInvalidTickCountReturnClientErrors() throws Exception {
        assertEquals(400, request("PUT", "/api/creatures/99/program", "otherwise: rest").statusCode());
        assertEquals(400, request("POST", "/api/step", "5").statusCode());
    }

    @Test
    void restartRestoresTheSameSixCreatureWorld() throws Exception {
        String initial = get("/api/world").body();
        assertEquals(200, request("PUT", "/api/creatures/2/program", "otherwise: rest").statusCode());
        HttpResponse<String> restarted = request("POST", "/api/restart", "");

        assertEquals(200, restarted.statusCode());
        assertEquals(initial, restarted.body());
        assertEquals(6, occurrences(restarted.body(), "\"programSource\":"));
    }

    @Test
    void forkApiCapturesBranchesEditsOnlyAlternateAndComparesRealWorldStates() throws Exception {
        String mainBefore = get("/api/world").body();
        HttpResponse<String> created = request("POST", "/api/forks", "");
        assertEquals(200, created.statusCode());
        assertTrue(created.body().contains("\"snapshotTick\":0"));
        assertEquals(2, stringValues(created.body(), "stateHash").size());
        assertEquals(stringValues(created.body(), "stateHash").get(0),
                stringValues(created.body(), "stateHash").get(1));

        HttpResponse<String> edited = request("PUT", "/api/forks/fork-1/alternate/creatures/1/program", "otherwise: rest");
        assertEquals(200, edited.statusCode());
        assertEquals(mainBefore, get("/api/world").body());
        assertNotEquals(stringValues(edited.body(), "stateHash").get(0),
                stringValues(edited.body(), "stateHash").get(1));

        String beforeInvalid = get("/api/forks/fork-1").body();
        HttpResponse<String> rejected = request("PUT", "/api/forks/fork-1/alternate/creatures/1/program", "otherwise: jump");
        assertEquals(422, rejected.statusCode());
        assertEquals(beforeInvalid, get("/api/forks/fork-1").body());

        HttpResponse<String> advanced = request("POST", "/api/forks/fork-1/advance", "1");
        assertEquals(200, advanced.statusCode());
        assertEquals(2, occurrences(advanced.body(), "\"tick\":1"));
        assertNotEquals(stringValues(advanced.body(), "stateHash").get(0),
                stringValues(advanced.body(), "stateHash").get(1));
        assertEquals(mainBefore, get("/api/world").body());
    }

    @Test
    void forkBranchCanAdvanceIndependentlyAndDeleteSafely() throws Exception {
        assertEquals(200, request("POST", "/api/forks", "").statusCode());
        HttpResponse<String> oneSide = request("POST", "/api/forks/fork-1/branches/original/advance", "2");
        assertEquals(200, oneSide.statusCode());
        assertTrue(oneSide.body().contains("\"original\":{\"id\":\"fork-1-original\""));
        assertTrue(oneSide.body().contains("\"alternate\":{\"id\":\"fork-1-alternate\""));
        assertTrue(oneSide.body().contains("\"tick\":2"));
        assertTrue(oneSide.body().contains("\"tick\":0"));
        assertEquals(400, request("POST", "/api/forks/fork-1/advance", "101").statusCode());
        assertEquals(204, request("DELETE", "/api/forks/fork-1", "").statusCode());
        assertEquals(404, get("/api/forks/fork-1").statusCode());
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static List<String> stringValues(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\":\\\"([^\\\"]*)\\\"").matcher(json);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, ""); }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "text/plain; charset=utf-8")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
