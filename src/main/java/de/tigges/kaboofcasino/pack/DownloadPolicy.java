package de.tigges.kaboofcasino.pack;

import java.util.concurrent.TimeUnit;

/**
 * Slots and deadlines of pack downloads (game port and own HTTP server alike). Pure.
 * <ul>
 *   <li><b>Slots per pack</b>: the casino pack and the music pack have separate pools, so music downloads (22 MB, at
 *       a deploy restart everybody downloads it at once) can never take a slot the required casino pack needs; a
 *       full pool answers 503 + Retry-After.</li>
 *   <li><b>Deadlines per download</b> (checked every {@link #checkMillis}): cut when nothing was accepted for
 *       {@link #stallNanos} (the client stopped reading), when the average after {@link #graceNanos} is below
 *       {@link #minBytesPerSecond}, or at the pack's hard cap. A cut frees the slot at once, so idle or trickling
 *       connections can no longer block downloads (and with {@code pack.enforce} logins) until a restart.</li>
 * </ul>
 *
 * @param casino            limits of the required casino pack
 * @param music             limits of the optional music pack
 * @param graceNanos        the minimum rate applies from this age on
 * @param minBytesPerSecond average body bytes per second a download must reach after the grace time
 * @param stallNanos        longest time without any accepted bytes
 * @param checkMillis       how often a running download is checked
 */
record DownloadPolicy(Limits casino, Limits music, long graceNanos, long minBytesPerSecond, long stallNanos,
                      long checkMillis) {

    /**
     * @param name         {@code casino} or {@code music} (logs)
     * @param slots        concurrent downloads of this pack
     * @param hardCapNanos a download of this pack is cut at this age whatever its rate
     */
    record Limits(String name, int slots, long hardCapNanos) {
    }

    /** Why a running download is cut (or {@link #OK}). */
    enum Verdict {
        OK("running"),
        STALLED("nothing accepted"),
        TOO_SLOW("below the minimum rate"),
        OVERTIME("hard time limit");

        private final String text;

        Verdict(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    static final DownloadPolicy DEFAULT = new DownloadPolicy(
            new Limits("casino", 16, TimeUnit.SECONDS.toNanos(300)),
            new Limits("music", 4, TimeUnit.SECONDS.toNanos(180)),
            TimeUnit.SECONDS.toNanos(20), 16L * 1024, TimeUnit.SECONDS.toNanos(15), 1000L);

    /** The limits of {@code pack}: the music pack's, else the casino pack's. */
    Limits limits(PackFile pack) {
        return PackFile.MUSIC_ID.equals(pack.id()) ? music : casino;
    }

    /**
     * @param elapsedNanos       age of the download
     * @param sinceProgressNanos time since bytes were last accepted (or since the start)
     * @param sentBytes          body bytes accepted so far
     */
    Verdict verdict(Limits limits, long elapsedNanos, long sinceProgressNanos, long sentBytes) {
        if (elapsedNanos >= limits.hardCapNanos()) {
            return Verdict.OVERTIME;
        }
        if (sinceProgressNanos >= stallNanos) {
            return Verdict.STALLED;
        }
        if (elapsedNanos >= graceNanos
                && (double) sentBytes * 1_000_000_000L < (double) minBytesPerSecond * elapsedNanos) {
            return Verdict.TOO_SLOW;
        }
        return Verdict.OK;
    }
}
