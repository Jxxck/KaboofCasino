# What is in KaboofCasino

A tour of everything the plugin puts on a server: the seven games, what the plugin adds to them, and the tools for
the people who run the casino. The games, their look and their rules come from the mod
[MineVegas Casino](https://www.curseforge.com/minecraft/mc-mods/minevegas-casino) by byMehd; sections marked
**new** are things the mod does not have.

Players need no mod. They accept the server's resource pack and see real machines in the world, with moving parts,
sounds and their own screens.

<!-- media: overview.png — a furnished casino hall with several machines in view -->

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

<!-- media: slot-machine.png — the machine in the world -->
<!-- media: slot-machine.webp — a spin with a win, about 6 s -->
<!-- media: gui-slot.png — the screen with a stake set -->

## Fortune wheel

A wheel three blocks wide that turns in the world. Each spin takes up to three separate bets; the segment under the
pointer pays between ×0.25 and ×5, or nothing.

<!-- media: fortune-wheel.webp — a spin, about 8 s -->
<!-- media: gui-wheel.png -->

## Plinko

Up to three balls fall through the pegs of a three-block board, each with its own stake, and land in pockets worth
×0.25 to ×8.

<!-- media: plinko.webp — three balls dropping, about 8 s -->
<!-- media: gui-plinko.png -->

## Roulette

A 2×2 table with a turning rotor and a rolling ball. Bet on red, black or green; red and black pay ×2, green ×36.

**New: several players at one table.** As soon as somebody else stands at the table, a 15-second betting window
opens. Up to eight players bet, then one spin decides for all of them. Alone, you spin at once.

<!-- media: roulette.webp — the ball rolling and landing, about 8 s -->
<!-- media: gui-roulette.png -->
<!-- media: roulette-multiplayer.png — several players around the table during the countdown -->

## Blackjack

A table with an animated dealer who shuffles, deals and turns the cards in front of you. Six decks, the dealer
stands on 17, you can hit, stand or double, and a blackjack pays 3:2.

**New: up to five players at one table.** With others nearby, the dealer waits 15 seconds for everyone's bets and
then plays one round against all seats, each player deciding in turn.

<!-- media: blackjack.webp — a hand being dealt and played, about 10 s -->
<!-- media: gui-blackjack-bet.png -->
<!-- media: gui-blackjack-turn.png -->
<!-- media: blackjack-multiplayer.png -->

## Poker

**Against the dealer (Casino Hold'em).** You place an ante and get two cards; after the flop you call for twice the
ante or fold. The dealer needs a pair of fours to play. A winning ante pays from 1:1 up to 100:1 for a royal flush.

<!-- media: gui-poker-bet.png -->
<!-- media: gui-poker-decision.png -->

**New: Texas Hold'em between players.** When two or three players sit down at the table, they play No-Limit Texas
Hold'em against each other with the chips they bought in with, and the croupier only deals. Blinds, checks, bets,
raises, all-ins and side pots work as at a real table; the showdown turns the others' cards face up. A short
explanation of the rules is one click away. Buy-in, blinds and thinking time are settings.

<!-- media: holdem.webp — a hand between two players, about 12 s -->
<!-- media: gui-holdem-turn.png -->
<!-- media: gui-holdem-showdown.png -->

## Duck derby

Four rubber ducks race down a pool three blocks wide and four long. Pick your duck: first place pays ×3, second
place returns the stake.

Race alone, or start a group race: other players get ten seconds to bet on their own ducks, up to 64 of them, and
a panel above the pool shows who bet on which duck.

<!-- media: duck-derby.webp — a race from start to finish, about 10 s -->
<!-- media: gui-derby.png -->
<!-- media: duck-derby-group.png — the panel with several bettors -->

## New: chips and the cashier

Games are played with chips, which are real items in six denominations: 1, 5, 25, 100, 500 and 1000. Chips cannot be
turned back into ores, so the casino has its own money and the server's economy stays as it is.

The cashier is a counter with three tabs:

- **Exchange** ores for chips at rates the admin sets (by default from 1 chip for coal up to 256 for a netherite
  ingot, nine times as much for a block).
- **Change** chips into other denominations.
- **Prizes**: a shop where the admin offers any items for chips, with an optional limit per player and day.

<!-- media: cashier.png — the cashier in the world -->
<!-- media: gui-cashier-exchange.png -->
<!-- media: gui-cashier-prizes.png -->
<!-- media: chips.png — the six chips in an inventory -->

## New: Chip Shop

A second counter that sells the whole casino for chips: floors and walls, furniture, bar and tables, lights and
signs, tech, machines and outfits, sorted into seven tabs. Prices follow each item's recipe and can be set per item,
with daily limits if wanted.

<!-- media: chip-shop.png -->
<!-- media: gui-chip-shop.png -->

## New: leaderboards

Boards for the wall in eight sizes from 2×2 to 6×4 and a free-standing column. They list the top ten in twelve
categories (biggest win, highest multiplier, net profit, chips staked, most rounds, and the biggest win in each of
the seven games), for this week or for all time. A board turns its pages by itself, or an admin pins one page.

`/casino top` shows the same lists in chat, and `/casino leaderboard off` hides a player from all of them.

<!-- media: leaderboard-wall.png — a large wall board -->
<!-- media: leaderboard-column.png -->

## New: jukebox

A two-block jukebox with 98 tracks in nine genres: every music disc and the game's own soundtrack, played from each
player's client, plus the casino's lounge music. Anyone nearby picks the track, the mode (in order, shuffle, repeat)
and the range of 16, 32 or 48 blocks.

<!-- media: jukebox.png -->
<!-- media: gui-jukebox.png -->

## New: casino outfits

Seven outfits of four pieces each, worn like armour: tuxedo with top hat, croupier, ruby, sapphire, ivory dinner
jacket, high roller and evening gown. They are crafted from wool or bought in the Chip Shop.

<!-- media: outfits.png — players wearing different outfits -->

## Decoration

From the mod: 27 floor and wall blocks (marble, velvet carpets, dark oak panelling with gold trim), chairs, stools
and sofas you can sit on, bar counters, shelves and glasses, chandeliers and sconces, casino signs, plaques, velvet
rope posts, TVs in three sizes, and speakers that play lounge music.

**New:** stairs and slabs for all thirteen dark oak wall blocks, and casino signs in 2×2 and 4×4.

<!-- media: decoration.png — a furnished corner: bar, sofas, chandelier -->
<!-- media: decoration-stairs.png — the new stairs and slabs -->

## Croupier and guide book

A villager brought onto a Croupier Counter becomes the casino's croupier. He sells the hiring card, and hiring him
earns the Croupier Badge that the blackjack and poker table recipes ask for.

The Casino Guide is a book that explains the casino and shows every recipe as a crafting grid.

<!-- media: croupier.png -->
<!-- media: gui-guide.png — a recipe page of the guide -->

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
