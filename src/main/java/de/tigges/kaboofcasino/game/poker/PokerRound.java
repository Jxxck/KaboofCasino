package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.game.blackjack.BlackjackRound;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
public final class PokerRound {
   public static final int BOARD = 5;

   public static final int FIRST_BOARD = 2;
   public static final int SECOND_FLIP = 22;
   public static final int TURN_TIMEOUT = 600;
   private final BlackjackShoe shoe;
   private final PokerRound.Listener listener;
   private final int bias;
   private final List<Integer> player = new ArrayList<>();
   private final List<Integer> dealer = new ArrayList<>();
   private final List<Integer> board = new ArrayList<>();
   private final List<PokerRound.Step> steps = new ArrayList<>();
   private final boolean[] revealed = new boolean[2];
   private int sequence;
   private int now;
   private int deadline;
   private PokerRound.Phase phase = PokerRound.Phase.DEALING;
   private PokerRound.Result result = PokerRound.Result.NONE;
   private boolean called;
   private int playerScore = -1;
   private int dealerScore = -1;

   public PokerRound(BlackjackShoe shoe, PokerRound.Listener listener) {
      this(shoe, listener, 0);
   }

   public PokerRound(BlackjackShoe shoe, PokerRound.Listener listener, int bias) {
      this.shoe = shoe;
      this.listener = listener;
      this.bias = Math.max(-100, Math.min(100, bias));
      int t = 0;
      if (shoe.needsShuffle()) {
         shoe.shuffle();
         listener.shuffled(0);
         t += 36;
      }

      t += 14;

      for (int i = 0; i < 2; i++) {
         this.at(t, () -> this.deal(PokerRound.Target.PLAYER));
         t += 22;
         this.at(t, () -> this.deal(PokerRound.Target.DEALER));
         t += 22;
      }

      for (int i = 0; i < 3; i++) {
         this.at(t, () -> this.deal(PokerRound.Target.BOARD));
         if (i < 2) {
            t += 22;
         }
      }

      this.at(t + 23 + 4, this::openDecision);
   }

   public PokerRound.Phase phase() {
      return this.phase;
   }

   public PokerRound.Result result() {
      return this.result;
   }

   public int now() {
      return this.now;
   }

   public int deadline() {
      return this.deadline;
   }

   public boolean called() {
      return this.called;
   }

   public boolean isOver() {
      return this.phase == PokerRound.Phase.DONE;
   }

   public List<Integer> player() {
      return Collections.unmodifiableList(this.player);
   }

   public List<Integer> dealer() {
      return Collections.unmodifiableList(this.dealer);
   }

   public List<Integer> dealerVisible() {
      List<Integer> out = new ArrayList<>(this.dealer);

      for (int i = 0; i < out.size(); i++) {
         if (!this.revealed[i]) {
            out.set(i, -1);
         }
      }

      return out;
   }

   public List<Integer> board() {
      return Collections.unmodifiableList(this.board);
   }

   public int playerScore() {
      return this.playerScore;
   }

   public int dealerScore() {
      return this.dealerScore;
   }

   public boolean canCall() {
      return this.phase == PokerRound.Phase.DECISION;
   }

   public boolean canFold() {
      return this.phase == PokerRound.Phase.DECISION;
   }

   public int payoutThirds() {
      return switch (this.result) {
         case WIN -> 5 + PokerHands.antePays(this.playerScore);
         case NO_QUALIFY -> 3 + PokerHands.antePays(this.playerScore);
         case PUSH -> 3;
         default -> 0;
      };
   }

   public boolean call() {
      if (!this.canCall()) {
         return false;
      } else {
         this.called = true;
         this.setPhase(PokerRound.Phase.BOARD);
         this.deal(PokerRound.Target.BOARD);
         this.after(22, () -> this.deal(PokerRound.Target.BOARD));
         this.after(49, this::showdown);
         return true;
      }
   }

   public boolean fold() {
      if (!this.canFold()) {
         return false;
      } else {
         this.decide(PokerRound.Result.FOLD, BlackjackRound.Reaction.COLLECT);
         return true;
      }
   }

   public void abort() {
      this.steps.clear();
      this.phase = PokerRound.Phase.DONE;
   }

   public void tick() {
      if (this.phase != PokerRound.Phase.DONE) {
         this.now++;
         if (this.phase == PokerRound.Phase.DECISION && this.now >= this.deadline) {
            this.listener.turnTimedOut(this.now);
            this.fold();
         }

         do {
            PokerRound.Step due = null;

            for (PokerRound.Step s : this.steps) {
               if (s.at <= this.now && (due == null || s.at < due.at || s.at == due.at && s.order < due.order)) {
                  due = s;
               }
            }

            if (due == null) {
               return;
            }

            this.steps.remove(due);
            due.run.run();
         } while (this.phase != PokerRound.Phase.DONE);
      }
   }

