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
            MachineType.ROULETTE_TABLE),
    /**
     * The blackjack and poker recipes' ingredient; the mod hands it out for a hired croupier (games.md 10.3: a hiring
     * card used on a croupier).
     */
    CROUPIER_BADGE("croupier_badge", "roulettecasino:croupier_badge", Msg.ITEM_CROUPIER_BADGE, null),
    /** The croupier's one trade (24 emeralds); used on a croupier it hires him for a badge (games.md 10.2-10.3). */
    CROUPIER_HIRING_CARD("croupier_hiring_card", "roulettecasino:croupier_hiring_card", Msg.ITEM_HIRING_CARD, null),
    /** The casino guide book (games.md 11): opens the guide dialog. */
    CASINO_GUIDE("casino_guide", "roulettecasino:casino_guide", Msg.ITEM_CASINO_GUIDE, null),
    /**
     * The croupier's hand (games.md 10.5, 20.1): admins in creative set up the machines with it
     * ({@code de.tigges.kaboofcasino.admin}); stack size 1, uncommon, no recipe ({@code /casino give croupier_hand}).
     */
    CROUPIER_HAND("croupier_hand", "roulettecasino:croupier_hand", Msg.ITEM_CROUPIER_HAND, null),
    BLACKJACK_TABLE("blackjack_table", "roulettecasino:blackjack_table", Msg.ITEM_BLACKJACK_TABLE,
            MachineType.BLACKJACK_TABLE),
    POKER_TABLE("poker_table", "roulettecasino:poker_table", Msg.ITEM_POKER_TABLE, MachineType.POKER_TABLE),
    /** The four racing ducks: crafting components of the derby mechanism only (not bets, games.md 7). */
    DERBY_DUCK_YELLOW("derby_duck_yellow", "roulettecasino:derby_duck_yellow", Msg.ITEM_DERBY_DUCK_YELLOW, null),
    DERBY_DUCK_RED("derby_duck_red", "roulettecasino:derby_duck_red", Msg.ITEM_DERBY_DUCK_RED, null),
    DERBY_DUCK_WHITE("derby_duck_white", "roulettecasino:derby_duck_white", Msg.ITEM_DERBY_DUCK_WHITE, null),
    DERBY_DUCK_BLACK("derby_duck_black", "roulettecasino:derby_duck_black", Msg.ITEM_DERBY_DUCK_BLACK, null),
    DUCK_DERBY_MECHANISM("duck_derby_mechanism", "roulettecasino:duck_derby_mechanism", Msg.ITEM_DERBY_MECHANISM,
            null),
    DUCK_DERBY("duck_derby", "roulettecasino:duck_derby", Msg.ITEM_DUCK_DERBY, MachineType.DUCK_DERBY);

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
