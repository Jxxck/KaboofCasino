package de.tigges.kaboofcasino.pack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The download pools of the served packs ({@link DownloadPolicy}): one counter per pack, so a pack can never take
 * another pack's slots. Also counts started downloads and cuts per verdict (logs, tests). Thread-safe (netty event
 * loops, HTTP server threads, the watchdog).
 */
final class DownloadSlots {

    /** One pack's pool. */
    static final class Pool {
        private final PackFile pack;
        private final DownloadPolicy.Limits limits;
        private final AtomicInteger active = new AtomicInteger();

        private Pool(PackFile pack, DownloadPolicy.Limits limits) {
            this.pack = pack;
            this.limits = limits;
        }

        PackFile pack() {
            return pack;
        }

        DownloadPolicy.Limits limits() {
            return limits;
        }

        boolean tryAcquire() {
            while (true) {
                int n = active.get();
                if (n >= limits.slots()) {
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

        int active() {
            return active.get();
        }
    }

    private final DownloadPolicy policy;
    private final List<Pool> pools = new ArrayList<>();
    private final AtomicLong started = new AtomicLong();
    private final Map<DownloadPolicy.Verdict, AtomicInteger> cuts = new EnumMap<>(DownloadPolicy.Verdict.class);

    DownloadSlots(DownloadPolicy policy, List<PackFile> packs) {
        if (packs.isEmpty()) {
            throw new IllegalArgumentException("no pack to serve");
        }
        this.policy = policy;
        for (PackFile p : packs) {
            pools.add(new Pool(p, policy.limits(p)));
        }
        for (DownloadPolicy.Verdict v : DownloadPolicy.Verdict.values()) {
            cuts.put(v, new AtomicInteger());
        }
    }

    DownloadPolicy policy() {
        return policy;
    }

    List<Pool> pools() {
        return List.copyOf(pools);
    }

    /** The pool serving {@code path} (query already removed), or null (404). */
    Pool find(String path) {
        for (Pool p : pools) {
            if (p.pack().path().equals(path)) {
                return p;
            }
        }
        return null;
    }

    /** The pool of {@code pack}, or null. */
    Pool of(PackFile pack) {
        for (Pool p : pools) {
            if (p.pack() == pack) {
                return p;
            }
        }
        return null;
    }

    /** Number for the next download (logs). */
    long nextNumber() {
        return started.incrementAndGet();
    }

    void cut(DownloadPolicy.Verdict verdict) {
        cuts.get(verdict).incrementAndGet();
    }

    int cuts(DownloadPolicy.Verdict verdict) {
        return cuts.get(verdict).get();
    }

    /** "casino 2/8, music 1/4". */
    String occupancy() {
        StringBuilder b = new StringBuilder();
        for (Pool p : pools) {
            if (!b.isEmpty()) {
                b.append(", ");
            }
            b.append(p.limits().name()).append(' ').append(p.active()).append('/').append(p.limits().slots());
        }
        return b.toString();
    }
}
