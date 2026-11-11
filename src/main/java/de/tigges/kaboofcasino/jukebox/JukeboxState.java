package de.tigges.kaboofcasino.jukebox;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * What a jukebox is set to (stored in the machine record's data, {@link #encode}/{@link #decode}): playing or not, the
 * current track (its genre is the playlist), the mode and the range. The timeline itself (when the current track
 * started) lives only at runtime: after a restart or a chunk load the current track starts again from its beginning.
 * Pure.
 */
public record JukeboxState(boolean playing, String track, Mode mode, int range) {

    /** In list order, shuffled within the genre, or the one track again and again. */
    public enum Mode {
        ORDER("order"), SHUFFLE("shuffle"), REPEAT("repeat");

        final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static Mode of(String id) {
            for (Mode m : values()) {
                if (m.id.equals(id)) {
                    return m;
                }
            }
            return ORDER;
        }
    }

    /** The ranges a jukebox can be set to (blocks), the default first. */
    public static final List<Integer> RANGES = List.of(32, 48, 16);
    public static final int DEFAULT_RANGE = 32;

    public JukeboxState {
        if (!RANGES.contains(range)) {
            range = DEFAULT_RANGE;
        }
        mode = mode == null ? Mode.ORDER : mode;
    }

    /** A new jukebox: stopped, on the first track everybody can hear (not a lounge track of the music pack). */
    public static JukeboxState fresh(JukeboxCatalog catalog) {
        String first = catalog.tracks().stream().filter(t -> !t.needsMusicPack()).findFirst()
                .orElse(catalog.tracks().getFirst()).id();
        return new JukeboxState(false, first, Mode.ORDER, DEFAULT_RANGE);
    }

    public String encode() {
        return "v=1;on=" + (playing ? 1 : 0) + ";t=" + track + ";m=" + mode.id + ";r=" + range;
    }

    /** The stored state; anything unreadable or unknown falls back to the fresh jukebox's values. */
    public static JukeboxState decode(String data, JukeboxCatalog catalog) {
        JukeboxState d = fresh(catalog);
        if (data == null || data.isEmpty()) {
            return d;
        }
        boolean on = d.playing;
        String track = d.track;
        Mode mode = d.mode;
        int range = d.range;
        for (String part : data.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = part.substring(0, eq);
            String v = part.substring(eq + 1);
            switch (k) {
                case "on" -> on = v.equals("1");
                case "t" -> track = catalog.track(v).isPresent() ? v : track;
                case "m" -> mode = Mode.of(v);
                case "r" -> {
                    try {
                        range = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        range = d.range;
                    }
                }
                default -> {
                }
            }
        }
        return new JukeboxState(on, track, mode, range);
    }

    public JukeboxState withPlaying(boolean on) {
        return new JukeboxState(on, track, mode, range);
    }

    public JukeboxState withTrack(String id) {
        return new JukeboxState(playing, id, mode, range);
    }

    public JukeboxState withNextMode() {
        return new JukeboxState(playing, track, mode.next(), range);
    }

    public JukeboxState withNextRange() {
        int i = RANGES.indexOf(range);
        return new JukeboxState(playing, track, mode, RANGES.get((i + 1) % RANGES.size()));
    }

    /**
     * The track after the current one in its genre: the next in the list (after the last the first), a random other
     * one when shuffled ({@code random} gives a number below its argument), the same one on repeat.
     */
    public String next(JukeboxCatalog catalog, IntUnaryOperator random) {
        return next(catalog, random, mode);
    }

    /** "Nächster Titel" by hand: like {@link #next}, but repeat goes on in list order (a skip should skip). */
    public String skipped(JukeboxCatalog catalog, IntUnaryOperator random) {
        return next(catalog, random, mode == Mode.REPEAT ? Mode.ORDER : mode);
    }

    private String next(JukeboxCatalog catalog, IntUnaryOperator random, Mode mode) {
        JukeboxCatalog.Track current = catalog.track(track).orElse(catalog.tracks().getFirst());
        List<JukeboxCatalog.Track> list = catalog.of(current.genre());
        int at = list.indexOf(current);
        return switch (mode) {
            case REPEAT -> current.id();
            case ORDER -> list.get((at + 1) % list.size()).id();
            case SHUFFLE -> {
                if (list.size() < 2) {
                    yield current.id();
                }
                int pick = random.applyAsInt(list.size() - 1);
                yield list.get(pick >= at ? pick + 1 : pick).id();
            }
        };
    }
}
