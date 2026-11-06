package de.tigges.kaboofcasino.game.blackjack;

import java.util.ArrayList;
import java.util.List;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
public final class BlackjackTableState implements BlackjackRound.Listener {
   private static final int FORMAT = 1;

   private static final int HEADER = 18;
   private static final int PER_CARD = 8;
   public long start;
   public int spot = 2;
   public final List<BlackjackCardMotion.Card> cards = new ArrayList<>();
   public BlackjackRound.Phase phase = BlackjackRound.Phase.DONE;
   public long phaseAt;
   public BlackjackRound.Result result = BlackjackRound.Result.NONE;
   public long resultAt = -1L;
   public long deadline = -1L;
   public long clearAt = -1L;
   public DealerPlayAnimation.Kind gesture = DealerPlayAnimation.Kind.NONE;
   public long gestureAt;
   public boolean gestureToDealer;
   public int gestureIndex;
   public long watchFrom = -1L;
   public long watchTo = -1L;
   public boolean doubled;

   public BlackjackTableState() {
   }

   public BlackjackTableState(long start, int spot) {
      this.start = start;
      this.spot = spot;
   }

   public boolean isActive() {
      return this.phase != BlackjackRound.Phase.DONE;
   }

   @Override
   public void shuffled(int at) {
      this.gesture = DealerPlayAnimation.Kind.SHUFFLE;
      this.gestureAt = this.start + (long)at;
   }

