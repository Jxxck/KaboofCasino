package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Pos;
import java.util.Objects;
import java.util.UUID;

/**
 * One machine in the world, stored in the chunk PDC of its anchor (server.md 3.5): saved with the chunk's blocks,
 * so carriers and record roll back together after a crash.
 *
 * @param number per-type running number ("Spielautomat #3"), from the DB at placement
 * @param data   game-specific state that must survive a reload (slot: the last reel stops, "0,0,0")
 * @param own    the croupier's-hand settings of this machine (name, win chance, bias); {@link MachineSettings.Own#NONE}
 *               for records written before they existed
 */
public record MachineRecord(UUID id, MachineType type, Pos anchor, Facing facing, UUID placer, long placedAt,
                            int number, String data, MachineSettings.Own own) {

    /**
     * Format version. Still 1: the own settings are optional keys that are only written when set, and readers of
     * version 1 ignore keys they do not know.
     */
    public static final int VERSION = 1;

    public MachineRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(facing);
        Objects.requireNonNull(placer);
        Objects.requireNonNull(data);
        Objects.requireNonNull(own);
    }

    /** A record without own settings (a newly placed machine). */
    public MachineRecord(UUID id, MachineType type, Pos anchor, Facing facing, UUID placer, long placedAt, int number,
                         String data) {
        this(id, type, anchor, facing, placer, placedAt, number, data, MachineSettings.Own.NONE);
    }

    public MachineRecord withData(String newData) {
        return new MachineRecord(id, type, anchor, facing, placer, placedAt, number, newData, own);
    }

    public MachineRecord withOwn(MachineSettings.Own newOwn) {
        return new MachineRecord(id, type, anchor, facing, placer, placedAt, number, data, newOwn);
    }
}
