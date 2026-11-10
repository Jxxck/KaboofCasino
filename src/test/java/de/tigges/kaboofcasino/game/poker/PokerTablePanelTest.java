package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardMotion;
import de.tigges.kaboofcasino.game.blackjack.BlackjackLayout;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.ShoeRandom;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A shared hand on the felt and on the panel: every player's cards lie at his own seat, the panel's hand per seat is
 * his two cards plus the board as far as they show, and the panel lists the countdown, each player and the dealer.
 */
class PokerTablePanelTest {

    private static Messages messages;

    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("messages_de.yml");
        try (InputStream in = PokerTablePanelTest.class.getClassLoader().getResourceAsStream("messages_de.yml")) {
            Files.write(file, in.readAllBytes());
        }
        messages = Messages.load(file, () -> PokerTablePanelTest.class.getClassLoader()
                .getResourceAsStream("messages_de.yml")).messages();
    }

    /** The controller's mapping of a shared hand onto the mod's view (PokerController.Table). */
    private static final class View implements PokerTableRound.Listener {
        final PokerTableState view = new PokerTableState(1_000, 0);
        final int[] spots;

        View(int... spots) {
            this.spots = spots;
        }

        @Override
        public void shuffled(int at) {
            view.shuffled(at);
        }

        @Override
        public void cardDealt(int seat, int index, int card, boolean faceUp, int at) {
            boolean toDealer = seat == PokerTableRound.DEALER;
            if (!toDealer) {
                view.seat = spots[seat];
            }
            view.cardDealt(toDealer, index, card, faceUp, at);
        }

        @Override
        public void holeCardRevealed(int index, int card, int at) {
            view.holeCardRevealed(index, card, at);
        }

        @Override
        public void decisionOpened(int deadline, int at) {
            view.seat = 1;
            view.turnOpened(deadline, at);
        }

        @Override
        public void decisionTimedOut(int seat, int at) {
        }

        @Override
        public void phaseChanged(PokerRound.Phase phase, int at) {
            view.phaseChanged(phase, at);
        }

        @Override
        public void decided(int seat, PokerRound.Result result, int thirds, int at) {
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
            view.reaction(reaction, at);
            view.decided(PokerRound.Result.WIN, at);
        }

        @Override
        public void cleared(int at) {
            view.cleared(at);
        }
    }

    @Test
    void everyPlayersCardsLieAtHisSeatAndThePanelReadsThemWithTheBoard() {
        View v = new View(0, 2);
        PokerTableRound r = new PokerTableRound(new BlackjackShoe(new ShoeRandom(7), 1), v, 2);
        while (r.phase() != PokerRound.Phase.DECISION) {
            r.tick();
        }
        for (BlackjackCardMotion.Card c : v.view.cards) {
            BlackjackLayout.Slot slot = v.view.slot(c);
            if (!c.toDealer) {
                assertEquals(PokerLayout.SEAT_X[c.spot], slot.x(), 3.0, "a player card at its own seat");
            }
        }
        long later = v.view.start + r.now() + 30;
        for (int seat = 0; seat < 2; seat++) {
            List<Integer> cards = new ArrayList<>(r.hand(seat));
            cards.addAll(r.board());
            assertEquals(PokerHands.category(PokerHands.score(cards)), PokerPanel.seatHand(v.view, v.spots[seat],
                    later), "seat " + seat);
        }
        assertEquals(-1, PokerPanel.seatHand(v.view, 1, later), "nobody at the middle seat");
        assertEquals(-1, PokerPanel.dealerHand(v.view, later).dealer(), "the dealer's cards are still down");
        // both call: the showdown shows the dealer's hand too
        r.call(0);
        r.call(1);
        while (r.phase() != PokerRound.Phase.RESULT) {
            r.tick();
        }
        List<Integer> his = new ArrayList<>(r.dealer());
        his.addAll(r.board());
        PokerPanel.Hands dealer = PokerPanel.dealerHand(v.view, v.view.start + r.now() + 30);
        assertEquals(PokerHands.category(PokerHands.score(his)), dealer.dealer());
        assertEquals(PokerHands.qualifies(PokerHands.score(his)), dealer.qualifies());
    }

    @Test
    void thePanelListsTheCountdownEveryPlayerAndTheDealer() {
        List<ItemAmount> stake = List.of(new ItemAmount("kaboofcasino:chip_25", 2));
        PokerPanel.TableContent window = new PokerPanel.TableContent(List.of(
                new PokerPanel.SeatLine(UUID.randomUUID(), "Jxxq", stake, -1, 0),
                new PokerPanel.SeatLine(UUID.randomUUID(), "Freund", stake, -1, 0)), -1, false, 12, -1);
        String text = plain(PokerPanel.table(messages, window));
        assertTrue(text.startsWith("Einsätze noch 12 s\n"), text);
        assertTrue(text.contains("Jxxq") && text.contains("Freund"), text);
        assertEquals(3, text.split("\n").length, "the countdown and one line per player");

        PokerPanel.TableContent hand = new PokerPanel.TableContent(List.of(
                new PokerPanel.SeatLine(UUID.randomUUID(), "Jxxq", stake, PokerHands.PAIR, PokerRound.Result.WIN.code),
                new PokerPanel.SeatLine(UUID.randomUUID(), "Freund", stake, PokerHands.HIGH_CARD,
                        PokerRound.Result.FOLD.code)), PokerHands.HIGH_CARD, false, -1, -1);
        String shown = plain(PokerPanel.table(messages, hand));
        String[] lines = shown.split("\n");
        assertEquals(3, lines.length, shown);
        assertTrue(lines[0].contains("Ein Paar") && lines[0].contains("GEWONNEN!"), shown);
        assertTrue(lines[1].contains("AUSGESTIEGEN"), shown);
        assertTrue(lines[2].startsWith("CROUPIER Höchste Karte"), shown);

        PokerPanel.TableContent deciding = new PokerPanel.TableContent(hand.seats(), -1, false, -1, 25);
        assertTrue(plain(PokerPanel.table(messages, deciding)).startsWith("Mitgehen oder aussteigen: noch 25 s"));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
