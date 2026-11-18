# KaboofCasino – Implementation notes

The maintainer's working notes, in the order the plugin grew: step 1 foundation + slot machine; step 2 fortune wheel +
Plinko; step 3 roulette; step 4 seats, deco, music; step 5 blackjack; step 6 poker; step 7 duck derby; step 8
croupier, guide, TVs, dealer station; then part 2 with chips, the cashier, the leaderboard, the Chip-Shop, outfits,
multiplayer tables, Texas Hold'em and the jukebox. They describe what was built, how the next games plug in, and the
pitfalls found on the way. Version numbers below 0.19.0 are versions that only ever ran on the author's own server
(a permadeath SMP, which is why item safety and "permadeath" come up so often); "live" means that server. Until
0.20.0 the plugin was German only, so the texts, button labels and commands quoted in these notes are the German
ones (`messages_de.yml`, "DREHEN", `/casino abholen`); section "Languages" says how English came in.

References such as `PLAN.md`, `PLAN-2.md`, `games.md`, `render.md` and `server.md` (here and in code comments) point
to design notes and an analysis of the mod that are not part of this repository: `PLAN` = the numbered design
decisions, `games.md` = the mod's rules, `render.md` = look, pack, GUI and pack delivery, `server.md` = item safety,
protection and storage. What matters of them is repeated in this file.

Licence: the plugin is MIT (`LICENSE`). MineVegas Casino, which it ports, is MIT with an attribution condition: every
public description must link https://www.curseforge.com/minecraft/mc-mods/minevegas-casino (it is in the README,
`plugin.yml`, `pack.mcmeta`, `credits/CREDITS.md` in the pack, `/casino` and `/casino credits`). The mod's licence text
is `LICENSE-MineVegas.txt` at the repository root and at the root of the plugin jar, and `credits/LICENSE.txt` in the
packs. `NOTICE.md` lists what comes from where.

## 1. Build

```bash
./gradlew build
```

On a machine that also runs a server, keep the build small (this is what the author uses):

```bash
systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
  ./gradlew --no-daemon --max-workers=2 --offline build
```

- Gradle 9.8.2 (wrapper), Kotlin DSL, Shadow 9.6.1, JUnit 5.14.4, Java 25 (Gradle fetches a JDK 25 toolchain if none
  is installed). `--offline` works once everything is in Gradle's cache and the mod jar is in `libs/`.
- `fetchModJar` downloads the MineVegas Casino jar named in `gradle.properties` (`casino.modJarUrl`) into
  `casino.modJar` (`libs/`, ignored by git) and checks `casino.modJarSha1`. A jar that is already there with the
  right SHA-1 is used as it is; another SHA-1 fails the build.
- `-Xlint:all` with `-processing`, `-serial` and `-classfile`: JOML (the `Transformation` types of paper-api) is a
  Java 1.2 class file with type annotations and javac flags every use of it. 0 warnings otherwise.
- The game-port pack delivery needs `io.papermc.paper.network.ChannelInitializeListener(Holder)`, which is only in the
  server jar, and netty. The two classes are compile-only stand-ins in `src/paperStubs` (signatures only, never
  packaged); netty (`casino.nettyVersion`, 4.2.16.Final as in Paper 26.3-205) comes from Maven Central, compile only
  and for the EmbeddedChannel tests. After a Paper update, check the netty version in the server's
  `libraries/io/netty` and run the build once with `-Pcasino.paperServerJar=<server>/versions/26.3/paper-26.3.jar`:
  `PaperStubsTest` then compares the stand-ins with the real classes.
- Outputs: `build/libs/KaboofCasino-<version>.jar` (deployable, Hikari shaded to `de.tigges.kaboofcasino.libs.hikari`,
  sqlite-jdbc from the server classpath), `build/pack/KaboofCasino-pack.zip` + `.sha1` + `gui-layout.json`.
- `buildPack` (JavaExec of the `:pack-tool` subproject) runs before `processResources`; the zip, its SHA-1 and the GUI
  manifest are embedded in the jar under `pack/`. Inputs: the mod jar pinned by path + SHA-1 in `gradle.properties`
  (read as a zip, never executed) and `pack-src/`.
- `build/reports/gui/slot-preview-<W>x<H>.png` (from `GuiPreviewTest`): the slot GUI drawn like the client draws a
  chest title on the scaled screens 480x270 (1080p, scale 4), 456x256 (1366x768) and 427x240 (720p/1440p/4K with
  "Auto"). Look at them after every GUI change.

## Languages (0.20.0)

One language per server, `language: en` (default) or `de` in `config.yml`, fixed while the plugin runs
(`config.Language`, a process-wide switch set when the settings are first loaded; a changed value waits for the
restart, because the pack and the item names are loaded once).

- **Texts**: `messages_en.yml` and `messages_de.yml` are both in the jar; the one of the language is copied to the
  data folder and merged as before. `MessagesTest` keeps the two files equal in keys and placeholders.
