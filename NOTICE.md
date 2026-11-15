# Notices

KaboofCasino is a Paper plugin by **Jxxck**, released under the MIT License (`LICENSE`).

It is a port of another project and contains code and assets from it. The notices below belong to the plugin: keep
them when you redistribute the plugin jar, its resource packs or its source, in original or modified form.

## MineVegas Casino by byMehd

- Project: https://www.curseforge.com/minecraft/mc-mods/minevegas-casino
- Version used: 1.1.0 (Fabric, Minecraft 26.3)
- Licence: MIT with an attribution condition, Copyright (c) 2026 byMehd. Full text: `LICENSE-MineVegas.txt`
  (also at the root of the plugin jar and as `credits/LICENSE.txt` in both resource packs).

What comes from MineVegas Casino:

- **Code.** The 34 source files whose header says "Verbatim port of the MineVegas Casino class of the same name"
  (game rules, odds, layouts and animation maths in `src/main/java/de/tigges/kaboofcasino/game/**`, and
  `music/MusicTimeline.java`). The rest of the games was rebuilt for the Paper API from the mod's behaviour.
- **Assets.** Models, textures, GUI art, sounds, texts, recipes and the guide book in the resource packs. `pack-tool/`
  converts them from the mod's jar while building; the meshes in `pack-src/meshes/` were generated from the mod's
  render geometry. The mod's jar itself is not part of this repository (the build downloads it from CurseForge).

The second condition of that licence applies to this plugin and to everything built from it:

> Any modified version of the Software, and any work that includes all or part of its code or assets (a fork, a
> port, a remake), must display in its public description, on every platform where it is published, a visible link
> to the original project: https://www.curseforge.com/minecraft/mc-mods/minevegas-casino

So if you fork or republish KaboofCasino, keep `LICENSE-MineVegas.txt` and put that link into your public description.

## Lounge music by Kevin MacLeod

The optional music pack contains six recordings by Kevin MacLeod (incompetech.com), licensed under Creative Commons
Attribution 4.0 International (https://creativecommons.org/licenses/by/4.0/):

"Airport Lounge", "Backbay Lounge", "Jazz Brunch", "George Street Shuffle", "Cool Vibes", "Bossa Antigua".

They are taken unchanged from MineVegas Casino, which converted the MP3 files to Ogg Vorbis and normalised their
loudness to -20 LUFS; the compositions are unchanged. No endorsement by the composer is implied. The music pack carries
the full credits (`credits/CREDITS.md`, `credits/MUSIC-CREDITS.md` with the source link of every title) and the
licence text (`credits/LICENSE-MUSIC-CC-BY-4.0.txt`); in the game, `/casino credits` shows them.

## HikariCP

The plugin jar contains HikariCP 7.1.0 (https://github.com/brettwooldridge/HikariCP), Copyright Brett Wooldridge,
relocated to `de.tigges.kaboofcasino.libs.hikari`. Licensed under the Apache License, Version 2.0; the licence text is
in the plugin jar as `META-INF/licenses/Apache-2.0.txt` and at https://www.apache.org/licenses/LICENSE-2.0.

## Minecraft

Text inside the casino screens is drawn with glyphs of Minecraft's default font. `pack-src/vanilla/` holds the three
bitmap sheets of that font which the pack tool reads to measure and copy glyphs (see the README there); the casino
pack ships the copied glyphs. These are Mojang's and are not covered by the MIT licence of this project.

The casino jukebox plays the game's own music from each player's client; no music of the game is part of this project.

Minecraft is a trademark of Mojang AB. KaboofCasino is not an official Minecraft product and is not approved by or
associated with Mojang or Microsoft.
