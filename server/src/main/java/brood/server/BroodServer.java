package brood.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

/** Small loopback-only HTTP adapter; the simulation core itself remains framework-independent. */
public final class BroodServer {
    private BroodServer() { }

    public static void main(String[] args) throws IOException {
        int port = configuredPort();
        String host = System.getenv().getOrDefault("BROOD_HOST", "127.0.0.1");
        HttpServer server = create(new InetSocketAddress(host, port), new SimulationSession());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Brood listening on http://" + host + ":" + port);
    }

    private static int configuredPort() {
        String environmentPort = System.getenv("PORT");
        if (environmentPort != null && !environmentPort.isBlank()) return Integer.parseInt(environmentPort);
        return Integer.getInteger("brood.port", 8080);
    }

    static HttpServer create(InetSocketAddress address, SimulationSession session) throws IOException {
        HttpServer server = HttpServer.create(address, 0);
        server.createContext("/api", exchange -> handle(exchange, session));
        server.createContext("/", BroodServer::serveStaticAsset);
        return server;
    }

    private static void serveStaticAsset(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            if (!exchange.getRequestMethod().equals("GET")) {
                send(exchange, 405, "Method not allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String asset = path.equals("/") ? "index.html" : path.substring(1);
            if (asset.isBlank() || asset.startsWith("/") || asset.contains("\\")
                    || asset.indexOf(0) >= 0 || hasDotSegment(asset)) {
                send(exchange, 400, "Invalid asset path");
                return;
            }

            try (InputStream resource = BroodServer.class.getClassLoader()
                    .getResourceAsStream("static/" + asset)) {
                if (resource == null) {
                    send(exchange, 404, "Not found");
                    return;
                }
                byte[] bytes = resource.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType(asset));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static boolean hasDotSegment(String asset) {
        for (String segment : asset.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) return true;
        }
        return false;
    }

    private static String contentType(String asset) {
        int dot = asset.lastIndexOf('.');
        String extension = dot < 0 ? "" : asset.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "json", "map" -> "application/json; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "txt" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }

    private static void handle(HttpExchange exchange, SimulationSession session) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if ("http://localhost:5173".equals(origin) || "http://127.0.0.1:5173".equals(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        try {
            if (exchange.getRequestMethod().equals("OPTIONS")) { send(exchange, 204, ""); return; }
            String path = exchange.getRequestURI().getPath();
            switch (exchange.getRequestMethod() + " " + path) {
                case "GET /api/health" -> send(exchange, 200, "{\"status\":\"ready\"}", "application/json; charset=utf-8");
                case "GET /api/world" -> sendWorld(exchange, session.snapshot());
                case "POST /api/step" -> {
                    int count = Integer.parseInt(readBody(exchange, 8));
                    sendWorld(exchange, session.advance(count));
                }
                case "POST /api/restart" -> sendWorld(exchange, session.restart());
                default -> {
                    if (!handleProgramRoute(exchange, session, path)
                            && !handleForkRoute(exchange, session, path)) {
                        send(exchange, 404, "Unknown Brood API route");
                    }
                }
            }
        } catch (brood.lang.CompileException exception) {
            String diagnostic = exception.diagnostics().isEmpty() ? exception.getMessage() : exception.diagnostics().getFirst().toString();
            send(exchange, 422, diagnostic);
        } catch (NoSuchElementException exception) {
            send(exchange, 404, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, exception.getMessage() == null ? "Invalid request" : exception.getMessage());
        } catch (Exception exception) {
            send(exchange, 500, "Simulation request failed: " + exception.getMessage());
        } finally {
            exchange.close();
        }
    }

    private static boolean handleProgramRoute(HttpExchange exchange, SimulationSession session, String path)
            throws IOException {
        String prefix = "/api/creatures/";
        String suffix = "/program";
        if (!exchange.getRequestMethod().equals("PUT") || !path.startsWith(prefix) || !path.endsWith(suffix)) {
            return false;
        }
        long id = Long.parseLong(path.substring(prefix.length(), path.length() - suffix.length()));
        sendWorld(exchange, session.applyProgram(id, readBody(exchange, 16_385)));
        return true;
    }

    private static boolean handleForkRoute(HttpExchange exchange, SimulationSession session, String path)
            throws IOException {
        if (exchange.getRequestMethod().equals("POST") && path.equals("/api/forks")) {
            sendFork(exchange, session.createFork());
            return true;
        }
        String[] parts = path.split("/", -1);
        if (parts.length == 4 && parts[1].equals("api") && parts[2].equals("forks")) {
            String forkId = forkId(parts[3]);
            if (exchange.getRequestMethod().equals("GET")) sendFork(exchange, session.fork(forkId));
            else if (exchange.getRequestMethod().equals("DELETE")) {
                session.deleteFork(forkId);
                send(exchange, 204, "");
            } else return false;
            return true;
        }
        if (parts.length == 5 && parts[1].equals("api") && parts[2].equals("forks")
                && parts[4].equals("advance") && exchange.getRequestMethod().equals("POST")) {
            int ticks = Integer.parseInt(readBody(exchange, 8));
            sendFork(exchange, session.advanceFork(forkId(parts[3]), ticks));
            return true;
        }
        if (parts.length == 7 && parts[1].equals("api") && parts[2].equals("forks")
                && parts[4].equals("branches") && parts[6].equals("advance")
                && exchange.getRequestMethod().equals("POST")) {
            int ticks = Integer.parseInt(readBody(exchange, 8));
            sendFork(exchange, session.advanceForkBranch(forkId(parts[3]), parts[5], ticks));
            return true;
        }
        if (parts.length == 8 && parts[1].equals("api") && parts[2].equals("forks")
                && parts[4].equals("alternate") && parts[5].equals("creatures")
                && parts[7].equals("program") && exchange.getRequestMethod().equals("PUT")) {
            long creatureId = Long.parseLong(parts[6]);
            String source = readBody(exchange, 16_385);
            sendFork(exchange, session.applyAlternateProgram(forkId(parts[3]), creatureId, source));
            return true;
        }
        return false;
    }

    private static String forkId(String value) {
        if (!value.matches("fork-[1-9][0-9]*")) throw new IllegalArgumentException("Invalid experiment ID");
        return value;
    }

    private static void sendFork(HttpExchange exchange, SimulationSession.ForkView fork) throws IOException {
        send(exchange, 200, ForkJson.encode(fork), "application/json; charset=utf-8");
    }

    private static String readBody(HttpExchange exchange, int maxBytes) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) throw new IllegalArgumentException("Request body exceeds the supported limit");
        return new String(body, StandardCharsets.UTF_8);
    }

    private static void sendWorld(HttpExchange exchange, brood.sim.WorldState world) throws IOException {
        send(exchange, 200, WorldJson.encode(world), "application/json; charset=utf-8");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, body, "text/plain; charset=utf-8");
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
    }
}
