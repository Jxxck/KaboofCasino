package de.tigges.kaboofcasino.game.poker;

import java.util.List;
import java.util.UUID;

/**
 * What the Texas Hold'em menu shows one seated player at one moment (built by {@link HoldemTable}).
 *
 * @param status     the status line ({@code hd_status_<status>})
 * @param hole       the viewer's hole cards in this hand (empty outside one)
 * @param holeAt     game tick each hole card was dealt
 * @param board      the board cards
 * @param boardAt    game tick each board card was dealt
 * @param pot        all chips in the middle
 * @param toCall     what the viewer has to put in to call (0: a check)
 * @param myTurn     the viewer acts now
 * @param canFold    PASSEN is active
 * @param canCheck   the middle button is CHECKEN and active
 * @param canCall    the middle button is MITGEHEN and active
 * @param canRaise   the size buttons and SETZEN / ERHÖHEN / ALL-IN are active
 * @param opening    nobody has bet on this street: the right button says SETZEN
 * @param minRaiseTo the smallest bet or raise (the viewer's bet on the street after it)
 * @param maxRaiseTo the viewer's all-in as such a bet
 * @param ticksLeft  ticks left in the viewer's turn (0 otherwise)
 * @param inHand     the viewer still plays this hand (not folded): the left button is PASSEN, else GEHEN
 * @param turn       a number that changes with every turn the viewer gets (the menu resets its bet size then)
 * @param seats      every seat, in table order
 * @param gameTime   the world game tick of this view
 */
public record HoldemView(String status, List<Integer> hole, List<Long> holeAt, List<Integer> board, List<Long> boardAt,
                         int pot, int toCall, boolean myTurn, boolean canFold, boolean canCheck, boolean canCall,
                         boolean canRaise, boolean opening, int minRaiseTo, int maxRaiseTo, int ticksLeft,
                         boolean inHand, int turn, List<Seat> seats, long gameTime) {

    public HoldemView {
        hole = List.copyOf(hole);
        holeAt = List.copyOf(holeAt);
        board = List.copyOf(board);
        boardAt = List.copyOf(boardAt);
        seats = List.copyOf(seats);
    }

    /**
     * One seat: who, the chips in front of the player (stack), the bet on this street, and the state as words for the
     * tooltip ({@code action}: the last action, empty if none).
     */
    public record Seat(UUID player, String name, int spot, int stack, int bet, boolean acting, boolean folded,
                       boolean allIn, boolean dealer, boolean winner, boolean me, String action) {
    }
}
