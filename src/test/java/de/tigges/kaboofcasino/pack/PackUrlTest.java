package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.CasinoConfig;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** The pack's download address per client: a fixed host as before, or the client's own handshake address. */
class PackUrlTest {

    private static final PackFile PACK = PackFile.load(PackUrlTest.class.getClassLoader());

    private static CasinoConfig.Pack pack(String serve, String host) {
        return new CasinoConfig.Pack(true, false, serve, host, 25565, 25580, "0.0.0.0", 300);
    }

    private static InetSocketAddress client(String host, int port) {
        return InetSocketAddress.createUnresolved(host, port);
    }

    @Test
    void theShippedDefaultIsAuto() {
        assertEquals(PackUrl.AUTO, CasinoConfig.DEFAULTS.pack().urlHost());
        assertTrue(PackUrl.auto(CasinoConfig.DEFAULTS.pack()));
    }

    @Test
    void aFixedHostIgnoresTheHandshake() {
        CasinoConfig.Pack game = pack(CasinoConfig.Pack.MINECRAFT_PORT, "play.example.org");
        URI expected = URI.create("http://play.example.org:25565" + PACK.path());
        assertEquals(expected, PackUrl.of(game, null, PACK));
        assertEquals(expected, PackUrl.of(game, client("other.example.net", 4711), PACK));
        CasinoConfig.Pack http = pack(CasinoConfig.Pack.HTTP_PORT, "203.0.113.7");
        assertEquals(URI.create("http://203.0.113.7:25580" + PACK.path()), PackUrl.of(http, client("x", 1), PACK));
        assertEquals("http://play.example.org:25565" + PACK.path(), PackUrl.describe(game, PACK));
    }

    @Test
    void autoOnTheGamePortTakesHostAndPortOfTheHandshake() {
        CasinoConfig.Pack p = pack(CasinoConfig.Pack.MINECRAFT_PORT, "auto");
        assertEquals(URI.create("http://mc.example.org:25570" + PACK.path()),
                PackUrl.of(p, client("mc.example.org", 25570), PACK));
        assertEquals(URI.create("http://192.168.1.20:25565" + PACK.path()),
                PackUrl.of(p, client("192.168.1.20", 25565), PACK));
        assertEquals(URI.create("http://localhost:25565" + PACK.path()),
                PackUrl.of(p, client("localhost", 25565), PACK));
        assertEquals(URI.create("http://mc.example.org:25565" + PACK.path()),
                PackUrl.of(pack(CasinoConfig.Pack.MINECRAFT_PORT, "AUTO"), client("mc.example.org", 25565), PACK));
    }

    @Test
    void autoWithTheOwnHttpServerKeepsItsPort() {
        CasinoConfig.Pack p = pack(CasinoConfig.Pack.HTTP_PORT, "auto");
        assertEquals(URI.create("http://mc.example.org:25580" + PACK.path()),
                PackUrl.of(p, client("mc.example.org", 25570), PACK));
        assertTrue(PackUrl.describe(p, PACK).contains(":25580" + PACK.path()));
    }

    @Test
    void hostsAreCleanedForTheUrl() {
        assertEquals("mc.example.org", PackUrl.host("mc.example.org."));
        assertEquals("mc.example.org", PackUrl.host("mc.example.org\0FML3\0"));
        assertEquals("my_server.example.org", PackUrl.host("my_server.example.org"));
        assertEquals("[2001:db8::1]", PackUrl.host("2001:db8::1"));
        assertEquals("[2001:db8::1]", PackUrl.host("[2001:db8::1]"));
        assertEquals("[::ffff:192.0.2.1]", PackUrl.host("::ffff:192.0.2.1"));
        CasinoConfig.Pack p = pack(CasinoConfig.Pack.MINECRAFT_PORT, "auto");
        assertEquals(URI.create("http://[2001:db8::1]:25565" + PACK.path()),
                PackUrl.of(p, client("2001:db8::1", 25565), PACK));
    }

    @Test
    void anythingButAHostNameOrAddressGivesNoUrl() {
        CasinoConfig.Pack p = pack(CasinoConfig.Pack.MINECRAFT_PORT, "auto");
        assertNull(PackUrl.of(p, null, PACK));
        for (String bad : new String[] {"", ".", "a b", "evil.example/path", "user@host", "host?x=1", "host#f",
            "-leading.example", "trailing-.", "host///203.0.113.7///1700000000///sig", "höst.example",
            "2001:db8::zz", "a".repeat(254), "\0", "http://x"}) {
            assertNull(PackUrl.host(bad), bad);
            assertNull(PackUrl.of(p, client(bad, 25565), PACK), bad);
        }
        assertNull(PackUrl.of(p, client("mc.example.org", 0), PACK), "no port");
    }

    @Test
    void logTextOfAClientHostIsPrintable() {
        assertEquals("unknown", PackUrl.printable(null));
        assertEquals("a?b?c:25565", PackUrl.printable(client("a\nbäc", 25565)));
        assertEquals(64 + ":1".length(), PackUrl.printable(client("x".repeat(500), 1)).length());
    }
}
