# KaboofCasino – Implementation notes (step 1: foundation + slot machine; step 2: fortune wheel + Plinko; step 3: roulette; step 5: blackjack)

Binding sources: `Casino-work/PLAN.md` (decisions), `research/games.md` (rules), `research/render.md` (look, pack,
GUI, delivery), `research/server.md` (item safety, permadeath, protection, storage). This file describes what steps 1,
2, 3 and 5 built, how the next games plug in, and the pitfalls found on the way.

Licence: MineVegas Casino is MIT with an attribution condition. Every public description must link
https://www.curseforge.com/minecraft/mc-mods/minevegas-casino (it is in `pack.mcmeta`, `credits/CREDITS.md` in the
pack, `/casino`, `/casino credits`, `plugin.yml` and the README). The mod's licence text is `LICENSE-MineVegas.txt`
at the repository root and at the root of the plugin jar, and `credits/LICENSE.txt` in the pack.

## 1. Build

```bash
cd /root/Jack_DONT_TOUCH/plugins-src/KaboofCasino
systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
  ./gradlew --no-daemon --max-workers=2 --offline build
```

- Gradle 9.8.2 (wrapper), Kotlin DSL, Shadow 9.6.1, JUnit 5.14.4, Java 25. Everything is in the offline cache.
- `-Xlint:all` with `-processing`, `-serial` (as PostMarkt) and `-classfile`: JOML (the `Transformation` types of
  paper-api) is a Java 1.2 class file with type annotations and javac flags every use of it. 0 warnings otherwise.
- The game-port pack delivery needs `io.papermc.paper.network.ChannelInitializeListener(Holder)`, which is only in the
  server jar, and netty. `paperNetworkApi` extracts `io/papermc/paper/network/**` from the server jar named by
  `casino.paperServerJar` into `build/server-api/paper-network-api.jar` (compileOnly); netty
  (`casino.nettyVersion`, 4.2.16.Final as in Paper 26.3-205) comes from `casino.serverLibraries`
  (compileOnly, and for the EmbeddedChannel tests). Nothing of either is shaded or committed. After a Paper update,
  check the netty version in `server/libraries/io/netty`.
- Outputs: `build/libs/KaboofCasino-<version>.jar` (deployable, Hikari shaded to `de.tigges.kaboofcasino.libs.hikari`,
  sqlite-jdbc from the server classpath), `build/pack/KaboofCasino-pack.zip` + `.sha1` + `gui-layout.json`.
- `buildPack` (JavaExec of the `:pack-tool` subproject) runs before `processResources`; the zip, its SHA-1 and the GUI
  manifest are embedded in the jar under `pack/`. Inputs: the mod jar pinned by path + SHA-1 in `gradle.properties`
  (read as a zip, never executed) and `pack-src/`.
- `build/reports/gui/slot-preview-<W>x<H>.png` (from `GuiPreviewTest`): the slot GUI drawn like the client draws a
  chest title on the scaled screens 480x270 (1080p, scale 4), 456x256 (1366x768) and 427x240 (720p/1440p/4K with
  "Auto"). Look at them after every GUI change.

## 2. Architecture

