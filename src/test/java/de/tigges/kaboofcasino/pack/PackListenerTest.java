package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The configuration-phase offers with a scripted client: the casino pack keeps its enforce rules, the music pack
 * comes only after the casino pack is loaded, is never required and never disconnects (also with enforce on).
 */
class PackListenerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
    private static PackFile pack;
    private static PackFile music;
    private static Messages messages;

    @BeforeAll
    static void load(@TempDir Path dir) {
        pack = PackFile.load(PackListenerTest.class.getClassLoader());
        music = PackFile.loadMusic(PackListenerTest.class.getClassLoader());
        messages = Messages.load(dir.resolve(de.tigges.kaboofcasino.config.Language.current().messagesFile()),
                () -> PackListenerTest.class.getClassLoader().getResourceAsStream(de.tigges.kaboofcasino.config.Language.current().messagesFile())).messages();
    }

    /** DEFAULTS with another pack/music section (by reflection, so new config sections do not break this test). */
    private static CasinoConfig config(boolean enforce, boolean musicOn) throws Exception {
        return config(enforce, musicOn, "example.org");
    }

    /** @param urlHost {@code pack.url-host}: a fixed host, or {@code auto} */
    private static CasinoConfig config(boolean enforce, boolean musicOn, String urlHost) throws Exception {
        CasinoConfig d = CasinoConfig.DEFAULTS;
        CasinoConfig.Pack p = d.pack();
        CasinoConfig.Pack pack = new CasinoConfig.Pack(true, enforce, p.serve(), urlHost, 25565, p.port(),
                p.bind(), 5);
        RecordComponent[] parts = CasinoConfig.class.getRecordComponents();
        Object[] args = new Object[parts.length];
        Class<?>[] types = new Class<?>[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            args[i] = parts[i].getAccessor().invoke(d);
            if (types[i] == CasinoConfig.Pack.class) {
                args[i] = pack;
            } else if (types[i] == CasinoConfig.Music.class) {
                args[i] = new CasinoConfig.Music(musicOn);
            }
        }
        return CasinoConfig.class.getDeclaredConstructor(types).newInstance(args);
    }

    /** A client that answers each push with the next scripted statuses; null = never answers. */
    private static final class Client implements Audience {
        final Deque<List<ResourcePackStatus>> script = new ArrayDeque<>();
        final List<ResourcePackRequest> requests = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final List<UUID> popped = new ArrayList<>();
        boolean connected = true;
        boolean disconnected;
        /** The address of the client's handshake; null = unknown. */
        java.net.InetSocketAddress virtualHost;

        Client answer(ResourcePackStatus... statuses) {
            script.add(statuses.length == 0 ? List.of() : List.of(statuses));
            return this;
        }

        @Override
        public void sendResourcePacks(ResourcePackRequest request) {
            requests.add(request);
            ResourcePackInfo info = request.packs().getFirst();
            String which = info.id().equals(PackFile.MUSIC_ID) ? "music" : "casino";
            log.add(which + " request");
            for (ResourcePackStatus s : script.isEmpty() ? List.<ResourcePackStatus>of() : script.poll()) {
                log.add(which + " " + s);
                request.callback().packEventReceived(info.id(), s, this);
            }
        }

        @Override
        public void removeResourcePacks(UUID id, UUID... others) {
            popped.add(id);
        }

        PlayerConfigurationConnection connection() {
            PlayerProfile profile = (PlayerProfile) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PlayerProfile.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getId" -> PLAYER;
                        case "getName" -> "Tester";
                        default -> null;
                    });
            return (PlayerConfigurationConnection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PlayerConfigurationConnection.class}, (proxy, method, args) -> switch (
                            method.getName()) {
                        case "getAudience" -> this;
                        case "getProfile" -> profile;
                        case "isConnected" -> connected;
                        case "getVirtualHost" -> virtualHost;
                        case "disconnect" -> {
                            disconnected = true;
                            connected = false;
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }
    }

    private static PackSessions run(CasinoConfig config, Client client) {
        return run(config, client, id -> false);
    }

    private static PackSessions run(CasinoConfig config, Client client, java.util.function.Predicate<UUID> musicOff) {
        PackSessions sessions = new PackSessions();
        PackListener listener = new PackListener(() -> config, () -> messages, pack, music, sessions);
        listener.musicOptOut(musicOff);
        listener.millisPerTimeoutSecond = 20; // 5 "seconds" = 100 ms
        listener.configure(client.connection());
        return sessions;
    }

    private static final ResourcePackStatus[] LOADED = {ResourcePackStatus.ACCEPTED, ResourcePackStatus.DOWNLOADED,
        ResourcePackStatus.SUCCESSFULLY_LOADED};

    @Test
    void bothPacksLoadOneAfterTheOther() throws Exception {
        Client c = new Client().answer(LOADED).answer(LOADED);
        PackSessions s = run(config(true, true), c);
        assertEquals(List.of("casino request", "casino ACCEPTED", "casino DOWNLOADED", "casino SUCCESSFULLY_LOADED",
                "music request", "music ACCEPTED", "music DOWNLOADED", "music SUCCESSFULLY_LOADED"), c.log,
                "the music request only after the casino pack is loaded");
        ResourcePackRequest main = c.requests.get(0);
        ResourcePackRequest second = c.requests.get(1);
        assertTrue(main.required());
        assertFalse(second.required(), "the music pack is optional");
        assertFalse(second.replace(), "never replaces the casino pack");
        assertEquals(1, second.packs().size());
        assertEquals(PackFile.MUSIC_ID, second.packs().getFirst().id());
        assertEquals(music.sha1(), second.packs().getFirst().hash());
        assertEquals("http://example.org:25565" + music.path(), second.packs().getFirst().uri().toString());
        assertTrue(second.prompt() != null);
        assertTrue(s.hasPack(PLAYER));
        assertTrue(s.hasMusic(PLAYER));
        assertFalse(c.disconnected);
        assertTrue(c.popped.isEmpty());
    }

    @Test
    void autoSendsTheAddressTheClientConnectedTo() throws Exception {
        Client c = new Client().answer(LOADED).answer(LOADED);
        c.virtualHost = java.net.InetSocketAddress.createUnresolved("mc.example.net", 25570);
        PackSessions s = run(config(true, true, "auto"), c);
        assertEquals("http://mc.example.net:25570" + pack.path(), c.requests.get(0).packs().getFirst().uri().toString());
        assertEquals("http://mc.example.net:25570" + music.path(),
                c.requests.get(1).packs().getFirst().uri().toString());
        assertTrue(s.hasPack(PLAYER));
        assertFalse(c.disconnected);
    }

    @Test
    void aFixedHostIsSentWhateverTheClientConnectedTo() throws Exception {
        Client c = new Client().answer(LOADED).answer(LOADED);
        c.virtualHost = java.net.InetSocketAddress.createUnresolved("mc.example.net", 25570);
        run(config(true, true), c);
        assertEquals("http://example.org:25565" + pack.path(), c.requests.get(0).packs().getFirst().uri().toString());
    }

    @Test
    void autoWithoutAUsableAddressCountsAsAFailedDownload() throws Exception {
        for (java.net.InetSocketAddress host : new java.net.InetSocketAddress[] {null,
            java.net.InetSocketAddress.createUnresolved("host///203.0.113.7///1///sig", 25565)}) {
            Client enforced = new Client();
            enforced.virtualHost = host;
            PackSessions s = run(config(true, true, "auto"), enforced);
            assertTrue(enforced.requests.isEmpty(), "nothing is sent without an address");
            assertTrue(enforced.disconnected, "enforce: no pack, no entry");
            assertFalse(s.hasPack(PLAYER));

            Client soft = new Client();
            soft.virtualHost = host;
            PackSessions t = run(config(false, true, "auto"), soft);
            assertTrue(soft.requests.isEmpty());
            assertFalse(soft.disconnected, "without enforce nobody is disconnected");
            assertFalse(t.hasPack(PLAYER));
            assertEquals(ResourcePackStatus.FAILED_DOWNLOAD, t.status(PLAYER), "the join hint follows");
        }
    }

    @Test
    void decliningTheMusicNeverKicksEvenWithEnforce() throws Exception {
        Client c = new Client().answer(LOADED).answer(ResourcePackStatus.DECLINED);
        PackSessions s = run(config(true, true), c);
        assertFalse(c.disconnected);
        assertTrue(s.hasPack(PLAYER));
        assertFalse(s.hasMusic(PLAYER));
        assertEquals(ResourcePackStatus.DECLINED, s.musicStatus(PLAYER));
    }

    @Test
    void aFailedMusicDownloadNeverKicks() throws Exception {
        for (ResourcePackStatus failure : List.of(ResourcePackStatus.FAILED_DOWNLOAD, ResourcePackStatus.INVALID_URL,
                ResourcePackStatus.FAILED_RELOAD, ResourcePackStatus.DISCARDED)) {
            Client c = new Client().answer(LOADED).answer(ResourcePackStatus.ACCEPTED, failure);
            PackSessions s = run(config(true, true), c);
            assertFalse(c.disconnected, failure.toString());
            assertTrue(s.hasPack(PLAYER));
            assertFalse(s.hasMusic(PLAYER));
        }
    }

    @Test
    void noAnswerForTheMusicWithdrawsItWithoutKicking() throws Exception {
        Client c = new Client().answer(LOADED).answer(ResourcePackStatus.ACCEPTED);
        long start = System.nanoTime();
        PackSessions s = run(config(true, true), c);
        assertTrue(System.nanoTime() - start >= 90_000_000L, "waited the timeout");
        assertFalse(c.disconnected);
        assertEquals(List.of(PackFile.MUSIC_ID), c.popped, "popped: no resource reload later in the world");
        assertFalse(s.hasMusic(PLAYER));
        assertNull(s.musicStatus(PLAYER));
        assertTrue(s.hasPack(PLAYER));
    }

    @Test
    void withoutTheCasinoPackNoMusicIsOffered() throws Exception {
        Client soft = new Client().answer(ResourcePackStatus.DECLINED);
        PackSessions s = run(config(false, true), soft);
        assertEquals(List.of("casino request", "casino DECLINED"), soft.log);
        assertFalse(soft.disconnected, "enforce off: a hint only");
        assertFalse(s.hasMusic(PLAYER));

        Client hard = new Client().answer(ResourcePackStatus.ACCEPTED, ResourcePackStatus.FAILED_DOWNLOAD);
        run(config(true, true), hard);
        assertTrue(hard.disconnected, "enforce on: the casino pack's rule is unchanged");
        assertEquals(1, hard.requests.size());
    }

    @Test
    void musicSwitchedOffMeansNoSecondRequest() throws Exception {
        Client c = new Client().answer(LOADED).answer(LOADED);
        PackSessions s = run(config(false, false), c);
        assertEquals(1, c.requests.size());
        assertFalse(s.hasMusic(PLAYER));
        assertTrue(s.hasPack(PLAYER));
    }

    @Test
    void aPlayerWhoTurnedTheMusicOffGetsNoMusicRequest() throws Exception {
        Client c = new Client().answer(LOADED).answer(LOADED);
        PackSessions s = run(config(false, true), c, PLAYER::equals);
        assertEquals(1, c.requests.size(), "only the casino pack");
        assertFalse(s.hasMusic(PLAYER));
        assertTrue(s.hasPack(PLAYER));
        // everybody else still gets the offer
        Client other = new Client().answer(LOADED).answer(LOADED);
        assertTrue(run(config(false, true), other, id -> !id.equals(PLAYER)).hasMusic(PLAYER));
    }

    @Test
    void forgettingAPlayerForgetsBothStatuses() throws Exception {
        Client c = new Client().answer(LOADED).answer(LOADED);
        PackSessions s = run(config(false, true), c);
        assertTrue(s.hasMusic(PLAYER));
        s.forget(PLAYER);
        assertFalse(s.hasMusic(PLAYER));
        assertNull(s.status(PLAYER));
    }
}
