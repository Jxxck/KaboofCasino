package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The own HTTP server ({@code pack.serve: http-port}) on an ephemeral loopback port: both packs, 404, 405, separate
 * pools, and the watchdog that cuts a client that stopped reading.
 */
class PackHttpServerTest {

    private static PackFile pack;
    private static PackFile music;
    private static PackHttpServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() throws Exception {
        pack = PackFile.load(PackHttpServerTest.class.getClassLoader());
        music = PackFile.loadMusic(PackHttpServerTest.class.getClassLoader());
        server = new PackHttpServer(List.of(pack, music));
        server.start("127.0.0.1", 0);
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        server.stop();
        client.close();
    }

    private static HttpResponse<byte[]> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("X-Minecraft-Username", "Tester").build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void bothPacksAreServedWithTheirHashes() throws Exception {
        for (PackFile p : List.of(pack, music)) {
            HttpResponse<byte[]> r = send("GET", p.path());
            assertEquals(200, r.statusCode());
            assertEquals("application/zip", r.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(p.size(), r.body().length);
            assertEquals(p.sha1(), PackFile.sha1(r.body()));
            HttpResponse<byte[]> h = send("HEAD", p.path() + "?x=1");
            assertEquals(200, h.statusCode());
            assertEquals(Integer.toString(p.size()), h.headers().firstValue("Content-Length").orElseThrow());
            assertEquals(0, h.body().length);
        }
    }

    /** A client that sends GET and then reads nothing (tiny receive buffer). */
    private static Socket stalled(PackHttpServer on, PackFile file) throws IOException {
        Socket s = new Socket();
        s.setReceiveBufferSize(4096);
        s.connect(new InetSocketAddress("127.0.0.1", on.port()), 5000);
        OutputStream out = s.getOutputStream();
        out.write(("GET " + file.path() + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        return s;
    }

    private static boolean await(BooleanSupplier condition, long millis) throws InterruptedException {
        long end = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    @Test
    void stalledMusicClientsNeverBlockTheCasinoPack() throws Exception {
        DownloadSlots.Pool songs = server.slots().of(music);
        List<Socket> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                clients.add(stalled(server, music));
            }
            assertTrue(await(() -> songs.active() == 4, 3000), "4 music slots held: " + songs.active());
            assertEquals(503, send("GET", music.path()).statusCode(), "the music pool is full");
            HttpResponse<byte[]> r = send("GET", pack.path());
            assertEquals(200, r.statusCode(), "the casino pack has its own pool and threads");
            assertEquals(pack.sha1(), PackFile.sha1(r.body()));
        } finally {
            for (Socket c : clients) {
                c.close();
            }
        }
        assertTrue(await(() -> songs.active() == 0, 5000), "hung-up clients free their slots: " + songs.active());
    }

    @Test
    void theWatchdogCutsAClientThatStoppedReading() throws Exception {
        long ms = TimeUnit.MILLISECONDS.toNanos(1);
        DownloadPolicy fast = new DownloadPolicy(new DownloadPolicy.Limits("casino", 8, 4000 * ms),
                new DownloadPolicy.Limits("music", 4, 8000 * ms), 1000 * ms, 32 * 1024, 300 * ms, 50);
        PackHttpServer quick = new PackHttpServer(List.of(pack, music), fast);
        quick.start("127.0.0.1", 0);
        try (Socket client = stalled(quick, music)) {
            assertTrue(!client.isClosed());
            DownloadSlots.Pool songs = quick.slots().of(music);
            assertTrue(await(() -> songs.active() == 1, 3000));
            assertTrue(await(() -> songs.active() == 0, 5000), "cut and released");
            assertEquals(1, quick.slots().cuts(DownloadPolicy.Verdict.STALLED)
                    + quick.slots().cuts(DownloadPolicy.Verdict.TOO_SLOW), "cut as stalled (or too slow)");
        } finally {
            quick.stop();
        }
    }

    @Test
    void everythingElseIs404Or405() throws Exception {
        assertEquals(404, send("GET", "/pack/0000.zip").statusCode());
        assertEquals(404, send("GET", "/").statusCode());
        HttpResponse<byte[]> post = send("POST", music.path());
        assertEquals(405, post.statusCode());
        assertTrue(post.headers().firstValue("Allow").orElseThrow().contains("GET"));
    }
}
