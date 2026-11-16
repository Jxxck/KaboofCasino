# What is in KaboofCasino

A tour of everything the plugin puts on a server: the seven games, what the plugin adds to them, and the tools for
the people who run the casino. The games, their look and their rules come from the mod
[MineVegas Casino](https://www.curseforge.com/minecraft/mc-mods/minevegas-casino) by byMehd; sections marked
**new** are things the mod does not have.

Players need no mod. They accept the server's resource pack and see real machines in the world, with moving parts,
sounds and their own screens.

The pictures and clips were taken on a Paper server running this plugin. The players in them use a shader pack, which
is where the lighting comes from; the machines, screens and furniture look the same without one.

<p align="center">
  <img src="media/overview.webp" alt="A furnished casino hall with poker, blackjack, roulette, the duck derby and the slot machines" width="100%">
</p>

## The games at a glance

| Game | Players | You win | Average return |
| --- | --- | --- | --- |
| Slot machine | 1 | ×2 to ×100 for three sevens | 97.5 % |
| Fortune wheel | 1 | ×0.25 to ×5, three bets per spin | 97.7 % |
| Plinko | 1 | ×0.25 to ×8, three balls per round | 97.5 % |
| Roulette | 1 to 8 | ×2 on red or black, ×36 on green | 97.3 % |
| Blackjack | 1 to 5 | ×2, a blackjack pays ×2.5 | about 99.5 % with good play |
| Poker | 1, or 2 to 3 against each other | up to 100:1 on the ante | about 98.5 % against the dealer |
| Duck derby | 1 to 64 | ×3 for first place, stake back for second | 100 % |

These are the mod's odds. An admin can set a win chance for every game or for a single machine.

## Slot machine

A three-block-tall machine with three spinning reels and a lever. Stake your chips, pull, and watch the reels stop
one after another. Eight combinations pay, from ×2 up to the ×100 jackpot for three sevens; lamps and a floating
panel above the machine show the result to everyone nearby.

<p align="center">
  <img src="media/slot-machine.webp" alt="Four slot machines in a row under a casino sign" width="72%">
</p>
<p align="center">
  <img src="media/slot-machine-spin.webp" alt="A spin: the reels stop one after another and the machine pays" width="58%">
  <img src="media/gui-slot.webp" alt="The slot machine screen with one stake field and the payout table" width="38%">
</p>

## Fortune wheel

A wheel three blocks wide that turns in the world. Each spin takes up to three separate bets; the segment under the
pointer pays between ×0.25 and ×5, or nothing.

<p align="center">
  <img src="media/fortune-wheel.webp" alt="The fortune wheel turns and stops on a winning segment" width="58%">
  <img src="media/gui-wheel.webp" alt="The fortune wheel screen with three stake fields and the payout table" width="38%">
</p>

## Plinko

Up to three balls fall through the pegs of a three-block board, each with its own stake, and land in pockets worth
×0.25 to ×8.

<p align="center">
  <img src="media/plinko.webp" alt="A ball falls through the pegs of the Plinko board" width="58%">
  <img src="media/gui-plinko.webp" alt="The Plinko screen with one stake field per ball" width="38%">
</p>

## Roulette

A 2×2 table with a turning rotor and a rolling ball. Bet on red, black or green; red and black pay ×2, green ×36.

**New: several players at one table.** As soon as somebody else stands at the table, a 15-second betting window
opens. Up to eight players bet, then one spin decides for all of them. Alone, you spin at once.

<p align="center">
  <img src="media/roulette.webp" alt="The ball rolls around the rotor and lands on red" width="58%">
  <img src="media/gui-roulette.webp" alt="The roulette screen with a bet on black" width="38%">
</p>
<p align="center">
  <img src="media/roulette-multiplayer-spin.webp" alt="Three players at one table: the panel lists their bets while the ball rolls" width="49%">
  <img src="media/roulette-multiplayer.webp" alt="Two players at the roulette table; the panel above it counts down the betting window" width="49%">
</p>

## Blackjack

A table with an animated dealer who shuffles, deals and turns the cards in front of you. Six decks, the dealer
stands on 17, you can hit, stand or double, and a blackjack pays 3:2.

**New: up to five players at one table.** With others nearby, the dealer waits 15 seconds for everyone's bets and
then plays one round against all seats, each player deciding in turn.

<p align="center">
  <img src="media/blackjack.webp" alt="The dealer plays his hand, busts and pays the table" width="58%">
  <img src="media/gui-blackjack-turn.webp" alt="The blackjack screen during a hand, with HIT, STAND and DOUBLE" width="38%">
</p>
<p align="center">
  <img src="media/gui-blackjack-bet.webp" alt="The blackjack betting screen" width="38%">
  <img src="media/blackjack-multiplayer.webp" alt="A round for three players: the panel above the table lists every seat, stake and hand" width="58%">
</p>

## Poker

**Against the dealer (Casino Hold'em).** You place an ante and get two cards; after the flop you call for twice the
ante or fold. The dealer needs a pair of fours to play. A winning ante pays from 1:1 up to 100:1 for a royal flush.

<p align="center">
  <img src="media/gui-poker-bet.webp" alt="The poker screen before the deal, with the ante payouts" width="38%">
  <img src="media/gui-poker-decision.webp" alt="After the flop: call or fold" width="38%">
</p>

**New: Texas Hold'em between players.** When two or three players sit down at the table, they play No-Limit Texas
Hold'em against each other with the chips they bought in with, and the croupier only deals. Blinds, checks, bets,
raises, all-ins and side pots work as at a real table; the showdown turns the others' cards face up. A short
explanation of the rules is one click away. Buy-in, blinds and thinking time are settings.

<p align="center">
  <img src="media/holdem.webp" alt="A Texas Hold'em hand ends: the cards are turned over and the panel names the winner" width="58%">
  <img src="media/gui-holdem-turn.webp" alt="The Texas Hold'em screen on your turn, with FOLD, CALL and RAISE" width="38%">
</p>
<p align="center">
  <img src="media/holdem-showdown.webp" alt="Showdown at the poker table: every hand lies face up and the panel shows who won with what" width="72%">
</p>

## Duck derby

Four rubber ducks race down a pool three blocks wide and four long. Pick your duck: first place pays ×3, second
place returns the stake.

Race alone, or start a group race: other players get ten seconds to bet on their own ducks, up to 64 of them, and
a panel above the pool shows who bet on which duck.

<p align="center">
  <img src="media/duck-derby.webp" alt="Four rubber ducks race down the pool" width="58%">
  <img src="media/gui-derby.webp" alt="The duck derby screen: pick a duck, then race alone or start a group race" width="38%">
</p>
<p align="center">
  <img src="media/duck-derby-group.webp" alt="A group race: the panel above the pool lists three players and their bets" width="72%">
</p>

## New: chips and the cashier

Games are played with chips, which are real items in six denominations: 1, 5, 25, 100, 500 and 1000. Chips cannot be
turned back into ores, so the casino has its own money and the server's economy stays as it is.

The cashier is a counter with three tabs:

- **Exchange** ores for chips at rates the admin sets (by default from 1 chip for coal up to 256 for a netherite
  ingot, nine times as much for a block).
- **Change** chips into other denominations.
- **Prizes**: a shop where the admin offers any items for chips, with an optional limit per player and day.

<p align="center">
  <img src="media/cashier.webp" alt="The cashier counter" width="58%">
  <img src="media/gui-cashier-exchange.webp" alt="The cashier screen: diamonds on the Ores tab are worth 512 chips" width="38%">
</p>
<p align="center">
  <img src="media/gui-cashier-change.webp" alt="The Change tab; its Value row shows the six chips: 1000, 500, 100, 25, 5 and 1" width="38%">
</p>

## New: Chip Shop

A second counter that sells the whole casino for chips: floors and walls, furniture, bar and tables, lights and
signs, tech, machines and outfits, sorted into seven tabs. Prices follow each item's recipe and can be set per item,
with daily limits if wanted.

<p align="center">
  <img src="media/chip-shop.webp" alt="The Chip Shop counter" width="58%">
  <img src="media/gui-chip-shop.webp" alt="The Chip Shop screen on the Machines tab" width="38%">
</p>

## New: leaderboards

Boards for the wall in eight sizes from 2×2 to 6×4 and a free-standing column. They list the top ten in twelve
categories (biggest win, highest multiplier, net profit, chips staked, most rounds, and the biggest win in each of
the seven games), for this week or for all time. A board turns its pages by itself, or an admin pins one page.

`/casino top` shows the same lists in chat, and `/casino leaderboard off` hides a player from all of them.

<p align="center">
  <img src="media/leaderboard-wall.webp" alt="A large leaderboard on the wall showing the biggest wins" width="49%">
  <img src="media/leaderboard-column.webp" alt="The free-standing leaderboard column" width="49%">
</p>

## New: jukebox

A two-block jukebox with 98 tracks in nine genres: every music disc and the game's own soundtrack, played from each
player's client, plus the casino's lounge music. Anyone nearby picks the track, the mode (in order, shuffle, repeat)
and the range of 16, 32 or 48 blocks.

<p align="center">
  <img src="media/jukebox.webp" alt="The jukebox between two speakers" width="58%">
  <img src="media/gui-jukebox.webp" alt="The jukebox screen with genres, tracks and the playing title" width="38%">
</p>

## New: casino outfits

Seven outfits of four pieces each, worn like armour: tuxedo with top hat, croupier, ruby, sapphire, ivory dinner
jacket, high roller and evening gown. They are crafted from wool or bought in the Chip Shop.

<p align="center">
  <img src="media/outfits.webp" alt="Three players in a tuxedo with top hat, the high roller suit and the evening gown" width="72%">
</p>

## Decoration

From the mod: 27 floor and wall blocks (marble, velvet carpets, dark oak panelling with gold trim), chairs, stools
and sofas you can sit on, bar counters, shelves and glasses, chandeliers and sconces, casino signs, plaques, velvet
rope posts, TVs in three sizes, and speakers that play lounge music.

**New:** stairs and slabs for all thirteen dark oak wall blocks, and casino signs in 2×2 and 4×4.

<p align="center">
  <img src="media/decoration.webp" alt="A bar with stools, a cash register, a serving tray and a bottle shelf" width="49%">
  <img src="media/decoration-stairs.webp" alt="A booth built from the new stairs and slabs, with sofas, a table and wall sconces" width="49%">
</p>

## Croupier and guide book

A villager brought onto a Croupier Counter becomes the casino's croupier. He sells the hiring card, and hiring him
earns the Croupier Badge that the blackjack and poker table recipes ask for.

The Casino Guide is a book that explains the casino and shows every recipe as a crafting grid.

<p align="center">
  <img src="media/croupier.webp" alt="The croupier behind his counter" width="58%">
  <img src="media/gui-guide.webp" alt="A recipe page of the Casino Guide" width="36%">
</p>

## For the people who run the server

- **No other plugin needed.** No economy plugin, no client mod. The plugin serves its resource pack itself, on the
  Minecraft port.
- **Bets are safe.** An open round survives a logout or a crash and is settled when the player is back. Winnings
  that do not fit into the inventory are kept and handed over later.
- **Screens close in danger.** A casino screen does not open during a fight and closes when a monster comes near,
  so nobody dies with a menu open.
- **Machines are protected.** By default only the owner and admins can break a machine, and explosions do not
  destroy it.
- **The Croupier's Hand** is the admin tool: in creative mode, click a machine to name it, set its win chance or
  card bias, or copy its settings to another machine; click a cashier or the Chip Shop to edit prizes and prices.
- **Everything else is in `config.yml`**: stake limits, exchange rates, prices, table sizes and timers, language
  and time zone.
- **English or German**, set with one line.
