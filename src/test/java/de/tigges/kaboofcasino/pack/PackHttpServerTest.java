package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The own HTTP server ({@code pack.serve: http-port}) on an ephemeral loopback port: both packs, 404, 405. */
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

    @Test
    void everythingElseIs404Or405() throws Exception {
        assertEquals(404, send("GET", "/pack/0000.zip").statusCode());
        assertEquals(404, send("GET", "/").statusCode());
        HttpResponse<byte[]> post = send("POST", music.path());
        assertEquals(405, post.statusCode());
        assertTrue(post.headers().firstValue("Allow").orElseThrow().contains("GET"));
    }
}
