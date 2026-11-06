# KaboofCasino – Implementation notes (step 1: foundation + slot machine; step 2: fortune wheel + Plinko)

Binding sources: `Casino-work/PLAN.md` (decisions), `research/games.md` (rules), `research/render.md` (look, pack,
GUI, delivery), `research/server.md` (item safety, permadeath, protection, storage). This file describes what steps 1
and 2 built, how the next games plug in, and the pitfalls found on the way.

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
4. Rounds go through `WagerEngine.commitAll` via `MachineController.commit`. Instant games (roulette next): decide →
   commit → animate → `settleRound(NORMAL)` per record at the mod's payout tick, exactly like the wheel. Blackjack/poker need `RoundState.PLAYING` with the
   card order and decisions in the record and the mod's timeout rule (stand/fold) in the settlement of QUIT/STOP/
   JOIN_RECOVERY/TIMEOUT; double down = a second take + record update in one tick. Derby: `ENROLLED` (refund on
   quit/death) → `DECIDED` at the race start. Bump `LedgerCodec.VERSION` and keep reading v1.
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
| Step 2: the wheel pennant at the mod's 36 degree clamp pokes 0.08 px above the machine. | The display clamps at 32 degrees (`WheelGeometryTest`). |

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

## 11. Testing

Unit tests: `./gradlew build` (see the final report for counts). Step 2 adds `WheelOddsTest` / `PlinkoOddsTest`
(exact RTP by enumeration for every stake size, the forced pools at several chances by enumerating every random
number pair, 10^7-round simulations of the shipped code path), `WheelGeometryTest` / `PlinkoGeometryTest` (moving
parts inside the 3x3 footprint at every reachable angle/position), `PackKeysTest` + `WheelPackKeysTest` +
`PlinkoPackKeysTest`, `RecipesDataTest`, `StakeMenuRulesTest`, the multi-stake engine tests and `GuiPreviewTest`
(`build/reports/gui/{slot,wheel,plinko}-preview-<W>x<H>.png`). Game-port delivery from outside the JVM:
`python3 -I testing/gameport-test.py <port> <sha1>` (GET/HEAD/404/405, status ping, legacy ping, garbage, a silent
client; refuses the live ports). Smoke test on the default PostMarkt test instance
(`PostMarkt/testing/ptest.sh`, README there) with the pack-aware bot of this repo:

```bash
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing
$T/ptest.sh uninstall LodServerSupport DHSupport PostMarkt   # clean instance (done 2026-11-06)
$T/ptest.sh install build/libs/KaboofCasino-0.2.0.jar
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
- Step 2 ships as `KaboofCasino-0.2.0.jar`: remove `KaboofCasino-0.1.0.jar` from `plugins/`. The live `config.yml`
  gets the new keys merged in: `wheel:`/`plinko:` (natural odds), `lights.max-updates-per-tick: 4` and
  `bets.cap-per: slot` (so the live cap becomes 64 per bet slot, 3 x 64 per wheel/Plinko round, like the mod; set
  `round` for 64 per round). Its `rounds.max-open: 4` stays (new installs: 8): one three-slot round plus one more
  stake fit at a time; two three-slot rounds at once are refused ("rounds.full").
