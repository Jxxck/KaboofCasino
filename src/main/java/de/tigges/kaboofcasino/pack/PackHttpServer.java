package de.tigges.kaboofcasino.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.tigges.kaboofcasino.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves the packs on {@code GET|HEAD /pack/<sha1>.zip} (render.md 8.5): a JDK {@code HttpServer} on its own daemon
 * threads (never MAIN), the zips from memory (the casino pack and the optional music pack, each under its own hash).
 * Everything else is 404/405 without a body. All players arrive from 10.44.0.20 (the tunnel), so the log lines name
 * the client's {@code X-Minecraft-Username} header instead of the IP.
 *
 * <p>Same rules as the game port ({@link DownloadPolicy}): every pack has its own slot pool (503 when full; HEAD needs
 * no slot), the body is written in 64 KiB slices (the JDK stream copies each write into a buffer of twice its size,
 * so one 22 MB write would cost 44 MB), and a watchdog thread checks every running download each second; a stalled,
 * too slow or overlong one is closed from the watchdog, which also ends a write that blocks on a client that stopped
 * reading. One thread per slot of all pools plus a few for short answers, so casino downloads never queue behind
 * music downloads.
 */
public final class PackHttpServer {

    private static final int SHORT_ANSWER_THREADS = 4;

    private final DownloadSlots slots;
    private final Set<Download> running = ConcurrentHashMap.newKeySet();
    private HttpServer server;
    private ExecutorService pool;
    private ScheduledExecutorService watchdog;

    public PackHttpServer(PackFile pack) {
        this(List.of(pack));
    }

    /** @param packs every pack that is served (distinct SHA-1, so distinct paths) */
    public PackHttpServer(List<PackFile> packs) {
        this(packs, DownloadPolicy.DEFAULT);
    }

    PackHttpServer(List<PackFile> packs, DownloadPolicy policy) {
        this.slots = new DownloadSlots(policy, packs);
    }

    /**
     * The JDK server reads its limits once, when its classes load: set them before the first server starts
     * (onLoad). Without them requests have no time limit. (The pack downloads have their own, shorter deadlines.)
     */
    public static void configureLimits() {
        System.setProperty("sun.net.httpserver.maxReqTime", "10");
        System.setProperty("sun.net.httpserver.maxRspTime", "900");
        System.setProperty("jdk.httpserver.maxConnections", "64");
    }

    public void start(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        int threads = SHORT_ANSWER_THREADS;
        for (DownloadSlots.Pool p : slots.pools()) {
            threads += p.limits().slots();
        }
        AtomicInteger n = new AtomicInteger();
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "casino-pack-http-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "casino-pack-http-watchdog");
            t.setDaemon(true);
            return t;
        });
        long every = slots.policy().checkMillis();
        watchdog.scheduleAtFixedRate(this::check, every, every, TimeUnit.MILLISECONDS);
        server.setExecutor(pool);
        server.createContext("/", this::handle);
        server.start();
        for (DownloadSlots.Pool p : slots.pools()) {
            Log.get().info("Resource pack server on {}:{} serves the {} pack at {} ({} bytes, {} slots)", bind,
                    port(), p.limits().name(), p.pack().path(), p.pack().size(), p.limits().slots());
        }
    }

    /** The bound port (differs from the configured one only for port 0, tests). */
    int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    DownloadSlots slots() {
        return slots;
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String method = ex.getRequestMethod();
            DownloadSlots.Pool p = slots.find(ex.getRequestURI().getPath());
            if (p == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            boolean head = method.equals("HEAD");
            if (!head && !method.equals("GET")) {
                ex.getResponseHeaders().set("Allow", "GET, HEAD");
                ex.sendResponseHeaders(405, -1);
                return;
            }
            byte[] zip = p.pack().bytes();
            if (head) {
                ex.getResponseHeaders().set("Content-Type", "application/zip");
                ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
                ex.getResponseHeaders().set("Content-Length", Integer.toString(zip.length));
                ex.sendResponseHeaders(200, -1);
                return;
            }
            if (!p.tryAcquire()) {
                ex.getResponseHeaders().set("Retry-After", "5");
                ex.sendResponseHeaders(503, -1);
                return;
            }
            Download d = new Download(ex, p, ex.getRequestHeaders().getFirst("X-Minecraft-Username"));
            running.add(d);
            try {
                d.send(zip);
            } finally {
                running.remove(d);
                d.end();
            }
        } catch (IOException e) {
            Log.get().debug("pack download aborted: {}", e.toString());
        }
    }

    /** Watchdog thread: cuts downloads that break the policy. */
    private void check() {
        long now = System.nanoTime();
        for (Download d : running) {
            d.check(now);
        }
    }

    /** One GET body on a handler thread. */
    private final class Download {
        private final HttpExchange ex;
        private final DownloadSlots.Pool pool;
        private final String user;
        private final long number;
        private final long startNanos = System.nanoTime();
        private volatile long progressNanos = startNanos;
        private volatile long sent;
        private volatile DownloadPolicy.Verdict cut;
        private boolean ended;

        Download(HttpExchange ex, DownloadSlots.Pool pool, String user) {
            this.ex = ex;
            this.pool = pool;
            this.user = user == null ? "an unknown client" : user;
            this.number = slots.nextNumber();
        }

        void send(byte[] zip) throws IOException {
            Log.get().info("Resource pack ({}) download #{} to {} started ({})", pool.limits().name(), number, user,
                    slots.occupancy());
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            ex.sendResponseHeaders(200, zip.length);
            OutputStream out = ex.getResponseBody();
            for (int off = 0; off < zip.length; off += PackSniffer.CHUNK) {
                if (cut != null) {
                    throw new IOException("cut: " + cut.text());
                }
                int n = Math.min(PackSniffer.CHUNK, zip.length - off);
                out.write(zip, off, n);
                sent += n;
                progressNanos = System.nanoTime();
            }
            out.close();
        }

        /** Watchdog thread. */
        void check(long now) {
            if (cut != null) {
                return;
            }
            DownloadPolicy.Verdict v = slots.policy().verdict(pool.limits(), now - startNanos, now - progressNanos,
                    sent);
            if (v != DownloadPolicy.Verdict.OK) {
                cut = v;
                slots.cut(v);
                // closes the connection; a write blocked on a client that does not read fails at once
                ex.close();
            }
        }

        /** Handler thread, once. */
        void end() {
            if (ended) {
                return;
            }
            ended = true;
            pool.release();
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            DownloadPolicy.Verdict v = cut;
            if (v == null && sent == pool.pack().size()) {
                Log.get().info("Resource pack ({}) download #{} sent to {}: {} bytes in {} ms ({})",
                        pool.limits().name(), number, user, sent, millis, slots.occupancy());
            } else {
                Log.get().info("Resource pack ({}) download #{} to {} cut ({}): {}/{} bytes after {} ms ({})",
                        pool.limits().name(), number, user, v == null ? "closed by the client" : v.text(), sent,
                        pool.pack().size(), millis, slots.occupancy());
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }
}
