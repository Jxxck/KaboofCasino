# Changelog

Every release of KaboofCasino, newest first. The version is the one `/version KaboofCasino` shows.

## [0.20.0] - 2026-11-16

- **English is the default language.** `language: en` or `language: de` in `config.yml` sets the texts
  (`messages_en.yml` / `messages_de.yml`), the names of the plugin's own items, the number format, and the lettering
  on buttons and signs: the plugin now carries one resource pack per language.
- **English commands**: `/casino collect`, `/casino leaderboard off|on`, `/casino music off|on`,
  `/casino top [category] [week|total]`. The German words still work; tab completion follows the language.
- **`time-zone` setting** for the leaderboard's weeks and the shops' daily limits (default: the server's own zone;
  it was fixed to Europe/Berlin).
- `config.yml` is commented in English; the Chip Shop's categories are called `floors`, `furniture`, `bar`,
  `lights`, `tech`, `machines`, `outfits`.
- **Removed**: the Discord posts for big wins and weekly winners, which needed a plugin that is not public, and with
  them the `discord:` section of `config.yml`.

Updating from 0.19.0 or older: replace the jar and restart. An existing installation stays German and keeps
Europe/Berlin: the plugin writes `language: de` and `time-zone: Europe/Berlin` into its `config.yml`, renames the
changed settings and keeps their values. The German casino pack is unchanged, so nobody downloads it again; the
optional music pack is downloaded once more (its description is English now). To switch to English, set
`language: en` and restart.

## [0.19.0] - 2026-11-16

The first version prepared for the public. Versions 0.1.0 to 0.18.1 ran only on the author's own server; the commit
history shows how the plugin grew.

- The seven games of MineVegas Casino for players without the mod: slot machine, fortune wheel, Plinko, roulette,
  blackjack, poker and duck derby, with its decoration, croupier and guide book.
- Added by the plugin: chips and a cashier, a Chip-Shop, leaderboards, several players at one roulette or blackjack
  table, Texas Hold'em between players, a jukebox and casino outfits.
- The plugin serves its resource pack itself, on the Minecraft port. With `pack.url-host: auto` (the default) every
  player downloads it from the address they connected with; behind a proxy a fixed address is needed.
- Texts, item names and the labels in the screens are German in this version.
- Needs Paper 26.3 (build 205 or newer), Java 25 and `disable-mushroom-block-updates: true` in
  `config/paper-global.yml`. The [README](https://github.com/Jxxck/KaboofCasino#installation) has the steps.
