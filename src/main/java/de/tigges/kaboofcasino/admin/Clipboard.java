package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.machine.MachineSettings;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The server side of "Kopieren" ({@code CasinoSettings.COPIED}): token -> the copied win chance and bias, at most
 * {@value #MAX} tokens (the oldest goes first), saved with the world so a copy survives a restart (the mod keeps it in
 * its world data, {@code clipboard}). Text form for the overworld's PDC: {@code token=chance,bias;...} with {@code i}
 * for an inherited bias. Pure; thread: MAIN.
 */
final class Clipboard {

    static final int MAX = 256;

    /** One copied machine's croupier values ({@code CasinoSettings.Copied}). */
    record Copied(int chance, int bias) {
        Copied {
            if (chance < MachineSettings.INHERIT_CHANCE || chance > 100 || bias != MachineSettings.INHERIT_BIAS
                    && (bias < MachineSettings.MIN_BIAS || bias > MachineSettings.MAX_BIAS)) {
                throw new IllegalArgumentException("copied values out of range");
            }
        }

        /** What a lost token pastes ({@code getOrDefault(token, new Copied(-1, MIN_VALUE))}). */
        static final Copied NONE = new Copied(MachineSettings.INHERIT_CHANCE, MachineSettings.INHERIT_BIAS);
    }

    private final LinkedHashMap<UUID, Copied> copies = new LinkedHashMap<>();

    /** Stores a copy under a new token (evicting the oldest beyond {@value #MAX}). */
    void put(UUID token, Copied copied) {
        copies.remove(token);
        while (copies.size() >= MAX) {
            Iterator<UUID> oldest = copies.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        copies.put(token, copied);
    }

    Optional<Copied> get(UUID token) {
        return Optional.ofNullable(copies.get(token));
    }

    int size() {
        return copies.size();
    }

    String encode() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<UUID, Copied> e : copies.entrySet()) {
            if (!out.isEmpty()) {
                out.append(';');
            }
            Copied c = e.getValue();
            out.append(e.getKey()).append('=').append(c.chance()).append(',')
                    .append(c.bias() == MachineSettings.INHERIT_BIAS ? "i" : Integer.toString(c.bias()));
        }
        return out.toString();
    }

    /** Reads {@link #encode}; unreadable or out-of-range entries are skipped (the mod's {@code loadWorld}). */
    static Clipboard decode(String text) {
        Clipboard board = new Clipboard();
        if (text == null || text.isEmpty()) {
            return board;
        }
        for (String entry : text.split(";")) {
            try {
                int eq = entry.indexOf('=');
                int comma = entry.indexOf(',', eq + 1);
                if (eq < 0 || comma < 0) {
                    continue;
                }
                UUID token = UUID.fromString(entry.substring(0, eq));
                int chance = Integer.parseInt(entry.substring(eq + 1, comma));
                String b = entry.substring(comma + 1);
                int bias = b.equals("i") ? MachineSettings.INHERIT_BIAS : Integer.parseInt(b);
                board.put(token, new Copied(chance, bias));
            } catch (IllegalArgumentException e) {
                // skipped like the mod skips invalid clipboard entries
            }
        }
        return board;
    }
}