- **Names and fallbacks in code** (decoration, outfits, the croupier's hand, the floating panels): every table has
  the mod's German text (`de_de.json`) and its English one (`en_us.json`); tests compare both with the mod jar. The
  plugin's own items have names written for both languages. These are the fallbacks of translatable components: a
  client that has the pack sees the mod's items in its own language anyway.
- **Resource pack**: text that is drawn into pictures (buttons, tab labels, status lines) and built into models (the
  LED words on the cashier, the Chip Shop and the leaderboards) cannot follow a setting at runtime, so the pack tool
  builds one casino pack per language (`PackLang`; `build/pack/en`, `build/pack/de`) and the jar carries both
  (`pack/<language>/…`). The German pack is byte-identical to the one of 0.19.0. The music pack exists once.
- **Numbers and dates**: `ChipText` groups digits with `,` or `.`, decimals follow (`×2.5` / `×2,5`), big numbers on
  the boards read `12.3k` / `12,3 Tsd.`.
- **Commands**: the English and the German words are both accepted at all times (`collect` / `abholen`, `off` / `aus`,
  `week` / `woche`, the leaderboard categories); tab completion and the usage texts show the language's words.
  `BoardCategory.id()` and `BoardPeriod.id()` are the English words and what a pinned board stores; pins written by
  older versions carry the German words and are read as before.
- **Time zone**: weeks and shop days were counted in Europe/Berlin; now `time-zone:` (default `system`).
- **Upgrading an installation of 0.19.0 or older** (`ConfigLoader.migrate`, `Messages.migrate`): a `config.yml`
  without `language:` is a German one, so it gets `language: de` and `time-zone: Europe/Berlin` and nothing changes
  for its players; the Chip-Shop tabs' German config keys and the German message keys are renamed with their values;
  the `discord:` sections are removed (the posts through the author's own Discord plugin are gone).

## Credits in the game (0.20.1)

What a player reads names the plugin and its author: `/casino`, the pack prompt (`pack.prompt`), the cover of the
Casino Guide (`guide.credits`), the tooltip of the machine items (`item.slot-machine-lore`) and the description of the
packs in the client's pack list (`Credits.add`, `MusicPack.mcmeta`). MineVegas Casino is named in two places in the
game, the guide's cover and `/casino credits`; the music's CC BY credit stays in `/casino credits`, the music prompt
and the music pack's description. The licence texts and full credits are files: `LICENSE-MineVegas.txt` and `NOTICE.md`
in the jar, `credits/` in every pack. The mod's licence asks for a visible link to its CurseForge page in the public
description on every platform the plugin is published on; that is the README's (and any download page's) job, not the
game's.

A live `messages_*.yml` keeps the wording it was created with, so `Messages.migrate` drops each of these five texts
when it is still an earlier default (`Messages.REWORDED`: the first 16 hex digits of the SHA-256 of every default
these keys ever had, German and English) and the merge writes the new default. A text an admin changed has another
fingerprint and stays. When one of these defaults changes again, add the fingerprint of the wording it replaces.

## 2. Architecture

| Package | Content |
|---|---|
| `KaboofCasinoPlugin`, `CasinoContext` | wiring only; the context hands games the shared services |
| `config` | `config.yml` → `CasinoConfig` (validated, defaults merged into the live file), `messages_<language>.yml` → `Messages`/`Msg`, `Language` |
| `pack` | `PackFile` (embedded zip, re-hashed), `GamePortPack` + `PackSniffer` + `HttpSniff` (HTTP on the game port), `PackHttpServer` (own port), `PackListener` (configuration phase), `PackSessions` |
| `wager` | the bet engine: `WagerEngine` (pure), `Ledger`/`RoundRecord`/`ItemAmount`, `Account` (port) + `PlayerAccount` (Bukkit), `LedgerCodec` (player PDC), `LedgerListener` (quit/death/join/timer/disable); chips (PLAN-2 C1/C2): `Chips` (pure: denominations, the chip-value pseudo item `kaboofcasino:chips`, breakdown, selections, paying a value with change), `ChipItems` (the chip items) |
| `machine` | `MachineType` (footprints), `Facing`, `MachineRecord` + `ChunkRecords` (chunk PDC), `MachineService` (index, place, remove, verify, tick), listeners for interaction, breaking, protection, chunks, item vetoes; `CasinoItem(s)`, `Recipes` (from `pack/recipes.json`), `Heartbeat` |
| `visual` | `RigMath` (display transformations), `Displays` (non-persistent spawn helpers), `Coins` (payout coin displays), `FloatingPanel` (bet/result panel) |
| `gui` | PostMarkt's selection-model framework (`Menu`, `MenuService`, `MenuListener`, `ClickRoute`, `ClickRules`, `OpenRules`, `DisplayCopies`, `DisplayPurge`), `MenuSafety` (copied) + `DangerSweep` (5-tick), `GuiLayout` + `TitleBuilder` (font-glyph titles) |
| `game` | `MachineController` (what every machine game shares: interact checks, claim, range, menus, commit, settle, heartbeat, coin target, sounds/particles at local px), `GameMenu`, `StakeMenu` (three bet slots), `PanelText`, `Odds` (the mod's forced win-chance pools), `FairPayout` (verbatim port), `CasinoRandom` (L64X128MixRandom from SecureRandom) |
| `game.slot` | `SlotGame` (rules), `SlotSpin` (spin plan), `IdleLights`, `SlotController` (runtime), `SlotRig` (entities), `SlotMenu` (GUI), `SlotPanel`, `SlotGeometry` |
| `game.wheel` | `FortuneWheelGame`, `FortuneWheelSpin`, `FortuneWheelBlur` (verbatim ports), `WheelController`, `WheelRig`, `WheelMenu`, `WheelGeometry` |
| `game.plinko` | `PlinkoGame`, `PlinkoDrop`, `PlinkoLights` (verbatim ports), `PlinkoController`, `PlinkoRig`, `PlinkoMenu` |
| `game.roulette` | `RouletteSpin` (verbatim port), `RouletteGame` (colours, payouts, forced pools), `RouletteController`, `RouletteRig`, `RouletteMenu` (9 slots, one colour), `RoulettePanel` |
| `game.blackjack` | verbatim ports (`BlackjackRound`, `BlackjackShoe` + `copy()`, `BlackjackCards`, `BlackjackBias`, `BlackjackLayout`, `BlackjackCardMotion`, `BlackjackCardGeometry`, `BlackjackTableState`, `BlackjackFootprint`, `Dealer*`), `ShoeRandom`, `BlackjackStand` (stand result on a shoe copy), `BlackjackPayout`, `BlackjackController` (hand, PLAYING records, timeout/quit/death stand), `BlackjackRig` + `BlackjackDealerRig` + `BlackjackCardRig`, `BlackjackPanel`, `BlackjackBetMenu`, `BlackjackRoundMenu`, `BlackjackSnapshot` |
| `game.poker` | verbatim ports (`PokerRound`, `PokerHands`, `PokerBias`, `PokerLayout`, `PokerTableState`, `PokerTableGeometry`; the shoe, cards, dealer and card motion are blackjack's), `PokerCall` (the called outcome on a shoe copy), `PokerPayout`, `PokerController` (hand, PLAYING records, timeout/quit/death fold), `PokerRig`, `PokerPanel`, `PokerBetMenu`, `PokerRoundMenu`, `PokerSnapshot` |
| `game.derby` | verbatim ports (`DuckDerbyRace`, `DuckDerbyLayout`, `DuckDerbyGeometry`, `DuckDerbySignLights`, `DuckDerbyFootprint`, `DerbyPanelPresentation`), `DerbyRules` (winner draw, plan retry, runner-up, payouts), `DerbyController` (solo and group races, records, effects), `DerbyRig`, `DerbyBetMenu`, `DerbyView`, `DerbySnapshot`, `DerbyMember` |
| `deco.full` | the 27 full-cube deco blocks on 72 reserved `down=true` mushroom states (`FullDecoStates`, persistent table `tools/census/chosen-states.txt`), vanilla mushroom block items with `block_state`/`item_model`, drops, explosions, pistons, pilaster connections (`PilasterChoice`), the Paper-switch guard |
| `deco.display` | the 45 display-entity deco types (`DecoType`): chunk-PDC records (`kaboofcasino:deco`), carriers (barrier / light + interaction / vanilla stairs and slabs), rigs, placement and support rules, connections (tables, bars, rope posts, the sofa's verbatim `SofaLayout`), protection, candle flames, TV channels, the dealer station's `DealerStationHook` and lookup |
| `croupier` | the croupier villager as a display NPC at a counter (step 8, option A): `CroupierService` (index, chunk lifecycle, trade screen, hiring, counter loss), `CroupierSpawnTrigger` + `VillagerOnCounterTrigger`, `CroupierEligibility`, `CroupierPlacement`, `CroupierRig`, `CroupierTrade`, `CroupierHiring`, records in chunk PDC `kaboofcasino:croupiers` |
| `guide` | the casino guide book (step 8): `GuideBook` (the mod's `casino_guide.json`, embedded as `pack/guide.json`), `RecipePictures` (the recipe pictures' lines, `pack/guide-pictures.json`), `GuideDialogs` (spread → dialog model, pure), `GuidePaper` (Paper dialogs), `GuideSessions` (navigation, history, click rate), `GuideService` + `GuideListener` |
| `cashier` | the cashier (step 9, PLAN-2 C3): `CashierService` + `CashierController` (the machine kind `CASHIER`, one player at a time), `CashierRig`, the three tab menus (`ExchangeMenu`, `ChangeMenu`, `ShopMenu` on `CashierMenu`/`CashierView`), `Cashbox` (the atomic exchange, change and buy), `CashierMath`, `ShopEntry`/`Prizes`, `DailyLimits` (player PDC), `CashierLog` (DB table `cashier`); the shop editor of the croupier's hand is `admin/Shop*` |
| `admin` | the croupier's hand (step 8, partial port of the mod's settings): `AdminService` (dialog sessions, clicks, edits, labels), `AdminDialogs`/`AdminPaper` (machine and whole-casino dialogs), `AdminClick` (payload parsing), `HandCopy` + `Clipboard` (copy/paste), `EditRate`, `AdminLabels` (floating names); `machine.MachineSettings` (own values over type values), `config.ConfigEdits` (comment-keeping `config.yml` edits) |
| `seat` | sitting on chairs, stools and sofas: a non-persistent item-display vehicle, the mod's dismount search, forced stand-up on danger/combat/damage/quit/death/teleport, rescue of players found inside deco barriers |
| `music` | `MusicTimeline` (verbatim), the station per world (world PDC), playback per player who loaded the music pack, note particles; second pack delivery in `pack/`; `MusicOptOut` (`/casino musik aus|an`, player PDC) |
| `storage` | SQLite: `Database` (1 connection, 1 thread, migrations), `RoundLog` (RoundSink + stats), `MachineIndex` |
| `command` | `/casino` (Brigadier `BasicCommand`) |
| `pack-tool/` | builds the pack (see 6) |

Threads: everything Bukkit on MAIN; JDBC only on `KaboofCasino-DB`; the game-port sniffer on the connection's netty
event loop (never blocks; the zip is a shared read-only buffer); the HTTP server (http-port mode) on
`casino-pack-http-*`; the pack send on Paper's configuration threads (blocking there is intended).

Stores (server.md 7):

| Data | Where | Authority |
|---|---|---|
| open rounds, owed items | player PDC `kaboofcasino:ledger` | **the only money store**, saved with the inventory |
| machines | chunk PDC `kaboofcasino:machines` of the anchor chunk | saved with the barrier blocks |
| rig entities | nowhere (non-persistent, rebuilt at chunk load) | – |
| claims, spin runtime, lights | RAM | rebuilt/ended on restart; money unaffected |
| round log, machine index, stats | `plugins/KaboofCasino/casino.db` | never authoritative |

Enable order: config → pack (hash check) → GUI layout → DB (migrations, `VOID_CRASH` sweep of OPEN rows older than
1 h, machine numbers) → engine → menus/safety → machines → listeners → recipes → command → pack delivery → already
loaded chunks → 1-tick timer (machines, break timers, sweep every `safety.sweep-ticks`, ledger every
`owed.delivery-ticks`) → players already online (reload): recipe unlocks, pack status, join recovery. Disable order:
close menus → settle every online player (STOP) → remove rigs → recipes → pack delivery → DB. Paper saves the
players after `onDisable` (server.md F8).

### Pack delivery

`pack.serve: minecraft-port` (default): the pack is served over the Minecraft port itself, so no extra port forward
is needed. The URL is `http://<host>:<port>/pack/<sha1>.zip` (`PackUrl`): with `pack.url-host: auto` (default) host
and port are the ones the client's own handshake names (its virtual host), so the download takes the way the game
connection took; with a fixed `pack.url-host` the port is `pack.url-port` (the public game port, 25565). `auto` gives
no URL for a handshake that names no plain host or address (a proxy that rewrites it): the pack counts as failed for
that client and a warning asks for a fixed host.

- `GamePortPack.install` registers a Paper `ChannelInitializeListener` (key `kaboofcasino:pack`); it adds
  `PackSniffer` as the **first** handler of every new connection's pipeline (handler name `kaboofcasino_pack`).
- The sniffer buffers the first bytes and decides (`HttpSniff.classify`):
  - a known HTTP method prefix (`GET `, `HEAD `, `POST `, …) → HTTP; it waits for the end of the head (≤ 8 KiB,
    larger → 431);
  - anything else (a handshake starts with a VarInt length and packet id 0x00, the legacy ping with 0xFE, TLS or
    garbage) → the sniffer removes itself and fires the buffered bytes on unchanged; from then on the pipeline is
    exactly vanilla's.
- HTTP answers `HTTP/1.1` with `Connection: close` and closes after the write:
  - `GET`/`HEAD /pack/<sha1>.zip` (query ignored) → 200, `application/zip`, Content-Length, the body is the
    embedded zip as a read-only buffer;
  - any other path → 404; other methods → 405 with `Allow: GET, HEAD`;
  - each pack has its own download pool (`DownloadPolicy`: casino 16, music 4; music downloads can never take a casino
    slot); a full pool → 503 with `Retry-After: 5`; HEAD needs no slot. A GET body is written in 64 KiB slices while
    the channel is writable (≤ ~128 KiB queued per connection) and checked every second: nothing accepted for 15 s,
    below 16 KiB/s on average after 20 s, or older than the hard cap (casino 300 s, music 180 s) → reset
    (SO_LINGER 0) and the slot is free again.
- Before answering, vanilla's `timeout` handler (ReadTimeoutHandler) is removed so a slow download is not cut after
  30 s; nothing else of the Minecraft pipeline is touched.
- A client that sends nothing: after 5 s the sniffer passes through and vanilla's own read timeout takes over. An
  unfinished HTTP head at 5 s → 400 and close.
- Two INFO lines per download: `Resource pack (<pack>) download #n started on the game port (casino a/8, music b/4)`
  and `… sent: N bytes in M ms (…)` or `… cut (<reason>): sent/size bytes after M ms (…)`.
- On disable, the listener is removed and every sniffer already installed becomes a pass-through (`disabled` flag),
  so a plugin reload never leaves a dead handler in the pipeline.
- ProtocolLib 5.x (live) injects its handlers in `channelActive`/at login behind the decoder; tested together on the
  test instance (pack download, join, status ping, legacy ping).
- Paper's `settings.connection-throttle` (bukkit.yml) is `0` on live and on the test instance, i.e. off. Paper applies
  the throttle only to login handshakes (intent LOGIN) after the handshake packet is decoded, so HTTP requests and
  status pings never count against it. If it is ever turned on, the pack download still does not count, but the
  login that follows the download within the throttle time would not be refused either: the download is not a
  handshake.

`pack.serve: http-port`: the JDK `HttpServer` on `pack.bind:pack.port` (25580); the URL uses `pack.port` (with
`auto` the handshake's host and `pack.port`). Same pools and deadlines; 16 handler threads (one per slot + 4); a
watchdog thread closes downloads that break the rules (also a write blocked on a non-reading client); slices of
64 KiB (one big write made the JDK copy the body into a buffer twice its size).
The forward has to exist for that mode.

## 3. The bet engine (PLAN decision 2)

- **Commit** (`WagerEngine.commitAll`, one tick): one record per stake (the slot machine has one, a wheel or Plinko
  round up to three, each paid in its own item). Checks for all stakes together: owed empty, open records + stakes
  <= `rounds.max-open` (default 8), every item in `bets.items`, every count >= 1, every stake at most
  `bets.max-stake` (`bets.cap-per: slot`, the default, like the mod: a stack per bet slot) or all stakes together
  (`cap-per: round`), enough *plain* items of every item (stakes of the same item add up). Then the takes are
  planned for all stakes merged per item (`Account.planTake`, nothing changes if planning fails), applied (one take
  per inventory slot, clone + `setItem`) and **one** ledger write with all records follows; all or nothing. The game decides the outcome **before** the commit, so every record holds its final payout
  (`RoundState.DECIDED`).
- **Settle** (one tick): record out + payout into `owed` in one write, then `deliverOwed` gives what fits and keeps the
  rest owed. `deliverOwed` gives one item type at a time and writes the ledger right after each give, so an
  exception in the middle can never pay something twice. Settling a missing record is a no-op, so every path may
  call it.
- **Owed**: delivered every `owed.delivery-ticks` and by `/casino abholen` / the ABHOLEN button; while anything is
  owed, commits are refused (`OWED_PENDING`). Nothing ever goes on the ground; payout "coins" are ItemDisplays.
- **Quit/kick/timeout** (`PlayerQuitEvent` LOWEST, before the quit save): settle all (QUIT). Dead players are skipped.
- **Death** (`PlayerDeathEvent` LOW, before DeathBan's HIGH grave): DeathBan's grave holds at most 54 stacks
  (`addItem` per drop into one or two chests) and leaves the rest of `getDrops()` on the ground. So
  `DeathDrops.plan` puts payouts + owed into the drops only as far as they fit: first into partial plain stacks of
  the same item already in the drops, then into free slots (54 − non-empty drops); everything else stays **owed** in
  the PDC and is delivered by the owed timer after a revive (WARN log with both lists). `drainForDeath` builds the
  stacks first and writes the ledger (rounds gone, rest owed) last. MONITOR: if the death ended cancelled, only the
  stacks still found in the drops (by identity, as much as they still hold) are taken back and settled into the
  inventory; anything someone else already removed is logged CRITICAL (`CANCELLED_DEATH_MISSING`).
  keepInventory → settle into the inventory.
- **Crash**: the ledger and the inventory roll back together. A record still in the file is settled at the next join
  (JOIN_RECOVERY), or after a **death** respawn when the player joined dead (revive); the End-exit respawn does not
  recover (`LedgerListener.recoverOnRespawn`). On enable, players already online are recovered too.
- **Stop/disable**: settle all (STOP).
- **Watchdog**: records whose `ends` is more than 10 s past and that no loaded machine owns
  (`MachineGame.ownsRound`) are settled (TIMEOUT): chunk unloaded mid-round, plugin reload. A machine owns a round
  only while its controller ticked within 2 s (`Heartbeat`) and now < the round's end + 60 s, so a stuck or frozen
  machine cannot hold a stake forever.
- **PLAYING records** (blackjack, step 5): a hand with open decisions. Its payout is always what the hand pays if it
  is settled *now* with the mod's timeout rule (stand), finished with the cards the table's shoe would deal next
  (`BlackjackStand` on `BlackjackShoe.copy()`). The game computes the new value *before* each decision and rewrites
  the records with `WagerEngine.revise` (same ids; one ledger write; the double down takes its second stake in the
  same step, all or nothing: `DONE` / `GONE` / `NOT_ENOUGH`); only then the live hand deals the card. Quit, stop,
  death, the watchdog and the join after a crash therefore need no game logic: they settle the stored payout, which
  is exactly what the table then shows (the live hand stands as soon as its owner is gone). The double down is the
  game's rule: neither `bets.max-stake` nor the owed list stops it (the mod checks neither).
  Poker (step 6) uses the same records: at DEAL the payout is the fold (nothing); at CALL the outcome after turn and
  river is computed on a shoe copy (`PokerCall`) and written together with the call stake (2 x the ante); see
  section 10, step 6.
  A PLAYING record's `ends` is its last revision + 60 s (deal, hit, double, call, result); the table owns its records
  while its hand logic ran within 2 s (the heartbeat beats only after it) and until `ends` + 60 s grace, so a stuck
  hand (exception loop, missing rig) hands them to the watchdog (`BlackjackOwnershipTest`).
- Ledger version 2 = PLAYING exists; it is written only while such a record is open (`LedgerCodec.version`), so a
  ledger without one stays version 1 and readable by 0.3.x; version-1 ledgers read unchanged.
- The round log row gets the final stake, payout and detail at settlement (a blackjack hand changes them while it
  runs; for the other games they are unchanged).
- An unreadable ledger is never guessed: CRITICAL log, nothing changes, the casino is blocked for that player.
- Model check: `WagerEngineTest.everyInterleavingOfSaveCrashAndSettleIsConsistent` runs all 6^5 orders of commit,
  save, settle, crash, quit, join and checks conservation and at-most-once payment.

## 4. Machines (PLAN decision 4)

- Footprint: `MachineType.carriers(anchor, facing)` (offsets rotate with the facing). Barriers are the carriers: collision,
  clicks, immunity. Positions are `util.Pos` (world key + three ints), never `Block#getBlockKey` (Y 1023 collides).
- Placement (`MachineService.place`): all carriers in one chunk (a 3x3 machine across a chunk border is refused
  with the side to move it to: "stell sie einen Block weiter nach links/rechts"), replaceable + in height + world
  border + no living entity + no liquid and no waterlogged block at any carrier, no other non-spectator player within
  2 blocks (box to box) of any carrier, solid-centre support under the whole bottom row, game rules (slot: the mod's spacing rule `crowded`), spawn
  protection for non-ops at every carrier; barriers set, then a synthetic `BlockPlaceEvent` **per carrier** (any veto reverts all
  three), record into the chunk PDC, index row, rig spawned, one item taken.
- Breaking: `BlockDamageEvent` starts the plugin's timer (oak-plank speed, hardness 3.0, `BreakProgress`), cracks via
  `sendBlockDamage` on all carriers, `BlockDamageAbortEvent`/distance (in the machine's world)/quit stop it; only placer + ops
  (`machines.break: owner|anyone`); refused while a round runs. Creative (placer/op): instant, no drop.
  `/casino remove`: admin removal (settles, no drop).
- Protection: explosions (block lists filtered), pistons (moved blocks or any of the piston's 6 neighbours),
  fluids/buckets into carriers (also bucket items from dispensers, `BlockDispenseEvent`), portals created over a
  carrier (`PortalCreateEvent`), fire, `EntityChangeBlockEvent`. `/fill`, `/setblock`, structures, Litematica and the
  piston exploits arrive without events: `MachineService.verify` checks every carrier every `machines.verify-ticks`,
  at chunk load and at every interaction; a missing carrier removes the machine (remaining barriers to air, no drop).
- Offline players (permadeath): at quit the block box of feet and head is stored (`logouts` table, schema 2, in
  memory from enable, removed at the player's next join; bots are not recorded). No carrier may lie within one block
  of such a box ("Hier ist jemand ausgeloggt – stell die Maschine woanders hin."; `machines.refuse-logout-spots`,
  only switched off for tests). Safety net for every other way into a machine (ender pearls, teleports, old data):
  at join, one tick later and on suffocation damage (cancelled) a player whose box overlaps a carrier is moved to
  the nearest spot with two free cells on safe ground (the machine's front first, up to 4 blocks around and 3 up or
  down; else the highest block above), with a WARN log (`SafetyListener`, `MachineService.rescue`, `Rescue`).
- Cells with any fluid state (water, lava, waterlogged blocks, seagrass, kelp, bubble columns) are refused.
- Chunk fit (`MachineType.chunkFit`): a machine with a facing moves along its width first, then along its depth, by
  the smallest shift that fits (3-wide machines: always one block sideways; the 4 x 2 blackjack table: up to two
  blocks sideways or one forward/back; "stell sie 2 Blöcke weiter nach links", `machine.chunk-border-far`).
- Tables without the mod's support rule (roulette, blackjack) need at least one supported cell while their
  `needs-support` is on (default); the upright machines need every bottom cell supported.
- Breaking (owner/OP, survival): the item goes into the breaker's inventory, only if it is full at his feet (never
  at the anchor, which may hang over lava or the void); creative breaks drop nothing.
- `verify` skips carriers in unloaded chunks and checks the rest; `remove` never loads a chunk (carriers in unloaded
  chunks stay; with the one-chunk rule that only concerns old data).
- Idle lights of all machines share `lights.max-updates-per-tick` (default 4, `visual.LightBudget`): a machine that is
  due (its `idle-light-ticks` passed, a player within `idle-light-range`) queues once and is served in turn, so the
  packet volume stays bounded however many machines stand; with few machines every one keeps its cadence.
- Records: `ChunkRecords.put/remove` rewrite the raw container list and keep every other entry verbatim, also an
  unreadable one (CRITICAL `MACHINE_RECORD_UNREADABLE` when read; that machine stays inactive, nothing is lost).
- Rigs: all entities at the anchor's bottom centre, `setPersistent(false)` inside the spawn consumer, tagged
  `kaboofcasino:part`; `checkRig` respawns after `/kill`. `EntitiesLoadEvent` removes any tagged entity from disk.
- Item identity is only the PDC tag `kaboofcasino:item`. Casino items are paper: `ItemVetoListener` blocks them in
  non-casino recipes (grid and crafter), merchant purchases and the cartography table; casino recipes use
  `MaterialChoice(PAPER)` and check every paper's tag (`Recipes.papersValid`), so later item-component changes never
  break crafting. Recipes are unlocked along the mod's advancement chain on first possession (diamond → reel, reel →
  mechanism, mechanism → slot machine): `PlayerInventorySlotChangeEvent` plus an inventory scan at join.
- Bots: EgorsFakePlayer bots are recognised by their handle class `dev.egor.fakeplayer.Bot` (or a subclass) and
  remembered by UUID until they quit; never by name or team.

## 5. Display maths and animation (render.md 2-3)

`RigMath.part(facing, pivot, Q)`: the client draws an ItemDisplay (transform NONE) as
`entity + T + L·R180·(m/16 − 0.5)`. With `T = Rf·pivot` and `L = Rf·Q·R180` a model whose centre (8,8,8) is the
pivot lands exactly where the mod's `SlotMachineLayout.toBlock` puts it (`RigMathTest` checks all facings).
`Rf = rotationY(−turns·90°)` (the mod's renderer turn).

Floating panels of every machine (slot, wheel, Plinko, roulette, blackjack, poker, derby) are text displays with one
opacity for every viewer: the mod's look-angle dimming (`CasinoFloatingPanels.faded` with `DerbyPanelFocus.opacity`:
down to 25 % when the panel is more than ~5-28° off the camera's view) and its 7.5-10-block distance fade are not
built; the panels show fully within 10 blocks and are cut beyond.

Slot specifics:
- Body: the mod's three block models via generated item definitions `roulettecasino:block/slot_machine_*`.
- Reels: 72 generated **partial** drums (`kaboofcasino:slot/drum_<reel>_<blur>_<face>`: 22 facets of 7.5° covering
  ±82.5°, texture offset so face `f` is at the front). The display shows the model of the nearest face
  (`round(turn) mod 8`) turned about X by `(turn − round(turn)) × 45°` (≤ 22.5°), so the mod's window (±60°) is
  always covered and no facet reaches behind the cabinet's back plate (lowest corner z = 2.69 px ≥ 2 px; a full
  drum stuck ~6 px out of the back). A face or blur change swaps the item with interpolation 0; otherwise the client
  interpolates. Blur strip by speed (< 0.35, < 0.62, else). While a reel is fast every tick (1 tick ahead,
  interpolation 1), slower every 2 ticks (2 ahead, interpolation 3). `GeometryTest` checks the facet geometry
  (behind-plate, back-to-back machines, window coverage at ±22.5°).
- Lever: converted arm + ball meshes, pivot at the model centre; every tick while it moves.
- Lights: lamp mesh + 15 bulbs in one model, faces `tintindex 0..15`, `light_emission 15`; colours via
  `custom_model_data.colors` (lamp `gray(0.3+0.7l)`, bulbs `gray(0.28+0.72b)`). During a spin every 2 ticks, then the
  mod's 14-tick blend; idle chase every `slot.idle-light-ticks` only with a player within `slot.idle-light-range`.
- Drum flash (mod: tint 1.0/0.62): display brightness 15/9.
- Panel: one TextDisplay at +3.3 (view range 10 blocks, scale 0.44): head + name, EINSATZ, sprite × count; from the
  result on the three symbol glyphs and the result line; hidden 70 ticks after the result, independent of the coin
  shower.
- Timing: a win settles at `resultTick + 12`; a loss settles at the result tick and the machine unlocks there (the
  mod's `readyTick` for an empty payout). A win unlocks when the coin shower is done and `readyTick` passed.
- Sounds/particles: the mod's cue list and positions (`SlotGeometry`, `Facing.toBlock`).

## 6. GUI (PLAN decision 6, render.md 5)

- A 9×6 chest; its title is the art. `TitleBuilder` keeps a pen (window x, starts at 8) and emits space characters of
  the `kaboofcasino:gui` font (U+F801.. = −2^i, U+F821.. = +2^i) to reach absolute positions.
- Art glyphs: a glyph whose top-left should be at window (x, y) has `ascent = 13 − y` (title drawn at y 6, glyph top
  = `6 + 7 − ascent`), ≤ 256×256 px, advance = rightmost opaque column + 1. The pack tool writes all of this into
  `gui-layout.json`; the plugin never hard-codes chars or advances.
- Text: one font per window row that a GUI uses (`kaboofcasino:t_<y>`, `t_m8` for −8), collected at build time from
  the GUI anchors. They use a compact glyph sheet of copied vanilla glyphs (baseline row 11, `ascent = 17 − y`) in
  the smallest cell height that allows that ascent (16 px for y ≥ 1, 32 for y ≥ −15, 64 for y ≥ −47). Needed
  because a bitmap provider must have `ascent ≤ height`: plain vanilla fonts cannot draw above y 5, and the mod's
  headlines are above the window. `GuiLayout.width` uses the same advances as the client.
- Anchors: the pack tool writes every text/glyph position of a screen (`slot_headline`, `slot_payouts`, `slot_bet`,
  `slot_multiplier_<row>`, `slot_reel_<k>`; window x, cap-top y, alignment) into `gui-layout.json`; the plugin draws
  by anchor name (`TitleBuilder.text(anchor, …)`), so art and text cannot drift apart.
- The slot screen (`SlotMachineGui` in the pack tool, `SlotMenu.title` in the plugin): mod art drawn with its
  inventory under the vanilla inventory; re-composed for the common scaled screens (427×240 is the tightest: 125 px
  beside the chest, 9 px above/below):
  - 16 felt rows cut between the bet slot and the button;
  - the bet slot moved by (−1, −4) onto grid slot (4,3);
  - 19 plain columns cut from the left panel's right margin, the 8 px gap between the panels removed, and 15 plain
    columns cut from the payout panel between the symbols and the multipliers (JACKPOT re-centred);
  - result 324×266, ending 125 px right of the chest; at 427×240 only the outer frame lines at the top and bottom
    are cut. `GuiPreviewTest` asserts no content box (headline, payouts header, reel window, multipliers, JACKPOT,
    inventory) is clipped on any of the three screens. Reel faces
  of the last stops are title glyphs (the menu is never open during a spin); stake, bettable-items book and the
  DREHEN/ABHOLEN button are display items (button = `oversized_in_gui` model over slots 39-41).
- Selection model: bottom clicks select (left: that stack, right: +1, shift-left: all, capped), the stake tile undoes,
  DREHEN commits (commit button: LEFT/RIGHT only, click guard, busy flag). No real item ever sits in a slot.
- Safety: menus open only through `MenuService.open` (any other open of a casino holder is cancelled), only in the
  interaction tick, never for creative/spectator/dead/sleeping/fake players, never in danger. `MenuSafety`
  (PostMarkt copy, widened to any `Mob`) closes on damage and on any mob targeting the player (golems, angry wolves,
  bees, piglins included). `DangerSweep` every 5 ticks closes on:
  - air, fire, lava, frost, falling/gliding, void, water or a bubble column, Levitation, a vehicle (hook
    `casinoSeats` for future casino seats);
  - horizontal drift > 0.2 blocks per sweep after a 10-tick grace (water currents, ice, pushes);
  - monsters with line of sight (`safety.monster-radius`), any mob within 24 blocks whose target is the player;
  - primed TNT, a TNT minecart with a fuse, a creeper with a fuse > 0 (`safety.tnt-radius`), withers, the wither
    effect;
  - the menu no longer valid: machine gone, spinning, eye farther than interaction range + 4.5 from the machine's
    block box (as the mod's `isWithinBlockInteractionRange`);
  - idle: no spin for `gui.idle-close-seconds` (120, 0 = off) closes the slot menu and frees the machine (with the
    `machine.idle-closed` note; silently for the blackjack and poker bet menus, as the mod's `betIdle`).
  The commit checks safety again. A danger close never decides a round.

## 7. Pack tool (`pack-tool/`)

`PackTool.build`: `ModJar` (pinned SHA-1; assets plus the recipe and recipe-advancement data) → `PackTree` (sorted
path → bytes; different contents for one path fail) → `Credits` → `SlotMachinePack` / `WheelPack` / `PlinkoPack`
(models, items, entity sheets into the blocks atlas, meshes, lights, drums/plates) → `SlotMachineGui` / `WheelGui` /
`PlinkoGui` (tiles, glyphs, anchors, buttons; the 3x3 screens through `MachineGuiArt`) → the 72x14 collect button
and the stake marker → `Sounds` + `Lang` (once, for all shipped games) → `TextGlyphs` (text fonts) → `GuiFont` (gui
font + manifest) → `TexturePadding` (atlas textures to multiples of 16, UVs rescaled; animated or item/generated odd textures
fail) → `GuidePictures` (the guide's recipe pictures: font and art, read from the final pack) → `ZipOut` (sorted,
fixed DOS time, PNG/OGG stored, rest deflated 9). Byte-identical output for identical
inputs (`PackBuildTest`). Next to the zip it writes `gui-layout.json` (glyphs, anchors, text rows),
`recipes.json` (`ModRecipes`: the shipped recipes and their unlock items from the mod's JSON), `guide.json`
(`GuideData`) and `guide-pictures.json` (`GuidePictures`: the lines of every recipe picture).

`pack-src/`: `meshes/slot_*.json`, `wheel_pennant.json`, `plinko_{ball,lamp,bulb,rim,glow}.json` (converted by
`tools/meshes/convert-{slot,wheel,plinko}.sh` with the research's `mesh2json.py`, which re-bakes every quad with a
port of the 26.3 FaceBakery: 0 mismatches), `vanilla/` (default-font bitmaps and
provider definitions from the 26.3 client jar, see its README).

## 8. Extension points for the next games

1. `MachineType`: new constant with its footprint (offsets rotate with the facing) and `rise` (placed cell to
   anchor).
2. `CasinoItem`: the placing item (+ intermediates), `Msg` names; add the recipe ids to `ModRecipes.SHIPPED` (the
   recipe, its unlock and the paper rule come from the mod's JSON).
3. A `MachineController` subclass (one per machine: `running/busyMessage/newMenu` plus
   `spawn/despawn/tick/removed/ownsRound/checkRig`); its menu extends `GameMenu` (or `StakeMenu` for three bet
   slots); register it in the `machines.games(...)` switch in `KaboofCasinoPlugin`.
4. Rounds go through `WagerEngine.commitAll` via `MachineController.commit`. Instant games: decide → commit →
   animate → `settleRound(NORMAL)` per record at the mod's payout tick, exactly like the wheel. Card games
   (blackjack, poker): `RoundState.PLAYING` records whose payout is the timeout rule's result (stand/fold)
   computed on a copy of the shoe before every decision, rewritten with `WagerEngine.revise` (a mid-round stake goes
   into the same call); see `BlackjackController`/`BlackjackStand` and `PokerController`/`PokerCall`. The derby
   (step 7) uses PLAYING (refund) during its group countdown and DECIDED from the start. Former note for the derby: `ENROLLED` (refund on quit/death) → `DECIDED` at the race start (a
   new state means a new ledger version written only while such a record is open, see `LedgerCodec.version`).
5. Pack: a `<Game>Pack` + `<Game>Gui` class in the pack tool; GUI art glyphs go through `GuiFont.art/grid` with their
   window position, text positions are anchors (`GuiFont.anchor`, drawn with `TitleBuilder.text(anchor, …)`). Check
   `slot-preview-<W>x<H>.png`-style previews at 480×270, 456×256 and 427×240 (copy
   `GuiPreviewTest`). Add the game's sound events and lang prefixes to the lists `PackTool` passes to `Sounds`/`Lang`.
   Every literal model key and every `sound(world, "...")` is checked against the pack (`PackKeysTest`); keys built at
   run time need a `modelKeys()` test like `WheelPackKeysTest`.
6. Exchange rules, `win_chance` per machine, the croupier's hand, names: not built; `slot.win-chance`,
   `wheel.win-chance`, `plinko.win-chance` are global per type. `Odds` already ports the mod's pools for all games.

## 9. Pitfalls found in step 1 (in addition to PostMarkt's section 7)

| Pitfall | Handling |
|---|---|
| Bitmap glyphs need `ascent <= height`; the vanilla fonts cannot draw above window y 5. | Tall-cell text sheet (`TextGlyphs`). |
| The chest is centred on 222 px: art reaching 36 px above it is clipped at GUI scale 4 on 1080p (270 px). | Re-composition cuts 12 felt rows; `GuiPreviewTest` asserts the art is on screen. |
| Advance = rightmost non-transparent column + 1, not the cell width. | Computed in the pack tool exactly like `BitmapProvider`. |
| Font glyphs > 256 px are dropped by the client. | Tiles of 179×133. |
| `ItemStack.of` in a static initializer needs a running server (unit tests die). | Create stacks at use time. |
| `Material/BlockType#isInteractable` are deprecated. | Own list in `MachineListener.interactable`. |
| `sendBlockDamage(loc, 0f, id)` clears (stage −1); 0 < p < 0.111 shows stage 0. | `BreakListener.stop`. |
| Clients send `ABORT_DESTROY_BLOCK` when they stop; the test bot's `dig` sends `FINISH`, which fires no event. | Timer also stops on distance/quit; with the bot, `dig … 6000` still breaks after 4.5 s. |
| `ZipEntry.setTime` converts with the local time zone. | `setTimeLocal` (deterministic). |
| The test instance's world is Peaceful: `/summon zombie` fails silently in scripts. | Set `difficulty easy` for danger tests, back to peaceful afterwards. |
| JOML class files trigger `[classfile]` warnings with `-Xlint:all`. | `-Xlint:-classfile`. |
| DeathBan's grave holds 54 stacks; the rest of `getDrops()` falls on the ground. | `DeathDrops.plan`: only what fits goes into the drops, the rest stays owed. |
| A full reel drum reaches ~6 px behind the cabinet's back plate (visible from behind, intersects a back-to-back machine). | Face-indexed partial drums, turned at most ±22.5°. |
| Most players use GUI scale "Auto": 427×240 scaled pixels on 720p, 1440p and 4K. | The slot art is re-composed to 324 px; previews at three sizes. |
| `ChannelInitializeListenerHolder` is only in the server jar; vanilla's `ReadTimeoutHandler` ("timeout") cuts a long HTTP download at 30 s. | Compile against an extract of the server jar; remove "timeout" when answering HTTP. |
| `EmbeddedChannel` runs scheduled tasks only with `advanceTimeBy` + `runScheduledPendingTasks`. | Used in `PackSnifferTest`. |
| Step 2: the mod's 3x3 screens need 36 px above the chest for the title; the bet slots and the button leave no felt to cut between them. | Upper part 20 px higher, felt rows below the button cut, a 72x14 button (`MachineGuiArt`). |
| Step 2: row cuts across the whole image tear the payout table apart. | Rows are cut in the left panel only; the payout panel keeps window = mod - 20. |
| Step 2: `Images.argb` returns its argument when it already is ARGB, so a second "copy" aliases the first. | Copies via `Images.crop`. |
| Step 2: a display is culled by its own box, which grows only upward from the entity; parts one block below vanish when looking down. | 3x3 rigs stand at the machine's bottom centre with a 3.2 x 3.2 box. |
| The wheel pennant at the mod's 36 degree clamp pokes 0.154 px above the machine, exactly like the mod. | Kept; `WheelGeometryTest` allows it. |
| games.md lists the roulette's gold confetti as 0xFFD268; the mod has 0xFFD168 (`RouletteBlockEntity.java:36-38`). | 0xFFD168. |
| A machine placed where a player logged out buries him (only online players are visible to the placement checks). | Logout spots and the rescue net. |

- **One clock per game** (step 5 review): Blackjack runs on the world's game time (`World#getGameTime`, the mod's
  renderer clock) for the hand's view, the rig (idle phase, cadence, light refresh, coins) and the round menu's card
  delays; the server tick (`Bukkit.getCurrentTick`, the uptime) only feeds the shared `LightBudget`, whose queue
  counts server ticks for every machine. Mixing them froze the idle dealer on old worlds (live game time 33 M,
  uptime hours: "now - last" stayed negative). The other games use the game time only as the idle-light pattern's
  clock and the server tick for their spins and cadences, never comparing the two. Test worlds: the overworld's game
  time lives in `dimensions/minecraft/overworld/data/paper/level_overrides.dat` (`data.game_time`), not only in
  `level.dat`; the test instance now runs at 50 M.

## 10. Deviations from the mod (step 1)

Bets and payouts:
- Bets are a selection (click items in the own inventory) instead of dragging a stack into the bet slot; nothing
  stays in the machine when the menu closes (the mod keeps stakes in the block entity).
- Only **plain** stacks are bettable (no custom name, enchantments, lore or other changed components): renamed or
  enchanted copies of a bet item are refused and stay in the inventory. Payouts are always plain items.
- Stake cap 64 per round (PLAN decision 1); the mod has none.
- Winnings go into the inventory (overflow: owed list) at `resultTick + 12`; the mod ejects owner-locked item
  entities one stack every 1-3 ticks. The ejection pace, sound and the flying stacks are kept as ItemDisplays.
- Quit/stop mid-spin settles at once, crash → next join, death → grave as far as it fits, rest owed (the mod
  finishes the spin in the world and ejects items, which despawn if the owner is away).
- Own RNG (L64X128MixRandom) instead of the level random; same distributions (RTP 499/512 exact, simulation 10^7).

Machine:
- One player per machine is enforced at menu open ("<name> spielt gerade …"); the mod lets others open and blocks
  their clicks.
- Break while spinning is refused (the mod pays the drawn result at once).
- Placement refuses water, lava and waterlogged positions (barrier carriers would hold or hide the fluid; the mod's
  slot block is not waterloggable and would replace the water).
- No exchange rules, no per-machine `win_chance`, no croupier's hand, no machine labels (creative + hand only in the
  mod), no "breakable" world flag (owner/op rule instead), no light emission of the machine blocks (barriers emit none).
- The machine's collision is the 1×3 barrier column (the mod's model is 1.5 blocks wide).

Look:
- Reel shading (the mod's per-vertex `0.42 + 0.58·cos³·²`) is replaced by item lighting on the partial drums; the
  drum flash uses display brightness 15/9. The drum arc is ±82.5° turned by up to ±22.5° (the mod draws ±60° with a
  scrolling texture); the visible window is the same.
- Idle lights are server-driven tints (every 4 ticks near players); all machines use the mod's per-position clock.
- GUI: 16 felt rows removed, the bet slot 4 px higher, the left panel 19 px and the payout panel 15 px narrower, the
  panels joined; the mod's hover lines on paytable rows are missing, the "Bettable items" list is a book item tooltip,
  the SPIN button has no hover state, labels are German literals from de_de.json, the vanilla "Inventar" label is
  drawn by the client in its own colour (#404040; since the step 9 fix round it sits on a light cream plate in every
  felt title art, `InventoryLabelPlate`, window x 5..59, y 126..137).
- The slot menu closes itself after 120 s without a spin (`gui.idle-close-seconds`) and in more danger situations
  than the mod knows (any targeting mob, vehicles, levitation, drift, water); the mod's screen stays open.

Same as the mod (fixed in this round): losing spins unlock at the result tick, the panel hides 70 ticks after the
result, recipes unlock along the mod's advancement chain, the range check uses the block's box.

### Step 2 (fortune wheel, Plinko)

Bets and payouts:
- Three bet slots as a selection (the slot machine's model, per slot; see `StakeMenu`); nothing stays in the machine
  when the menu closes (the mod keeps stakes in the block entity, owner-locked, and ejects them when the machine
  breaks).
- Each slot holds up to `bets.max-stake` (64) like the mod's stack per slot (3 x 64 per round); `bets.cap-per:
  round` restores PLAN decision 1's cap for the whole round.
- Each slot is one record; a paying slot settles where the mod starts ejecting (wheel: result + 12; Plinko: that
  ball's landing + 10), a slot that pays nothing at the result (wheel) or at its landing (Plinko). Winnings go into the
  inventory (overflow owed) instead of owner-locked item entities; the ejection pace (1/2/3 ticks by stacks pending,
  per ball in Plinko) and the payout sound are kept as coin displays.
- Fair rounding, the slot index in the roll (also for Plinko balls), the weighted wheel table, the binomial Plinko
  paths and the forced pools are the mod's (verbatim ports; RTP 250/256 and 998/1024 exact and simulated).
- The machine unlocks like the mod: when everything is out, the result is shown (Plinko) and `readyTick` passed.

Look:
- Plate blur cross-fade: the mod's continuous alpha becomes baked blends at weights 0.25 / 0.5 / 0.75 per transition
  (quarter steps of `FortuneWheelBlur`).
- Wheel flash: brightness 9/15 instead of the grey tint 0.62.
- Plinko pocket glow: four baked alpha steps instead of the continuous alpha (0..97); the rims appear per pocket
  above 0.01 as in the mod.
- The pennant uses the mod's 36 degree limit; there its corner reaches 0.154 px above the machine, like the mod.
- Plinko's lamps, bulbs, rims and the wheel's bulbs are self-lit full bright like the mod; the faces of the converted
  meshes keep their per-face shading (the mod forces an up normal).
- Machine labels (creative + Croupier's Hand only in the mod) are not built; the floating panel is a text display:
  head + name, title, the stake icons, Plinko's landed multipliers (empty until a ball lands) and the result line,
  without the felt and brass frame, the 3D item icons and the per-viewer fades.
- GUI: upper part 20 px higher, felt rows under the button removed, the button 72x14 instead of 72x18; the left panel
  19 px and the payout panels 15 px narrower (wheel: columns 304..318; Plinko: 261..266 and 312..320) with the
  lettering re-centred; the wheel's bet frames moved by -5/0/+5 px, Plinko's by (+2, +7/+3/-1) px onto the grid,
  Plinko's bullet dots 6 px and "1 Kugel" labels 8 px left (the mod shrinks the label into 31 px). The mod's text
  scaling (`CasinoText.fitted`) does not exist in a font, so long labels keep their size.
- The active empty bet field shows a gold frame; a filled active field glints.

### Step 3 (roulette)

- Bets are a selection over nine slots (three per colour; the mod's columns RED, BLACK, GREEN), one colour per spin as
  in the mod; nothing stays on the table when the menu closes (the mod keeps the stake in the table under its owner).
  Choosing a slot of another colour while one is staked is refused with "Nur eine Farbe behalten". The other
  colours are veiled like the mod (chip veil, muted label, the 0xB4313B36 column fill) by one translucent
  `oversized_in_gui` item per column, because a chest title cannot change while it is open; the fill ends with the
  last bet frame (the mod's reaches 4 px further, where our button now sits).
- One player per table at a time (the menu claim); the mod lets others open the menu and watch, but only the stake
  owner can act, so one player's bet per spin either way.
- All records settle at the result (tick 160); the mod ejects the payout stacks every third game tick from the next
  tick on, aimed at the owner. Here the items go into the inventory (overflow owed) at the result and the coin
  displays keep the pace and the payout sound. The table unlocks when the last coin is out (the tick after the
  result if nothing is paid), the panel hides 20 ticks after the result, as in the mod.
- Rotor and ball: 2-tick rotor updates (interpolated, at most 27 degrees per update), 1-tick ball updates (its own
  spin reaches ~80 degrees per tick); both use the light at the display's position (the mod: the table's light).
- The floating panel is a text display: head + name, the chosen colour's header (a gui-font bar in the mod's header
  colour 0x9B2631 / 0x21252B / 0x1B9656 with the cream label on it) and the stakes; the mod draws one felt column
  per colour with the item icons and per-viewer fades.
- Placement (permadeath deviation): the mod's table needs no support below; here at least one of the four cells
  needs a sturdy block below (`roulette.needs-support: true`, default; `false` restores the mod), so no table hangs
  over lava or the void. Like every machine it must fit into one chunk (the message says where to move it:
  links/rechts/vorne/hinten from the player's view), keep 2 blocks from other players and from logout spots.
- The rig stands at the table centre so the displays' culling boxes cover the whole table; rotor and ball are
  visible to 64 blocks like the mod's renderer.
- "Das Roulette dreht sich." when someone clicks a spinning table is our addition (the mod just opens no menu).
- The colour of a pocket is the mod's parity rule (0 green, odd red, even black), and the converted rotor shows
  exactly that; the ball comes to rest over pocket `3 x frame` (`RouletteGeometryTest`).

### Step 4 (seats, deco, music)

- **Full-cube deco** (27 blocks: 9 fabric floors, 5 stone floors, 11 dark-oak panels, 2 pilasters) are reserved
  vanilla mushroom-block states (render.md 4, PLAN decision 7; `block-updates.disable-mushroom-block-updates: true`
  in `paper-global.yml` is required). Of the 96 `down=true` states 11 come from world generation (the swamp
  abandoned camp template `campsite_swamp_2`) and 3 are the all-true defaults, so 82 are usable; 80 are used
  (`FullDecoStates`, `tools/census/chosen-states.txt`: the first 72 lines are pinned by a SHA-256 in the test; never
  reorder, new blocks take spare states; spare: brown masks 29 and 30; the rest of the mod's content needs no full
  cubes). The census of the live world (2026-11-07 02:01 UTC after the radius-5000 pre-generation: 581 401 + 44 857 +
  144 832 chunks; the 8 pilaster corner states again at 03:15 UTC) found 0 blocks in any chosen state and no
  `down=true` state at all; rerun `tools/census/mushroom_census.py` before deploying.
- **The switch is read live** (`PaperSwitch`: Paper's `GlobalConfiguration.blockUpdates.disableMushroomBlockUpdates`
  by reflection, the `rotate` behaviour as a fallback). While it is off (at enable, on `/casino reload` or at run
  time) a CRITICAL is logged, placing deco is refused and every conversion stops: reserved states drop vanilla loot,
  pick-block is vanilla, no pilaster/star rewrites. `/casino` and `/casino reload` show admins the status line.
- **Digging like the mod**: the deco blocks have the mod's materials (wool floors 0.8 with shears fastest, panels as
  planks 2.0 with the axe, stone floors 1.5 that drop only with a pickaxe: `POLISHED_ANDESITE.isPreferredTool`, as the
  mod's `requiresCorrectToolForDrops`; explosions and destroy drops unchanged). Vanilla digging itself takes the mod's
  time through one transient `block_break_speed` multiplier (`kaboofcasino:full_deco_dig`, `DecoDigging`), set while a
  survival player in reach looks at a reserved state (a ray per tick) and when the left click starts, so the server
  never insta-mines and the client's cracks, particles and sounds are its own. It is never saved
  (`addTransientModifier`) and is removed by key on join, quit, death, respawn, world change, game-mode change, abort,
  break, switch off and plugin disable; a held-slot change or hand swap recomputes it in the same tick. Expected times
  (ticks): wool floor 24 by hand / 5 with shears, panel 61 by hand / 10 with an iron axe / 8 with Haste II, stone
  floor 8 with an iron pickaxe (151 by hand, no drop). A real client may see one restore of the block when it clicks
  before the attribute packet arrived.
- Other full-deco deviations: wood sound for every block, not flammable, explodes easily (blast resistance 0.2); the
  pilasters show 27 of their 256 connection states (no band, both or one side per axis, and the four baseboard outer
  corners; T-junctions exact; other mixed bands show the nearest); star facing does not turn with structure rotation;
  pilasters update one tick later and only on events the plugin sees. Natural and player-placed vanilla mushroom
  blocks keep their states with the switch on (tested).
- **Display deco** (41 types incl. chair, stool, sofa and the speaker): one record per block in the chunk PDC,
  displays rebuilt at chunk load, a round-robin verifier. Carriers: barrier (full collision, broken with the machine
  break timer; the item goes into the breaker's inventory), light blocks with the mod's light level plus an interaction
  box (one left click breaks), or vanilla stairs/slabs placed by vanilla from the deco item (crimson/warped under
  velvet, polished andesite/diorite/blackstone under checkers and marble; the display sits over them at 1.032, scaled
  about a pivot a quarter block inside the carrier's solid part so every face lies 0.008..0.024 outside the carrier's;
  hotfix 0.9.1, see below).
  Deviations: carriers are full cubes or nothing. A rope post's carrier is a barrier one block high, so a line of
  rope posts is a 1-block wall that players can jump onto and walk across; the mod's post and the rope between two
  posts collide up to 24 px (1.5 blocks), which nobody can jump, and raising it would need a second solid block above
  the post, which the mod does not occupy. Sign plates have no collision. Barrier and light deco ignore explosions and
  fire, pistons never move or pop deco (refused), light-carrier deco (signs, chandelier, sconce, VIP plaque, speaker)
  breaks at the first hit, stairs/slabs feel like their carrier, a large sign or chandelier must fit in one chunk.
- A deco slab never merges with a plain slab of its carrier: either order is refused, as the mod's slab only merges
  with itself. The record stores how many deco slabs the cell holds (1, or 2 after the same deco slab was added);
  breaking and explosions drop that, never more than the carrier holds. Records from 0.5.0 read the carrier once and
  then store it.
- A decoration that lost one carrier (no event: `/fill`, `/setblock`, a structure, an exploit) is removed without a
  drop. Its other cells that still hold its carrier (the other light blocks of a large sign or chandelier) become air;
  the block that replaced the missing one stays.
- Candle flames run over an index of the loaded chandeliers and sconces only; each world's players are read once per
  tick and filtered per block to 32 blocks per axis (the client's `animateTick` range).
- The bottle and glass display shelves are solid barrier carriers on the wall (the mod's 16x16x6 and 16x9.6x6 px
  cabinets), dug with the barrier timer at the mod's hardness 0.8 (copied from the chest, but `strength(0.8F)` in
  `CasinoBarAndDecor.small`): axe fastest, no tool needed for the drop. A shelf placed by 0.5.0 on a light block gets
  its barrier at its first check after load, unless a living entity or a logout spot is in the way; until then it
  cannot be broken.
- Digging a deco barrier ignores `block_break_speed` modifiers in the `kaboofcasino` namespace (the full-cube deco's
  transient modifier); every other modifier counts as in vanilla.
- **Seats**: right click (not sneaking, not riding, not in danger or combat: "Nicht jetzt – du bist in Gefahr."),
  one rider per seat, the mod's seat heights and yaw, cushion sounds; every dismount is steered to the mod's 12 spots
  with a vanilla-style safety check, then `Rescue.nearest`, then the top of the seat. Stand-up on damage, a targeting
  mob, any danger the sweep finds ("Gefahr – du stehst auf."), seat removal, quit/kick (before the save: the file
  holds the safe spot and no vehicle), death, teleport, plugin disable.
- **Speaker and music**: the speaker toggles with the lever click (light 2 on), and calls the music service. The
  lounge music (Kevin MacLeod, CC BY 4.0, credits in `/casino credits` and in the music pack) plays to players who
  loaded the optional music pack: the current track of the shared timeline from its start when entering 48 blocks
  (a vanilla client cannot seek), at the mod's volume curve fixed at the start, the next track of the order after it,
  stopped on leaving, the speaker going off, world change or death; vanilla music stopped (category-only `stopSound(MUSIC)`) at every 10-tick check in range (the mod stops it every
  tick and cancels the music manager). Note
  particles like the mod (the music's, off with `music.enabled: false`).

**Music pack delivery:**

The optional second pack `KaboofCasino-music.zip` (the mod's six lounge tracks, Kevin MacLeod, CC BY 4.0, ~22 MB;
`MusicPack` in the pack tool, built by `buildPack` next to the casino pack and embedded as `pack/KaboofCasino-music.zip`
+ `.sha1`) has its own fixed id (`kaboofcasino-music`) and its own SHA-1; the casino pack contains no music. Both are
served by the same delivery (game port or own HTTP server) under `/pack/<sha1>.zip`. `PackListener` sends it with
`required(false)` only after the casino pack is SUCCESSFULLY_LOADED (never together: the client merges open prompts
into one dialog and a failed download discards every pack that is not active yet). Decline, failure or no answer
never disconnect, also with `pack.enforce: true`; without an answer within `pack.timeout-seconds` the pack is popped
again so the client cannot reload its resources later in the world. `PackSessions.hasMusic` gates every music call.
`config.yml` `music.enabled` switches pack offer, music and note particles off. A vanilla client that accepted the
casino pack's prompt has its server entry on "Enabled", so the music pack is then accepted silently: in practice
players get it whenever they accept the casino pack (a second resource reload during the configuration phase).

- **Crafting**: every casinodeco recipe, the chair, stool and speaker recipes come from the mod's JSON
  (`ModRecipes`), shaped and shapeless, unlocked by the advancement's item (also lists of items). Many casino items
  share a material (all mushroom-stem deco, paper), so Bukkit's material match may pick the wrong one of several
  same-pattern recipes; the crafting listener sets the result of the recipe the grid holds slot by slot
  (`Recipes.craft`, `RecipeMatch`) and empties it for anything else (tested: six ruby velvet give four ruby velvet
  stairs, six plain stems give nothing).

### Step 5 (blackjack)

Rules, timing, texts and sounds are the mod's: the pure classes are verbatim ports (`BlackjackRound`, `BlackjackShoe`,
`BlackjackCards`, `BlackjackBias`, `BlackjackLayout`, `BlackjackCardMotion`, `BlackjackCardGeometry`,
`BlackjackTableState`, `BlackjackFootprint`, `DealerKinematics`, `DealerIdleAnimation`, `DealerPlayAnimation`,
`DealerPlayPoses`): 6 decks, reshuffle at the deal below 60 cards, peek with A/10, S17, double on any two cards (one
card), no split/insurance/surrender, 3:2, natural vs natural push, player 21 beats dealer 21, 600-tick turn timer
that stands ("Die Zeit ist um: Du bleibst stehen."), the mod's tick plan, sound plan and confetti. Differences:

- **RTP** (player following the mod's own chart, `BlackjackBias.bestPlay`; `tools/rtp/BlackjackRtp.java`):
  exact first round of a fresh 6-deck shoe 0.995618 per item staked (0.995196 per base stake, EV -0.480 %);
  infinite deck 0.994067 per base stake (games.md's -0.593 %); Monte Carlo of the mod's exact shoe with the 60-card
  cut over 1.8e10 rounds **0.995453 ± 0.0000076 per item staked** (0.995014 ± 0.0000083 per base stake), win rate
  0.4362. The mod's table says RETURN 0.995, WON 0.436 (bias 0). `BlackjackRtpSimulationTest` plays 10^7 rounds
  through `BlackjackRound`/`BlackjackShoe`/`FairPayout` (0.99541 ± 0.00032 per item, outcome counts identical to the
  independent tool's replay, 34 s). Odd stakes change nothing in expectation (a blackjack pays exactly 2.5 n on
  average). The plugin keeps one shoe per loaded table like the mod (not saved), so the real value lies between the
  long-run 0.99545 and the fresh-shoe 0.99562.
- One player per table, as in the mod: someone else clicking a running table or a table whose owner has the bet menu
  open gets "<name> spielt an diesem Tisch."; a stale claim is taken over.
- Bets are a selection over the three bet slots (nothing real lies in the menu); at DEAL every filled slot becomes
  one PLAYING record (see section 3). Each stake is paid in its own item; an odd blackjack stake pays 2.5 n as floor or
  ceil with the fair roll of its slot, derived from the round's seed (the mod: `level.getRandom().nextInt(2)`).
- Hands that end without their owner (quit, kick, stop, crash, death, chunk unload) are settled as **stand**
  (PLAN decision 2) with the cards the shoe would deal: the records always hold that payout, and the live hand
  stands at once when the owner is gone, so the table shows what was paid. The mod lets the round run on to its
  600-tick timeout and, after a restart or chunk unload, refunds an undecided wager. A table removed while loaded
  (admin) refunds an undecided hand, as in the mod.
- The shoe is the mod's (`new Random(seed)` Fisher-Yates, cards from the end) seeded from the casino generator; its
  random is `ShoeRandom`, java.util.Random's exact LCG with a copyable state, because the stand payout is predicted on
  a copy of the shoe (`BlackjackShoe.copy()`, the one plugin addition to a verbatim class; `BlackjackStandTest`
  checks prediction = table for 60 000 random hands with random decisions, with and without bias).
- The double down takes the same stakes again from the storage slots (plain items, per item kind, as the mod) and
  ignores `bets.max-stake` and the owed list like the mod; not enough items: "Du hast nicht genug Gegenstände zum
  Verdoppeln.", nothing changes.
- A decision in danger is refused and closes the menu (the hand runs on and times out to stand); the mod has no
  danger rule.
- The bias ("which side the cards favour", the Croupier's Hand setting) is `blackjack.bias` (default 0); exchange
  rules and per-table settings are not built.
- The camera turn toward the table (the mod's client turns it over 0.6 s): possible via rotation teleports, omitted
  (through the tunnel they risk jitter for little gain).
- Keyboard: 1 = KARTE, 2 = HALTEN, 3 = DOPPELN over any slot of the round menu (see step 6); the bet menu closes
  silently after 120 s without a deal, like the mod's `betIdle`.
- Look (`BlackjackRig`, `BlackjackDealerRig`, `BlackjackCardRig`; pack `BlackjackPack`, `BlackjackDealerModel`,
  `BlackjackCardModels`): the table is the mod's eight block models `blackjack_0..7` as item displays at the cell
  centres (view range 2.0); the dealer is eight displays (torso, head, upper arms, forearms, legs) generated from
  `BlackjackDealerModel.createLayer` with the 26.3 box-UV rules and the five texture layers composited in draw order
  (cutout rule; the per-layer 0.18 px inflation is lost), posed on the server with the verbatim
  `DealerIdleAnimation`/`DealerPlayAnimation`/`DealerKinematics` (idle phase from the anchor, all gestures and the
  turn toward the player's spot); one display per card from `BlackjackCardMotion.pose` (53 two-sided models from
  `blackjack_cards.png`, padded 350 → 352 px) plus up to seven shoe cards while shuffling. Cadence: moving cards every
  tick (interpolation 1), dealer gestures every tick while a player is within 64 blocks, the idle loop every 8 ticks
  (every 4 during its quick moves: shoulder sweep, elbow tweak, finger tap; interpolation n + 1) only within 32
  blocks and only when the shared idle budget `lights.max-updates-per-tick` (with the other machines' idle lights)
  grants it; unchanged transformations are not resent. Idle traffic per table and tracking player: 30.6 → 22.4
  metadata packets/s (`BlackjackIdleCadenceTest`, game time 1 M; 30.0 → 22.1 at the live world's 33.4 M). Displays
  are tracked to 128 blocks (`spigot.yml` `display: 128`) whatever their view range, so idle sends cannot be limited
  to near players without per-player hiding (not done: it would also hide the dealer between 32 and 64 blocks, where
  the mod still draws him). A hand stays at the mod's per-tick motion (gestures 8 parts, moving cards). Idle 17 entities, one more per
  visible card. All displays stand at the anchor's bottom centre with a 5.2 x 2.25 culling box covering table,
  dealer and every card pose in all facings (`BlackjackGeometryTest`). The dealer's light is the renderer's
  `dealerLight` (the anchor and the two cells behind the table), refreshed every 40 ticks; item lighting instead of
  entity lighting.
- Spectator panel: a text display above the table (view range 10 blocks, hard cut instead of the 7.5-10 fade) with
  head + name, the stakes as sprites, "DU n" / "CROUPIER n" (the 22-tick appear delay and flip hiding of
  `extractPanel`) and the result in the mod's colours, using the mod's translation keys (the pack's language file);
  no felt frame or 3D item icons.
- GUIs (`BlackjackBetMenu`, `BlackjackRoundMenu`; pack `BlackjackGui*`): the bet view is the mod's 366 x 266 art
  recomposed like the other games (three bet frames on grid slots 29/31/33, AUSTEILEN 72 x 14 over 48-50 instead of
  72 x 18, bets book 45, the payout panel minus 15 plain columns, labels bold with shadow via a style
  `shadow_color`). The mod never draws `place_bet` or `header_*`, so neither do we. The round view is a second chest
  (new title, opened in the DEAL click) carrying the mod's 160 x 196 panel rebuilt 288 px wide: dealer cards in slots
  0-8, player cards 18-26 (oversized card items; 27 px step up to five cards, 18 px up to nine), totals 17/35,
  stakes 27-32 (doubled: the doubled count and "x2" like the mod; above 99, which a slot cannot show, the base count
  next to the "x2"), timer bar 39, status/result
  40, KARTE / HALTEN / DOPPELN 52 x 17 over 45-47 / 48-50 / 51-53 (DOPPELN greyed when the hand cannot double or the
  items are missing). The mod's timing: cards appear 22 ticks after the deal, the hole card's face 8 ticks after the
  reveal, buttons locked 6 ticks after a press. It is a chest in the middle of the screen, not a side panel, and the
  inventory stays visible (inert); of the mod's keys only the number keys reach the server from a chest, so the
  tooltips' key hint says "(Taste 1/2/3)" instead of `*_tip`'s "(H, T oder 1)" etc. The controller refreshes the
  open round menu every tick (only changed slots are sent) and closes it when the hand is over; a right-click by
  the owner reopens it during the hand.
- Recipe: the mod's (`WWW/GBG/PRP` with the Croupier's Badge), unlocked by holding a badge. Badges come from hiring a
  croupier (see step 8: the croupier's hiring card used on him gives the badge); `/casino give blackjack_table` and
  `/casino give croupier_badge` work too.
- Placement (permadeath deviation, like the roulette): `blackjack.needs-support: true` (default) wants at least one of
  the eight cells on a sturdy block; the mod's `BlackjackTableBlock.canSurvive` is always true (`false` restores
  that). One supported cell is enough, so a table can still reach over a hole, as the roulette.

### Step 6 (poker)

The mod's poker is **Casino Hold'em against the house**: one player per table plays against the drawn dealer (no NPC
entity, no other players in the hand, no pot between players, no side pots, no rake). The three seats are only
positions: the hand is dealt to the seat nearest the player (`PokerLayout.nearestSeat`); someone else clicking a
running table, or a table whose owner has the bet menu open, gets "<name> spielt an diesem Tisch.". Rules, timing,
texts and sounds are the mod's: the pure classes are verbatim ports (`PokerRound`, `PokerHands`, `PokerBias`,
`PokerLayout`, `PokerTableState`, `PokerTableGeometry`; two redundant array casts of the decompiler removed for
-Xlint). One deck, reshuffled before every hand (`needsShuffle` is < 60 cards); deal player, dealer (face down),
player, dealer, flop; the decision opens at t = 209 with a 600-tick timer that folds ("Die Zeit ist um: Du steigst
aus."). CALL takes 2 x the ante of every bet slot from the storage slots (plain items per item kind, like
`takeCallStake`; not enough: "Du hast nicht genug Gegenstände zum Mitgehen (zweimal den Einsatz).", nothing changes),
deals turn and river, reveals the dealer's cards (index 1, then 0 after 22 ticks) and resolves 46 ticks later. The
dealer qualifies with a pair of 4s; not qualified: the ante pays the paytable and the call pushes; otherwise win
(ante paytable + call 1:1), push (refund) or loss. Ante paytable: royal flush 100:1, straight flush 20:1, four of a
kind 10:1, full house 3:1, flush 2:1, anything else 1:1. A fold loses the ante; the dealer's cards stay hidden.
Payouts per stake: `thirds` of the called stake (3 x the ante) in thirds of an item, so they always come out whole;
the roll of `CasinoExchange.payout` comes from the round's seed and the bet slot. Differences:

- **RTP** (`tools/rtp/PokerRtp.java`; every one of the 27 813 810 024 000 deals counted exactly, grouped by the five
  board cards, 268 s): with the mod's own strategy (`PokerBias.simpleCall`) **0.984468006 per item staked** (ante +
  calls; net -4.2727 % of the ante), calls 87.5 %, player wins (win + no-qualify) 0.491145; the optimal strategy (call
  iff the call EV >= -1 ante) 0.991827 per item, house edge 2.1576 % of the ante (the published 2.16 %); always
  calling 0.973668. Monte Carlo of the exact game path (java.util.Random LCG, the mod's Fisher-Yates, 8e9 hands)
  0.98447715 +- 0.0000109. The mod's table says RETURN 98.2 %, WON 49.1 % (bias 0; its 40 000-hand measurement has
  an SE of ~0.5 points). `PokerRtpSimulationTest` plays 10^7 hands through `PokerRound`/`BlackjackShoe`/`FairPayout`
  (0.98445 +- 0.00031, outcome counts identical to the tool's replay, ~97 s) plus rigged shoes for every result
  and paytable line. The payout thirds always come out whole (stake 3 x ante after the call), so the roll and odd
  antes never matter.

- **Records**: at DEAL every filled bet slot becomes one PLAYING record holding the fold (payout nothing) - the mod's
  timeout rule, PLAN decision 2. At CALL the outcome after turn and river is computed on a copy of the shoe with the
  round's own draw and bias (`PokerCall`, prediction = table checked by `PokerCallTest` for 40 000 hands at random
  decision times, with and without bias); the records get 3 x the ante as stake, the call stake is taken and the
  outcome becomes the payout in **one** ledger write (`WagerEngine.revise`, all slots of the hand together); only
  then the live round deals. So stop, crash, death and the watchdog settle a fold before the call and the final
  outcome after it, without game logic; a dead owner's hand folds at once, so the table shows what was paid. When the
  decision opens (and when the owner comes back) the records' end moves to now + 120 s, so neither the watchdog nor
  the table's ownership (end + 60 s) releases them during the 600-tick decision, even at 5 TPS. At the decision the
  records are rewritten with the table's result (same value) and settle 22 ticks later with the first payout coin,
  one coin every 3 ticks.
- **Reconnect** (the mod keeps the round; a tunnel drop disconnects everyone at once): a quit or kick **before the
  call** (dealing or decision) settles nothing of that hand. Its records pay the fold, so leaving them open can never
  pay too much; they stay in the player file saved at the quit (`MachineGame.holdsForReconnect`, honoured by the quit
  and the join recovery of `LedgerListener`). The hand runs on to the mod's 600-tick timeout with its anchor chunk
  held loaded (a counted plugin chunk ticket, `MachineService.holdChunk`, released when the hand ends or the owner is
  back). An owner who rejoins in time is re-attached and calls or folds as usual. Otherwise the timeout folds and the
  records settle as a fold at his next join (stop or crash meanwhile: the same, the file holds the fold). Order: the
  join recovery runs inside the join event and decides on the table's state at that moment (held only while the hand
  is undecided and uncalled); the table sees the owner only at its next tick, so exactly one of them acts. A quit
  **after the call** settles the stored outcome at once, as before; death folds at once. A table removed (admin)
  while its owner is away cannot rewrite his file: his held records then settle as a fold at his next join, not as
  the mod's refund.
- Blackjack keeps "owner gone -> stand at once" on quit: standing never forfeits the stake, so it needs no reconnect
  grace.
- After a restart or chunk unload the mod refunds an undecided wager (`recoverInterrupted`); here the stored fold or
  called outcome is paid (PLAN decision 2). A table removed while loaded (admin) refunds an undecided hand in full,
  including a paid call, as the mod's `onTableBroken` does.
- A CALL in danger is refused and closes the menu (the hand runs on and times out to a fold, as the mod's timer
  would); FOLD only gives up the ante and stays allowed. The mod has no danger rule. A CALL whose records were
  settled elsewhere meanwhile (`revise` GONE: watchdog, admin) says "Diese Hand ist schon abgerechnet (ausgestiegen):
  Mitgehen geht nicht mehr." and the hand folds, which is what those records paid.
- Keyboard: `PokerScreen.keyPressed` maps S/1 to MITGEHEN and F/2 to AUSSTEIGEN while the button is active. In a
  chest the number keys and F reach the server as `NUMBER_KEY` / `SWAP_OFFHAND` clicks over a slot: 1 = MITGEHEN,
  2 and F = AUSSTEIGEN over any top slot (`Menu.keyButton`), through the same busy, open-guard, press-lock and danger
  path as a click; the vanilla swap stays cancelled. So number key 1 anywhere over the round menu calls, as in the
  mod (whose key works anywhere on its screen; a chest needs the cursor over one of its slots); the 6-tick press
  lock and the call's danger check apply as to a click. S has no inventory binding. Blackjack likewise: 1 = KARTE,
  2 = HALTEN, 3 = DOPPELN (the mod's H/T, S/R and D have no inventory binding; F is no blackjack key).
- The bet menu closes after 120 s without a deal (`gui.idle-close-seconds`, the mod's 2400-tick `betIdle`), silently
  like the mod (blackjack too). Slot machine, wheel, Plinko and roulette never close in the mod; the plugin's idle
  close there keeps its "Menü geschlossen" note.
- Safety re-review notes (0.6.0 live): an admin removing a table while its owner is away (quit before the call)
  cannot rewrite the offline file, so his held records pay a fold at his next join, not the mod's refund; number
  key 1 over the round menu calls (see Keyboard).
- Not built: the camera turn toward the table (possible via rotation teleports, omitted: through the tunnel they
  risk jitter for little gain) and the panel's felt frame. The bet view draws three texts at full size where the mod
  shrinks them ("DER EINSATZ ZAHLT", "Ab Paar Vieren", "sonst bezahlt"); a smaller font row would need scaled glyph
  advances in the text builder, so the boxes were widened instead.
- The call ignores `bets.max-stake` and the owed list like the mod (it is part of the hand).
- Bias ("which side the cards favour") is `poker.bias` (default 0), exchange rules and per-table settings are not
  built. `poker.needs-support: true` (default) wants one of the twelve cells on a sturdy block (the mod: always).
- Recipe: the mod's (with the Croupier's Badge, see step 8; it unlocks with the badge like the blackjack table);
  `/casino give poker_table` works too.
- Look (`PokerRig`, `PokerPanel`; pack `PokerPack`): the table is the mod's twelve block models `poker_0..11` as item
  displays at their cells (turned like the blockstate, view range 2.0); the dealer is blackjack's eight-part rig at the
  same `DealerPlacement`, posed from `PokerTableState.dealerInput` (shuffle, deal aimed at the slot, flip, pay /
  collect / push toward the seat, celebrate, clear, and watching the player's seat during the decision); cards are
  blackjack's card displays at the `PokerLayout` slots (two at the player's seat, the dealer's two face down until
  revealed, index 1 first, five board cards, up to seven deck cards while shuffling). Same cadence and shared idle
  budget as blackjack; distances from the anchor centre to the player's eyes (the mod's BER rule). Idle 20 entities,
  peak 30 plus coins (render.md: 22/31); culling box 5.2 x 2.25 covering every pose in all facings
  (`PokerGeometryTest`, also against an independent evaluation of the mod's formulas). The spectator panel (text
  display above the board, 10 blocks, hard cut) shows head + name, the stakes, "DU <hand>" and "CROUPIER <hand>" (red
  when the dealer does not qualify) and the result from 4 ticks after the decision, via the mod's lang keys; no felt
  frame or 3D item icons. Coins fly from the seat's bet spot (`eject` at 15.5 px).
- GUIs (`PokerBetMenu`, `PokerRoundMenu`; pack `PokerGui`, `PokerGuiArt`, `PokerGuiItems`): the bet view is the mod's
  366 x 266 art recomposed like blackjack (bet frames on grid slots 29/31/33, AUSTEILEN 72 x 18 over 48-50, bets book
  45, the paytable "DER EINSATZ ZAHLT" with the odds computed from `PokerHands.antePays`, MITGEHEN 2 x Einsatz 1:1,
  CROUPIER Ab Paar Vieren / sonst bezahlt; 15 plain columns cut from the payout panel). The round view is a second
  chest opened in the DEAL click: the mod's 160 x 196 panel pieces rebuilt 304 px wide with dealer box | board |
  player box side by side (only one 32 px card row fits above the chest): board cards in slots 0/2/3/5/6, dealer cards
  9/10, player cards 16/17, hand names 18/26 (the dealer's red when he does not qualify), EINSATZ / BILANZ label 19,
  status / result 22, stakes 27-29 (the ante, "x3" in 43 after the call, like `anteStakes`), the NET lines per item
  kind at the result (signed, up to 6), timer bar 39 (red at 38 steps or fewer), MITGEHEN over 45-48 (greyed unless
  the decision is open and 2 x the ante of every item kind is in the storage slots) and AUSSTEIGEN over 50-53. The
  mod's client timing: cards appear 22 ticks after the deal, a dealer card's face 8 ticks after the reveal, buttons
  locked 6 ticks after a press; keys 1 / 2 / F as described above (tooltip hints "(Taste 1)", "(F oder 2)"). The controller refreshes the open round
  menu every tick (only changed slots are sent) and closes it when the hand is over; the owner's right-click reopens
  it during the hand. Deviations of the art: buttons 68 px each with a 22 px gap (to match the click areas), longer
  German texts drawn unscaled, NET columns 54 / 36 px apart.
- A hand older than 6 000 ticks is treated as stuck (a listener error): it ends, its records settle as stored and the
  table is free again (the longest real hand is ~1 000 ticks plus coins).

### Step 7 (duck derby)

Four ducks race in four lanes; a player stakes 1-3 stacks (one per bet row) on **one** duck. 1st pays 3 x the stake,
2nd returns it, 3rd and 4th lose it (`DuckDerbyBlockEntity` 48-49, 389-390). The pure classes are verbatim ports
(`DuckDerbyRace`: SplitMix64 planner with the story dice, 48 attempts, finish times, scoring, poses, gate, quacks;
`DuckDerbyLayout`, `DuckDerbyGeometry`, `DuckDerbySignLights`, `DuckDerbyFootprint`, `DerbyPanelPresentation`);
`Odds.derby` ports `CasinoOdds.derby`. Footprint 3 x 4, anchor part 10 (front centre), the sign on the back row.

- **Solo** ("Solo spielen"): needs no countdown running. The winner is drawn (`nextInt(4)`, or with `derby.win-chance`
  0..100 `CasinoOdds.derby` for the player's lane), then `seed = nextLong()` and the plan (`DerbyRules.draw`; if the
  planner rejects all 48 attempts, a new seed, games.md 7 open question 1). The stakes are committed in one tick as
  DECIDED records holding that result, and the race starts at once; every derby menu on the machine closes (the mod
  refunds other players' drafts there; here nothing real lies in a menu).
- **RTP** (`tools/rtp/DerbyRtp.java`): exactly `3p + (1 - p)/3` per item staked (`CasinoOdds.java:59`), with chance c %
  `(2c + 25)/75`: natural odds and every group race **1** (win 1/4, push 1/4, loss 1/2), c = 10 0.6, c = 20 13/15,
  c = 50 5/3. P(2nd) = 1/3 for each non-winner is exact by the planner's lane symmetry (checked by re-scoring
  960 000 candidates under every relabelling: 0 differ) and measured over 220 000 plans (chi-square p = 0.36). No null
  plan in 220 000 (model estimate 9e-82; `DerbyRules.draw` would retry 16 seeds, a group launch without a plan refunds).
  Story mix 35.0 / 30.0 / 35.1 %, last finisher race tick 129..162, `endTick` 243..276. The plugin's `DuckDerbyRace`
  equals an independent transcription and the mod's own class bit for bit over 20 000 plans. Tests: `DerbyRulesTest`,
  `DuckDerbyRaceTest` (golden plans, invariants), `DerbyRtpSimulationTest` (races through the shipped path; sizes via
  `KABOOF_DERBY_PLANS` / `KABOOF_DERBY_RACES`). Planner speed ~100-120 plans/s per thread (8 ms per plan, only at a
  launch).
- **Group** ("Gruppe (10s)" / "Bestätigen" / "Zurückziehen"): allowed at natural odds or exactly 25 (`groupAllowed`).
  Each entrant's stakes are committed on their own as PLAYING records whose payout is the refund (the mod's held
  escrow); the 200-tick countdown starts with the first entrant and is never extended; up to 64 entrants on any ducks,
  one duck each; joining after the countdown has ended is refused. Withdraw settles the refund at once; the last one
  out cancels the countdown. Entrants who quit, are kicked or die meanwhile are settled by those hooks (the refund;
  death: into the drops) and drop out, as the mod removes and refunds them. **When the countdown ends the bet window
  is closed, and only then** the winner (`nextInt(4)`: groups always use natural odds), the seed and the plan exist;
  each entrant's records are rewritten to DECIDED with his result in **his own** ledger write (`WagerEngine.revise`).
  One player's quit, death or crash never touches another's records; an entrant whose records are already gone at
  that moment simply drops out.
- **No leak**: before the bets are locked there is no result anywhere (no winner, no seed, no plan, nothing in a
  record); the records of the countdown hold only the refund. The rig and the panel show the race from the launch on;
  the panel shows "SIEGER / ZWEITER" from `resultTick`.
- **The race**: quacks, splashes, the finish line, the result messages ("Deine Ente hat gewonnen!" etc. for online
  entrants), the win/push/lose cues and the confetti follow the mod's tick plan. The records settle at `resultTick + 12`
  (when the mod's credits fall due) into the inventory, the rest owed; then "N Gegenstände erhalten" and, within
  16 blocks, the payout sound at the anchor and five happy-villager particles toward the player (`DerbyCredits.pay`).
  The machine unlocks at `endTick`. The machine cannot be opened while a race runs ("Das Rennen läuft."); breaking is
  refused while entrants wait or a race runs.
- **Quit, kick, stop, crash, death** during the countdown settle the refund (the mod: refund); during the race the
  stored result, which is what the race shows (the mod settles the credits at the launch too). Its anchor chunk is held
  loaded (counted plugin ticket) from the first entrant to the race's end, so a race with nobody near still ends and
  pays on time. A table removed by an admin settles what is stored: the refund in the countdown, the result in the
  race (the mod makes the credits due at once).
- Rewards: the mod's `DerbyCredits` ledger (pay only within 16 blocks of the machine, on the claim button or at
  join, and only to a living player) is the plugin's owed list: winnings go straight into the inventory wherever the
  player is (the 16 blocks, measured from the anchor block's centre like the mod, only decide the payout sound and
  particles), the rest owed and delivered by the owed timer, `/casino abholen` or the menu's "Gewinn abholen" button.
  A player who dies with a race's result open gets it in the death drops (the grave) instead of the mod's ledger
  waiting until he is alive again. The plugin's global owed lock applies: while anything is owed no new round starts
  (`OWED_PENDING`), so "Gewinn abholen" takes the SOLO button's place while anything is owed (SOLO could not start
  then anyway; the menu has no free slot for a separate claim button) - a deviation from the mod's separate button.
- Plugin additions: "Das Gruppenrennen ist voll (64 Spieler)." and "Die Anmeldung ist geschlossen: Das Rennen startet
  gleich." (the mod only refuses silently, `joinGroup` returns false), and the stuck guard: a race older than its
  `endTick` + 600 ticks or a countdown older than its 200 + 600 ticks settles every entrant as stored (TIMEOUT) and
  frees the machine (`DerbyStuckGuardTest`), checked first in every tick so an error later in the tick cannot stop it.
- Load: a launch and a payout read and write each entrant's ledger once (`WagerEngine.settleRounds`); the menu's owed
  count is cached for 10 ticks per viewer. The payout is spread over `resultTick + 12 .. + 16` (step k settles the
  first ceil(n (k + 1) / 5) entrants in join order). Measured on the test instance with 8 bots, 3 bet rows each
  (three races): records at the launch 1.3-3.3 ms (0.2-0.4 ms per entrant: ~11-14 ms for 64), payout 4.5-8.8 ms in
  total (0.6-1.1 ms per entrant: ~35-70 ms for 64, so 7-14 ms per payout tick), the race plan itself 19-30 ms on the
  busy test server (8 ms warm in a test JVM) once per race, solo or group.
- Legacy shared bets (`startLegacy`, unreachable in the mod) and the exchange rules are not built; the `forced` winner
  argument is never used by the mod's menu and is not built.
- Config `derby:` `win-chance` (-1 natural, 0..100 solo only), `needs-support` (default true: one of the twelve cells on
  solid ground; the mod needs none), `idle-light-ticks`/`idle-light-range` for the sign bulbs' idle chase under the
  shared light budget.
- Recipes: the mod's `duck_derby`, `duck_derby_mechanism` with its 23 other duck orders (no recipe-book unlock in the
  mod, craftable) and the four racing ducks.
- **Look** (`DerbyRig`, `DerbyPanel`; pack `DerbyPack`, `DerbyModels`, meshes `pack-src/meshes/derby_{duck,gate}.json`
  from `tools/meshes/convert-derby.sh`, 52 quads, 0 unmatched): the tub is the mod's twelve block models
  `duck_derby_0..11` as item displays at their cells (turned like the blockstate, view range 2.0: tub, rim, posts, sign
  board, flag). The renderer's parts: 4 duck displays (the converted 48-quad `DUCK` mesh with the lane's `LANE_UV`
  baked in, pivot (lane centre, 13 + lift, z) px turned about X by the pitch; `plan.pose(lane, elapsed)` inside
  `0 <= elapsed <= endTick + 1`, else z 56.5), one display with the four gate bars (sunk 3.6 px x `plan.gate`), one
  for `signLights` (the full-bright `dd_sign` overlay at z 3.025 and the 12 bulbs at z 3.08, tint i =
  `gray(round(255 x level))` from the controller's idle chase, brightness 15). 18 item displays + the panel = 19
  entities (render.md 2.5: 21 with a label). All stand at the anchor's bottom centre (light = the renderer's
  `lightCoords`, also for the tub), culling box 7.2 x 2.0 holding the machine, the sign and every duck pose in all
  facings (`DerbyGeometryTest`, against an independent evaluation of the renderer's pose stack and the blockstate).
  Cadence (`DerbyRigCadence`): ducks and gates every tick with the pose of now + 1 and interpolation 1 while a
  player's eyes are within 64 blocks of the anchor (the renderer's view distance); with nobody that near but a player
  inside the display tracking range (128 = `spigot.yml` `entity-tracking-range.display`, a constant because Paper's
  getters for it are deprecated, capped at view distance x 16; horizontal like the tracker) every 4th tick with the
  pose of now + 4 and interpolation 4, each send granted by the shared idle budget (asked only when due), so a client
  with a higher entity distance sees the race too; nobody tracking: nothing. Only what changed is sent, at rest
  nothing; the player scan is cached 4 ticks. Race traffic per tracking player: 3.76 display updates per race tick
  (75/s) with someone within 64 blocks, 0.96 (19/s) with the nearest player at 100 blocks (`DerbyRigCadenceTest`).
  The bulbs are resent only when a grey changes.
- **Panel**: one text display centred like the renderer's at machine (24, 2.6 blocks, 32) (a group panel taller than
  70 px lifted as `sharedPanel` does; moved when its line count changes), view range 10 (hard cut instead of the
  7.5-10 fade), rebuilt only when its content changes and only while a player is within 24 blocks. Group: the
  countdown "Start in N s" with the brass bar (the remaining time in 20 steps), "N Spieler" / "N Spieler · Rennen
  läuft" / from `resultTick` "SIEGER: X · ZWEITER: Y", up to 8 entrants in join order (the lane strip under the head,
  name, up to three stakes), "+ N Spieler". Own entry first (`DerbyLobby.visible`): an entrant within 12 blocks whose
  order differs gets his own copy (a text display not visible by default, `showEntity` to him only, the shared one
  `hideEntity`'d for him; kept to 14 blocks, `DerbyPanelViewers`); it goes when he walks away, leaves the world, quits,
  the group panel ends or the rig despawns, the shared panel shown to him again first (Paper keeps both directions per
  entity id, so nothing stale is left). Solo: the player's head and name, per staked lane the duck's name on the
  lane colour over the bronze line (`background` draws `HEADER_EDGE` only as a line) and its stakes, from `resultTick`
  "SIEGER: <Ente>" / "ZWEITER: <Ente>" in bold with the duck's colour. Words are the mod's lang keys with German
  fallbacks; bars and strips are glyphs of the pack font `kaboofcasino:derby_panel`. Not built: the felt texture and
  brass frame of the panel, 3D item icons, the per-viewer fade and look-angle dimming, the countdown's 1.5 x text
  size, the winner/second column frames (the result lines name both); the lane box is 11 px instead of 14 to fit a
  text line, and the header label is centred with the German name's width.

- **GUI** (`DerbyBetMenu`, `DerbyMenuView`; pack `DerbyGui`, `DerbyGuiArt`, `DerbyGuiItems`): the mod's felt art
  recomposed for a chest. Bets are a selection (nothing real lies in the menu): bet i (lane i % 4, row i / 4) on grid
  columns 0/3/5/8, rows 1-3, one lane only (the others veiled; a click there shows "Eine Ente pro Rennen!"); the duck
  cards above the columns, the hint (pick / "Deine Ente: …" / countdown, in the code's order countdown before
  confirmed) and the player count on the right panel; SOLO over 36-39 (CLAIM "Gewinn abholen" in its place while
  anything is owed), help 40 (the bettable items in its tooltip), GROUP / CONFIRM / WITHDRAW over 41-44 (greyed with
  the group_unavailable tooltip when the win chance forbids groups), the lobby as 8 player heads in row 5 (own entry
  first; name, duck and "Einsatz bestätigt" in the tooltip) with a pager in 53; the help panel re-laid out above the
  inventory. Deviations: lanes on 18 px grid columns instead of the 52 px pitch, brand and hint moved to the right
  panel, the lobby as a row of head items (forced: a chest can show players only as items in slots, names only in
  tooltips) instead of drawn names and skins, one pager instead of the two arrows, an 18 px help button, buttons 83 px
  wide.
### Step 8 (croupier, guide book, TVs, dealer station)

- **Casino TVs and dealer station** (display deco, games.md 16 and 18; `DecoType`, now 45 types):
  `roulettecasino:casino_tv` (1 x 1, light 5 when on), `casino_tv_large` (2 x 2, light 7) and `casino_tv_3x3`
  (3 x 3, light 7) on light carriers in every cell (oak planks, hardness 2.0, metal sound);
  `roulettecasino:dealer_station` ("Croupier-Theke") on a barrier (lectern copy: hardness 2.5, wood sound, no tool
  needed for the drop), `Place.FREE`: a plain block of the mod, so no support rule, no facing, it never pops. The four
  are in no `mineable/*` tag of the mod, so every tool digs them at hand speed (`DecoType.anyTool`: the dealer station
  takes 75 ticks with anything; the chair and stool, untagged in the mod as well, keep step 4's axe speed).
- **TV placement** (`Place.TV`, `CasinoTelevisionBlock.getStateForPlacement`): only on the side of a block, facing =
  the clicked face; part p sits `p % columns` steps toward `facing.getCounterClockWise()` (the viewer's right) and
  `p / columns` up (`DecoGeometry.wallPart`); every cell replaceable, inside height and border, with a full sturdy
  face behind it. One record for all cells, which must lie in the anchor's chunk (the chunk-border hint shifts along
  the wall, as for the large sign).
- **Integrity**: the support of every cell is checked on neighbour changes and by the verifier; a lost wall removes
  the whole TV and drops its item once at the anchor (the mod's `tick` → `destroyBlock(anchor, true)`). A hit on any
  column's click box removes all cells and gives one item. A cell whose light block vanished without an event
  (`/fill`, an exploit) removes the TV without a drop (deco rule; the mod would drop the anchor's item). Explosions
  and pistons leave TVs alone (light carriers; the mod's TV is IMMOVEABLE, but an explosion could break it: here TVs
  take no direct explosion damage).
- **Channels**: the record's state is the channel 0..8 (0 = off). A right click on a click box with the main hand,
  not sneaking (sneaking passes, as `isSecondaryUseActive`), steps `DecoRecord.nextChannel`: off → 3 → 1 → 2 → 4 →
  5 → 6 → 7 → 8 → off on all cells at once (one record update swaps every cell's model and sets the light blocks to
  5/7 or 0), with the lever click (BLOCKS, volume 0.25, pitch 0.8 when on afterwards, 0.65 when off) at the clicked
  cell. Saved in the chunk record: survives chunk reloads and restarts.
- **Look**: one item display per cell with the mod's own cell model `roulettecasino:block/<tv>_screen<channel>_<part>`
  and the blockstate's `y` (`DecoModels.tvKey`, display key `kaboofcasino:deco/<tv>_screen<channel>_<part>`): 126
  models; the small TV's blockstate also names parts 1..3, which the mod never places (not shipped). The screens are
  the mod's static pictures (`light_emission` 15 faces). Click boxes: one Interaction per column over the screen's
  rows (`getShape`: small 3.5-12.5 px, large 7-25, 3 x 3 10.5-37.5), square in x/z like the signs' boxes. View range
  1.25 (small), 2.0 (large, 3 x 3). The dealer station is one display of its model; its barrier carries the centre
  below it (a hanging lantern or chandelier, from the 12 px post) and nothing on its top or sides, like the mod's shape.
- **Croupier hook** (`DealerStationHook`, used by the `croupier` package): `onPlaced(record, player)`,
  `onRemoved(record)` (broken or carrier lost; not on chunk unload or plugin disable), `boolean onUse(player,
  record)` (main hand, not while sneaking with an item, never spectators or fake players; true = the held item is
  not used). `DisplayDecoService.dealerStationHook(hook)` sets it (null = `DealerStationHook.NONE`, the default: the
  mod's station has no use). Loaded stations: `dealerStations()` and `dealerStationsNear(Pos, radius)` (same world,
  nearest first), from an index kept with chunk loads and unloads. Exceptions of `onPlaced`/`onRemoved` are logged as
  incidents; one of `onUse` is reported to the player like any deco use.
- **Pack** (`DecoDisplayPack`): the four item definitions with their item and icon models and textures, the TV cell
  models and the dealer model with display definitions, `block.roulettecasino.casino_tv*`/`dealer_station` in all 12
  languages; the screen pictures (512 x 288, 256 x 144) need no padding. Recipes: the mod's four (`ModRecipes.SHIPPED`),
  their results are the paper deco items.
- **Clearance** (safety, as machines): barrier deco that does not stand on support below (`DecoType.playerClearance`:
  the dealer station, `Place.FREE`, and the two wall shelves, `Place.WALL`) is refused within 2 blocks (box to box,
  `MachineService.PLAYER_CLEARANCE`) of another non-spectating player ("Hier steht jemand zu nah – das braucht zwei
  Blöcke Abstand zu anderen Spielern.") and, as every barrier deco, on a logout spot: nobody can be capped in a hole.
- **Deviations** (deco rules): no collision on TVs (the mod's panel collides 2 px deep) and a full 16 px barrier for
  the dealer station (the mod's top ends at 11.7 px); TVs take no direct explosion damage; a TV breaks at the first
  hit instead of 60 ticks; no villager
  job-site POI (the croupier module finds stations through the lookup).
- **Tests**: `DecoBlockstateTest.televisionsEveryState` (all 288 + 288 + 648 states: model, `y` and cell of every
  placed part; the legacy `on=true, screen=0` = channel 1) and `dealerStation`; `DecoGeometryTest` (cells to the
  viewer's right and up, click boxes, chunk fit along the wall); `DecoRulesTest` (light per channel, the cycle,
  hardness and sound, untagged dig speed, the station's support and lookup); `RecipesDataTest`
  (`televisionAndDealerStationRecipes`, deco results); `DecoDisplayPackTest.televisionCellModels`; `DecoPackKeysTest`
  covers the new keys and names.
- **Recipe unlocks** (`ModRecipes.unlock`): an unlock item that is an item tag of the mod is expanded to its items
  (the pack tool now reads `data/<ns>/tags/item/`), so holding any racing duck unlocks the derby mechanism
  (`#roulettecasino:derby_ducks`; before, that unlock never fired). The badge unlocks the blackjack and poker tables;
  the guide unlocks with leather, the counter with green wool, the small and large TV with tinted glass, the 3 x 3 TV
  with the large TV (`RecipesDataTest`).
- **Music opt-out** (Jack's request): `/casino musik aus|an` stores the choice in the player's PDC
  (`kaboofcasino:music_off`); from the next login no music pack is offered (`PackListener` asks `MusicOptOut` in the
  configuration phase: remembered since the plugin started, else read from the player file), so nothing plays for
  him. `/casino credits` names the command (`PackListenerTest.aPlayerWhoTurnedTheMusicOffGetsNoMusicRequest`).
- **Croupier's hiring card** (`roulettecasino:croupier_hiring_card`, `CasinoItem.CROUPIER_HIRING_CARD`): the croupier's
  one trade (24 emeralds, 64 uses, 2 XP, games.md 10.2); used on a croupier it hires him for a Croupier's Badge
  (games.md 10.3). Giveable with `/casino give croupier_hiring_card`; no recipe (as in the mod).
- **Guide book** (`roulettecasino:casino_guide`, games.md 19; package `guide`): the mod's 34 spreads as Paper dialogs
  (multi_action, ESC allowed, no pause, after_action none, 4 button columns, footer "Fertig"). Data: the pack tool's
  `GuideData` writes `guide.json` (the mod's `casino_guide.json` unchanged, the German text of its 134 lang keys, the
  name keys of 91 casino ids) from the SHA-checked jar; `processResources` embeds it as `pack/guide.json`.
  - Mapping: a top line "« Vorherige Seite  n / 34  Nächste Seite »"; title widgets bold in the mod's gold
    `#F2C65A`, labels and texts in its colours; items as item bodies (labels beside the icon become the description,
    else the name, a link if the mod's `index` points to a spread); tiles as icon + link; recipes as pictures (see
    "Recipe pictures" below); scenes as their main items' icons (the hiring scene
    and the villager figures: a villager spawn egg named "Dorfbewohner"/"Croupier"); the arrow "↓", rules as a
    struck-through line; chips and previews dropped (every variant is shown); the cover ends with the licence line.
    Buttons: the 4 bookmarks (open chapter in gold), « Vorherige Seite / Inhalt (only with a way back) / Nächste
    Seite ».
  - Clicks: custom click `kaboofcasino:guide` with `{do, to}`; only known kinds, pages 1-34, players with an open
    guide, one click per tick (close always); back history capped at 64; no commands, no callbacks. Page turn:
    `item.book.page_turn` (0.6, 1.0) for the reader only.
  - Opening: right click in the air or on a plain block with either hand, nothing used up; not on machines or deco,
    not on doors, containers or work stations unless sneaking; always at the cover (as the mod).
  - Safety (PLAN decision 3): in survival/adventure it does not open in combat or danger ("Nicht jetzt – du bist in
    Gefahr.") and closes on damage, a mob targeting the player or the danger sweep ("Gefahr – Handbuch zu."); the
    sweep counts riding a vehicle and standing in water as danger, so it cannot be read on a horse or in a minecart
    (open decision for Jack, easy to relax). Creative is unrestricted.
  - Plugin notes (`guide.note-*`, gray italic, right under the mod text they amend): spread 3 under text 043 ("In
    einem Dorf vergibt die Theke den Beruf des Croupiers."): "Hier: Stell einen arbeitslosen Dorfbewohner auf die
    Theke (Leine ab) und klick die Theke an."; spread 33 under 219 (creative tab: `/casino give croupier_hand`) and
    213 "Einsätze und Auszahlung" (not in the dialog here: config.yml); spread 34 under 262 (the name field instead of
    a tab) and 260 (slider only, no typed number).
  - Recipe pictures (Jack's request, 2026-11-08: a picture of the crafting grid instead of the text listing): one
    plain-message body per recipe whose glyphs (font `kaboofcasino:guide_recipe`, built by the pack tool's
    `GuidePictures` + `GuideIcons`) draw the mod's recipe widget (`CasinoGuideScreen`: 3 x 3 slots of 18 px in its
    colours, the gold arrow from `book.png`, the 26 px output slot with its frame outline, the shapeless sign above
    the arrow, the result's count like an item stack's, right-aligned with the vanilla shadow) on a 128 x 68 felt
    card in the book's frame colours, with the caption right above it: the result's name, for shapeless recipes
    "Formlos · Name" (the mod's "Formloses Rezept" made long names too wide); a caption wider than 240 px is cut with
    "…" (`GuideDialogs.caption`, by the German name), the result icon's tooltip has the full name. All 89 recipes are drawn horizontally (the mod draws 9 of them vertically for lack of room); shapeless
    ingredients fill the grid from the top left. Icons (`GuideIcons`): vanilla items by their vanilla texture's
    resource location (40 items in `GuideIcons.VANILLA`; nothing of Mojang's is copied into the pack), casino items
    from the pack's own item definition (the `gui` icon of the mod's display-context selects, `item/generated`
    layers, the `all`/`side` face of a cube, the lower half of a slab, the lower half plus the upper left quarter of
    stairs; animated textures show their first frame); a font can only draw flat images, so blocks show their front
    face, not the inventory's 3D render (0 fallbacks to the "?" glyph today). Every icon has its item's tooltip (the
    server sends the stack; a tag shows its first member's name + "(beliebige Sorte)"), icons of items with an index
    entry on another spread are links (as the mod's), the shapeless sign has the mod's tooltip. Layout: a dialog
    line is centred on its own width, and a vanilla texture's glyph width is only known to the client, so such a
    glyph ends a line of its own and is also drawn once 8192 px to the left (off screen): centring then cancels its
    width exactly, whatever texture the client has. Opaque block faces count as 17 px. The caption sits between two
    runs of -8192 px (negative line width: still centred, never wraps), so recipes with many vanilla items (the
    nugget-heavy decoration, the TV) get up to two blank lines above the caption; every picture is 9 to 11 lines.
    The text box (9 px per line + padding) contains the card, so all tooltips work.
  - Deviations: the caption is only the name ("Formlos · Name" for shapeless recipes); recipe pictures show
    flat icons (block fronts instead of 3D renders) and no tag cycling (the first member, as before); scenes are
    16 px icons (dialog items are always 16 x 16); items
    without a label show their name (the mod only on hover); the added top navigation line; "Fertig" needs one round
    trip, ESC closes at once.
  - Tests: `GuideBookTest`, `GuideDialogsTest` (all 34 spreads with and without history, a full snapshot of spread
    3, styles; every recipe a picture with its caption, tooltips, links, shapeless sign and count; every picture
    glyph in the pack's font), `GuideSessionsTest`, `GuideActionTest` (forged payloads); pack tool
    `GuidePicturesTest` (a model of the client's line layout: every icon in its slot for random, 1 px and 17 px
    vanilla widths, the card centred and inside the text box, no line that could wrap, counts right-aligned, hidden
    copies off screen; textures ours or the table's vanilla ones, no Mojang file in the pack, byte-identical, under
    64 KB; with `-Pcasino.clientJar=<26.3 client jar>` also: every vanilla texture exists, 16 x 16, one frame, the
    opaque ones opaque), `GuideDataTest` (every key in all
    12 languages, every mod key with its exact German text, every item id resolves).
- **Croupier's hand** (`roulettecasino:croupier_hand`, games.md 20; Jack's partial proposal; package `admin`):
  - Access: creative + `kaboofcasino.admin`; outside creative every use shows the mod's `creative_only` message,
    without the permission "Dazu fehlt dir die Berechtigung.". Main hand on a machine opens its dialog, sneak + use on
    a machine pastes; use in the air or on a plain block opens the whole-casino dialog, sneak in the air forgets the
    copy. Nobody breaks a machine with the hand in the main hand. Every 5 ticks a dialog whose player lost the
    permission, the mode or the hand, or whose machine is gone, is closed.
  - Machine dialog: name (64 characters, no control or `§` codes, empty = automatic "<Typ> #n"), win chance 0..100
    (roulette, derby, slot machine, wheel, Plinko) or bias -100..100 (blackjack, poker) with "Wie das Casino"; Save,
    Copy, Paste, Reset (two clicks within 60 ticks, "Sicher?" in between). Stored in the machine record as
    `MachineSettings.Own` (three optional chunk-PDC keys `name`/`chance`/`bias`, written only when set; record
    version stays 1; an invalid stored value reads as inherit and is logged; 0.7.0 reads such records unchanged, but
    rewrites them without the keys when a game saves its data, so a downgrade loses own settings). Effective value
    = own if set, else the type's from `config.yml` (`MachineSettings.winChance/bias`; the only place games ask, a
    source scan test holds them to it). `/casino reload` never touches own values.
  - Whole-casino dialog: the seven type values (win chance with "normal" = -1, bias) and "breakable"; Save and
    "Alles normal" (two clicks: every win chance -1, every bias 0). Written into the live `config.yml` with Bukkit's
    comment-keeping `YamlConfiguration` (only the `ConfigEdits.EDITABLE` paths, temp file + atomic move; the new file
    gets the old one's POSIX permissions, owner and group, so e.g. 664 stays 664), then the plugin's own reload. Bet lists and exchange rules stay config-only.
  - `machines.breakable` (default true, new key merged at start): false = survival players cannot mine machines
    (silent, as the mod), their owners included: then only creative players or `/casino remove` remove machines.
  - Copy/paste: the hand keeps only the copy's identity (type, token, position, number, name) in its PDC and shows the
    mod's "Kopierte Einstellungen: …" lore; the values stay on the server under the token (at most 256, overworld
    PDC, survive restarts). Chance/bias paste only between machines of the same game; a token that dropped out pastes
    "inherit" (mod). Mod messages pasted / nothing_to_paste / copy_forgotten.
  - Limits and logs: 20 edits per player per game second (opening counts, as the mod), one click per tick, the target
    always from the server-side session; every change logged with old and new values, copy and forget too.
  - Floating labels: a text display per machine (the mod's heights, colours and size), shown only to permitted
    players holding the hand within 8 blocks, despawned when nobody sees it.
  - Texts: the mod's keys as translatable components with its German text as fallback (67 checked against the jar);
    vanilla `gui.done`, `options.percent_value`, `options.generic_value`.
  - Deviations: no bet lists, payouts or machine list/search (Jack's scope), so the tabs collapse into two dialogs;
    values apply on Save, Fertig/ESC, Copy and Forget (the mod's sliders apply while dragging); a typed name is saved
    on Fertig/ESC (the mod drops the draft); sliders only, no number box (help texts about typing left out); buttons
    that would do nothing are hidden (dialogs cannot disable one); "Kopiert!"/"Eingefügt!"/"Sicher?" appear in the
    re-sent dialog instead of 2-second labels; no type-level "inherit" for the bias (0 = normal); the mod's quirk
    "sechs Casinomaschinen" kept; labels with one background colour, estimated width.
  - Tests: `MachineRecordCodecTest` (0.7.0 readability, lenient values), `MachineSettingsTest` (resolution, name
    rule, ranges, source scan), `BreakRulesTest`, `ConfigEditsTest` (comments kept, live-file round trip),
    `AdminClickTest`, `AdminSessionsTest`, `AdminDialogsTest` (snapshots), `AdminTextsTest` (every fallback = the
    mod's German text), `CopyPasteTest`, `EditRateTest`.
- **Croupier** (games.md 10; Jack's option A: a display NPC at a counter). Package `croupier`; the only part in which
  A and A' ("appears with the counter") differ is the `CroupierSpawnTrigger`.
  - **Trigger A** (`VillagerOnCounterTrigger`, explicit: nothing ever converts on its own): only when a player
    right-clicks a counter without a croupier (`DealerStationHook.onUse`: main hand, as the mod would use the block;
    permission `kaboofcasino.use`; the player within reach, vanilla's `isWithinBlockInteractionRange(pos, 1.0)` with
    the block interaction range attribute) does the counter look into the block directly above it, never further. A
    villager there becomes its croupier if every rule of `CroupierEligibility` holds: feet in that block and standing
    on the counter's top (on ground, y = counter + 1), profession none, not a nitwit, level 1, villager XP 0, no trades,
    adult, not leashed, no custom name, not riding, no passengers, not trading, not asleep, the counter has no croupier,
    and the clicking player within reach. Otherwise the player gets a short action-bar answer in the mod's style
    (`croupier.*` in `messages_de.yml`: "Stell einen arbeitslosen Dorfbewohner auf die Theke und klick sie an.", or the
    reason: nitwit, has a job, child, leash, name, not free, busy, not on top, too far). A refused villager is logged
    once per reason with the clicking player (not a dying one, not one only passing by). A villager standing on a
    counter that nobody clicks stays a villager. The conversion releases the villager's job site, bed, potential job
    site and meeting point (`Villager.releaseAllPois()` by reflection, as the mod's hiring does before it removes a
    villager; no API has it; a failure only leaves the claims, logged once), keeps `createSnapshot().getAsString()`,
    the Minecraft version and every inventory stack (`serializeAsBytes`, Base64) in the record, spawns the croupier
    and removes the villager; logged with world, position, villager UUID and the clicking player's name. A' would be
    `stationPlaced` calling `spawns.spawn(station, Origin.NONE, placer)`.
  - **Record** (`CroupierRecord`, `CroupierCodec`, `CroupierRecords`): chunk PDC `kaboofcasino:croupiers`, a list of
    JSON strings, version 1 (`v`); spot (block, feet height, facing), counter id and block, villager UUID, snapshot,
    items, uses, XP, vanilla's restock bookkeeping (last restock game time, last check's day, restocks today),
    creation time and the player who brought it. A newer or unreadable entry is logged CRITICAL and
    kept verbatim (that croupier stays inactive). Croupier, counter and record always share one chunk: it is placed
    only beside a counter in the counter's chunk and re-attaches only to one there, so nothing is looked up across a
    chunk border. Index on chunk load/unload; after a load the record's counter is checked (missing = counter lost).
  - **Spot** (`CroupierPlacement`): on the ground beside the counter, the side opposite the nearest player first
    (then the sides turning least towards him), facing the counter; free = feet and head blocks without casino cells,
    other croupiers or liquids, head passable, feet passable or carpet-low (up to 3 px, standing on it), a floor below
    (collision top >= 0.8: full blocks, paths 15/16); else on the counter's top (11.7 px, the mod's shape) looking at
    the player.
  - **Look** (`CroupierRig`): the blackjack dealer's eight displays (`kaboofcasino:blackjack/dealer_*`, the mod's
    croupier profession texture) with `BlackjackDealerRig`'s transformations moved from the table placement to the
    feet, the idle loop only (`BlackjackDealerRig.pose` with no gesture, `BlackjackRig.idleEvery` cadence, pose of
    now + n + 1, within 32 blocks, shared idle budget `ctx.lights()`), brightness of the two body blocks every 40
    ticks; an Interaction with the villager's hit box (0.6 x 1.95) as click box. Non-persistent, tagged
    `kaboofcasino:part`, rebuilt at chunk load (4 per tick) and when an entity vanished (checked every 20 ticks).
  - **Trade** (`CroupierTrade`): a right click with the main hand, not sneaking (a sneaking click does nothing, as
    with a villager), opens a vanilla merchant screen (`Bukkit.createMerchant()` + `MenuType.MERCHANT.builder()`;
    `createMerchant(Component)` and `openMerchant` are deprecated) titled `entity.roulettecasino.villager.croupier`
    ("Croupier"): 24 emeralds -> 1 hiring card, 64 uses, 2 villager XP, price multiplier 0 (demand and gossip never
    change it), Hero of the Village lowers it as in vanilla (by 7, to 17 emeralds, at level I). Uses and XP go into the record
    (`PlayerPurchaseEvent`, MONITOR); the player's orb is vanilla's 3-6 (+5 on a level-up: 10/70/150/250 XP) at the
    croupier (the recipe's own reward is off, Paper's custom merchant would drop the villager XP at the player).
    Restock as vanilla (`WorkAtPoi` + `Villager.shouldRestock`/`restock`, `CroupierTrade.shouldRestock`): in working
    hours (day time 2000-9000, the villager schedule's WORK) the croupier works at its counter at most every 300 game
    ticks (then on a coin flip): its work sound, then up to 2 restocks per day, the second at least 2400 game ticks
    after the first; the count resets when the day (overworld day count, `fullTime / 24000`) is later than at the last
    check (from day 1 on, as vanilla) or 12 000 game ticks passed since the last restock. Only with its counter (no job
    site, no work) and not while a player trades (vanilla would restock and resend the offers). One player at a
    time: a second one gets the mod's `message.roulettecasino.croupier_busy` ("Dieser Croupier ist beschäftigt.",
    action bar), which the mod only uses for hiring (a vanilla villager ignores the second player). The screen closes
    beyond 8 blocks. Sounds as a villager: yes/no when the payment changes (`notifyTradeUpdated`, its 20-tick gate),
    the ambient (trade) voice by vanilla's ambient clock within 16 blocks, the cartographer's work sound at every
    work at the counter; voice pitch 0.8-1.2.
  - **Hiring** (`CroupierHiring`, verbatim where pure): a card in the clicked hand, or in the off hand with an empty
    main hand; busy check; the kept villager items drop at the croupier's block like `Block.popResource`; the croupier
    is gone for good; survival: one card used, the badge into that hand if it is empty now, else the inventory, else
    dropped at the player; creative: card kept, badge added; burst (totem 34, poof 12, the three confetti dusts, end
    rod 14), `entity.villager.yes` + `block.enchantment_table.use`, the 14-tick stream to the hands with its end burst,
    `roulettecasino:card_flip` and the amethyst chime. Logged.
  - **Counter lost** (`DealerStationHook.onRemoved`, or missing at chunk load): vanilla's job-site rule. XP 0: the
    croupier is the villager again (`createEntitySnapshot(...).createEntity` at its feet; a snapshot from another
    Minecraft version is restored with a warning; if it cannot be restored the croupier stays, CRITICAL). XP > 0: it
    keeps its profession, stays without a counter (tradeable, hireable, no restock; one on the counter's top steps
    down if a floor carries it) and takes a free counter placed right next to it (same height and chunk; placed into
    its feet block it stands on the counter's top); a vanilla villager would look for a free job site within 48
    blocks, here only an adjacent counter is taken. Using a counter that has a croupier does nothing (the mod's
    station has no use). A croupier whose chunk unloads disappears with it and comes back from its record.
  - **Deviations**: the croupier is the blackjack table's dealer model with its idle loop, not a villager in the mod's
    croupier outfit: no head tracking, no crossed arms, no biome skin and no level badge, and no villager entity (no
    walking, sleeping, gossip, breeding, zombie conversion, level-up regeneration); the conversion needs the click on
    the counter (the mod's villager claims the station by itself); the busy message also for trading; no restock while
    a player trades; re-attaching only to an adjacent counter (not within 48 blocks); the trade screen is the vanilla one (the mod's
    `croupier_trade.png` look is a client mixin; as a title glyph the art would hide the offer button, and vanilla's
    dark labels would be unreadable on its dark green), and it shows no level name or XP bar (a plugin merchant has
    no villager level; the record keeps the XP); no danger sweep on the trade screen (as a villager's). A villager
    the mod's job-site rule has already made jobless (profession, XP 0, level 1, no job site: vanilla's
    `ResetProfession` resets it within a second) counts as unemployed; with its job site it is refused (`EMPLOYED`).
  - **Crash caveat**: the villager is removed and the croupier's record written in the same tick, but the entity and
    the chunk data are saved separately. A server crash between the two saves can duplicate the jobless level-1
    villager (the villager comes back and the croupier exists) or lose it (the villager is gone, the record was not
    saved). Nothing else is at stake (a jobless level-1 villager has no trades or experience).
  - **Tests**: `CroupierEligibilityTest` (every refusal on its own), `CroupierPlacementTest` (side order, blocked
    sides, carpet/path heights, chunk border, counter top, loss, re-attach), `CroupierCodecTest` (round trip, minimal
    v1, newer and broken entries, the store keeps foreign entries), `CroupierTradeTest` (the trade, Hero of the
    Village, levels, orb, vanilla's restock rule step by step, working hours), `CroupierHiringTest` (card hand, card and badge, the stream point for point
    against the mod's arithmetic, colours), `CroupierRigTest` (feet offset = the dealer's placement, pack keys and
    texts); pack tool `CroupierPackTest` (the mod's trade JSON = the plugin's constants).

### Step 9 (part 2: chips, cashier, chip stakes; PLAN-2 C1-C4)

- **Chips** (`wager/Chips`, `wager/ChipItems`): six items, denominations 1, 5, 25, 100, 500, 1000 (white, red, green,
  black, purple, gold). Each is `minecraft:paper` with `item_model kaboofcasino:chip_<n>`, the item name translatable
  `item.kaboofcasino.chip_<n>` (German fallback "25er-Chip"; fixed in code, not from `messages_de.yml`, so chips made
  before and after a text change still stack) and the PDC id `kaboofcasino:item = kaboofcasino:chip_<n>`; identity
  only by that tag on paper (a plain paper, a display copy, a payout coin's look are never chips). Stack 64. Registered
  in `ItemCatalog` (`/casino give chip_25 <player> <n>`); no recipe makes them, only the cashier, payouts or give.
- **Two kinds of chip amounts in one `ItemAmount`**: `kaboofcasino:chips` with `count` = a chip value (what round
  records, payouts and the owed list hold) and `kaboofcasino:chip_<n>` with `count` = a number of chips (what an
  inventory gives or takes; never stored in a ledger: `Ledger` normalizes owed chips to one value). `Chips.breakdown`
  pays a value in the largest denominations first; `Chips.pay` picks chips worth exactly a value whenever possible,
  else the chips of the smallest total above it, i.e. the least change (for a total largest first: with
  denominations that divide each other this finds an exact combination whenever one exists, so the fewest chips;
  `ChipsTest` against brute force: exact when possible, least change otherwise).
- **Accounts** (`Account`, `PlayerAccount`): `countPlain` counts a material's plain stacks, a denomination's chips (by
  tag, renamed ones too) or the total chip value; `planTake` takes materials or denomination keys (pristine chips
  first); `give` gives materials, chips, or a chip value in the largest denominations, returning what did not fit as a
  chip value; `planPay`/`applyPay` pay a value with change (the change that does not fit is returned for the owed
  list).
- **Stakes** (`WagerEngine`): only chip values are bet items (`Limits.betItems = {kaboofcasino:chips}`); a `Request`
  carries the chips its stake takes (`withTake`, the bet slot's selection). `checkAll`: max stake in chip value,
  the take worth exactly the stake and only chips (else `BAD_COUNT`, a forged request), enough of every taken chip;
  the commit takes exactly the selected chips. `revise` (blackjack double, poker call) pays a chip value with
  `planPay`: exact chips or one larger chip and the change back as chips, change that does not fit owed in the same
  ledger write. Payouts are value x multiplier with the existing fair rounding on whole value units (the games'
  arithmetic is unchanged, only the unit; RTP tests now enumerate every stake 1..2000 and sparse sizes up to
  1 000 000). Settling gives chips in the largest denominations; what does not fit stays owed as a value; new rounds
  wait for owed chips as before.
- **Ledger version 3** (`LedgerCodec`): same layout, `kaboofcasino:chips` amounts allowed. A ledger is written with
  the lowest version that holds it: 3 only with a chip value in it, else 2 (a PLAYING hand) or 1. Records and owed
  items of 0.8.0 (ores) stay valid and are paid in ores (`LedgerMigrationTest`: a ledger built key by key as 0.8.0
  wrote it, with a slot round, a crash-left PLAYING blackjack hand and owed ores, read and settled by 0.9.0; the
  rewrite of an ore ledger is key by key the old layout). **Rollback to 0.8.0** after a clean stop: every round is
  settled, so a ledger holds at most owed items; ore-only ledgers stay version 1/2 and 0.8.0 delivers them; a ledger
  with owed chips is version 3, which 0.8.0's codec refuses (`CorruptLedgerException`): it logs CRITICAL, refuses
  casino actions for that player and never writes the ledger, so the chips wait untouched until 0.9.0 is back. Chip
  items themselves stay in inventories; 0.8.0 has no model for them (missing-model look), does not accept them as
  stakes (not plain) and still blocks them in recipes (casino tag).
- **Bet slots** (`StakeMenu` for wheel, Plinko, roulette, blackjack, poker, derby; `SlotMenu`; `ChipStakes`): a slot
  holds a `Chips.Selection` from the player's own chips (nothing moves until the commit): left on a chip stack adds
  that stack, right one chip, shift-left all unselected chips of that kind; never more than unselected and never past
  `bets.max-stake` (chip value per slot, or per round with `cap-per: round`); right on a slot takes the smallest chip
  away, left on the active slot clears it. A click on anything but a chip: "Hier wird mit Chips gespielt. Erze tauschst
  du an der Kasse in Chips." The slot shows the largest chip (a display copy) named "1.250 Chips" with one lore line
  per chip kind. The bettable-items book became "Mit Chips spielen" (the six chips, the cashier hint).
- **Everywhere a stake or payout is shown**: floating panels (`PanelText.stakes`: the largest chip's sprite
  `kaboofcasino:item/chip_<n>` from the items atlas and the value), the blackjack/poker round menus' stake and net
  cells (largest chip, named with the value), the derby's confirmed stake, payout coins (`Coins` with a chip's look,
  never a chip: one per stack per denomination), owed and delivery messages ("1.250 Chips"), `/casino stats` ("Chips").
- **Death**: winnings and owed chips go into the death drops as chip stacks (largest first, topping up pristine chip
  stacks), what the grave does not hold stays owed as a value; a cancelled death takes back exactly what is still
  there.
- **Dupe surface** (C1): no recipe makes chips; a chip in any crafting grid or crafter, a villager trade (paper sells
  to librarians and cartographers) or the cartography table is vetoed as for every casino item; anvil, smithing table
  and grindstone give no result with a chip in them (`ItemVetoListener.onPrepareResult`: no renamed "1000er-Chip" made
  of a 1); paper is not compostable, no fuel, no loom input; casino menus are no block inventories, so no hopper reaches
  them; display copies and coin looks carry no casino tag. Creative players can copy any stack (vanilla, admins only).
- **Config**: `bets.max-stake` is a chip value (default 2000, 1..1 000 000); `bets.items` is gone (ores are exchanged at
  the cashier, `cashier.rates`). New texts in `chips:` of `messages_de.yml` (new keys, so they merge into the live
  file); the old item texts (`bet.not-allowed`, `bet.not-plain`, `bet.max`, `gui.stake.*-lore`, `gui.slot.spin-lore`,
  `gui.slot.stake-lore`, `gui.bets.*`) are removed from the jar's file.
- **Cashier** ("Kasse", C3; package `cashier`): machine kind `CASHIER`, 2 wide x 1 deep x 2 tall, barrier carriers in
  all four cells, the clicked cell is the anchor and the second cell lies to the placer's right; both bottom cells need
  support; chunk fit, 2-block player clearance, logout spots, spawn protection, breaking rules (`machines.break`,
  `machines.breakable`, `/casino remove`), verify and chunk records are MachineService's. No rounds, never busy,
  `MachineType.isGame()` false (every switch over the type handles it; the hand's machine dialog refuses it, the
  whole-casino dialog lists only the seven games). Item `CasinoItem.CASHIER` ("Kasse"), placed by admins via
  `/casino give cashier`; `cashier.craftable: false` by default, true registers the recipe `kaboofcasino:cashier`
  (glass pane, small casino sign, glass pane over black marble bar, cash register, black marble bar; unlocks with a
  cash register; switches on `/casino reload`).
  - Look (`CashierRig`, pack `CashierPack`): seven item displays: two `black_marble_bar_end` counter pieces with their
    marble tops, the mod's `cash_register` and a `casino_chip_pile` on top, a new `kaboofcasino:cashier/screen` (the
    mod's clear glass in a brass frame) at the customer's edge with a sign like the mod's small sign (walnut, panel,
    brass, warm bulbs) and self-lit red LEDs spelling KASSE in its 7-row letters.
  - Menu (`CashierGui` art, derby felt style, "KASSE" brand, a receipt panel; 9 x 6 chest with font title, one tab per
    menu because the title is the art; one player per cashier; idle close after `gui.idle-close-seconds`; danger
    sweep, MenuSafety and range as the games): **Wechseln** (inventory clicks select plain ores that have a rate:
    left the stack, right +1, shift all; selection in 9-26, the chips you get in 30-35, rates book 45, clear 46,
    WECHSELN 48-51, value 53), **Kleingeld** (selected chips 12-17, target 21-26: 1000 ... 1, default one step below
    the largest selected chip; result 30-35), **Prämien** (36 prizes a page in 9-44 with price and "Heute noch x von y",
    pages 45/46, KAUFEN 48-51, the player's chips 53, the price 52). Receipt numbers are 10-digit picture items.
  - Atomic (`Cashbox`, on MAIN inside the click, every check and plan before the first change): **exchange** = rates,
    ledger read, counts, `planTake`; then take the ores, give the chip value largest first, one ledger write only if
    something did not fit (owed chips, WARN log; delivered by the owed timer); **change** = `planTake`, room check
    (`Account.spareSlots`, Bukkit's `addItem` rules) or refused; then take and give (value-neutral; the rest of a value
    not divisible by the target comes in the next smaller chips); **buy** = daily limit, ledger read, `planPay`, room
    for the prize and the change or refused ("Kein Platz im Inventar für die Prämie und das Wechselgeld."); then pay,
    give the prize, write the day's count. Inventory, ledger and daily counts are all in the player file: a crash rolls
    the whole transaction back or keeps it. No way from chips back to ores. Prizes are never owed (the owed list holds
    only plain items and chip values, not items with components); if the room check was ever wrong, the rest of the
    prize is dropped at the player's feet, locked to him (fix round).
  - Shop (`shop.items`, a list so a deleted entry never comes back): `item` = a casino id (built fresh, names follow
    the messages) or `id[components]` (`ItemFactory.createItemStack`, written with `ItemMeta.getAsComponentString`),
    `count` 1-64, `price` 1-100 000 000 chips, `limit` 0-1000 per player and Berlin day (0 = none; counts in the
    player PDC `kaboofcasino:shop_limits`, keyed by item and count so a new price keeps the day's count). Chips are
    never prizes, nor anything holding chips or casino items (fix round). Default: chair 64, stool 48, chip pile 32,
    small sign 96, bottles 24, coupes 24.
  - Admin (croupier's hand on a cashier, creative + `kaboofcasino.admin`): a dialog with six prizes a page (icon, price,
    limit, remove), the off-hand item as a new prize with its own price and limit, Speichern / Nebenhand hinzufügen /
    pages / Fertig (ESC applies valid inputs); one click per tick, the hand's 20 edits a second; if the list changed
    since the dialog opened nothing is written. Written with `ConfigEdits` (comments, mode and owner kept), then
    reloaded; every old -> new value logged.
  - Rates (`cashier.rates`, a list): coal, copper ingot, redstone 1; lapis 2; iron 4; gold 8; emerald 16; diamond 32;
    netherite ingot 256; each block x 9.
  - Log: every exchange, change and purchase at INFO (WARN with owed chips) and in `casino.db` table `cashier`
    (`V3__cashier.sql`: player, name, kind, machine, items in/out, chip value, owed, detail, time), written on the DB
    thread (not authoritative).
- **RoundLog** (C4 data): chip rounds are rows with `stake_item = payout_item = kaboofcasino:chips` and the values in
  `stake_count`/`payout_count`, with game, player and times as before; ore rounds of 0.8.0 keep their items.

#### Step 9 fix round (reviews of 43d9a57..ea1af1b)

- **Shop double-buy**: after a purchase `ShopMenu` clears the selection, and a second KAUFEN within 10 ticks
  (`BUY_GAP_TICKS`, server ticks) is ignored, so a fast double click buys once (bot test: one `cashier BUY` row).
- **Prize rest**: `Prizes.goods().give()` drops what did not fit at the player's feet (`World.dropItem` with owner and
  thrower = the player, no mob pickup, no despawn, no pickup delay) and logs a WARN; with the room check this never
  happens, it is the safety net. The owed list is not used: it holds plain items and chip values only, a prize can carry
  components.
- **Prize contents**: a prize whose `minecraft:container` or `minecraft:bundle_contents` holds a chip or any casino item,
  at any depth (deeper than 8 counts as such), is refused (`Prizes.smuggles`): the admin dialog shows "Chips und
  Casino-Items, auch in Kisten oder Bündeln, können keine Prämie sein." instead of the new-prize line, the add action
  refuses it with the same text, and such a `shop.items` entry is skipped with a WARN (`CashierService.shop()`, the
  filtered list per config).
- **Stale rate**: the exchange tab remembers the value its WECHSELN button showed; if the rates or the ores give another
  value at the click, nothing is exchanged ("Die Kurse oder deine Erze haben sich inzwischen geändert. Schau noch
  mal.") and the menu shows the current value.
- **Fire-proof chips**: chips carry `minecraft:damage_resistant {types: "#minecraft:is_fire"}` (no chips existed on
  live, so all chips stack alike): they float in lava and survive fire like netherite.
- **Texts**: goods are "Prämien" (tab, head, help, dialog title "Kasse – Prämien", messages), "Preis" is only the cost;
  every chip amount and count in a text goes through `util/ChipText` (dot grouping, "1 Chip" / "1.250 Chips";
  `CashierMath.format` and `ItemNames.value` delegate to it): menus, messages, admin errors, `/casino stats`; the mod's tone ("Klicke ... an", "Wähle",
  "Umschalt", "Schaffe erst Platz.", "Rest in kleineren Chips."); the derby says "<n> Chips erhalten" for chip
  payouts (`chips.derby-received`) and keeps the mod's "<n> Gegenstände erhalten" for ore records; the "Mit Chips
  spielen" book adds "Gewinne und Erstattungen kommen in möglichst großen Chips zurück."; the guide's machines spread
  has a plugin note about chips and the cashier (`guide.note-chips`).
- **Receipt dots**: `CashierView.digits` sends three more custom-model-data strings after the ten digit cells, one per
  thousands gap (`CashierView.GAPS`), "." where more digits follow; `CashierGui` draws the vanilla '.' (with its
  shadow) there (models `num_<a|b>_gap_<g>`, texture `dot`), spaced like the client's "1.287".
- **Chip readability** (`ChipPack`): the chip item definitions are `select display_context`: in `gui` a `composite` of
  the chip and a value badge ("1", "5", "25", "100", "500", "1K", 3x5 numerals in cream #F3E7BF with a #20252C
  outline, top left, models `chip_<n>_badge`); everywhere else (hand, ground, frames) the chip alone. Each
  denomination also has its own edge-insert pattern (1: four short ticks, 5: four long caps, 25: eight short pieces,
  100: two wide stripes, 500: a dotted ring, 1000: a solid ring), so chips differ without colour in hand and on the
  ground too. Deterministic as before (pack-tool tests).
- **"Inventar" label**: a light plate (gold border, cream fill) under the vanilla label in all ten felt title arts
  (slot, wheel, Plinko, roulette, blackjack bet/round, poker bet/round, derby, cashier).

### Step 10 (part 2: the leaderboard; PLAN-2 C4)

- **Data** (`board/BoardQueries`, schema 4 `V4__leaderboard.sql`): plays, not bet slots. A play is one bet commit of
  one player (a three-slot wheel or roulette spin, a derby entry on three lanes, one blackjack or poker hand); its rows
  share player, machine and creation time, which the round log also stores as `play` (`RoundLog.play`), so 0.9.x rows
  without the column group the same way. One scan of the covering index
  `rounds_board (status, stake_item, refund, player, machine, created_at, settled_at, game, stake_count,
  payout_count)` reads every play of the settled chip rounds (`stake_item = kaboofcasino:chips`; ore rounds of 0.8.0
  and older never match) with its summed stake and payout, then Java aggregates all 24 lists (`aggregate`, pure).
  Refunds count nowhere (`refund = 1`, set from the detail: derby entries that never raced "refund until the start",
  tables removed mid-hand "refund (table removed)"; V4 marks the old rows). Categories: the biggest win of one play
  (payout minus stake), the highest payout / stake of one play in hundredths (from `leaderboard.min-stake` chips staked
  on the play, default 25), net profit (sum, listed only while positive), staked chips (sum), plays ("Meiste Runden"),
  and per game the biggest win of one play ("Spielautomat: größter Gewinn", ...). Ties: the latest name,
  alphabetically. Names: the player's latest name in the log. Periods (`BoardPeriod`): this week from Monday 00:00
  Europe/Berlin (summer time included) by a play's last settle time, and all time. A snapshot of 200 000 rows took
  6.4 s with the first per-category queries, 1.0 s with the one scan (`BoardBenchmarkTest`, `KABOOF_BENCH=1`).
- **Computing** (`BoardService`): one `BoardSnapshot` (15 places per list, 10 shown) on the DB thread, at most every
  `leaderboard.refresh-seconds` (30..3600, default 30) and only when something changed (a settled round, via
  `RoundLog.afterSettle`; a hide or show that changed the mirror; `/casino reload`; the week turning). Boards and
  `/casino top` read the latest snapshot on MAIN and never wait for the database. A failed computation (an SQL error,
  the DB executor refusing the job) always releases the "computing" mark and leaves the data dirty, so the next second
  tries again.
- **Hiding**: `/casino bestenliste aus|an` (at most every 3 s per player) sets or clears the PDC flag
  `kaboofcasino:board_hidden` and its mirror, the table `board_hidden` (the DB thread cannot read player files).
  Either one hides a player: a join with the mirror saying hidden sets the PDC flag again (a kill -9 before the save
  lost it), a join with the flag writes the mirror; only "an" clears both. It takes effect at once: the boards and
  `/casino top` drop hidden players from the lists they hold (on MAIN, from a set filled from the mirror at start), and
  a computation starts right after the mirror changed. Hidden players are left out (no "anonym" line).
- **`/casino top [kategorie] [woche|gesamt]`**: the same top 10 in chat (default: biggest win, this week; words in any
  order, "Glücksrad" also with umlaut); a hidden player is told he is hidden.
- **Weekly summary** (`leaderboard.weekly-log`, default on): the first computation after Monday 00:00 logs last week's
  top 3 per category once (`board_weeks` remembers it across restarts) as one INFO line "Bestenliste der Woche
  dd.MM.–dd.MM.yyyy: ..." (English: ISO dates).
- **Boards** (machine kinds `LEADERBOARD_WALL`, `LEADERBOARD_COLUMN`; items `leaderboard_wall` "Bestenliste
  (Wandtafel)" and `leaderboard_column` "Bestenliste (Säule)", `/casino give`; recipes only with
  `leaderboard.craftable: true`: wall `GSG/WWW` gold, small casino sign, green wool; column `S/W/B` small casino sign,
  green wool, black marble bar; both unlock with a small casino sign). Machine rules as for every machine: barrier
  carriers, chunk fit, 2-block player clearance, logout spots, spawn protection, breaking and drops, verify and rescue.
  - The wall board is 3 wide x 2 tall and hangs on a wall: only a click on a block's side face places it (facing away
    from the wall, the clicked cell's neighbour is the bottom middle), and every cell needs a block with a full face
    behind it (no panes, fences or other carriers); a click on a floor says "Häng die Tafel an eine Wand ...".
  - The column is 1x1x3 on support like the slot machine: a post in the bottom cell and a 1.5-wide sign board over the
    upper two. Its rows are smaller (scale 0.32: names up to about 13 characters) and its values compact ("12,3 Tsd.",
    "123 Tsd.", "1,2 Mio.", never rounded up).
  - A wall board is never placed while a player (the placer too) is below its bottom row: one block around the row
    sideways, down to 24 blocks (`MachineService.shaft`), so it cannot become a lid over a shaft.
  - Look (`pack-tool/LeaderboardPack`): the mod's big-sign style, walnut and brass frame with warm bulbs, green felt,
    a header with red self-lit LED letters ("BESTENLISTE" / "TOP 10"; `light_emission`, no animation, no light
    blocks). Text (`BoardRig`, `BoardView`): four shared text displays on the felt (title, subtitle "Diese Woche · in
    Chips", the names column left-aligned, the values column right-aligned; each column ends in a line of spaces wider
    than any entry so the client aligns every line to one edge), ranks 1-3 in gold, silver and bronze, names cut to
    fit with "…", full brightness, no background; places 1-3 in gold #F2C65A, silver #B9C3CE, bronze #D08A4B. No player heads: a skin needs a profile lookup per offline player.
  - Pages: every category, each for this week and all time; a board turns to the next page every
    `leaderboard.cycle-seconds` (default 15, 0 = off); a right click goes to the next category (with the category
    pinned: the other period; both pinned: "Diese Tafel ist fest eingestellt."), at most twice a second per board.
    Text is sent only when the page or the data changed.
  - The croupier's hand on a board opens its pin dialog (`admin/BoardPins`): "Kategorie: automatisch" or one category,
    "Zeitraum: automatisch" or one period, the current choices marked; a click applies at once (machine record data
    `cat=<id>;period=<id>`, logged), "Fertig"/ESC closes.

### Step 11 (part 2: the Chip-Shop; PLAN-2 C6)

- **Machine** `CHIP_SHOP` (item `chip_shop` "Chip-Shop", `/casino give chip_shop`; recipe `kaboofcasino:chip_shop`
  with `chipshop.craftable` (default true since the fix round, Jack: "fit in ... Crafting"; `false` = `/casino give`
  only, like the cashier): glass pane, small casino sign, glass pane over black marble bar, chip pile, black marble
  bar; unlocks with a chip pile): the cashier's footprint and rules (2 wide x 1 deep x 2 tall, both bottom
  cells on support, chunk fit, clearance, logout spots, spawn protection, breaking, verify), no game, one player at a
  time, idle close after `gui.idle-close-seconds`. Look (`ChipShopRig`): the cashier's marble counter, the pack's
  glass display case `kaboofcasino:chipshop/display` on it and the CHIP-SHOP sign `kaboofcasino:chipshop/sign`.
- **Catalog** (`chipshop/ChipShopCatalog`, built from the item catalog, rebuilt after a reload): every deco block
  (full blocks, stairs, slabs), seats and sofa, the speaker, the TVs, the dealer station, the guide, and the machines
  without a croupier's badge with all their parts (slot machine, reel and mechanism, roulette mechanism, wheel, Plinko,
  roulette table, the four ducks, derby mechanism, duck derby); since 0.14.1 (Jack 2026-11-10: "alle neuen Rezepte und
  Items in den Shop, außer die Sachen wo du einen Croupier brauchst") also the cashier and the shop itself (Maschinen)
  and all nine leaderboards (Technik). Never the chips and the croupier's badge, hiring card and hand; the blackjack
  and poker tables only with `chipshop.tables`. The optional recipes of the cashier, the leaderboards and the shop
  price these items even while they are switched off (`ChipShopService.pricing`).
  Tabs (`ChipShopCategory`): Böden und Wände, Möbel, Bar & Tische, Licht & Schilder, Technik, Maschinen, Outfits.
- **Prices** (`ChipShopPrices`): an item's cost is the cheapest of its mod recipes, the sum of its ingredients per
  crafted item; ores at the cashier's rates (`cashier.rates`, nuggets a ninth of the ingot), the other ingredients from
  `chipshop.values` (wool 1, dye 0.5, planks 0.25, signs 0.54, torch 0.28, paper 0.5, glass 0.5, glowstone 4, piston
  6, ...), casino items by their own cost (repeated relaxation: recipe cycles such as block and slab never loop).
  Per tab (`chipshop.categories.<tab>`, Jack 2026-11-09 "Deko teurer machen"): Böden und Wände markup 1.25 but at
  least 5 a block, 4 a stair, 3 a slab; Möbel, Bar & Tische, Licht & Schilder, Technik markup 3.75 and at least 5;
  Maschinen 1.25 without a minimum. A purchase of n pieces costs cost x markup x n rounded UP as a whole (below 25 to
  whole chips, below 250 to 5, else to 25: nothing sells below its ingredients), at least min x n; a fixed price from
  `chipshop.items` is per piece and wins. The stack lore and the side panel show the total.
- **Menu** (`ChipShopMenu`, the cashier's 9x6 layout and side panel): the six tabs in slots 0-5 (the tab's item icon,
  the active one glinting), the tab's items in 9-44 (36 a page, pages 45/46), the amount toggle 47 (one piece or a
  stack), KAUFEN 48-51, the price of the choice (52, the total) and the player's chips (53). The guide (first) and
  the dealer station are listed with the machines. A click chooses an item (glint);
  KAUFEN buys the amount, Umschalt-click always a stack (the item's stack size; the shop's KAUFEN is the only commit
  button that takes Shift-clicks, `Button.commitWithShift`, click guard and busy flag still apply); a second purchase
  within 10 ticks is ignored; KAUFEN's lore names both amounts ("Umschalt: 64 Stück für X"); a purchase above
  1.000 chips needs a second click 0.5 to 3 s after the first (the button then reads "Nochmal klicken: X Chips", the
  first click buys nothing, a reflex double click confirms nothing, a new choice or amount disarms it); the amount
  toggle's art says "64", not "Stapel": the word needs 30 px of the button's 14 and everything on sale stacks to 64
  (its name says "Menge: ein Stapel"); more than 64 pieces or a total above 100.000.000 is refused ("Stapel zu teuer – kauf einzeln."); if the price, limit or offer changed since it was shown, nothing is bought ("Das Angebot hat sich
  inzwischen geändert.").
- **Purchase** (`ChipShopService.buy`): the cashier's atomic `Cashbox.buy` with the article as a one-off prize: daily
  limit (pieces, PDC `kaboofcasino:chipshop_limits`), ledger read, payment plan with change, room for the goods and the
  change, or refused before anything moves; then pay, change in chips (what does not fit is owed), the goods into the
  storage slots (a rest the room check missed is dropped at the feet, locked to the buyer). Logged at INFO and in
  `casino.db` table `chipshop` (schema 5: player, name, machine, item, quantity, unit price, price, chips paid, change,
  owed, time). The cashier's Prämien stay for the admin's special prizes; since the step 11 review the jar ships none
  (the six examples are gone, the deco is in the Chip-Shop).
- **Croupier's hand** on a Chip-Shop (`admin/ChipShopEditor`, `ChipShopDialogs`): six items a page (hidden ones too)
  with icon, current price ("automatisch" or fixed), limit and hidden; inputs price (empty = automatic), limit and
  "ausblenden"; Speichern / pages / Fertig (ESC applies valid inputs). Written into `chipshop.items` with
  `ConfigEdits` (comments, mode, owner kept), reloaded, every change logged; a stale page writes nothing.

#### Step 11 ships as 0.11.0 (deploy and rollback)

- **Schema 5** (`V5__chipshop.sql`: table `chipshop`) is applied at the first start; 0.10.0 refuses a newer schema.
  Rollback: restore `casino.db`, or `DELETE FROM schema_version WHERE version = 5;` (the table stays, unused).
- **Chip-Shops under 0.10.0**: unknown machine kind, logged CRITICAL `MACHINE_RECORD_UNREADABLE`, barriers stay (as
  the boards under 0.9.1). Items bought in the shop are ordinary casino items and stay valid.
- **Texts and config**: the sections `chipshop:` (messages) and `chipshop:` (config: craftable true, categories,
  tables false, values, items []) are new and merge in. Prices can be tuned in `chipshop.categories.<tab>`,
  `chipshop.values` or per item in `chipshop.items` (or later with the croupier's hand). `guide.note-chips` changed
  (names the Chip-Shop): delete that key from the live `messages_de.yml` before the start so the new text merges in.
- **Pack**: new models, GUI art and glyphs (appended code points); clients download the new pack once.
- **The cashier's sample Prämien**: 0.9.0 to 0.11.0 (before the review) shipped six examples in `shop.items`; the
  live config got them at the first 0.9.0 start. Delete exactly these from the live `shop.items` (keep anything else,
  Jack's own prizes): `roulettecasino:casino_chair` count 1 price 64 limit 0; `roulettecasino:casino_stool` count 1
  price 48 limit 0; `casinodeco:casino_chip_pile` count 1 price 32 limit 0; `casinodeco:small_casino_sign` count 1
  price 96 limit 0; `casinodeco:casino_bottles` count 1 price 24 limit 0; `casinodeco:champagne_coupes` count 1 price
  24 limit 0 (an entry with another price, count or limit was edited by Jack: keep it). A list key never merges, so
  the empty default does not remove them by itself.

### The casino jukebox (0.17.0; Jack 2026-11-10, not in MineVegas)

- Jack: "eine Art Jukebox, wo man die Musik einstellen kann, mit ganz vielen Musiktypen, in passender GUI und
  Block-Design". Machine `JUKEBOX` (1x1x2, item `jukebox`, recipe `kaboofcasino:jukebox` GSG/PDP/PPP with gold, a
  casino speaker, dark oak planks and a diamond, Chip-Shop tab Technik). Look: `JukeboxPack` (walnut cabinet, velvet
  grille behind brass bars, amber tubes, selector strip, a glass window in a brass frame, a stepped arch outlined in
  light with a red note) and the rig's record behind the glass (`JukeboxRig`, the track's disc, turning while it plays).
- Music: `pack-src/jukebox/tracks.json` (shipped in the jar, the pack tool reads the same file): 98 tracks in 9 genres
  (Casino Lounge, Schallplatten, Minecraft Klassik, Kreativ, Entspannt, Abenteuer, Nether, Unterwasser, Das Ende).
  Every vanilla track is a `kaboofcasino:jukebox.<id>` sound event in the casino pack pointing at the client's own file
  (`minecraft:records/...`, `minecraft:music/...`) at the vanilla volume, streamed: no music files in the pack (+25 KB).
  Exact lengths from the 26.3 asset index (Ogg granule / rate). The lounge genre plays the music pack's events.
- Playback (`JukeboxService`, `JukeboxListening`): the jukebox is the clock; a listener hears the nearest playing
  jukebox whose range (16/32/48) covers him, RECORDS, non-positional, volume by distance fixed at the start (waits
  while he walks in), joins the current track only while it is fresh or has 20 s left, switches at once when the track
  changes; vanilla background music stops, the casino speakers stay silent inside the range
  (`MusicService.silencedBy`). `/casino musik aus`, no casino pack (or no music pack for the lounge) = silence.
  `jukebox.enabled`. State in the machine record's data (`JukeboxState`: on, track, mode order/shuffle/repeat, range).
- Menu (`JukeboxMenu`, art `JukeboxGui`: the chip shop's look with nine tabs): genres, tracks (click = play), mode,
  ABSPIELEN/STOPPEN, next, range; the receipt shows the genre and what plays, so the menus of everybody who has it open
  are reopened on a change. Previews: `ChipShopArtPreviewTest` (`build/reports/gui/jukebox-*.png`). Tests: `JukeboxTest`.

### Texas Hold'em fixes (0.17.0, from the review of 0.16.0)

- A leaving player (quit, death) with nothing left to decide at the table (all-in run-out) stays in the hand: his
  result is fixed by the shoe and booked at once (`HoldemHand.projected`), every seat's record gets the projected end.
  Otherwise he folds, but first gets back the part of his bet nobody matched (never his blind); the others then face
  the highest live bet. A big blind all-in for less makes the bet the higher blind. A player in danger gets 20 s more
  once per turn instead of the danger lock on calls (a punch could force a fold). A fresh deck from a fresh seed every
  hand. Two timeouts in a row stand a player up after the hand. After every hand (and when someone leaves) the seated
  players' files are saved one per tick (Paper saves each player on his own schedule). A record settled elsewhere
  closes the seat (and voids the running hand). Hold'em seats no longer count on the leaderboards
  (`RoundLog.refund`: `holdem seat=`). Sneaking players get the solo game at a table nobody sits at. The `<chips>`
  placeholder in the bet-size tooltips is filled in.

### Texas Hold'em (0.16.0; Jack 2026-11-10, not in MineVegas; replaces the shared hand of 0.15.0)

- Jack: "Poker so bauen, dass man es im Multiplayer gegeneinander spielen kann (Texas Hold'em)". Alone at a poker
  table the mod's Casino Hold'em against the croupier runs unchanged. With another usable player within 8 blocks, or
  players already seated, a click opens the buy-in menu (`HoldemBuyInMenu`: the poker bet view with the table's terms
  instead of the paytable and HINSETZEN) and the table plays No-Limit Texas Hold'em (`HoldemTable`, `HoldemHand`):
  up to three players, the croupier only deals. `poker.holdem.*`: enabled, blinds 5/10, buy-in 100-2000, 30 s to act,
  10 s before the first hand, 6 s between hands. A table with a bias only plays solo. Between hands a seated player
  more than 16 blocks away (or in another world) stands up automatically (no blinds for an empty chair).
- `HoldemHand` (pure): blinds (heads-up the button posts the small blind and acts first before the flop), four betting
  rounds (check, call, bet, raise of at least the last full raise, all-in; an incomplete all-in raise does not reopen
  the betting for whoever acted on the last full raise), timeout = check if possible else fold, a leaving player folds
  at once. Pots by commitment level (an uncalled bet comes back as its own pot), split on a tie with the odd chips from
  the seat after the button. The pots are booked when the showdown starts (`stack`) and shown when the cards have
  turned (`shownStack`). `HoldemHandTest`: order, raises, side pots, splits, timeouts, leaving, and 4000 random hands in
  which no chip is made or lost.
- Books: one PLAYING record per seated player, stake = the buy-in, payout = the chips at the table at the start of the
  current hand; each hand's result is written into all records in one tick; GEHEN settles it (the chips come back).
  Stop, crash or a removed table mid-hand void that hand (everybody gets its start). Quit or death mid-hand: the new
  `MachineGame.playerLeaving` hook (called by `LedgerListener` before quit and death settle anything) folds the player
  (the pot may go to the last one left right then) and writes what he has behind into his record, so his chips in the
  pot stay there. When a player leaves the table the seated players' files are saved too (`Player.saveData`, slow on
  this disk, so only then), so no file can hold a win whose losses another file lacks after a crash.
- Display: hole cards lie face down at their seats (`BlackjackCards.HIDDEN`; the client gets the faces only at the
  showdown, `revealed` turns them over); the board as in the mod. The panel (`HoldemPanel`) shows the blinds, the pot or
  the seconds to the next hand, one line per seat (turn marker, head, chips, [D], last action, at the showdown the
  hand). The menu (`HoldemMenu`, art from the pack tool's `HoldemGui` on the poker round panel): board and own cards,
  pot and to-call in the left box, bet size with four small buttons, players as heads with their chips, status line,
  timer, PASSEN/GEHEN, CHECKEN/MITGEHEN, SETZEN/ERHÖHEN/ALL-IN (keys 1/2/3, F). Previews: `PokerGuiPreviewTest`
  (`build/reports/gui/holdem-*.png`). Bot: `testing/holdem-test.sh`.

### Multiplayer blackjack (step 13, 0.14.0; PLAN-2 C5, not in MineVegas) and square signs

- Alone at the table the mod's hand runs unchanged (`Hand`, `BlackjackRound`). With another usable player within 8 blocks
  the first bet opens a window of `blackjack.countdown-seconds` (15; shortens to 2 s once nobody near is without a bet),
  up to 5 players take the free spot nearest to them. Until it closes a bet is a PLAYING record paying the stake back,
  as in the roulette. A menu may only open in a click, so the round menu opens at the bet and stays open.
- `BlackjackTableRound` is `BlackjackRound` widened to seats (deal seat by seat, dealer last; seats in spot order; natural
  and departed seats have no turn; the dealer plays once if some hand can still be beaten; each seat resolved against him).
  No bias: a table with `bias != 0` only plays alone. Each seat's records hold what it gets if nobody draws another card
  (`standResults` on a copy of the shoe), rewritten for everybody when somebody draws, so quit, death, stop or crash pay
  that; the table confirms the real result per seat at the end. `TABLE_RECORD_ENDS_MILLIS` (5 min) outlasts five timeouts.
  Bot: `testing/blackjack-mp-test.sh`.
- Square signs `casinodeco:casino_sign_2x2` and `_4x4` (`DecoType.Place.SQUARE_SIGN`, the TV's wall cells; `CasinoSignPack`,
  the 4 x 4 at half size and scale 2 with `Part.lift`); recipes four small signs = 2 x 2, four 2 x 2 = 4 x 4.

- Leaderboard text (`BoardRig.LINE`) steps 10 font px per line like the client's `TextDisplayRenderer` (it was 9, so the
  row block grew over the subtitle on wall and column). Wall boards also 2x2, 3x3, 4x3, 4x4, 5x3, 5x4, 6x4
  (`LEADERBOARD_WALL_<W>X<H>`, Jack; `LeaderboardPack.WALL_SIZES`, `BoardRig.layouts`, header heights must match; even
  widths have the anchor left of the middle; more than 3 blocks = half-size model at scale 2; recipes with
  `leaderboard.craftable`: a smaller board, gold, green wool). Always 10 places: the rows use what the board offers.

### Multiplayer roulette (step 12, 0.13.0; PLAN-2 C5, not in MineVegas)

- Whoever stands alone at the table spins at once, exactly as before (one DECIDED record per slot). With another usable
  player within 8 blocks, the first bet opens a window of `roulette.countdown-seconds` (default 15, 0 = always alone),
  up to 8 players, each with his own colour and slots. The window is never extended; it shortens to 2 s once nobody
  near is still without a bet. Everybody has his own menu (`holdsClaim`), one bet per player and spin.
- Until the window closes each bet is a PLAYING record paying the stake back (`"refund until the start"`, so the boards
  skip it if it stays one), the chips have left the inventory. Quit, death, stop, crash and table removal therefore
  refund through the existing hooks. Only after the window closes is the pocket drawn (natural odds, once) and
  `WagerEngine.revise` gives each player's records their result in his own ledger write; a player whose records are gone
  drops out without touching the others. Forced odds (`win-chance >= 0`) are per colour, so such tables only spin alone.
- The table cannot be broken during the window. The chunk is held from the window's start to the end of the payout. The
  panel shows the seconds left and one line per player (`RoulettePanel.table`); the mod's panel stays for a solo spin.
  Payout coins fly to their own winner. Bot: `testing/roulette-mp-test.sh`.

### The recipe book (0.12.1)

Bukkit recipes and the native recipe book work on materials, and casino items share them (chips, machines and most deco
are paper). By material the book showed plain paper for a casino ingredient and its click filled the grid with any
paper item, usually chips: nothing came out. Vanilla recipes were hit too ("Buch" pulled three chips). `Recipes.register`
now registers every recipe touching a shared material twice: the book's under its usual key with exact ingredients (the
casino item's own stack, a plain stack for a vanilla ingredient of a shared material, a tag expanded to plain stacks) and
an undiscovered twin `<id>_any` by material for older stacks and named vanilla items by hand. The twin is load-bearing:
if `ItemVetoListener` sets a result while no recipe matches, vanilla hands the ingredients back (dupe). Vanilla shaped
and shapeless recipes of a shared material get a hidden copy `kaboofcasino:plain/<path>`; `PlayerRecipeBookClickEvent`
swaps a click on the original for it, so ghost recipe, "make all" and returning the grid stay vanilla's. The client's
craftable highlight still counts item types (holding chips can show a paper recipe as craftable); the server never
places a wrong item. Bot: `testing/recipebook-test.sh`.

### Own stairs and slabs (0.12.1; Jack 2026-11-09, not in MineVegas)

Stairs and slabs of all 13 dark oak wall blocks, both woods (`DecoType.own()`; two gilded ones since 0.12.1, the other
eleven since 0.12.3: panelling, baseboard, dado rail, cornice, pilaster, diamond panel): `casinodeco:<block>_stairs` and
`_slab`, the last 26 `DecoType`s (appended, the order of the others stays), on nether wood carriers like the velvets (crimson, warped: wood sounds and tools, they do not
burn). Pack: `DecoDisplayPack.ownStairsAndSlabs` (the mod has no files for them: placing items, 40 stair states by the
turns of its velvet stairs, three slab types, end texture on top and bottom). The blocks' side patterns are drawn for a
whole face, so smaller faces get their own (`DecoTrim`, one row and column selection per pattern, stitched from the original;
`DecoDisplayBake.faceRoles` picks `#side`, `#half` or `#quarter` per face). Recipes `DecoItems.ownRecipes`, shaped and
counted like the mod's marble ones (6 blocks to 4 stairs, 3 blocks to 6 slabs). The names have no language key, so every
client shows the German fallback.

### Casino outfits (0.12.0; Jack 2026-11-09, not in MineVegas)

- **Items** (`outfit/Outfits`): seven styles with four pieces each, ids `casinodeco:outfit_<style>_<hat|jacket|trousers|shoes>`.
  Every piece is the leather armor item (stats and durability of leather) with `item_model` `kaboofcasino:outfit/...`,
  `equippable` with the asset `kaboofcasino:outfit_<style>`, and the PDC id, so the crafting veto keeps them out of dye
  and repair recipes. Hats have `equippable` without an asset: the client then draws the item model on the head
  (checked in the 26.3 client: `HumanoidArmorLayer.shouldRender` needs an asset, otherwise `ItemDisplayContext.HEAD`).
- **Pack** (`packtool/OutfitArt`, `OutfitPack`): painted from scratch in the mod's colours. Equipment asset plus 64x32
  `humanoid` (jacket, shoes) and `humanoid_leggings` textures, 32x32 icons, hats select on `display_context`: flat icon
  everywhere, 3D model on the head. Hat coordinates follow `CustomHeadLayer.translateToHead` (see `OutfitPack`).
- **Shop**: seventh tab Outfits (`ChipShopCategory.OUTFITS`, slot 6, the old "Kategorie" plate is gone). Prices come from
  the recipes like everything else, `chipshop.categories.outfits` (markup 15, min 50). Clothing does not stack: the
  amount toggle shows 1 and the Umschalt lore is hidden for it.
- **Recipes**: one shaped recipe per piece from wool and an accent, vanilla ingredients only (so the recipe book works);
  unlocked by holding any chip. `Recipes.all()` now includes the plugin's own recipes (needed for the prices).
- **Deploy**: three new settings merge in, no live edit needed; new pack; no schema change. Rollback to 0.11.1: the
  outfit items stay ordinary leather armor with a tag, but lose their shop tab and recipes.

#### Step 10 ships as 0.10.0 (deploy and rollback)

- **Schema 4** (`V4__leaderboard.sql`: columns `play` and `refund` on `rounds`, index `rounds_board`, tables
  `board_hidden` and `board_weeks`) is applied at the first start. 0.9.1 refuses a newer schema ("casino.db schema 4 is
  newer than this plugin; refusing to start"). Rollback: stop, restore `casino.db` from the backup, or run
  `DELETE FROM schema_version WHERE version = 4;` (the new columns, index and tables stay and do no harm: 0.9.1 never
  reads them, its inserts leave `play` empty and `refund` 0).
- **Boards under 0.9.1**: their machine records are unknown kinds; 0.9.1 logs CRITICAL `MACHINE_RECORD_UNREADABLE` per
  chunk, keeps the record and leaves the barriers standing (no rig, no drop). Break them with 0.10.0 before a rollback,
  or remove the barriers by hand afterwards.
- **Texts**: new keys (`board:`, the new `guide.*`) merge into the live `messages_de.yml`. Live already has
  `command.info` and `command.usage`, which keep their old text; to get the new ones (with `/casino top` and
  `/casino bestenliste`), delete these two keys from the live file before the start. The per-game board titles
  ("Spielautomat: größter Gewinn") are new keys too.
- **Config**: the `leaderboard:` section merges in (`craftable: false`).

#### Hotfix 0.9.1 (deco stairs and slabs flickering)

- Jack saw deco stairs and slabs "swap all the time" between the casino look and the vanilla carrier. Cause: z-fighting.
  0.9.0 scaled the cover 1.002 about the block centre, so faces on the block's middle planes (a slab's top, a stair's
  step and riser) did not move at all and lay exactly on the carrier's faces (the depth test picked either, at every
  distance), and the outer faces were only 0.001 out (flicker from about 30 blocks). Not entity churn: the cover
  displays kept their UUIDs for minutes with the verifier running every 100 ticks (bot test, before and after).
- Fix then: the cover was scaled 1.032 about a pivot inside the shape, so every face lay 0.008..0.024 blocks outside
  the carrier's. **Since 0.12.1 the scale is gone** (Jack: stairs and slabs were visibly bigger than the full blocks and
  their pixels did not line up): the pack bakes exact covers (`DecoDisplayBake.cover`, the 40 stair templates and the
  3 slab types). Each face keeps its exact size and UVs and moves 0.008 blocks (1/8 px, the smallest gap 0.9.1 had) along
  its normal as a flat element; the element itself stays underneath, grown 0.004 blocks, and only shows in the hairline
  along the edges. The cover's view range is 2.0 (128 blocks, Paper's display tracking range), so the vanilla carrier shows only where
  the server sends no display at all. No vanilla block's look is changed.

## 11. Testing

Unit tests: `./gradlew build` (see the final report for counts). Step 2 adds `WheelOddsTest` / `PlinkoOddsTest`
(exact RTP by enumeration for every stake size, the forced pools at several chances by enumerating every random
number pair, 10^7-round simulations of the shipped code path), `WheelGeometryTest` / `PlinkoGeometryTest` (moving
parts inside the 3x3 footprint at every reachable angle/position), `PackKeysTest` + `WheelPackKeysTest` +
`PlinkoPackKeysTest`, `RecipesDataTest`, `StakeMenuRulesTest`, the multi-stake engine tests and `GuiPreviewTest`
(`build/reports/gui/{slot,wheel,plinko,roulette}-preview-<W>x<H>.png`). Step 3 adds `RouletteOddsTest` (36/37 on
every colour for every stake, the forced pools with 100/1800 parts, the plan always ending on the drawn pocket,
10^7-round simulations per colour), `RouletteGeometryTest`, `RoulettePackKeysTest` and the roulette chunk-fit cases
in `PlacementRulesTest`. Step 5 adds `BlackjackRtpSimulationTest` (10^7 rounds through the shipped classes, the
exact outcome counts of `tools/rtp/BlackjackRtp.java`'s replay, rigged shoes for every resolve rule),
`BlackjackStandTest` (stand prediction = table for 60 000 random hands), `ShoeRandomTest`, `BlackjackPayoutTest`,
`WagerEngineReviseTest`, `BlackjackPlacementTest` (the mod's part order, chunk fit), `BlackjackGeometryTest` /
`BlackjackModelsTest` (dealer and card transforms against an independent ModelPart/PoseStack evaluation, culling
box), `BlackjackPackKeysTest`, `BlackjackPanelTest`, `BlackjackRoundMenuTest`, `BlackjackGuiTest` and
`BlackjackGuiPreviewTest` (`build/reports/gui/blackjack-*-preview-<W>x<H>.png`). Step 6 adds `PokerRtpSimulationTest` (10^7 hands
through the shipped classes, outcome counts identical to `tools/rtp/PokerRtp.java`'s replay, rigged shoes for every
result and paytable line), `PokerCallTest` (call prediction = table for 40 000 hands, timeout fold, qualifier
boundary), `PokerPayoutTest` (every result and paytable line, signed NET lines), `PokerOwnershipTest`,
`PokerPlacementTest` (the mod's 4 x 3 part order, chunk fit), `PokerGeometryTest` (table, dealer and cards against an
independent evaluation of the mod's renderer maths, culling box, entity budget), `PokerPanelTest`, `PokerPackKeysTest`,
`PokerPackTest`, `PokerRoundMenuTest`, `PokerGuiTest` and `PokerGuiPreviewTest`
(`build/reports/gui/poker-*-preview-<W>x<H>.png`). Step 7 adds `DerbyRulesTest`, `DuckDerbyRaceTest` (golden plans of an
independent transcription, invariants), `DerbyRtpSimulationTest`, `DerbyPlacementTest`, `DerbyGeometryTest` (tub,
ducks, gates, sign against an independent evaluation of the renderer, culling box, rotation steps, traffic),
`DerbyPanelTest` (including: no result line before `resultTick` for any winner, solo and group), `DerbyPackKeysTest`,
`DerbyPackTest`, `DerbyBetMenuTest`, `DerbyGuiTest` and `DerbyGuiPreviewTest`
(`build/reports/gui/derby-*-preview-<W>x<H>.png`). Step 9 adds `ChipsTest` (denominations, breakdown, selections,
paying a value: exact against brute force, else value-neutral change), `WagerEngineChipsTest` (selected chips leave,
value checks, forged takes, owed chips as a value, double down paid with change, change that does not fit owed, ore
records next to chips, death), `LedgerMigrationTest` (a 0.8.0 ledger built key by key: read, settled in ores, owed
ores, version choice, 0.8.0's refusal of version 3), the chip rules in `StakeMenuRulesTest`, the RTP enumerations of
wheel, Plinko, roulette, poker and derby over every chip value 1..2000 and sparse values up to 1 000 000, a blackjack
chip payout test, `PackKeysTest.everyChipHasItsModelAndPanelSprite` and the pack tool's `ChipPackTest`. Chips on the
test instance: `testing/chips-test.sh slot|wheel|plinko|roulette|blackjack|poker|derby` (one round each with chips;
the wheel spins until a quarter segment, blackjack deals until a natural). The cashier: `CashboxTest` (exchange,
change, buy against `FakeAccount`: nothing changes on any refusal, owed chips, room checks, limits), `CashierMathTest`,
`ShopEntryTest`, `CashierLogTest`, `CashierGuiTest` + `CashierGuiPreviewTest`
(`build/reports/gui/cashier-*-preview-*.png`), `CashierConfigTest`, `ShopDialogsTest`, `CashierPlacementTest`, pack
tool `CashierPackTest`; `ConfigLoaderTest.theMergeKeepsTheFileMode`. Step 8 adds the croupier tests (`CroupierEligibilityTest`,
`CroupierPlacementTest`, `CroupierCodecTest`, `CroupierTradeTest`, `CroupierHiringTest`, `CroupierRigTest`, pack tool
`CroupierPackTest`), the guide tests (`GuideBookTest`, `GuideDialogsTest`, `GuideSessionsTest`, `GuideActionTest`,
pack tool `GuideDataTest`, `GuidePicturesTest`), the croupier's-hand tests (`MachineRecordCodecTest`, `MachineSettingsTest`,
`BreakRulesTest`, `ConfigEditsTest`, `AdminClickTest`, `AdminSessionsTest`, `AdminDialogsTest`, `AdminTextsTest`,
`CopyPasteTest`, `EditRateTest`), the TV/dealer-station cases in the deco tests, the unlock checks in
`RecipesDataTest`, `WagerEngineTest.settleRoundsIgnoresDuplicateAndGoneIds`/`deliveredOfCountsOnlyTheSettledRowsItems`
and `PackListenerTest.aPlayerWhoTurnedTheMusicOffGetsNoMusicRequest`. Croupier on the test instance:
`testing/croupier-test.sh setup|hint|refuse|leash|push|trade|reopen|breakA|reattach|hire|fresh|breakB|wait|hole`
(a villager converts only on Alice's click on its counter; `wait` keeps an eligible villager on a counter for 35 s
without one; `hole` needs bot Bob) and `testing/croupier-restock-test.sh` (vanilla restock timing with `/time set`,
about 6 minutes); the bot's `selecttrade <n>` picks a merchant offer, `MERCHANT` lines log the offers. Step 4 adds `FullDecoStatesTest`, `PilasterChoiceTest`, `DecoFullPackTest` (every state of the three mushroom blocks
evaluated with the client's condition semantics), the display-deco tests (`DecoBlockstateTest`: every state of the mod's
blockstates picks the same model and turn; `DecoGeometryTest`, `DecoTransformTest`, `DecoRulesTest`, `SofaLayoutTest`,
`DecoPackKeysTest`, `DecoDisplayPackTest`: 280 baked stair states against vanilla's shapes), `SeatMathTest`, the
music tests (timeline golden values, playback rules, nearest speaker, station codec, both packs' delivery and the
optional offer never kicking), `RecipeMatchTest` and the census tool's own test
(`python3 -I -B tools/census/test_mushroom_census.py`). The bot (`testing/casinobot.sh <Name> <casino mode> [music
mode]`) answers the music pack separately and logs stop-sound packets. Blackjack on the test instance
(table placed at 29 -58 6 from 29.5 -57 8.5; bet slots 29/31/33, AUSTEILEN 49, KARTE 46, HALTEN 49, DOPPELN 52;
`blackjack.bias: 100` + `/casino reload` makes winning hands likely for the death/owed cases):

```bash
$T/ptest.sh rcon "casino give blackjack_table Alice" "give Alice diamond 20" "tp Alice 29.5 -57 8.5 180 0"
$T/ptest.sh b Alice hotbar 0; $T/ptest.sh b Alice place 29 -58 6 up          # place
$T/ptest.sh bx Alice 5 OPEN place 29 -57 6 south                           # bet menu
$T/ptest.sh b Alice click 81 right; $T/ptest.sh b Alice click 49            # 1 diamond, AUSTEILEN
$T/ptest.sh b Alice click 46                                               # KARTE (52 DOPPELN, 49 HALTEN)
$T/ptest.sh rcon "save-all flush"; python3 -I testing/datsum.py Alice       # PLAYING record with its stand payout
``` Game-port delivery from outside the JVM:
`python3 -I testing/gameport-test.py <port> <sha1>` (GET/HEAD/404/405, status ping, legacy ping, garbage, a silent
client; refuses the live ports). Smoke test on the default PostMarkt test instance
(`PostMarkt/testing/ptest.sh`, README there) with the pack-aware bot of this repo:

```bash
T=../PostMarkt/testing      # the author's test harness (not public), or $PTEST_DIR
$T/ptest.sh uninstall LodServerSupport DHSupport PostMarkt   # clean instance (done 2026-11-06)
$T/ptest.sh install build/libs/KaboofCasino-0.4.0.jar
# plugins/KaboofCasino/config.yml on the instance: url-host 127.0.0.1, url-port 25572 (serve minecraft-port)
#   or serve http-port with port 25581 + bind 127.0.0.1; slot.win-chance 100 for forced wins
$T/ptest.sh start
testing/casinobot.sh Alice accept          # decline|fail|ignore for the other paths
$T/ptest.sh rcon "casino give slot_machine Alice" "give Alice diamond 64"
$T/ptest.sh b Alice hotbar 0; $T/ptest.sh b Alice place 0 -58 3 up       # place
$T/ptest.sh bx Alice 5 OPEN place 0 -57 3 north                         # open the menu
$T/ptest.sh b Alice click 82 right; $T/ptest.sh b Alice click 40         # stake 1 from hotbar 1, DREHEN
$T/ptest.sh rcon "save-all flush"; python3 -I testing/datsum.py Alice    # inventory + ledger from the .dat
testing/craft-test.sh                                                    # crafting vetoes and the reel recipe
```

Duck derby on the test instance (#1 placed at 81 -58 58 from 81.5 -57 60.5; open with `place 81 -57 58 south`; bet
slots row 1 = 9/12/14/17 for lanes 0-3 (click to select, right-click an inventory item for +1), SOLO 37, GROUP /
CONFIRM / WITHDRAW 42; `derby.win-chance: 100` or `0` + `/casino reload` force a solo win or a loss/push; helper
`testing/dk.sh open|bet|group|solo|withdraw|ledger|over`).

Poker on the test instance (table #1 placed at 90 -58 41 from 90.5 -57 44.5; bet slots 29/31/33 (click a slot to
select it, right-click an inventory item for +1), AUSTEILEN 49, MITGEHEN 46, AUSSTEIGEN 51; the decision opens 10.5 s
after the deal; `poker.bias: 100` + `/casino reload` makes winning hands likely; helper
`testing/pk.sh open|deal|call|fold|ends|ledger`):

```bash
$T/ptest.sh rcon "casino give poker_table Alice" "give Alice diamond 20" "tp Alice 90.5 -57 44.5 180 20"
$T/ptest.sh b Alice hotbar 0; $T/ptest.sh b Alice place 90 -58 41 up                 # place
$T/ptest.sh bx Alice 5 OPEN place 90 -57 42 south                                    # bet menu
$T/ptest.sh b Alice click 82 right; $T/ptest.sh b Alice click 49                      # 1 diamond, AUSTEILEN
sleep 11; $T/ptest.sh b Alice click 46                                               # MITGEHEN (51 AUSSTEIGEN)
$T/ptest.sh rcon "save-all flush"; python3 -I testing/datsum.py Alice                # PLAYING record, x3, outcome
```

## 12. Before going live

- Default `pack.serve: minecraft-port`: no port forward needed; the URL is
  `http://<public address>:25565/pack/<sha1>.zip`. Check it from outside (check-host.net HTTP check or `curl -I` from another network) before `pack.enforce: true`.
  Until then players without the pack are only refused at the machines. (`http-port` would need TCP 25580
  forwarded.)
- ProtocolLib is installed on live: the combination was tested on the test instance.
- Keep `require-resource-pack=false` and `resource-pack=` empty in `server.properties`.
- Backup + Jack's OK + restart with warning (PLAN "Ablauf pro Schritt").
- 0.17.0 (jukebox, Hold'em fixes): remove `KaboofCasino-0.16.0.jar`; `jukebox.enabled` and the `jukebox.*`,
  `poker.holdem-idle`, `-danger-time`, `-solo-hint`, `gui.holdem.min-lore`, `max-lore` keys merge in; nothing to delete. The casino pack's download cap is 450 s (the
  pack is ~6.95 MB). No migration.
- 0.16.0 (Texas Hold'em): remove `KaboofCasino-0.15.0.jar`; delete the obsolete `poker.countdown-seconds` line from the
  live `config.yml` (the `poker.holdem` block merges in) and the seven 0.15.0 keys `poker.joined`, `already`, `decide`,
  `called-wait`, `countdown`, `decide-countdown` from the live `messages_de.yml` (`poker.full` stays); the `poker.holdem-*`
  and `gui.holdem.*` keys merge in. No database migration; pack grows by ~0.3 MB (Hold'em GUI items). Rollback: the 0.15.0
  jar once nobody is seated (a clean stop settles the seats).
- 0.15.0 (multiplayer poker against the croupier, replaced by 0.16.0): `poker.countdown-seconds` and seven messages.
- 0.14.1 (shop): jar swap only.
- Download limits (step 4 fix, raised later): the casino pack has 16 slots and a 300 s cap with a 16 KiB/s
  minimum, so a 4.7 MB download needs about 16 KiB/s on average (slower clients fail it, and with enforce are kicked; a
  17th simultaneous first download of a new pack version gets 503); the music pack's 180 s needs about 124 KiB/s
  (optional). Check with `python3 -I testing/pack-slowreader-test.py 25572
  <casino_sha1> <music_sha1>` on the test instance: 503 for the 5th music GET, casino 200 + SHA ok, slow readers reset
  after ~16-20 s, music 200 afterwards.
- Step 4 ships as `KaboofCasino-0.5.0.jar` (26.7 MB: it embeds both packs): remove `KaboofCasino-0.4.0.jar`. Before the
  start: set `block-updates.disable-mushroom-block-updates: true` in `config/paper-global.yml` (Jack's OK), rerun the
  census (`python3 -I -B tools/census/mushroom_census.py`, read-only), and delete the old `command.credits` line from
  the live `messages_de.yml` so the new credits text is merged in. `config.yml` gets `music.enabled: true`. The casino
  pack grows to ~4.7 MB; the music pack (~22 MB) is offered after it, optional; clients that accepted the casino pack
  usually accept it silently (a second resource reload at login).
- Step 9 ships as `KaboofCasino-0.9.0.jar`: remove `KaboofCasino-0.8.0.jar`. Before the start, in the live
  `config.yml` delete `bets.max-stake` (the old value 64 meant items; deleted, the new default 2000 chips is merged in
  with its comment; keep a number if Jack wants another chip limit) and the `bets.items` list (gone; else one
  "unknown setting" warning per start); in the live `messages_de.yml` delete `bet.not-plain`, `bet.max`,
  `gui.slot.spin-lore`, `gui.slot.stake-lore`, `gui.stake.lore`, `gui.stake.active-lore`,
  `gui.stake.active-lore-round`, `gui.stake.play-lore`, `gui.stake.play-lore-round` and `gui.bets` (else one warning
  each). New keys merge in by themselves (`cashier.craftable`, `cashier.rates`, `shop.items` in `config.yml`;
  `chips:`, `cashier:`, `shop:` in `messages_de.yml`); the merge now keeps the files' mode, owner and group (like the
  hand's edits). The cashier is placed by an admin (`/casino give cashier`). The casino pack grows to 5,999,215 bytes
  (the 300 s hard cap then asks for 19.5 KiB/s on average; unchanged policy). Ledgers: no migration step; 0.8.0
  records and owed ores are paid in ores by 0.9.0 (tested on a crashed 0.8.0 world: an open blackjack hand and owed
  diamonds were settled at the join, the diamonds delivered once there was room). A clean stop before the deploy
  settles everything as always. **Rollback to 0.8.0**: see "Ledger version 3" above (owed chips wait untouched, chip
  items keep their tag but lose their look; cashiers' chunk records are kept, logged CRITICAL as an unknown machine
  kind, their barriers stay); `casino.db` gets schema 3 (the cashier log), which 0.8.0 refuses at
  start: put back `casino.db` from the deploy backup, or remove the version row
  (`sqlite3 casino.db "DELETE FROM schema_version WHERE version = 3"`; the extra table does not bother 0.8.0).
- Step 8 ships as `KaboofCasino-0.8.0.jar`: remove `KaboofCasino-0.7.0.jar`. Before the start delete the
  `command.credits` block (`credits: |-` and its indented lines) from the live `messages_de.yml` so the new credits text
  with the `/casino musik aus|an` hint is merged in (optionally `command.usage` too, which now names `musik`).
  `config.yml` gets `machines.breakable: true` merged in (it works on top of the live `machines.break: owner`: false
  stops every survival player); `messages_de.yml` gets the `guide:` section, `item.croupier-hiring-card`,
  `item.casino-guide`, `item.croupier-hand*` and `command.music-*`. No database migration and no ledger version;
  machine records gain the optional keys `name`/`chance`/`bias` only when an admin sets own values (a rollback to
  0.7.0 reads them but drops them when a game rewrites that record); croupiers live in the chunk PDC
  `kaboofcasino:croupiers` (0.7.0 ignores it: on a rollback croupiers are simply not shown, and a converted
  villager is not given back). The casino pack is 5,861,439 bytes (~5.59 MiB; the 300 s hard cap then asks for
  19.1 KiB/s on average; unchanged policy). Counters (`dealer_station`), TVs and the guide are craftable with the
  mod's recipes; the hiring card only comes from a croupier's trade (or `/casino give croupier_hiring_card`), the
  croupier's hand only from `/casino give croupier_hand` (creative + `kaboofcasino.admin`). A croupier appears only
  when a player brings an unemployed level-1 villager onto a free counter and right-clicks the counter (log line
  "Croupier: villager … became a croupier; clicked by <name>"); refusals are logged once per villager and reason.
  `messages_de.yml` also gets `croupier.*`, `deco.player-near` and the `guide.note-*` lines. With
  `machines.breakable: false` owners cannot mine their own machines either (only creative or `/casino remove`). The conversion releases the villager's bed and job
  site via reflection on the server's villager (`releaseAllPois`); after a Paper update that renames it, only a WARN
  is logged and the claims stay.
- Step 7 ships as `KaboofCasino-0.7.0.jar`: remove `KaboofCasino-0.6.0.jar`. The live `config.yml` gets `derby:`
  (`win-chance: -1`, `needs-support: true`, `idle-light-ticks: 4`, `idle-light-range: 16`) merged in, `messages_de.yml`
  the `item.duck-derby*`/`item.derby-duck-*`, `derby.*` and `gui.derby.*` keys; no database migration and no new
  ledger version (group entrants hold PLAYING records like poker hands, version 2 while open). The casino pack grows
  to ~5.25 MB (the 300 s hard cap then asks for 17.1 KiB/s on average; unchanged policy). The four racing ducks, the
  mechanism (24 duck orders) and the machine are craftable with the mod's recipes; `/casino give duck_derby` works too.
  Natural odds pay 100 % (games.md 7 open question 2: Jack keeps or lowers `derby.win-chance`; below 25 there is a
  house edge and no group races). A group race keeps up to 64 entrants with up to 3 records each: the live
  `rounds.max-open: 4` allows a full three-row bet plus one more stake per player.
- Step 6 ships as `KaboofCasino-0.6.0.jar`: remove `KaboofCasino-0.5.0.jar`. The live `config.yml` gets `poker:`
  (`bias: 0`, `needs-support: true`) merged in, `messages_de.yml` the `item.poker-table`, `poker.*` (including
  `poker.hand-settled`) and `gui.poker.*` keys. Delete the three lines `gui.blackjack.hit-lore`, `stand-lore` and
  `double-lore` from the live `messages_de.yml` before the start so the new texts with the key hints "(Taste 1/2/3)"
  are merged in (existing keys are never overwritten). No database migration, no new ledger version (poker hands use the PLAYING records of 0.4.0). The casino pack
  grows to ~5.0 MB, so the 300 s hard cap now asks for 16.3 KiB/s on average, slightly more than the 16 KiB/s
  minimum (unchanged values; `DownloadDeadlineTest`'s steady-download bound now follows the pack size). Tables come from
  `/casino give poker_table` or the recipe with a badge (step 8). A three-slot poker hand holds three PLAYING records, so the live `rounds.max-open: 4` allows
  one poker hand plus one more stake.
- Step 5 ships as `KaboofCasino-0.4.0.jar`: remove `KaboofCasino-0.3.0.jar`. The live `config.yml` gets `blackjack:`
  (`bias: 0`, `needs-support: true`) merged in, `messages_de.yml` the `blackjack.*`, `gui.blackjack.*` and
  `machine.chunk-border-far` keys; no database migration. Ledgers become version 2 only while a blackjack hand is
  open (0.3.0 cannot read such a ledger: a rollback is safe once no hand is open, which a clean stop guarantees).
  Tables come from `/casino give blackjack_table` or the recipe with a badge (step 8); the recipe is
  registered and unlocks with a badge. The live `rounds.max-open: 4` allows one three-slot hand plus one more stake.
- Step 3 ships as `KaboofCasino-0.3.0.jar`: remove `KaboofCasino-0.2.0.jar`. The live `config.yml` gets `roulette:`
  (natural odds, `needs-support: true`) and `machines.refuse-logout-spots: true` merged in; `casino.db` migrates to
  schema 2 (`logouts`) at the first start. Logout spots are recorded from then on: players who logged out before the
  update are protected by the rescue net only. The recipe book unlocks the table with the roulette mechanism.
- Step 2 shipped as `KaboofCasino-0.2.0.jar`: remove `KaboofCasino-0.1.0.jar` from `plugins/`. The live `config.yml`
  gets the new keys merged in: `wheel:`/`plinko:` (natural odds), `lights.max-updates-per-tick: 4` and
  `bets.cap-per: slot` (so the live cap becomes 64 per bet slot, 3 x 64 per wheel/Plinko round, like the mod; set
  `round` for 64 per round). Its `rounds.max-open: 4` stays (new installs: 8): one three-slot round plus one more
  stake fit at a time; two three-slot rounds at once are refused ("rounds.full").
