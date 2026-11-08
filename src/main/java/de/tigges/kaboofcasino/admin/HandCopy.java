package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.Optional;
import java.util.UUID;

/**
 * What "Kopieren" puts into the croupier's hand ({@code CasinoSettings} {@code copy}: {@code MineVegasType},
 * {@code MineVegasCopyToken}, {@code MineVegasCopyPosition}, {@code MineVegasCopyName}, {@code MineVegasCopyNumber}):
 * the source machine's type, position, name and number for the tooltip, and a random token. The values themselves
 * (win chance, bias) stay on the server under that token ({@link Clipboard}), as in the mod. Stored in the hand's
 * PDC as one string {@code 1|type|token|x|y|z|number|name} (the name last: it may hold {@code |}). Pure.
 *
 * @param number the source's running number (0 = none: the tooltip shows the type name only)
 */
record HandCopy(MachineType type, UUID token, int x, int y, int z, int number, String name) {

    static final String VERSION = "1";
    private static final int FIELDS = 8;

    HandCopy {
        if (type == null || token == null || number < 0 || name == null
                || MachineSettings.validName(name).filter(name::equals).isEmpty()) {
            throw new IllegalArgumentException("invalid hand copy");
        }
    }

    String encode() {
        return String.join("|", VERSION, type.id(), token.toString(), Integer.toString(x), Integer.toString(y),
                Integer.toString(z), Integer.toString(number), name);
    }

    /** The copy in a hand's PDC string; empty for anything else (missing, older or foreign data, forged values). */
    static Optional<HandCopy> decode(String text) {
        if (text == null || text.length() > 512) {
            return Optional.empty();
        }
        String[] f = text.split("\\|", FIELDS);
        if (f.length != FIELDS || !VERSION.equals(f[0])) {
            return Optional.empty();
        }
        try {
            Optional<MachineType> type = MachineType.byId(f[1]);
            UUID token = UUID.fromString(f[2]);
            if (type.isEmpty() || !token.toString().equals(f[2])) {
                return Optional.empty();
            }
            return Optional.of(new HandCopy(type.get(), token, Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]), f[7]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
