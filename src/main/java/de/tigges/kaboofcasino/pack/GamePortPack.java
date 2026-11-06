package de.tigges.kaboofcasino.pack;

import io.papermc.paper.network.ChannelInitializeListenerHolder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;

/**
 * Serves the pack on the Minecraft port itself (config {@code pack.serve: minecraft-port}), like Polymer's autohost:
 * Paper calls our {@code ChannelInitializeListener} for every new connection and {@link PackSniffer} sits at the very
 * front of its pipeline. HTTP requests for {@code /pack/<sha1>.zip} are answered there; everything else (handshake,
 * status ping, legacy ping) is replayed untouched to the game pipeline. Needs no extra port forward.
 *
 * <p>{@link #remove()} (onDisable) unregisters the listener and turns every handler that is still installed into a
 * pass-through. Downloads run on netty's event loop, never on MAIN.
 */
public final class GamePortPack {

    static final Key LISTENER = Key.key("kaboofcasino", "pack");
    static final String HANDLER = "kaboofcasino_pack";
    /** Concurrent downloads; more get 503 + Retry-After (the client retries at its next login). */
    static final int MAX_DOWNLOADS = 8;
    static final long SNIFF_TIMEOUT_MILLIS = 5_000;

    private final PackFile pack;
    private final Logger log;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger served = new AtomicInteger();

    public GamePortPack(PackFile pack, Logger log) {
        this.pack = pack;
        this.log = log;
    }

    /** Registers the channel listener (onEnable). */
    public void install() {
        ChannelInitializeListenerHolder.addListener(LISTENER, channel -> {
            if (enabled.get()) {
                channel.pipeline().addFirst(HANDLER, new PackSniffer(this));
            }
        });
        log.info("Resource pack is served on the game port at {}", pack.path());
    }

    /** onDisable: no new handlers, and installed ones pass everything through. */
    public void remove() {
        disable();
        if (ChannelInitializeListenerHolder.hasListener(LISTENER)) {
            ChannelInitializeListenerHolder.removeListener(LISTENER);
        }
    }

    /** Turns every installed handler into a pass-through. */
    void disable() {
        enabled.set(false);
    }

    boolean enabled() {
        return enabled.get();
    }

    PackFile pack() {
        return pack;
    }

    boolean tryAcquire() {
        while (true) {
            int n = active.get();
            if (n >= MAX_DOWNLOADS) {
                return false;
            }
            if (active.compareAndSet(n, n + 1)) {
                return true;
            }
        }
    }

    void release() {
        active.decrementAndGet();
    }

    void served(String method, int status, long bytes, long millis) {
        if (status == 200 && method.equals("GET")) {
            log.info("Resource pack sent over the game port ({} bytes in {} ms, #{})", bytes, millis,
                    served.incrementAndGet());
        } else {
            log.info("Game-port HTTP {} answered {}", method, status);
        }
    }

    Logger log() {
        return log;
    }
}
