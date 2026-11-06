# Vanilla font data (Minecraft 26.3 client jar, `assets/minecraft/textures/font/*.png` and `font/include/default.json`)

The pack tool needs the bitmap fonts of the default font for two things:

1. **Text widths.** The plugin centres and right-aligns German text inside GUI titles; it needs the exact advance of
   every glyph, computed like the client does (`BitmapProvider`: rightmost column with alpha != 0, plus 1).
2. **Shifted text fonts.** GUI text is placed at a window row y with a font whose bitmap `ascent` is `17 - y`. A
   bitmap provider needs `ascent <= height`, so the tool copies every glyph the casino uses (from `ascii.png`,
   `accented.png` and `nonlatin_european.png`) into its own sheet of 9 px wide cells, baseline at row 11, transparent
   below, and ships that sheet in the cell heights the used rows need (`textures/font/text_h16.png` for y >= 1,
   `text_h32.png` for y >= -15, `text_h64.png` for y >= -47). Only the rows the GUIs declare as anchors get a font
   (`font/t_<y>.json`, `t_m<y>` for negative y); the vanilla textures themselves are not shipped.

`fonts.json` lists the three bitmap providers in client order (first provider that has a character wins).
