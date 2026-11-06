# KaboofCasino – Implementation notes (step 1: foundation + slot machine)

Binding sources: `Casino-work/PLAN.md` (decisions), `research/games.md` (rules), `research/render.md` (look, pack,
GUI, delivery), `research/server.md` (item safety, permadeath, protection, storage). This file describes what step 1
built, how the next six games plug in, and the pitfalls found on the way.

Licence: MineVegas Casino is MIT with an attribution condition. Every public description must link
https://www.curseforge.com/minecraft/mc-mods/minevegas-casino (it is in `pack.mcmeta`, `credits/CREDITS.md` in the
pack, `/casino`, `/casino credits`, `plugin.yml` and the README).

## 1. Build

```bash
cd /root/Jack_DONT_TOUCH/plugins-src/KaboofCasino
systemd-run --scope -q -p MemoryMax=3G -p CPUWeight=20 -- choom -n 1000 -- nice -n 15 \
  ./gradlew --no-daemon --max-workers=2 --offline build
```

- Gradle 9.8.2 (wrapper), Kotlin DSL, Shadow 9.6.1, JUnit 5.14.4, Java 25. Everything is in the offline cache.
- `-Xlint:all` with `-processing`, `-serial` (as PostMarkt) and `-classfile`: JOML (the `Transformation` types of
  paper-api) is a Java 1.2 class file with type annotations and javac flags every use of it. 0 warnings otherwise.
- Outputs: `build/libs/KaboofCasino-<version>.jar` (deployable, Hikari shaded to `de.tigges.kaboofcasino.libs.hikari`,
  sqlite-jdbc from the server classpath), `build/pack/KaboofCasino-pack.zip` + `.sha1` + `gui-layout.json`.
- `buildPack` (JavaExec of the `:pack-tool` subproject) runs before `processResources`; the zip, its SHA-1 and the GUI
  manifest are embedded in the jar under `pack/`. Inputs: the mod jar pinned by path + SHA-1 in `gradle.properties`
  (read as a zip, never executed) and `pack-src/`.
- `build/reports/gui/slot-preview.png` (from `GuiPreviewTest`): the slot GUI drawn like the client draws a chest title
  at GUI scale 4 on 1920x1080. Look at it after every GUI change.

## 2. Architecture

