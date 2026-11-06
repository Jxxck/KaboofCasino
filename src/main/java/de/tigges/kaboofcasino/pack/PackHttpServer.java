package de.tigges.kaboofcasino.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.tigges.kaboofcasino.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves the pack on {@code GET|HEAD /pack/<sha1>.zip} (render.md 8.5): a JDK {@code HttpServer} on its own 4 daemon
 * threads (never MAIN), the zip from memory. Everything else is 404/405 without a body. All players arrive from
 * 10.44.0.20 (the tunnel), so the log line names the client's {@code X-Minecraft-Username} header instead of the IP.
 */
public final class PackHttpServer {

    private final PackFile pack;
    private HttpServer server;
    private ExecutorService pool;
    private final AtomicInteger served = new AtomicInteger();

    public PackHttpServer(PackFile pack) {
        this.pack = pack;
    }

    /**
     * The JDK server reads its limits once, when its classes load: set them before the first server starts
     * (onLoad). Without them requests have no time limit.
     */
    public static void configureLimits() {
        System.setProperty("sun.net.httpserver.maxReqTime", "10");
        System.setProperty("sun.net.httpserver.maxRspTime", "900");
        System.setProperty("jdk.httpserver.maxConnections", "64");
    }

    public void start(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        AtomicInteger n = new AtomicInteger();
        pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "casino-pack-http-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(pool);
        server.createContext("/", this::handle);
        server.start();
        Log.get().info("Resource pack server on {}:{} serves {} ({} bytes)", bind, port, pack.path(), pack.size());
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (!path.equals(pack.path())) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            boolean head = method.equals("HEAD");
            if (!head && !method.equals("GET")) {
                ex.getResponseHeaders().set("Allow", "GET, HEAD");
                ex.sendResponseHeaders(405, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            byte[] zip = pack.bytes();
            if (head) {
                ex.getResponseHeaders().set("Content-Length", Integer.toString(zip.length));
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, zip.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(zip);
            }
            String user = ex.getRequestHeaders().getFirst("X-Minecraft-Username");
            Log.get().info("Resource pack sent to {} ({} bytes, #{})", user == null ? "an unknown client" : user,
                    zip.length, served.incrementAndGet());
        } catch (IOException e) {
            Log.get().debug("pack download aborted: {}", e.toString());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }
}
