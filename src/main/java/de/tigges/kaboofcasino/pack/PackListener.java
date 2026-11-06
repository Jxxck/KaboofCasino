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
 * Sends the pack in the configuration phase (render.md 8.2): Paper runs this event on a configuration thread and
 * continues only after the handler returns, so the client downloads and loads the pack before it enters the world
 * (no reload in the world, permadeath-safe). The handler waits for a terminal status up to
 * {@code pack.timeout-seconds}.
 * <ul>
 *   <li>{@code pack.enforce: false} (default, kill switch): decline, failure or timeout only get a chat hint in the
 *       world; nobody is disconnected.</li>
 *   <li>{@code pack.enforce: true}: anything but SUCCESSFULLY_LOADED disconnects in the configuration phase, so the
 *       player never enters the world (no join/quit/DeathBan).</li>
 * </ul>
 * EgorsFakePlayer bots join without a configuration phase, so they never get the request and are exempt by design.
 * A new pack (new SHA-1) applies at the next login only.
 */
public final class PackListener implements Listener {

    private final Supplier<CasinoConfig> config;
    private final Supplier<Messages> messages;
    private final PackFile pack;
    private final PackSessions sessions;

    public PackListener(Supplier<CasinoConfig> config, Supplier<Messages> messages, PackFile pack,
                        PackSessions sessions) {
        this.config = config;
        this.messages = messages;
        this.pack = pack;
        this.sessions = sessions;
    }

    URI uri() {
        CasinoConfig.Pack p = config.get().pack();
        return URI.create("http://" + p.urlHost() + ":" + p.publicPort() + pack.path());
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        CasinoConfig.Pack p = config.get().pack();
        if (!p.enabled()) {
            return;
        }
        PlayerConfigurationConnection connection = event.getConnection();
        UUID id = connection.getProfile().getId();
        String name = connection.getProfile().getName();
        CompletableFuture<ResourcePackStatus> done = new CompletableFuture<>();
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PackFile.PACK_ID, uri(), pack.sha1()))
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
        ResourcePackStatus status = await(done, connection, p.timeoutSeconds() * 1000L);
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
        if (status != ResourcePackStatus.SUCCESSFULLY_LOADED && p.enforce() && connection.isConnected()) {
            connection.disconnect(messages.get().render(Msg.PACK_KICK));
        }
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
                return done.get(250, TimeUnit.MILLISECONDS);
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
