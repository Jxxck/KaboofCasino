package de.tigges.kaboofcasino.config;

/** Keys of messages_de.yml. Every key must exist in the shipped file (MessagesTest). */
public enum Msg {
    PREFIX("prefix"),
    SLOT_NO_BET("slot.no-bet"),
    SLOT_BUSY("slot.busy"),
    SLOT_SPACING("slot.spacing"),
    MACHINE_IN_USE("machine.in-use"),
    MACHINE_NOT_YOURS("machine.not-yours"),
    MACHINE_RUNNING("machine.running"),
    MACHINE_NO_SPACE("machine.no-space"),
    MACHINE_NO_SUPPORT("machine.no-support"),
    MACHINE_PLACE_DENIED("machine.place-denied"),
    MACHINE_REMOVED("machine.removed"),
    MACHINE_NONE("machine.none"),
    MACHINE_IDLE_CLOSED("machine.idle-closed"),
    BET_NOT_ALLOWED("bet.not-allowed"),
    BET_NOT_PLAIN("bet.not-plain"),
    BET_NOT_ENOUGH("bet.not-enough"),
    BET_MAX("bet.max"),
    OWED_PENDING("owed.pending"),
    OWED_DELIVERED("owed.delivered"),
    OWED_REMAINING("owed.remaining"),
    OWED_NONE("owed.none"),
    ROUNDS_FULL("rounds.full"),
    ERROR_CREATIVE("error.creative"),
    ERROR_NOT_USABLE("error.not-usable"),
    ERROR_UNSAFE("error.unsafe"),
    ERROR_DANGER_CLOSE("error.danger-close"),
    ERROR_NO_PACK("error.no-pack"),
    ERROR_INTERNAL("error.internal"),
    ERROR_NO_PERMISSION("error.no-permission"),
    PACK_PROMPT("pack.prompt"),
    PACK_KICK("pack.kick"),
    PACK_DECLINED("pack.declined"),
    COMMAND_INFO("command.info"),
    COMMAND_USAGE("command.usage"),
    COMMAND_GIVE_DONE("command.give-done"),
    COMMAND_GIVE_UNKNOWN("command.give-unknown"),
    COMMAND_PLAYER_UNKNOWN("command.player-unknown"),
    COMMAND_RELOADED("command.reloaded"),
    COMMAND_STATS_HEADER("command.stats-header"),
    COMMAND_STATS_LINE("command.stats-line"),
    COMMAND_STATS_EMPTY("command.stats-empty"),
    COMMAND_CREDITS("command.credits"),
    COMMAND_OPEN_ROUNDS("command.open-rounds"),
    GUI_SLOT_TITLE("gui.slot.title"),
    GUI_SLOT_PAYOUTS("gui.slot.payouts"),
    GUI_SLOT_BET("gui.slot.bet"),
    GUI_SLOT_SPIN("gui.slot.spin"),
    GUI_SLOT_SPIN_LORE("gui.slot.spin-lore"),
    GUI_SLOT_STAKE_LORE("gui.slot.stake-lore"),
    GUI_SLOT_COLLECT("gui.slot.collect"),
    GUI_SLOT_COLLECT_LORE("gui.slot.collect-lore"),
    GUI_BETS_TITLE("gui.bets.title"),
    GUI_BETS_LORE("gui.bets.lore"),
    ITEM_SLOT_MACHINE("item.slot-machine"),
    ITEM_SLOT_MACHINE_LORE("item.slot-machine-lore"),
    ITEM_SLOT_REEL("item.slot-machine-reel"),
    ITEM_SLOT_MECHANISM("item.slot-machine-mechanism"),
    PANEL_BET("panel.bet"),
    PANEL_LOSE("panel.lose"),
    PANEL_PUSH("panel.push"),
    PANEL_WIN("panel.win"),
    PANEL_JACKPOT("panel.jackpot");

    private final String key;

    Msg(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
