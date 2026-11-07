package de.tigges.kaboofcasino.pack;

import io.papermc.paper.network.ChannelInitializeListenerHolder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;

/**
 * Serves the packs on the Minecraft port itself (config {@code pack.serve: minecraft-port}), like Polymer's autohost:
 * Paper calls our {@code ChannelInitializeListener} for every new connection and {@link PackSniffer} sits at the very
 * front of its pipeline. HTTP requests for {@code /pack/<sha1>.zip} of one of the packs (the casino pack and the
 * optional music pack) are answered there; everything else (handshake, status ping, legacy ping) is replayed
 * untouched to the game pipeline. Needs no extra port forward.
 *
 * <p>Downloads take a slot of their pack's own pool and run under the deadlines of {@link DownloadPolicy} (casino
 * 16 slots / 300 s, music 4 slots / 180 s, cut when stalled for 15 s or below 16 KiB/s after 20 s). One INFO line when
 * a download starts and one when it ends, with the pools' occupancy.
 *
 * <p>{@link #remove()} (onDisable) unregisters the listener and turns every handler that is still installed into a
 * pass-through. Downloads run on netty's event loop, never on MAIN.
 */
public final class GamePortPack {

    static final Key LISTENER = Key.key("kaboofcasino", "pack");
    static final String HANDLER = "kaboofcasino_pack";
    static final long SNIFF_TIMEOUT_MILLIS = 5_000;

    private final DownloadSlots slots;
    private final Logger log;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public GamePortPack(PackFile pack, Logger log) {
        this(List.of(pack), log);
    }

    /** @param packs every pack that is served (distinct SHA-1, so distinct paths) */
    public GamePortPack(List<PackFile> packs, Logger log) {
        this(packs, log, DownloadPolicy.DEFAULT);
    }

    GamePortPack(List<PackFile> packs, Logger log, DownloadPolicy policy) {
        this.slots = new DownloadSlots(policy, packs);
        this.log = log;
    }

    /** Registers the channel listener (onEnable). */
    public void install() {
        ChannelInitializeListenerHolder.addListener(LISTENER, channel -> {
            if (enabled.get()) {
                channel.pipeline().addFirst(HANDLER, new PackSniffer(this));
            }
        });
        log.info("Resource packs are served on the game port ({}): {}", slots.occupancy(), paths());
    }

    private String paths() {
        StringBuilder b = new StringBuilder();
        for (DownloadSlots.Pool p : slots.pools()) {
            if (!b.isEmpty()) {
                b.append(", ");
            }
            b.append(p.limits().name()).append(' ').append(p.pack().path());
        }
        return b.toString();
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

    DownloadSlots slots() {
        return slots;
    }

    /** The pack served at {@code path} (query already removed), or null. */
    PackFile find(String path) {
        DownloadSlots.Pool pool = slots.find(path);
        return pool == null ? null : pool.pack();
    }

    /** A short answer (HEAD, 404, 405, 503, 400). */
    void answered(String method, int status) {
        log.info("Game-port HTTP {} answered {}", method, status);
    }

    void started(long number, PackFile pack) {
        log.info("Resource pack ({}) download #{} started on the game port ({})", pack.name(), number,
                slots.occupancy());
    }

    void finished(long number, PackFile pack, long bytes, long millis) {
        log.info("Resource pack ({}) download #{} sent: {} bytes in {} ms ({})", pack.name(), number, bytes, millis,
                slots.occupancy());
    }

    void cut(long number, PackFile pack, String why, long sent, long millis) {
        log.info("Resource pack ({}) download #{} cut ({}): {}/{} bytes after {} ms ({})", pack.name(), number, why,
                sent, pack.size(), millis, slots.occupancy());
    }

    Logger log() {
        return log;
    }
}