| Package | Content |
|---|---|
| `KaboofCasinoPlugin`, `CasinoContext` | wiring only; the context hands games the shared services |
| `config` | `config.yml` → `CasinoConfig` (validated, defaults merged into the live file), `messages_de.yml` → `Messages`/`Msg` |
| `pack` | `PackFile` (embedded zip, re-hashed), `PackHttpServer` (`GET/HEAD /pack/<sha1>.zip`), `PackListener` (configuration phase), `PackSessions` |
| `wager` | the bet engine: `WagerEngine` (pure), `Ledger`/`RoundRecord`/`ItemAmount`, `Account` (port) + `PlayerAccount` (Bukkit), `LedgerCodec` (player PDC), `LedgerListener` (quit/death/join/timer/disable) |
| `machine` | `MachineType` (footprints), `Facing`, `MachineRecord` + `ChunkRecords` (chunk PDC), `MachineService` (index, place, remove, verify, tick), listeners for interaction, breaking, protection, chunks, item vetoes; `CasinoItem(s)`, `Recipes` |
| `visual` | `RigMath` (display transformations), `Displays` (non-persistent spawn helpers) |
| `gui` | PostMarkt's selection-model framework (`Menu`, `MenuService`, `MenuListener`, `ClickRoute`, `ClickRules`, `OpenRules`, `DisplayCopies`, `DisplayPurge`), `MenuSafety` (copied) + `DangerSweep` (5-tick), `GuiLayout` + `TitleBuilder` (font-glyph titles) |
| `game` | `Odds` (the mod's forced win-chance pools), `CasinoRandom` (L64X128MixRandom from SecureRandom) |
| `game.slot` | `SlotGame` (rules), `SlotSpin` (spin plan), `IdleLights`, `SlotController` (runtime), `SlotRig` (entities), `SlotMenu` (GUI), `SlotPanel`, `SlotGeometry` |
| `storage` | SQLite: `Database` (1 connection, 1 thread, migrations), `RoundLog` (RoundSink + stats), `MachineIndex` |
| `command` | `/casino` (Brigadier `BasicCommand`) |
| `pack-tool/` | builds the pack (see 6) |

Threads: everything Bukkit on MAIN; JDBC only on `KaboofCasino-DB`; the HTTP server on `casino-pack-http-*`; the pack
send on Paper's configuration threads (blocking there is intended).

Stores (server.md 7):

| Data | Where | Authority |
|---|---|---|
| open rounds, owed items | player PDC `kaboofcasino:ledger` | **the only money store**, saved with the inventory |
| machines | chunk PDC `kaboofcasino:machines` of the anchor chunk | saved with the barrier blocks |
| rig entities | nowhere (non-persistent, rebuilt at chunk load) | – |
| claims, spin runtime, lights | RAM | rebuilt/ended on restart; money unaffected |
| round log, machine index, stats | `plugins/KaboofCasino/casino.db` | never authoritative |

Enable order: config → pack (hash check) → GUI layout → DB (migrations, `VOID_CRASH` sweep of OPEN rows older than
1 h, machine numbers) → engine → menus/safety → machines → listeners → recipes → command → HTTP server → already
loaded chunks → 1-tick timer (machines, break timers, sweep every `safety.sweep-ticks`, ledger every
`owed.delivery-ticks`). Disable order: close menus → settle every online player (STOP) → remove rigs → recipes →
HTTP → DB. Paper saves the players after `onDisable` (server.md F8).

## 3. The bet engine (PLAN decision 2)

- **Commit** (`WagerEngine.commit`, one tick): checks (owed empty, open rounds < `rounds.max-open`, item in
  `bets.items`, 1..`bets.max-stake`, enough *plain* items in storage slots 0-35), then the take (one take per slot,
  clone + `setItem`) and the ledger write with the round record. The game decides the outcome **before** the commit,
  so the record holds the final payout (`RoundState.DECIDED`).
- **Settle** (one tick): record out + payout into `owed` in one write, then `deliverOwed` gives what fits and keeps the
  rest owed. Settling a missing record is a no-op, so every path may call it.
- **Owed**: delivered every `owed.delivery-ticks` and by `/casino abholen` / the ABHOLEN button; while anything is
  owed, commits are refused (`OWED_PENDING`). Nothing ever goes on the ground; payout "coins" are ItemDisplays.
- **Quit/kick/timeout** (`PlayerQuitEvent` LOWEST, before the quit save): settle all (QUIT). Dead players are skipped.
- **Death** (`PlayerDeathEvent` LOW, before DeathBan's HIGH grave): rounds + owed into `getDrops()`, ledger cleared;
  MONITOR puts them back if the event ended cancelled; keepInventory → settle into the inventory.
- **Crash**: the ledger and the inventory roll back together. A record still in the file is settled at the next join
  (JOIN_RECOVERY), or after the respawn when the player joins dead (revive).
- **Stop/disable**: settle all (STOP).
- **Watchdog**: records whose `ends` is more than 10 s past and that no loaded machine owns
  (`MachineGame.ownsRound`) are settled (TIMEOUT): chunk unloaded mid-round, plugin reload.
- An unreadable ledger is never guessed: CRITICAL log, nothing changes, the casino is blocked for that player.
- Model check: `WagerEngineTest.everyInterleavingOfSaveCrashAndSettleIsConsistent` runs all 6^5 orders of commit,
  save, settle, crash, quit, join and checks conservation and at-most-once payment.

## 4. Machines (PLAN decision 4)

- Footprint: `MachineType.carriers(anchor, facing)` (offsets rotate with the facing). Barriers are the carriers: collision,
  clicks, immunity. Positions are `util.Pos` (world key + three ints), never `Block#getBlockKey` (Y 1023 collides).
- Placement (`MachineService.place`): replaceable + in height + world border + no living entity, solid-centre support,
  game rules (slot: the mod's spacing rule `crowded`), spawn protection for non-ops; barriers set (water kept as
  waterlogged), then a synthetic `BlockPlaceEvent` (reverted if cancelled), record into the chunk PDC, index row,
  rig spawned, one item taken.
- Breaking: `BlockDamageEvent` starts the plugin's timer (oak-plank speed, hardness 3.0, `BreakProgress`), cracks via
  `sendBlockDamage` on all carriers, `BlockDamageAbortEvent`/distance/quit stop it; only placer + ops
  (`machines.break: owner|anyone`); refused while a round runs. Creative (placer/op): instant, no drop.
  `/casino remove`: admin removal (settles, no drop).
- Protection: explosions (block lists filtered), pistons (moved blocks or any of the piston's 6 neighbours),
  fluids/buckets into carriers, fire, `EntityChangeBlockEvent`. `/fill`, `/setblock`, structures, Litematica and the
  piston exploits arrive without events: `MachineService.verify` checks every carrier every `machines.verify-ticks`,
  at chunk load and at every interaction; a missing carrier removes the machine (remaining barriers to air, no drop).
- Rigs: all entities at the anchor's bottom centre, `setPersistent(false)` inside the spawn consumer, tagged
  `kaboofcasino:part`; `checkRig` respawns after `/kill`. `EntitiesLoadEvent` removes any tagged entity from disk.
- Item identity is only the PDC tag `kaboofcasino:item`. Casino items are paper: `ItemVetoListener` blocks them in
  non-casino recipes (grid and crafter), merchant purchases and the cartography table; casino recipes use
  `MaterialChoice(PAPER)` and check every paper's tag (`Recipes.papersValid`), so later item-component changes never
  break crafting.

## 5. Display maths and animation (render.md 2-3)

`RigMath.part(facing, pivot, Q)`: the client draws an ItemDisplay (transform NONE) as
`entity + T + L·R180·(m/16 − 0.5)`. With `T = Rf·pivot` and `L = Rf·Q·R180` a model whose centre (8,8,8) is the
pivot lands exactly where the mod's `SlotMachineLayout.toBlock` puts it (`RigMathTest` checks all facings).
`Rf = rotationY(−turns·90°)` (the mod's renderer turn).

Slot specifics:
- Body: the mod's three block models via generated item definitions `roulettecasino:block/slot_machine_*`.
- Reels: 9 generated full drums (`kaboofcasino:slot/drum_<reel>_<blur>`, 48 facets, texture fixed at turn 0); the
  display turns them about X by `turn × 45°`; blur strip swapped by speed (< 0.35, < 0.62, else). Updates every 2
  ticks, keyframe 2 ticks ahead, interpolation 3.
- Lever: converted arm + ball meshes, pivot at the model centre; every tick while it moves.
- Lights: lamp mesh + 15 bulbs in one model, faces `tintindex 0..15`, `light_emission 15`; colours via
  `custom_model_data.colors` (lamp `gray(0.3+0.7l)`, bulbs `gray(0.28+0.72b)`). During a spin every 2 ticks, then the
  mod's 14-tick blend; idle chase every `slot.idle-light-ticks` only with a player within `slot.idle-light-range`.
- Drum flash (mod: tint 1.0/0.62): display brightness 15/9.
- Panel: one TextDisplay at +3.3 (view range 10 blocks, scale 0.44): head + name, EINSATZ, sprite × count; from the
  result on the three symbol glyphs and the result line.
- Sounds/particles: the mod's cue list and positions (`SlotGeometry`, `Facing.toBlock`).

## 6. GUI (PLAN decision 6, render.md 5)

- A 9×6 chest; its title is the art. `TitleBuilder` keeps a pen (window x, starts at 8) and emits space characters of
  the `kaboofcasino:gui` font (U+F801.. = −2^i, U+F821.. = +2^i) to reach absolute positions.
- Art glyphs: a glyph whose top-left should be at window (x, y) has `ascent = 13 − y` (title drawn at y 6, glyph top
  = `6 + 7 − ascent`), ≤ 256×256 px, advance = rightmost opaque column + 1. The pack tool writes all of this into
  `gui-layout.json`; the plugin never hard-codes chars or advances.
- Text: one font per window row y ∈ [−47, 232] (`kaboofcasino:t_<y>`, `t_m12` for −12) over one compact glyph sheet
  with 9×64 cells (copied vanilla glyphs, baseline row 11, `ascent = 17 − y`). Needed because a bitmap provider must
  have `ascent ≤ height`: plain vanilla fonts cannot draw above y 5, and the mod's headlines are above the window.
  `GuiLayout.width` uses the same advances as the client.
- The slot screen (`SlotMachineGui` in the pack tool, `SlotMenu.title` in the plugin): mod art drawn with its
  inventory under the vanilla inventory; re-composed so it fits 270 px at GUI scale 4 (12 felt rows cut between bet
  slot and button), the bet slot moved 1 px onto grid slot (4,3), the 8 px gap between the panels removed. Reel faces
  of the last stops are title glyphs (the menu is never open during a spin); stake, bettable-items book and the
  DREHEN/ABHOLEN button are display items (button = `oversized_in_gui` model over slots 39-41).
- Selection model: bottom clicks select (left: that stack, right: +1, shift-left: all, capped), the stake tile undoes,
  DREHEN commits (commit button: LEFT/RIGHT only, click guard, busy flag). No real item ever sits in a slot.
- Safety: menus open only in the interaction tick, never for creative/spectator/dead/sleeping/fake players, never in
  danger. `MenuSafety` (PostMarkt copy) closes on damage/targeting; `DangerSweep` every 5 ticks closes on air, fire,
  lava, frost, falling/gliding, void, monsters with line of sight (`safety.monster-radius`), primed TNT/fusing creeper
  (`safety.tnt-radius`), withers, the wither effect, or when the menu is no longer valid (machine gone, spinning,
  out of range = interaction range + 4.5). The commit checks safety again. A danger close never decides a round.

## 7. Pack tool (`pack-tool/`)

`PackTool.build`: `ModJar` (pinned SHA-1) → `PackTree` (sorted path → bytes; conflicting writes fail) →
`Credits` → `SlotMachinePack` (models, items, entity sheet into the blocks atlas, meshes, lights, drums, sounds,
lang) → `SlotMachineGui` (tiles, reel/symbol glyphs, buttons) → `TextGlyphs` (text fonts) → `GuiFont` (gui font +
manifest) → `TexturePadding` (atlas textures to multiples of 16, UVs rescaled; animated or item/generated odd textures
fail) → `ZipOut` (sorted, fixed DOS time, PNG/OGG stored, rest deflated 9). Byte-identical output for identical
inputs (`PackBuildTest`).

`pack-src/`: `meshes/slot_*.json` (converted by `tools/meshes/convert-slot.sh` with the research's `mesh2json.py`,
which re-bakes every quad with a port of the 26.3 FaceBakery: 0 mismatches), `vanilla/` (default-font bitmaps and
provider definitions from the 26.3 client jar, see its README).

## 8. Extension points for the next games

1. `MachineType`: new constant with its footprint (offsets rotate with the facing).
2. `CasinoItem`: the placing item (+ intermediates), `Msg` names, `Recipes` (paper rule per recipe).
3. A `MachineGame` implementation (one per machine; `spawn/despawn/tick/busy/interact/removed/ownsRound/checkRig`);
   register it in the `machines.games(...)` switch in `KaboofCasinoPlugin`.
4. Rounds go through `WagerEngine`. Instant games (wheel, Plinko, roulette): decide → `commit` → animate →
   `settle(NORMAL)` at the payout tick, exactly like `SlotController`. Blackjack/poker need `RoundState.PLAYING` with the
   card order and decisions in the record and the mod's timeout rule (stand/fold) in the settlement of QUIT/STOP/
   JOIN_RECOVERY/TIMEOUT; double down = a second take + record update in one tick. Derby: `ENROLLED` (refund on
   quit/death) → `DECIDED` at the race start. Bump `LedgerCodec.VERSION` and keep reading v1.
5. Pack: a `<Game>Pack` + `<Game>Gui` class in the pack tool; GUI art glyphs go through `GuiFont.art/grid` with their
   window position, texts are free (`TitleBuilder.text`). Check `slot-preview.png`-style previews (copy
   `GuiPreviewTest`). Keep sounds/lang filtered to what ships.
6. Exchange rules, `win_chance` per machine, the croupier's hand, names: not built; `slot.win-chance` is global.
   `Odds` already ports the mod's pools for all games.

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

## 10. Deviations from the mod (step 1)

- Bets are a selection (click items in the own inventory) instead of dragging a stack into the bet slot; nothing
  stays in the machine when the menu closes (the mod keeps stakes in the block entity).
- One player per machine is enforced at menu open ("<name> spielt gerade …"); the mod lets others open and blocks
  their clicks.
- Winnings go into the inventory (overflow: owed list) at `resultTick + 12`; the mod ejects owner-locked item
  entities one stack every 1-3 ticks. The ejection pace, sound and the flying stacks are kept as ItemDisplays.
- Quit/stop mid-spin settles at once, crash → next join, death → grave (the mod finishes the spin in the world and
  ejects items, which despawn if the owner is away).
- Break while spinning is refused (the mod pays the drawn result at once).
- No exchange rules, no per-machine `win_chance`, no croupier's hand, no machine labels (creative + hand only in the
  mod), no "breakable" world flag (owner/op rule instead), no light emission of the machine blocks (barriers emit none).
- The machine's collision is the 1×3 barrier column (the mod's model is 1.5 blocks wide).
- Reel shading (the mod's per-vertex `0.42 + 0.58·cos³·²`) is replaced by item lighting on a full drum; the drum
  flash uses display brightness 15/9.
- Idle lights are server-driven tints (every 4 ticks near players); all machines use the mod's per-position clock.
- Stake cap 64 per round (PLAN decision 1); the mod has none.
- Own RNG (L64X128MixRandom) instead of the level random; same distributions (RTP 499/512 exact, simulation 10^7).
- GUI: payout panel 8 px further left, 12 felt rows removed, the mod's hover lines on paytable rows are missing, the
  "Bettable items" list is a book item tooltip, the SPIN button has no hover state, labels are German literals from
  de_de.json, the vanilla "Inventar" label is drawn by the client in its own colour.

## 11. Testing

Unit tests: `./gradlew build` (see the final report for counts). Smoke test on the default PostMarkt test instance
(`PostMarkt/testing/ptest.sh`, README there) with the pack-aware bot of this repo:

```bash
T=/root/Jack_DONT_TOUCH/plugins-src/PostMarkt/testing
$T/ptest.sh uninstall LodServerSupport DHSupport PostMarkt   # clean instance (done 2026-11-06)
$T/ptest.sh install build/libs/KaboofCasino-0.1.0.jar
# plugins/KaboofCasino/config.yml on the instance: url-host 127.0.0.1, port 25581, bind 127.0.0.1, win-chance 100
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

- Jack forwards TCP 25580 (public 5.9.137.26:25580 → container 25580); check from outside (check-host.net) before
  `pack.enforce: true`. Until then players without the pack are only refused at the machines.
- Keep `require-resource-pack=false` and `resource-pack=` empty in `server.properties`.
- Backup + Jack's OK + restart with warning (PLAN "Ablauf pro Schritt").
