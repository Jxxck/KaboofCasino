# Vanilla font data (Minecraft 26.3 client jar, `assets/minecraft/textures/font/*.png` and `font/include/default.json`)

The pack tool needs the bitmap fonts of the default font for two things:

1. **Text widths.** The plugin centres and right-aligns German text inside GUI titles; it needs the exact advance of
   every glyph, computed like the client does (`BitmapProvider`: rightmost column with alpha != 0, plus 1).
2. **Shifted text fonts.** GUI text is placed with fonts that reuse these textures with another `ascent`. A bitmap
   provider needs `ascent <= height`, so text **above** y = 5 of the chest window (the mod's headlines) needs taller
   glyph cells: the tool ships one padded copy of `ascii.png` (glyphs on top, transparent rows below). All other text
   fonts reference the vanilla textures by id; they are not shipped.

`fonts.json` lists the three bitmap providers in client order (first provider that has a character wins).
