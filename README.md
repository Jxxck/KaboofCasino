# KaboofCasino

A casino for Paper servers that needs no client mod: slot machines, a fortune wheel, Plinko, roulette, blackjack,
poker and a duck derby, built in the world as real machines with animated 3D models and their own screens. Players
only accept the server resource pack; the plugin serves it itself.

**KaboofCasino is a Paper port of the mod
[MineVegas Casino](https://www.curseforge.com/minecraft/mc-mods/minevegas-casino) by byMehd.** The games, their
models, textures, sounds and texts come from MineVegas Casino. On Fabric or NeoForge, use the original mod.

## Download

The plugin jar is on the [releases page](https://github.com/Jxxck/KaboofCasino/releases). [CHANGELOG.md](CHANGELOG.md)
says what each version changed.

## What is in it

From MineVegas Casino:

- Seven games with the mod's rules and odds: slot machine, fortune wheel, Plinko, roulette, blackjack, poker
  (Casino Hold'em against the croupier) and duck derby.
- Casino decoration: marble, panelling and carpet blocks, chairs, stools and sofas to sit on, bars, chandeliers,
  signs, TVs, and speakers that play lounge music (optional second resource pack).
- The croupier, the Croupier's Badge and Hand, and the casino guide book with every recipe.

Added by this plugin:

- **Chips** as real items (1, 5, 25, 100, 500, 1000) and a **cashier** that exchanges ores for chips. Games take
  chips only.
- A **Chip Shop** that sells the decoration, machines and outfits for chips, and a prize shop at the cashier.
- **Leaderboards** as wall boards and columns, plus `/casino top`.
- **Multiplayer tables**: several players at one roulette or blackjack table, and No-Limit **Texas Hold'em** between
  the players at the poker table.
- A **jukebox** with 98 titles, **casino outfits**, bigger casino signs, and stairs and slabs of the wall blocks.

Stakes and winnings are items in the player's inventory. There is no virtual currency and no economy plugin is
needed. An open round is never lost: after a logout or a server crash it is settled when the player is back, and
winnings that do not fit into the inventory are kept and handed over later (`/casino collect`).

## Requirements

- **Paper 26.3** (build 205 or newer) on **Java 25**. Spigot and Folia are not supported.
- Players have to accept the casino resource pack (about 7 MB). Without it the casino does not look right and the
  machines cannot be used. The lounge music is a second, voluntary pack (about 22 MB).
- `block-updates.disable-mushroom-block-updates: true` in `config/paper-global.yml`. The full decoration blocks are
  drawn on unused mushroom block states, and without this setting Paper would change those states back. While it is
  `false`, these blocks cannot be placed and the server log says so.

## Installation

1. Put `KaboofCasino-<version>.jar` from the [releases page](https://github.com/Jxxck/KaboofCasino/releases) into
   `plugins/`.
2. Set `disable-mushroom-block-updates: true` in `config/paper-global.yml` and restart the server.
3. Join and accept the resource pack. `/casino give slot_machine` gives you the first machine;
   `/casino give` with an unknown name lists everything there is. `/casino give casino_guide` is the guide book.
4. Ask a player from outside your network to join. When they see the casino, set `pack.enforce: true` in
   `plugins/KaboofCasino/config.yml` so that nobody plays without the pack.

Players get machines and decoration by crafting (the guide book shows the recipes) or from the Chip Shop. A casino
starts with a cashier and a Chip Shop from an admin: `/casino give cashier` and `/casino give chip_shop`.

A world that already contains mushroom blocks with unusual states (placed with commands or by old versions of the
game) could show some of them as casino blocks. `tools/census/mushroom_census.py` counts those states in a world
before you switch the setting on.

### How the resource pack reaches the players

The plugin answers the pack download on the Minecraft port itself, so no second port has to be opened. With
`pack.url-host: auto` (the default) every player gets the address they connected with, which works as long as players
reach this server directly.

Behind a proxy (Velocity, BungeeCord, TCPShield and the like) that address leads to the proxy, not to this server.
Set `pack.serve: http-port`, forward `pack.port` (25580) and put the server's public address into `pack.url-host`.

Leave `resource-pack=` empty in `server.properties`.

## Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/casino` | About the plugin, your open rounds and winnings waiting for you | `kaboofcasino.use` |
| `/casino stats` | Your own statistics | `kaboofcasino.use` |
| `/casino top [category] [week\|total]` | Top 10 of this week or of all time | `kaboofcasino.use` |
| `/casino leaderboard off\|on` | Hide yourself from the leaderboards, or show yourself again | `kaboofcasino.use` |
| `/casino collect` | Collect winnings that did not fit into your inventory | `kaboofcasino.use` |
| `/casino music off\|on` | Turn the lounge music and jukeboxes off or on for yourself | `kaboofcasino.use` |
| `/casino credits` | Credits and licences | `kaboofcasino.use` |
| `/casino give <item> [player] [amount]` | Give a machine or any casino item | `kaboofcasino.admin` |
| `/casino remove` | Remove the machine you are looking at | `kaboofcasino.admin` |
| `/casino reload` | Reload `config.yml` and the texts | `kaboofcasino.admin` |

`kaboofcasino.use` is given to everybody, `kaboofcasino.admin` to operators. Admins also break other players'
machines and, in creative mode, use the Croupier's Hand (`/casino give croupier_hand`) to set a machine's name and
win chance and to edit the shops.

## Configuration

`plugins/KaboofCasino/config.yml` holds the language and time zone, the stake limits, the win chance or card bias of
every game, the exchange rates of the cashier, the prices of the Chip Shop, the safety rules (no casino screen during
a fight or next to a monster) and the pack delivery. `messages_en.yml` holds every text. Both files are commented, and
the plugin adds new settings to them when it is updated.

The games ship with the mod's odds. A win chance in `config.yml` overrides them per game.

## Language

The casino is English or German: `language: en` (the default) or `language: de` in `config.yml`, applied at the next
server restart. The setting covers the chat messages (`messages_en.yml` or `messages_de.yml`, both can be edited),
the names of the plugin's own items, the number format, and the lettering on buttons and signs, which is part of the
resource pack: the plugin carries one pack per language.

Item names and texts that come from MineVegas Casino follow each player's own Minecraft language where the mod has a
translation (twelve languages), whatever the server's setting.

The commands work in both languages (`/casino collect` and `/casino abholen`); tab completion suggests the words of
the server's language.

## Good to know

- The leaderboard's weeks start on Monday 00:00 and the shops' daily limits reset at midnight, in the server's time
  zone or the one set as `time-zone` in `config.yml`.
- A machine belongs to the player who placed it. By default only the owner and admins can break it
  (`machines.break`).

## Building

```bash
git clone https://github.com/Jxxck/KaboofCasino.git
cd KaboofCasino
./gradlew build
```

The plugin jar is `build/libs/KaboofCasino-<version>.jar`. The build needs internet access the first time: it
downloads the MineVegas Casino jar from CurseForge (27 MB, checked against a fixed SHA-1) and builds both resource
packs from it. If that download fails, save the file named in `gradle.properties` to `libs/` yourself. Gradle fetches
JDK 25 if it is not installed.

`docs/IMPLEMENTATION.md` describes the architecture. `testing/` holds the author's bot tests, which need a test
harness that is not part of this repository.

## Bugs and contributions

Report bugs in the [issue tracker](https://github.com/Jxxck/KaboofCasino/issues) with the plugin version, the Paper
version and the lines of the server log around the problem. If an item was lost or duplicated, say so: those reports
come first.

Pull requests are welcome. `./gradlew build` has to pass; it runs the unit tests and builds both resource packs.

## Credits and licence

KaboofCasino is written by Jxxck and released under the [MIT License](LICENSE).

It contains code and assets of **MineVegas Casino** by byMehd, used under that mod's licence (MIT with an attribution
condition, [LICENSE-MineVegas.txt](LICENSE-MineVegas.txt)):
https://www.curseforge.com/minecraft/mc-mods/minevegas-casino

That licence asks every port and fork to show this link in its public description. If you fork or republish
KaboofCasino, keep the link and the licence file.

The lounge music is by Kevin MacLeod (incompetech.com), licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). [NOTICE.md](NOTICE.md) lists everything that comes from
others.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
