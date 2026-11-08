package de.tigges.kaboofcasino.pack;

import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.Log;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Sends the packs in the configuration phase (render.md 8.2): Paper runs this event on a configuration thread and
 * continues only after the handler returns, so the client downloads and loads the packs before it enters the world
 * (no reload in the world, permadeath-safe). The handler waits for a terminal status up to
 * {@code pack.timeout-seconds}.
 * <ul>
 *   <li>{@code pack.enforce: false} (default, kill switch): decline, failure or timeout only get a chat hint in the
 *       world; nobody is disconnected.</li>
 *   <li>{@code pack.enforce: true}: anything but SUCCESSFULLY_LOADED disconnects in the configuration phase, so the
 *       player never enters the world (no join/quit/DeathBan).</li>
 * </ul>
 * <b>Music pack</b> ({@code music.enabled}, render.md 6): a second request with {@code required(false)}, sent only
 * after the casino pack is SUCCESSFULLY_LOADED. Never earlier: the client merges prompts that are open at the same
 * time into one dialog, and a failed download marks every pack that is not active yet as discarded
 * ({@code ServerPackManager.onDownload}), which would take the casino pack down with a failed music download. Its
 * outcome never disconnects anybody, whatever {@code pack.enforce} says; without an answer within the timeout the
 * pack is popped again, so the client never reloads its resources later in the world.
 *
 * <p>EgorsFakePlayer bots join without a configuration phase, so they never get the requests and are exempt by
 * design. A new pack (new SHA-1) applies at the next login only.
 */
public final class PackListener implements Listener {

    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final PackFile pack;
    private final PackFile music;
    private final PackSessions sessions;
    /** Milliseconds per configured timeout second (tests shorten it). */
    long millisPerTimeoutSecond = 1000L;
    /** Players who turned the lounge music off ({@code /casino musik aus}): no music pack is offered to them. */
    private volatile java.util.function.Predicate<UUID> musicOff = id -> false;

    /** Sets who gets no music pack offer (thread-safe predicate; read on the configuration threads). */
    public void musicOptOut(java.util.function.Predicate<UUID> musicOff) {
        this.musicOff = musicOff == null ? id -> false : musicOff;
    }

    /**
     * @param music the optional music pack, or null (never offered)
     */
    public PackListener(Supplier<CasinoConfig> config, Supplier<Messages> messages, PackFile pack, PackFile music,
                        PackSessions sessions) {
        this.config = config;
        this.messages = messages;
        this.pack = pack;
        this.music = music;
        this.sessions = sessions;
    }

    URI uri(PackFile file) {
        CasinoConfig.Pack p = config.get().pack();
        return URI.create("http://" + p.urlHost() + ":" + p.publicPort() + file.path());
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        configure(event.getConnection());
    }

    /** The configuration-phase work (configuration thread; blocking here is intended). */
    void configure(PlayerConfigurationConnection connection) {
        CasinoConfig c = config.get();
        CasinoConfig.Pack p = c.pack();
        if (!p.enabled()) {
            return;
        }
        UUID id = connection.getProfile().getId();
        String name = connection.getProfile().getName();
        CompletableFuture<ResourcePackStatus> done = new CompletableFuture<>();
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PackFile.PACK_ID, uri(pack), pack.sha1()))
                .required(p.enforce())
                .replace(false)
                .prompt(messages.get().render(Msg.PACK_PROMPT))
                .callback((packId, status, audience) -> {
                    if (!status.intermediate()) {
                        done.complete(status);
                    }
                })
                .build();
        connection.getAudience().sendResourcePacks(request);
        ResourcePackStatus status = await(done, connection, p.timeoutSeconds() * millisPerTimeoutSecond);
        if (!connection.isConnected()) {
            Log.get().info("Resource pack for {}: the connection closed while waiting", name);
            return;
        }
        if (id != null) {
            if (status == null) {
                sessions.forget(id);
            } else {
                sessions.put(id, status);
            }
        }
        Log.get().info("Resource pack for {}: {}", name, status == null ? "no answer within "
                + p.timeoutSeconds() + " s" : status);
        if (status != ResourcePackStatus.SUCCESSFULLY_LOADED) {
            if (p.enforce() && connection.isConnected()) {
                connection.disconnect(messages.get().render(Msg.PACK_KICK));
            }
            return;
        }
        if (music != null && c.music().enabled()) {
            if (id != null && musicOff.test(id)) {
                Log.get().info("Music pack for {}: not offered (the player turned the music off)", name);
            } else {
                offerMusic(connection, id, name, p);
            }
        }
    }

    /** The optional second pack. Never disconnects. */
    private void offerMusic(PlayerConfigurationConnection connection, UUID id, String name, CasinoConfig.Pack p) {
        CompletableFuture<ResourcePackStatus> done = new CompletableFuture<>();
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(music.id(), uri(music), music.sha1()))
                .required(false)
                .replace(false)
                .prompt(messages.get().render(Msg.PACK_MUSIC_PROMPT))
                .callback((packId, status, audience) -> {
                    if (!status.intermediate()) {
                        done.complete(status);
                    }
                })
                .build();
        connection.getAudience().sendResourcePacks(request);
        ResourcePackStatus status = await(done, connection, p.timeoutSeconds() * millisPerTimeoutSecond);
        if (!connection.isConnected()) {
            Log.get().info("Music pack for {}: the connection closed while waiting", name);
            return;
        }
        if (status == null) {
            // still downloading: pop it, or the client would apply it later with a resource reload in the world
            connection.getAudience().removeResourcePacks(music.id());
        }
        if (id != null) {
            sessions.putMusic(id, status);
        }
        Log.get().info("Music pack for {}: {}", name, status == null ? "no answer within " + p.timeoutSeconds()
                + " s (withdrawn)" : status);
    }

    /**
     * Waits for a terminal status in short slices and gives up early when the connection is gone (no configuration
     * thread is kept waiting for a client that left). Null on timeout or departure.
     */
    static ResourcePackStatus await(CompletableFuture<ResourcePackStatus> done, PlayerConfigurationConnection connection,
                                    long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (true) {
            try {
                return done.get(Math.max(1L, Math.min(250L, timeoutMillis)), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (!connection.isConnected() || System.nanoTime() >= deadline) {
                    return null;
                }
            } catch (ExecutionException e) {
                return ResourcePackStatus.FAILED_DOWNLOAD;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        CasinoConfig.Pack p = config.get().pack();
        sessions.required(p.enabled());
        if (p.enabled() && !sessions.hasPack(event.getPlayer().getUniqueId())
                && sessions.status(event.getPlayer().getUniqueId()) != null) {
            messages.get().send(event.getPlayer(), Msg.PACK_DECLINED);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.forget(event.getPlayer().getUniqueId());
    }
}