   private void at(int time, Runnable run) {
      this.steps.add(new PokerRound.Step(time, this.sequence++, run));
   }

   private void after(int delay, Runnable run) {
      this.at(this.now + delay, run);
   }

   private void setPhase(PokerRound.Phase next) {
      this.phase = next;
      this.listener.phaseChanged(next, this.now);
   }

   private void deal(PokerRound.Target target) {
      int card = this.bias == 0 ? this.shoe.draw() : this.shoe.drawFavouring(Math.abs(this.bias), c -> {
         double value = PokerBias.playerValue(this.player, this.dealer, this.board, target.ordinal(), c);
         return this.bias > 0 ? value : -value;
      });
      switch (target) {
         case PLAYER:
            this.player.add(card);
            this.listener.cardDealt(false, this.player.size() - 1, card, true, this.now);
            break;
         case DEALER:
            this.dealer.add(card);
            this.listener.cardDealt(true, this.dealer.size() - 1, -1, false, this.now);
            break;
         case BOARD:
            this.board.add(card);
            this.listener.cardDealt(true, 2 + this.board.size() - 1, card, true, this.now);
      }
   }

   private void openDecision() {
      this.setPhase(PokerRound.Phase.DECISION);
      this.deadline = this.now + 600;
      this.listener.turnOpened(this.deadline, this.now);
   }

   private void reveal(int index) {
      if (!this.revealed[index]) {
         this.revealed[index] = true;
         this.listener.holeCardRevealed(index, this.dealer.get(index), this.now);
      }
   }

   private void showdown() {
      this.setPhase(PokerRound.Phase.SHOWDOWN);
      this.reveal(1);
      this.after(22, () -> this.reveal(0));
      this.after(46, this::resolve);
   }

   private void resolve() {
      List<Integer> mine = new ArrayList<>(this.player);
      mine.addAll(this.board);
      List<Integer> his = new ArrayList<>(this.dealer);
      his.addAll(this.board);
      this.playerScore = PokerHands.score(mine);
      this.dealerScore = PokerHands.score(his);
      PokerRound.Result r;
      if (!PokerHands.qualifies(this.dealerScore)) {
         r = PokerRound.Result.NO_QUALIFY;
      } else if (this.playerScore > this.dealerScore) {
         r = PokerRound.Result.WIN;
      } else if (this.playerScore < this.dealerScore) {
         r = PokerRound.Result.LOSE;
      } else {
         r = PokerRound.Result.PUSH;
      }

      BlackjackRound.Reaction reaction = r.isWin()
         ? (PokerHands.antePays(this.playerScore) >= 3 ? BlackjackRound.Reaction.CELEBRATE : BlackjackRound.Reaction.PAY)
         : (r.isPush() ? BlackjackRound.Reaction.PUSH : BlackjackRound.Reaction.COLLECT);
      this.decide(r, reaction);
   }

   private void decide(PokerRound.Result r, BlackjackRound.Reaction reaction) {
      this.result = r;
      this.setPhase(PokerRound.Phase.RESULT);
      this.listener.decided(r, this.now);
      this.listener.reaction(reaction, this.now);
      this.after(BlackjackRound.resultHold(reaction), () -> {
         this.setPhase(PokerRound.Phase.CLEARING);
         this.listener.cleared(this.now);
         this.after(18, () -> this.setPhase(PokerRound.Phase.DONE));
      });
   }

   public interface Listener {
      default void shuffled(int at) {
      }

      void cardDealt(boolean var1, int var2, int var3, boolean var4, int var5);

      void holeCardRevealed(int var1, int var2, int var3);

      void turnOpened(int var1, int var2);

      default void turnTimedOut(int at) {
      }

      void phaseChanged(PokerRound.Phase var1, int var2);

      void decided(PokerRound.Result var1, int var2);

      void reaction(BlackjackRound.Reaction var1, int var2);

      void cleared(int var1);
   }

   public static enum Phase {
      DEALING,
      DECISION,
      BOARD,
      SHOWDOWN,
      RESULT,
      CLEARING,
      DONE;
   }

   public static enum Result {
      NONE(0),
      WIN(1),
      NO_QUALIFY(2),
      PUSH(3),
      LOSE(4),
      FOLD(5);

      public final int code;

      private Result(int code) {
         this.code = code;
      }

      public boolean isWin() {
         return this == WIN || this == NO_QUALIFY;
      }

      public boolean isPush() {
         return this == PUSH;
      }

      public boolean isLoss() {
         return this == LOSE || this == FOLD;
      }

      public static PokerRound.Result byCode(int code) {
         for (PokerRound.Result r : values()) {
            if (r.code == code) {
               return r;
            }
         }

         return NONE;
      }
   }

   private static record Step(int at, int order, Runnable run) {
   }

   private static enum Target {
      PLAYER,
      DEALER,
      BOARD;
   }
}
