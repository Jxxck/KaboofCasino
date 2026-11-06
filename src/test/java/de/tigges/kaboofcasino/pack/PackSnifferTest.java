package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The game-port HTTP sniffer on an EmbeddedChannel: what is served, what is replayed to Minecraft untouched. */
class PackSnifferTest {

    private static PackFile pack;

    @BeforeAll
    static void load() {
        pack = PackFile.load(PackSnifferTest.class.getClassLoader());
    }

    private static GamePortPack host() {
        return new GamePortPack(pack, LoggerFactory.getLogger("test"));
    }

    private static EmbeddedChannel channel(GamePortPack host) {
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addFirst(GamePortPack.HANDLER, new PackSniffer(host));
        ch.pipeline().fireChannelActive();
        return ch;
    }

    private static ByteBuf bytes(String s) {
        return Unpooled.wrappedBuffer(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static ByteBuf bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return Unpooled.wrappedBuffer(out);
    }

    /** Everything written to the socket. */
    private static byte[] outbound(EmbeddedChannel ch) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuf b;
        while ((b = ch.readOutbound()) != null) {
            byte[] chunk = new byte[b.readableBytes()];
            b.readBytes(chunk);
            out.writeBytes(chunk);
            b.release();
        }
        return out.toByteArray();
    }

