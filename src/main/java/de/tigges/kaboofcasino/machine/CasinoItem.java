package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.Msg;
import java.util.Optional;

/**
 * Casino items. All are {@code minecraft:paper} with {@code item_model} (render.md 2.4); identity is only the PDC tag
 * {@code kaboofcasino:item} (server.md 3.1). Paper has vanilla uses that {@link ItemVetoListener} blocks.
 */
public enum CasinoItem {
    SLOT_MACHINE("slot_machine", "roulettecasino:slot_machine", Msg.ITEM_SLOT_MACHINE, MachineType.SLOT_MACHINE),
    SLOT_MACHINE_REEL("slot_machine_reel", "roulettecasino:slot_machine_reel", Msg.ITEM_SLOT_REEL, null),
    SLOT_MACHINE_MECHANISM("slot_machine_mechanism", "roulettecasino:slot_machine_mechanism",
            Msg.ITEM_SLOT_MECHANISM, null),
    ROULETTE_MECHANISM("roulette_mechanism", "roulettecasino:roulette_mechanism", Msg.ITEM_ROULETTE_MECHANISM,
            null),
    FORTUNE_WHEEL("fortune_wheel", "roulettecasino:fortune_wheel", Msg.ITEM_FORTUNE_WHEEL,
            MachineType.FORTUNE_WHEEL),
    PLINKO("plinko", "roulettecasino:plinko", Msg.ITEM_PLINKO, MachineType.PLINKO),
    ROULETTE_TABLE("roulette_table", "roulettecasino:roulette_table", Msg.ITEM_ROULETTE_TABLE,
            MachineType.ROULETTE_TABLE);

    private final String id;
    private final String model;
    private final Msg name;
    private final MachineType machine;

    CasinoItem(String id, String model, Msg name, MachineType machine) {
        this.id = id;
        this.model = model;
        this.name = name;
        this.machine = machine;
    }

    public String id() {
        return id;
    }

    public String model() {
        return model;
    }

    public Msg nameMsg() {
        return name;
    }

    /** The machine this item places, if any. */
    public Optional<MachineType> machine() {
        return Optional.ofNullable(machine);
    }

    /** The item that places {@code type} (dropped when the machine is broken). */
    public static CasinoItem forMachine(MachineType type) {
        for (CasinoItem item : values()) {
            if (item.machine == type) {
                return item;
            }
        }
        throw new IllegalStateException("no item places " + type);
    }

    public static Optional<CasinoItem> byId(String id) {
        for (CasinoItem item : values()) {
            if (item.id.equals(id)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
