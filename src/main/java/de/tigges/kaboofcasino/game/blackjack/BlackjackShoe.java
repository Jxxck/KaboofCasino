package de.tigges.kaboofcasino.game.blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

// Verbatim port of the MineVegas Casino class of the same name (MIT, byMehd,
// https://www.curseforge.com/minecraft/mc-mods/minevegas-casino): decompiled source, package renamed. Pure.
// Plugin addition at the end: copy() (marked).
public final class BlackjackShoe {
   public static final int DECKS = 6;

   public static final int RESHUFFLE_BELOW = 60;
   private final Random random;
   private final int decks;
   private final List<Integer> cards = new ArrayList<>();
   private final List<Integer> rigged = new ArrayList<>();

   public BlackjackShoe(Random random) {
      this(random, 6);
   }

   public BlackjackShoe(Random random, int decks) {
      this.random = random;
      this.decks = Math.max(1, decks);
      this.shuffle();
   }

   public static BlackjackShoe rigged(Random random, int... first) {
      return riggedDecks(random, 6, first);
   }

   public static BlackjackShoe riggedDecks(Random random, int decks, int... first) {
      BlackjackShoe shoe = new BlackjackShoe(random, decks);

      for (int card : first) {
         shoe.rigged.add(card);
      }

      return shoe;
   }

   public void shuffle() {
      this.cards.clear();

      for (int deck = 0; deck < this.decks; deck++) {
         for (int card = 0; card < 52; card++) {
            this.cards.add(card);
         }
      }

      for (int i = this.cards.size() - 1; i > 0; i--) {
         int j = this.random.nextInt(i + 1);
         int swap = this.cards.get(i);
         this.cards.set(i, this.cards.get(j));
         this.cards.set(j, swap);
      }
   }

   public boolean needsShuffle() {
      return this.cards.size() < 60;
   }

   public int remaining() {
      return this.cards.size();
   }

   public int draw() {
      if (!this.rigged.isEmpty()) {
         return this.rigged.removeFirst();
      } else {
         if (this.cards.isEmpty()) {
            this.shuffle();
         }

         return this.cards.removeLast();
      }
   }

   public int drawFavouring(int percent, IntToDoubleFunction score) {
      if (!this.rigged.isEmpty() || this.cards.size() < 2) {
         return this.draw();
      } else if (this.random.nextInt(100) >= percent) {
         return this.cards.removeLast();
      } else {
         int top = this.cards.get(this.cards.size() - 1);
         int second = this.cards.get(this.cards.size() - 2);
         if (score.applyAsDouble(second) <= score.applyAsDouble(top)) {
            return this.cards.removeLast();
         } else {
            this.cards.remove(this.cards.size() - 2);
            return second;
         }
      }
   }

   // ---- KaboofCasino addition (not in the mod) -------------------------------------------------------------------
   // An exact, independent copy (cards, rigged cards, random state) for BlackjackStand, which settles a hand as
   // "stand" with the cards this shoe would deal next without dealing them. Needs a ShoeRandom.

   private BlackjackShoe(BlackjackShoe from) {
      this.random = ((ShoeRandom)from.random).copy();
      this.decks = from.decks;
      this.cards.addAll(from.cards);
      this.rigged.addAll(from.rigged);
   }

   public BlackjackShoe copy() {
      if (!(this.random instanceof ShoeRandom)) {
         throw new IllegalStateException("only a shoe with a ShoeRandom can be copied");
      }

      return new BlackjackShoe(this);
   }
}