   @Override
   public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
      BlackjackCardMotion.Card c = new BlackjackCardMotion.Card();
      c.toDealer = toDealer;
      c.index = index;
      c.value = card;
      c.faceUp = faceUp;
      c.dealtAt = this.start + (long)at;
      c.spot = this.spot;
      this.cards.add(c);
      this.gesture = DealerPlayAnimation.Kind.DEAL;
      this.gestureAt = this.start + (long)at;
      this.gestureToDealer = toDealer;
      this.gestureIndex = index;
   }

   @Override
   public void holeCardRevealed(int card, int at) {
      BlackjackCardMotion.Card hole = this.dealerCard(1);
      if (hole != null) {
         hole.value = card;
         hole.flipAt = this.start + (long)at;
      }

      this.gesture = DealerPlayAnimation.Kind.FLIP;
      this.gestureAt = this.start + (long)at;
   }

   @Override
   public void dealerPeek(boolean blackjack, int at) {
      BlackjackCardMotion.Card hole = this.dealerCard(1);
      if (hole != null) {
         hole.peekAt = this.start + (long)at;
      }

      this.gesture = DealerPlayAnimation.Kind.PEEK;
      this.gestureAt = this.start + (long)at;
   }

   @Override
   public void turnOpened(int deadline, int at) {
      this.deadline = this.start + (long)deadline;
      if (this.watchFrom < 0L) {
         this.watchFrom = this.start + (long)at;
      }

      this.watchTo = -1L;
   }

   @Override
   public void phaseChanged(BlackjackRound.Phase next, int at) {
      this.phase = next;
      this.phaseAt = this.start + (long)at;
      if (next != BlackjackRound.Phase.PLAYER_TURN) {
         this.deadline = -1L;
      }

      if ((next == BlackjackRound.Phase.DEALER_TURN || next == BlackjackRound.Phase.RESULT) && this.watchFrom >= 0L && this.watchTo < 0L) {
         this.watchTo = this.start + (long)at;
      }
   }

   @Override
   public void decided(BlackjackRound.Result result, int at) {
      this.result = result;
      this.resultAt = this.start + (long)at;
   }

   @Override
   public void reaction(BlackjackRound.Reaction reaction, int at) {
      this.gesture = switch (reaction) {
         case PAY -> DealerPlayAnimation.Kind.PAY;
         case COLLECT -> DealerPlayAnimation.Kind.COLLECT;
         case PUSH -> DealerPlayAnimation.Kind.PUSH;
         case CELEBRATE -> DealerPlayAnimation.Kind.CELEBRATE;
      };
      this.gestureAt = this.start + (long)at;
   }

   @Override
   public void cleared(int at) {
      this.clearAt = this.start + (long)at;

      for (BlackjackCardMotion.Card c : this.cards) {
         c.clearAt = this.clearAt;
      }

      this.gesture = DealerPlayAnimation.Kind.CLEAR;
      this.gestureAt = this.clearAt;
   }

   public BlackjackCardMotion.Card dealerCard(int index) {
      for (BlackjackCardMotion.Card c : this.cards) {
         if (c.toDealer && c.index == index) {
            return c;
         }
      }

      return null;
   }

   public void dealerInput(double now, DealerPlayAnimation.Input in) {
      in.clear();
      double t = now - (double)this.gestureAt;
      if (this.gesture != DealerPlayAnimation.Kind.NONE && t >= 0.0 && t < (double)DealerPlayAnimation.duration(this.gesture)) {
         in.kind = this.gesture;
         in.time = (float)t;
         if (this.gesture == DealerPlayAnimation.Kind.DEAL) {
            BlackjackLayout.Slot slot = BlackjackLayout.slot(this.gestureToDealer, this.spot, this.gestureIndex);
            in.bearing = (float)BlackjackLayout.bearing(slot.x(), slot.z());
         } else if (this.gesture == DealerPlayAnimation.Kind.PAY
            || this.gesture == DealerPlayAnimation.Kind.COLLECT
            || this.gesture == DealerPlayAnimation.Kind.PUSH) {
            double[] c = BlackjackLayout.spotCenter(this.spot);
            in.bearing = (float)BlackjackLayout.bearing(c[0], c[1]);
         }
      }

      if (this.watchFrom >= 0L) {
         double w = smooth((now - (double)this.watchFrom) / 10.0);
         if (this.watchTo >= 0L) {
            w *= 1.0 - smooth((now - (double)this.watchTo) / 10.0);
         }

         in.watch = (float)w;
         double[] c = BlackjackLayout.spotCenter(this.spot);
         in.watchBearing = (float)BlackjackLayout.bearing(c[0], c[1]);
         in.watchTime = (float)(now - (double)this.watchFrom);
      }
   }

   private static double smooth(double v) {
      v = v < 0.0 ? 0.0 : Math.min(v, 1.0);
      return v * v * (3.0 - 2.0 * v);
   }

   public List<Long> encode() {
      List<Long> out = new ArrayList<>();
      out.add(1L);
      out.add(this.start);
      out.add((long)this.spot);
      out.add((long)this.phase.ordinal());
      out.add(this.phaseAt);
      out.add((long)this.result.code);
      out.add(this.resultAt);
      out.add(this.deadline);
      out.add(this.clearAt);
      out.add((long)this.gesture.ordinal());
      out.add(this.gestureAt);
      out.add(this.gestureToDealer ? 1L : 0L);
      out.add((long)this.gestureIndex);
      out.add(this.watchFrom);
      out.add(this.watchTo);
      out.add(this.doubled ? 1L : 0L);
      out.add((long)this.cards.size());
      out.add(0L);

      for (BlackjackCardMotion.Card c : this.cards) {
         out.add(c.toDealer ? 1L : 0L);
         out.add((long)c.index);
         out.add((long)c.value);
         out.add(c.faceUp ? 1L : 0L);
         out.add(c.dealtAt);
         out.add(c.flipAt);
         out.add(c.peekAt);
         out.add(c.clearAt);
      }

      return out;
   }

   public static BlackjackTableState decode(List<Long> data) {
      if (data != null && data.size() >= 18 && data.get(0) == 1L) {
         int cardCount = (int)data.get(16).longValue();
         if (cardCount >= 0 && cardCount <= 64 && data.size() >= 18 + cardCount * 8) {
            BlackjackTableState s = new BlackjackTableState();
            s.start = data.get(1);
            s.spot = (int)Math.max(0L, Math.min(4L, data.get(2)));
            BlackjackRound.Phase[] phases = BlackjackRound.Phase.values();
            s.phase = phases[(int)Math.max(0L, Math.min((long)(phases.length - 1), data.get(3)))];
            s.phaseAt = data.get(4);
            s.result = BlackjackRound.Result.byCode((int)data.get(5).longValue());
            s.resultAt = data.get(6);
            s.deadline = data.get(7);
            s.clearAt = data.get(8);
            s.gesture = DealerPlayAnimation.Kind.byOrdinal((int)data.get(9).longValue());
            s.gestureAt = data.get(10);
            s.gestureToDealer = data.get(11) != 0L;
            s.gestureIndex = (int)data.get(12).longValue();
            s.watchFrom = data.get(13);
            s.watchTo = data.get(14);
            s.doubled = data.get(15) != 0L;

            for (int i = 0; i < cardCount; i++) {
               int o = 18 + i * 8;
               BlackjackCardMotion.Card c = new BlackjackCardMotion.Card();
               c.toDealer = data.get(o) != 0L;
               c.index = (int)data.get(o + 1).longValue();
               c.value = (int)data.get(o + 2).longValue();
               c.faceUp = data.get(o + 3) != 0L;
               c.dealtAt = data.get(o + 4);
               c.flipAt = data.get(o + 5);
               c.peekAt = data.get(o + 6);
               c.clearAt = data.get(o + 7);
               c.spot = s.spot;
               s.cards.add(c);
            }

            return s;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }
}
