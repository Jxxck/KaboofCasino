package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What {@link DerbyBetMenu} shows in each of its 54 slots ({@code DuckDerbyScreen}'s client logic, pure): the slot
 * layout, the hint line, the buttons' states, the lobby page, the lane veils and the help panel. The pack's
 * {@code DerbyGui} draws every picture for exactly these slots:
 * <pre>
 *  row 0     0 / 3 / 5 / 8 lane state (veil of a muted lane, gold lines of the staked one), 1 lobby cells,
 *            6 info (people, page, empty lobby), 7 hint; while the help is open 2 / 7 the help panel
 *  rows 1-3  the 12 bets: bet i (lane i % 4, row i / 4) in grid column 0 / 3 / 5 / 8
 *  row 4     36-39 SOLO (CLAIM while rewards are owed), 40 HELP, 41-44 GROUP / CONFIRM / WITHDRAW
 *  row 5     45-52 the lobby page's heads, 53 the pager
 * </pre>
 */
final class DerbyMenuView {

    static final int SLOTS = 54;
    static final int BETS = 12;
    static final int[] LANE_COLUMNS = {0, 3, 5, 8};
    static final int FIRST_ROW = 1;
    static final int[] LANE_SLOTS = {0, 3, 5, 8};
    static final int LOBBY = 1;
    static final int HELP_LEFT = 2;
    static final int INFO = 6;
    static final int HINT = 7;
    static final int HELP_RIGHT = 7;
    static final int[] SOLO = {36, 37, 38, 39};
    static final int SOLO_ART = 37;
    static final int HELP = 40;
    static final int[] GROUP = {41, 42, 43, 44};
    static final int GROUP_ART = 42;
    static final int FIRST_HEAD = 45;
    static final int PAGER = 53;
    /** {@code DuckDerbyScreen.PAGE_SIZE} and {@code DuckDerbyBlockEntity.MAX_PLAYERS}. */
    static final int PAGE_SIZE = 8;
    static final int MAX_PLAYERS = 64;
    static final int MAX_SECONDS = 10;
    /** How long a refused bet click shows "Eine Ente pro Rennen!" (the mod's hint for several staked lanes). */
    static final int SEVERAL_TICKS = 40;
    static final String MODEL = "kaboofcasino:gui/derby/";
    static final String BLANK = "kaboofcasino:gui/blank";

    /** What a click on a slot does. */
    enum Action {
        SOLO, CLAIM, GROUP, CONFIRM, WITHDRAW, HELP, CLOSE_HELP, PAGER, BET
    }

    enum Kind {
        /** A pack picture (custom model data strings), no tooltip. */
        PICTURE,
        /** A bet slot of the draft: {@code index}, the stake (or null) and whether it is the active one. */
        BET,
        /** A stake of the confirmed escrow (read-only). */
        ESCROW,
        /** A button slot: the art ({@code model}) or a blank over the rest of the button. */
        BUTTON,
        /** A member's head. */
        HEAD
    }

    /**
     * One slot's content; equal cells are not sent again.
     *
     * @param model    pack model (pictures, buttons)
     * @param strings  custom model data strings (pictures)
     * @param index    bet index (BET), the page shown (PAGER)
     * @param stake    the stake (BET, ESCROW), null for an empty bet slot
     * @param active   BET: the active slot; BUTTON: clickable
     * @param state    BET: "free", "locked"; BUTTON: "unavailable" for a group button the settings forbid
     * @param member   HEAD: the member
     */
    record Cell(Kind kind, String model, List<String> strings, Action action, int index, ItemAmount stake,
                boolean active, String state, DerbyMember member, int pages) {

        Cell {
            strings = List.copyOf(strings);
        }

        static Cell picture(String model, List<String> strings) {
            return new Cell(Kind.PICTURE, MODEL + model, strings, null, -1, null, false, "", null, 0);
        }

        static Cell button(Action action, String model, boolean enabled, String state) {
            return new Cell(Kind.BUTTON, model, List.of(), action, -1, null, enabled, state, null, 0);
        }

        static Cell bet(int index, ItemAmount stake, boolean active, String state) {
            return new Cell(Kind.BET, null, List.of(), Action.BET, index, stake, active, state, null, 0);
        }

        static Cell escrow(ItemAmount stake) {
            return new Cell(Kind.ESCROW, null, List.of(), null, -1, stake, false, "", null, 0);
        }

        static Cell head(DerbyMember member) {
            return new Cell(Kind.HEAD, null, List.of(), null, -1, null, false, "", member, 0);
        }

        static Cell pager(int page, int pages) {
            return new Cell(Kind.BUTTON, MODEL + "pager", List.of(), Action.PAGER, page, null, true, "", null, pages);
        }
    }

    /** Grid slot of bet {@code i}. */
    static int betSlot(int i) {
        return (FIRST_ROW + i / 4) * 9 + LANE_COLUMNS[i % 4];
    }

    /** {@code DuckDerbyMenu.stakedLane}: -1 nothing, the lane, -2 several. Index i: lane i % 4. */
    static int stakedLane(ItemAmount[] stakes) {
        int lane = -1;
        for (int i = 0; i < stakes.length; i++) {
            if (stakes[i] != null) {
                if (lane == -1) {
                    lane = i % 4;
                } else if (lane != i % 4) {
                    return -2;
                }
            }
        }
        return lane;
    }

    /** {@code DuckDerbyScreen.pages}. */
    static int pages(int members) {
        return Math.max(1, (members + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The countdown's seconds as the pack has them (0..10). */
    static int seconds(int seconds) {
        return Math.max(0, Math.min(MAX_SECONDS, seconds));
    }

    /**
     * The lane the screen treats as chosen: the escrow's while confirmed (the mod's escrow view holds the escrowed
     * stakes in that column), else the draft's; -2 while a refused click flashes "several".
     */
    static int chosen(DerbySnapshot s, ItemAmount[] stakes, boolean several) {
        if (s.confirmed() && s.confirmedLane() >= 0) {
            return s.confirmedLane();
        }
        return several ? -2 : stakedLane(stakes);
    }

    /**
     * {@code extractLabels}' hint: the chosen duck, several or pick; the countdown while enrolling; else "confirmed"
     * while confirmed (the mod checks {@code enrolling()} first, so a confirmed entrant sees the countdown).
     */
    static String hint(DerbySnapshot s, int chosen) {
        String hint = chosen >= 0 ? "picked_" + chosen : chosen == -2 ? "several" : "pick";
        if (s.enrolling()) {
            hint = "countdown_" + seconds(s.seconds());
        } else if (s.confirmed()) {
            hint = "confirmed";
        }
        return hint;
    }

    /** The whole menu for one state. */
    static Cell[] cells(DerbySnapshot s, UUID viewer, ItemAmount[] stakes, int active, int page, boolean help,
                        boolean several) {
        Cell[] out = new Cell[SLOTS];
        if (help) {
            for (int slot = 0; slot < SLOTS; slot++) {
                out[slot] = Cell.button(Action.CLOSE_HELP, BLANK, true, "");
            }
            out[HELP_LEFT] = new Cell(Kind.BUTTON, MODEL + "help_left", List.of(), Action.CLOSE_HELP, -1, null, true,
                    "", null, 0);
            out[HELP_RIGHT] = new Cell(Kind.BUTTON, MODEL + "help_right", List.of(), Action.CLOSE_HELP, -1, null, true,
                    "", null, 0);
            return out;
        }
        int chosen = chosen(s, stakes, false);
        // lane cards: the chosen lane's gold lines, the others veiled (a refused click only changes the hint)
        if (chosen >= 0) {
            for (int lane = 0; lane < 4; lane++) {
                out[LANE_SLOTS[lane]] = Cell.picture(lane == chosen ? "chosen_" + lane : "veil_" + lane, List.of());
            }
        }
        out[HINT] = Cell.picture("hint", List.of(hint(s, chosen(s, stakes, several))));
        // bets: the draft, or the escrow read-only in its column
        if (s.confirmed()) {
            List<ItemAmount> escrow = s.confirmedStakes();
            for (int row = 0; row < Math.min(3, escrow.size()) && s.confirmedLane() >= 0; row++) {
                out[betSlot(row * 4 + s.confirmedLane())] = Cell.escrow(escrow.get(row));
            }
        } else {
            int lane = stakedLane(stakes);
            for (int i = 0; i < BETS; i++) {
                boolean open = lane < 0 || i % 4 == lane;
                out[betSlot(i)] = Cell.bet(i, stakes[i], i == active, open ? "free" : "locked");
            }
        }
        // buttons (updatePlayButton)
        boolean has = stakedLane(stakes) >= 0;
        if (s.credits() > 0) {
            for (int slot : SOLO) {
                out[slot] = Cell.button(Action.CLAIM, slot == SOLO_ART ? MODEL + "claim_on" : BLANK, true, "");
            }
        } else {
            boolean solo = has && !s.confirmed() && !s.enrolling();
            for (int slot : SOLO) {
                out[slot] = Cell.button(Action.SOLO, slot == SOLO_ART ? MODEL + (solo ? "solo_on" : "solo_off")
                        : BLANK, solo, "");
            }
        }
        Action group = s.confirmed() ? Action.WITHDRAW : s.enrolling() ? Action.CONFIRM : Action.GROUP;
        boolean groupActive = s.confirmed() || has && s.groupAllowed() && s.members().size() < MAX_PLAYERS;
        String art = switch (group) {
            case WITHDRAW -> "withdraw_on";
            case CONFIRM -> groupActive ? "confirm_on" : "confirm_off";
            default -> groupActive ? "group_on" : "group_off";
        };
        String state = s.groupAllowed() ? "" : "unavailable";
        for (int slot : GROUP) {
            out[slot] = Cell.button(group, slot == GROUP_ART ? MODEL + art : BLANK, groupActive, state);
        }
        out[HELP] = Cell.button(Action.HELP, MODEL + "help", true, "");
        // the lobby: people, page, heads (own entry first), cells behind them
        List<DerbyMember> ordered = DerbyMember.visible(s.members(), viewer, s.members().size());
        int pages = pages(ordered.size());
        int p = Math.max(0, Math.min(page, pages - 1));
        List<String> lanes = new ArrayList<>();
        for (int k = 0; k < PAGE_SIZE; k++) {
            int i = p * PAGE_SIZE + k;
            if (i < ordered.size()) {
                DerbyMember m = ordered.get(i);
                out[FIRST_HEAD + k] = Cell.head(m);
                lanes.add(Integer.toString(Math.max(0, Math.min(3, m.lane()))));
            } else {
                lanes.add("");
            }
        }
        if (!ordered.isEmpty()) {
            out[LOBBY] = Cell.picture("lobby", lanes);
        }
        int shownPages = Math.min(pages, MAX_PLAYERS / PAGE_SIZE);
        out[INFO] = Cell.picture("info", List.of(Integer.toString(Math.min(MAX_PLAYERS, ordered.size())),
                pages > 1 ? Math.min(p + 1, shownPages) + "/" + shownPages : "", ordered.isEmpty() ? "empty" : ""));
        if (pages > 1) {
            out[PAGER] = Cell.pager(p, pages);
        }
        return out;
    }

    private DerbyMenuView() {
    }
}