    /** Everything that reached the game pipeline behind the sniffer. */
    private static byte[] inbound(EmbeddedChannel ch) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Object m;
        while ((m = ch.readInbound()) != null) {
            ByteBuf b = (ByteBuf) m;
            byte[] chunk = new byte[b.readableBytes()];
            b.readBytes(chunk);
            out.writeBytes(chunk);
            b.release();
        }
        return out.toByteArray();
    }

    private static String head(byte[] response) {
        String s = new String(response, StandardCharsets.ISO_8859_1);
        return s.substring(0, s.indexOf("\r\n\r\n") + 4);
    }

    @Test
    void getServesThePackAndCloses() {
        EmbeddedChannel ch = channel(host());
        ch.writeInbound(bytes("GET " + pack.path() + " HTTP/1.1\r\nHost: 127.0.0.1\r\nUser-Agent: Minecraft Java/26.3\r\n"
                + "\r\n"));
        byte[] response = outbound(ch);
        String head = head(response);
        assertTrue(head.startsWith("HTTP/1.1 200 OK\r\n"), head);
        assertTrue(head.contains("Content-Type: application/zip\r\n"));
        assertTrue(head.contains("Content-Length: " + pack.size() + "\r\n"));
        assertTrue(head.contains("Connection: close\r\n"));
        assertEquals(head.length() + pack.size(), response.length);
        byte[] body = java.util.Arrays.copyOfRange(response, head.length(), response.length);
        assertEquals(pack.sha1(), PackFile.sha1(body));
        assertFalse(ch.isOpen(), "closed after the write");
        assertEquals(0, inbound(ch).length, "the game pipeline saw nothing");
    }

    @Test
    void headHasNoBody() {
        EmbeddedChannel ch = channel(host());
        ch.writeInbound(bytes("HEAD " + pack.path() + "?x=1 HTTP/1.1\r\n\r\n"));
        byte[] response = outbound(ch);
        String head = head(response);
        assertTrue(head.startsWith("HTTP/1.1 200 OK"));
        assertTrue(head.contains("Content-Length: " + pack.size()));
        assertEquals(head.length(), response.length);
        assertFalse(ch.isOpen());
    }

    @Test
    void otherPathsAre404AndOtherMethods405() {
        EmbeddedChannel a = channel(host());
        a.writeInbound(bytes("GET /pack/0000.zip HTTP/1.1\r\n\r\n"));
        assertTrue(new String(outbound(a), StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 404 Not Found"));
        assertFalse(a.isOpen());
        EmbeddedChannel b = channel(host());
        b.writeInbound(bytes("POST " + pack.path() + " HTTP/1.1\r\nContent-Length: 0\r\n\r\n"));
        String r = new String(outbound(b), StandardCharsets.ISO_8859_1);
        assertTrue(r.startsWith("HTTP/1.1 405 Method Not Allowed") && r.contains("Allow: GET, HEAD"), r);
        assertFalse(b.isOpen());
    }

    @Test
    void handshakeBytesAreReplayedUntouched() {
        EmbeddedChannel ch = channel(host());
        // a status handshake: length 0x10, id 0x00, protocol 0xAD 0x06, "localhost", port 0x63DD, intent 1, then ping
        int[] hs = {0x10, 0x00, 0xAD, 0x06, 0x09, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't', 0x63, 0xDD, 0x01,
            0x01, 0x00};
        ch.writeInbound(bytes(hs));
        byte[] seen = inbound(ch);
        assertEquals(hs.length, seen.length);
        for (int i = 0; i < hs.length; i++) {
            assertEquals((byte) hs[i], seen[i]);
        }
        assertNull(ch.pipeline().get(GamePortPack.HANDLER), "the sniffer left the pipeline");
        ch.writeInbound(bytes(0x01, 0x00));
        assertArrayEquals(new byte[] {1, 0}, inbound(ch), "later bytes pass directly");
        assertTrue(ch.isOpen());
        assertEquals(0, outbound(ch).length);
    }

    @Test
    void legacyPingIsMinecraft() {
        EmbeddedChannel ch = channel(host());
        ch.writeInbound(bytes(0xFE, 0x01, 0xFA));
        assertArrayEquals(new byte[] {(byte) 0xFE, 1, (byte) 0xFA}, inbound(ch));
        assertTrue(ch.isOpen());
    }

    @Test
    void fragmentedFirstBytes() {
        EmbeddedChannel http = channel(host());
        http.writeInbound(bytes("G"));
        http.writeInbound(bytes("ET " + pack.path()));
        http.writeInbound(bytes(" HTTP/1.1\r\nHost: a\r\n"));
        assertEquals(0, outbound(http).length, "waits for the end of the head");
        http.writeInbound(bytes("\r\n"));
        assertTrue(new String(outbound(http), StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 200 OK"));

        // a handshake whose length byte is 'G' (71): the next byte is the packet id 0x00, not 'E'
        EmbeddedChannel mc = channel(host());
        mc.writeInbound(bytes('G'));
        assertEquals(0, inbound(mc).length, "one byte could still be HTTP");
        mc.writeInbound(bytes(0x00, 0x05));
        assertArrayEquals(new byte[] {'G', 0, 5}, inbound(mc));
        assertNull(mc.pipeline().get(GamePortPack.HANDLER));
    }

    @Test
    void aSilentClientIsPassedOnAfterTheTimeout() {
        EmbeddedChannel ch = channel(host());
        ch.advanceTimeBy(GamePortPack.SNIFF_TIMEOUT_MILLIS - 1, TimeUnit.MILLISECONDS);
        ch.runScheduledPendingTasks();
        assertTrue(ch.pipeline().get(GamePortPack.HANDLER) != null);
        ch.advanceTimeBy(2, TimeUnit.MILLISECONDS);
        ch.runScheduledPendingTasks();
        assertNull(ch.pipeline().get(GamePortPack.HANDLER), "vanilla's own timeout takes over");
        assertTrue(ch.isOpen());
        ch.writeInbound(bytes(0x10, 0x00));
        assertArrayEquals(new byte[] {0x10, 0}, inbound(ch));
    }

    @Test
    void anUnfinishedHttpRequestIsClosedAtTheTimeout() {
        EmbeddedChannel ch = channel(host());
        ch.writeInbound(bytes("GE"));
        ch.advanceTimeBy(GamePortPack.SNIFF_TIMEOUT_MILLIS + 1, TimeUnit.MILLISECONDS);
        ch.runScheduledPendingTasks();
        assertTrue(new String(outbound(ch), StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 400"));
        assertFalse(ch.isOpen());
        assertEquals(0, inbound(ch).length);
    }

    @Test
    void garbageIsMinecraftsProblem() {
        EmbeddedChannel ch = channel(host());
        ch.writeInbound(bytes("\u0016\u0003\u0001hello"));
        assertEquals(8, inbound(ch).length);
        assertTrue(ch.isOpen());
    }

    @Test
    void afterDisableEverythingPassesThrough() {
        GamePortPack host = host();
        EmbeddedChannel ch = channel(host);
        host.disable();
        ch.writeInbound(bytes("GET " + pack.path() + " HTTP/1.1\r\n\r\n"));
        assertTrue(new String(inbound(ch), StandardCharsets.ISO_8859_1).startsWith("GET "));
        assertEquals(0, outbound(ch).length);
    }

    @Test
    void downloadsAreLimited() {
        GamePortPack host = host();
        for (int i = 0; i < GamePortPack.MAX_DOWNLOADS; i++) {
            assertTrue(host.tryAcquire());
        }
        EmbeddedChannel ch = channel(host);
        ch.writeInbound(bytes("GET " + pack.path() + " HTTP/1.1\r\n\r\n"));
        String r = new String(outbound(ch), StandardCharsets.ISO_8859_1);
        assertTrue(r.startsWith("HTTP/1.1 503") && r.contains("Retry-After: 5"), r);
        host.release();
        EmbeddedChannel ok = channel(host);
        ok.writeInbound(bytes("GET " + pack.path() + " HTTP/1.1\r\n\r\n"));
        assertTrue(new String(outbound(ok), StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 200"));
    }

    @Test
    void classification() {
        byte[] get = "GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(HttpSniff.Kind.HTTP, HttpSniff.classify(get, get.length));
        assertEquals(HttpSniff.Kind.WAIT, HttpSniff.classify(get, 3));
        assertEquals(HttpSniff.Kind.WAIT, HttpSniff.classify(new byte[] {'H', 'E'}, 2));
        assertEquals(HttpSniff.Kind.MINECRAFT, HttpSniff.classify(new byte[] {'H', 0}, 2));
        assertEquals(HttpSniff.Kind.MINECRAFT, HttpSniff.classify(new byte[] {(byte) 0xFE}, 1));
        byte[] big = new byte[HttpSniff.MAX_HEAD + 10];
        System.arraycopy("GET ".getBytes(StandardCharsets.ISO_8859_1), 0, big, 0, 4);
        assertEquals(HttpSniff.Kind.HTTP_TOO_LARGE, HttpSniff.classify(big, big.length));
        assertEquals(new HttpSniff.Request("GET", "/pack/a.zip"),
                HttpSniff.requestLine("GET /pack/a.zip?y HTTP/1.1\r\n".getBytes(StandardCharsets.ISO_8859_1), 28));
    }
}