| Package | Content |
|---|---|
| `KaboofCasinoPlugin`, `CasinoContext` | wiring only; the context hands games the shared services |
| `config` | `config.yml` → `CasinoConfig` (validated, defaults merged into the live file), `messages_de.yml` → `Messages`/`Msg` |
| `pack` | `PackFile` (embedded zip, re-hashed), `GamePortPack` + `PackSniffer` + `HttpSniff` (HTTP on the game port), `PackHttpServer` (own port), `PackListener` (configuration phase), `PackSessions` |
| `wager` | the bet engine: `WagerEngine` (pure), `Ledger`/`RoundRecord`/`ItemAmount`, `Account` (port) + `PlayerAccount` (Bukkit), `LedgerCodec` (player PDC), `LedgerListener` (quit/death/join/timer/disable) |
| `machine` | `MachineType` (footprints), `Facing`, `MachineRecord` + `ChunkRecords` (chunk PDC), `MachineService` (index, place, remove, verify, tick), listeners for interaction, breaking, protection, chunks, item vetoes; `CasinoItem(s)`, `Recipes` (from `pack/recipes.json`), `Heartbeat` |
| `visual` | `RigMath` (display transformations), `Displays` (non-persistent spawn helpers), `Coins` (payout coin displays), `FloatingPanel` (bet/result panel) |
| `gui` | PostMarkt's selection-model framework (`Menu`, `MenuService`, `MenuListener`, `ClickRoute`, `ClickRules`, `OpenRules`, `DisplayCopies`, `DisplayPurge`), `MenuSafety` (copied) + `DangerSweep` (5-tick), `GuiLayout` + `TitleBuilder` (font-glyph titles) |
| `game` | `MachineController` (what every machine game shares: interact checks, claim, range, menus, commit, settle, heartbeat, coin target, sounds/particles at local px), `GameMenu`, `StakeMenu` (three bet slots), `PanelText`, `Odds` (the mod's forced win-chance pools), `FairPayout` (verbatim port), `CasinoRandom` (L64X128MixRandom from SecureRandom) |
| `game.slot` | `SlotGame` (rules), `SlotSpin` (spin plan), `IdleLights`, `SlotController` (runtime), `SlotRig` (entities), `SlotMenu` (GUI), `SlotPanel`, `SlotGeometry` |
| `game.wheel` | `FortuneWheelGame`, `FortuneWheelSpin`, `FortuneWheelBlur` (verbatim ports), `WheelController`, `WheelRig`, `WheelMenu`, `WheelGeometry` |
| `game.plinko` | `PlinkoGame`, `PlinkoDrop`, `PlinkoLights` (verbatim ports), `PlinkoController`, `PlinkoRig`, `PlinkoMenu` |
| `game.roulette` | `RouletteSpin` (verbatim port), `RouletteGame` (colours, payouts, forced pools), `RouletteController`, `RouletteRig`, `RouletteMenu` (9 slots, one colour), `RoulettePanel` |
| `game.blackjack` | verbatim ports (`BlackjackRound`, `BlackjackShoe` + `copy()`, `BlackjackCards`, `BlackjackBias`, `BlackjackLayout`, `BlackjackCardMotion`, `BlackjackCardGeometry`, `BlackjackTableState`, `BlackjackFootprint`, `Dealer*`), `ShoeRandom`, `BlackjackStand` (stand result on a shoe copy), `BlackjackPayout`, `BlackjackController` (hand, PLAYING records, timeout/quit/death stand), `BlackjackRig` + `BlackjackDealerRig` + `BlackjackCardRig`, `BlackjackPanel`, `BlackjackBetMenu`, `BlackjackRoundMenu`, `BlackjackSnapshot` |
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
is needed (TCP 25565 reaches the server, 25580 is not forwarded). The URL is
`http://<pack.url-host>:<pack.url-port>/pack/<sha1>.zip` (url-port = the public game port, 25565).

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
  - more than `MAX_DOWNLOADS` (8) concurrent downloads → 503 with `Retry-After: 5`.
- Before answering, vanilla's `timeout` handler (ReadTimeoutHandler) is removed so a slow download is not cut after
  30 s; nothing else of the Minecraft pipeline is touched.
- A client that sends nothing: after 5 s the sniffer passes through and vanilla's own read timeout takes over. An
  unfinished HTTP head at 5 s → 400 and close.
- One INFO line per download (`Resource pack sent over the game port (N bytes in M ms, #k)`).
- On disable, the listener is removed and every sniffer already installed becomes a pass-through (`disabled` flag),
  so a plugin reload never leaves a dead handler in the pipeline.
- ProtocolLib 5.x (live) injects its handlers in `channelActive`/at login behind the decoder; tested together on the
  test instance (pack download, join, status ping, legacy ping).
- Paper's `settings.connection-throttle` (bukkit.yml) is `0` on live and on the test instance, i.e. off. Paper applies
  the throttle only to login handshakes (intent LOGIN) after the handshake packet is decoded, so HTTP requests and
  status pings never count against it. If it is ever turned on, the pack download still does not count, but the
  login that follows the download within the throttle time would not be refused either: the download is not a
  handshake.

`pack.serve: http-port`: the JDK `HttpServer` on `pack.bind:pack.port` (25580) as before; the URL uses `pack.port`.
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
  - idle: no spin for `gui.idle-close-seconds` (120, 0 = off) closes the slot menu and frees the machine.
  The commit checks safety again. A danger close never decides a round.

## 7. Pack tool (`pack-tool/`)

`PackTool.build`: `ModJar` (pinned SHA-1; assets plus the recipe and recipe-advancement data) → `PackTree` (sorted
path → bytes; different contents for one path fail) → `Credits` → `SlotMachinePack` / `WheelPack` / `PlinkoPack`
(models, items, entity sheets into the blocks atlas, meshes, lights, drums/plates) → `SlotMachineGui` / `WheelGui` /
`PlinkoGui` (tiles, glyphs, anchors, buttons; the 3x3 screens through `MachineGuiArt`) → the 72x14 collect button
and the stake marker → `Sounds` + `Lang` (once, for all shipped games) → `TextGlyphs` (text fonts) → `GuiFont` (gui
font + manifest) → `TexturePadding` (atlas textures to multiples of 16, UVs rescaled; animated or item/generated odd textures
fail) → `ZipOut` (sorted, fixed DOS time, PNG/OGG stored, rest deflated 9). Byte-identical output for identical
inputs (`PackBuildTest`). Next to the zip it writes `gui-layout.json` (glyphs, anchors, text rows) and
`recipes.json` (`ModRecipes`: the shipped recipes and their unlock items from the mod's JSON).

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
   (blackjack, poker next): `RoundState.PLAYING` records whose payout is the timeout rule's result (stand/fold)
   computed on a copy of the shoe before every decision, rewritten with `WagerEngine.revise` (a mid-round stake goes
   into the same call); see `BlackjackController`, `BlackjackStand`, `ShoeRandom`. Poker can reuse the shoe copy
   and the PLAYING record as they are. Derby: `ENROLLED` (refund on quit/death) → `DECIDED` at the race start (a
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
  drawn by the client in its own colour.
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
- The camera turn toward the table (the mod's client turns it over 0.6 s) is impossible on a vanilla client.
- Look (`BlackjackRig`, `BlackjackDealerRig`, `BlackjackCardRig`; pack `BlackjackPack`, `BlackjackDealerModel`,
  `BlackjackCardModels`): the table is the mod's eight block models `blackjack_0..7` as item displays at the cell
  centres (view range 2.0); the dealer is eight displays (torso, head, upper arms, forearms, legs) generated from
  `BlackjackDealerModel.createLayer` with the 26.3 box-UV rules and the five texture layers composited in draw order
  (cutout rule; the per-layer 0.18 px inflation is lost), posed on the server with the verbatim
  `DealerIdleAnimation`/`DealerPlayAnimation`/`DealerKinematics` (idle phase from the anchor, all gestures and the
  turn toward the player's spot); one display per card from `BlackjackCardMotion.pose` (53 two-sided models from
  `blackjack_cards.png`, padded 350 → 352 px) plus up to seven shoe cards while shuffling. Cadence: moving cards every
  tick (interpolation 1), dealer gestures every tick while a player is within 64 blocks, idle every 4 ticks
  (interpolation 5) only within 32 blocks; unchanged transformations are not resent. Idle 17 entities, one more per
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
  stakes 27-32 (a doubled stake shows the base count plus "x2": a slot cannot show 128), timer bar 39, status/result
  40, KARTE / HALTEN / DOPPELN 52 x 17 over 45-47 / 48-50 / 51-53 (DOPPELN greyed when the hand cannot double or the
  items are missing). The mod's timing: cards appear 22 ticks after the deal, the hole card's face 8 ticks after the
  reveal, buttons locked 6 ticks after a press. It is a chest in the middle of the screen, not a side panel, and the
  inventory stays visible (inert); keyboard shortcuts do not exist in a chest, so the tooltips have only the first
  line of `*_tip`. The controller refreshes the open round menu every tick (only changed slots are sent) and closes
  it when the hand is over; a right-click by the owner reopens it during the hand.
- Recipe: the mod's (`WWW/GBG/PRP` with the Croupier's Badge), unlocked by holding a badge. Badges come from hiring a
  croupier (step 8); until then tables come from `/casino give blackjack_table` (the badge is giveable too).
- Placement (permadeath deviation, like the roulette): at least one of the eight cells needs a sturdy block below
  (`blackjack.needs-support: true`; `false` = the mod).

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
`BlackjackGuiPreviewTest` (`build/reports/gui/blackjack-*-preview-<W>x<H>.png`). Blackjack on the test instance
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
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing
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

## 12. Before going live

- Default `pack.serve: minecraft-port`: no port forward needed; the URL is `http://5.9.137.26:25565/pack/<sha1>.zip`.
  Check it from outside (check-host.net HTTP check or `curl -I` from another network) before `pack.enforce: true`.
  Until then players without the pack are only refused at the machines. (`http-port` would need TCP 25580
  forwarded.)
- ProtocolLib is installed on live: the combination was tested on the test instance.
- Keep `require-resource-pack=false` and `resource-pack=` empty in `server.properties`.
- Backup + Jack's OK + restart with warning (PLAN "Ablauf pro Schritt").
- Step 5 ships as `KaboofCasino-0.4.0.jar`: remove `KaboofCasino-0.3.0.jar`. The live `config.yml` gets `blackjack:`
  (`bias: 0`, `needs-support: true`) merged in, `messages_de.yml` the `blackjack.*`, `gui.blackjack.*` and
  `machine.chunk-border-far` keys; no database migration. Ledgers become version 2 only while a blackjack hand is
  open (0.3.0 cannot read such a ledger: a rollback is safe once no hand is open, which a clean stop guarantees).
  Tables come from `/casino give blackjack_table` until the croupier (step 8) hands out badges; the recipe is
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
