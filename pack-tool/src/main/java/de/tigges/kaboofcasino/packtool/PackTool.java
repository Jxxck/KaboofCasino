package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the KaboofCasino resource pack.
 *
 * <pre>
 * PackTool --mod-jar &lt;jar&gt; --mod-sha1 &lt;hex&gt; --src &lt;pack-src&gt; --out &lt;dir&gt;
 * </pre>
 * Writes {@code KaboofCasino-pack.zip}, {@code KaboofCasino-pack.zip.sha1} and {@code gui-layout.json} into the
 * output directory, the casino guide for the plugin's dialogs {@code guide.json} ({@link GuideData}) and its recipe
 * pictures {@code guide-pictures.json} ({@link GuidePictures}), plus the
 * optional music pack {@code KaboofCasino-music.zip} + {@code .sha1} ({@link MusicPack};
 * a separate zip with its own hash, nothing of it is in the casino pack). Same inputs give byte-identical output
 * (sorted entries, fixed timestamps, deterministic PNG and JSON writers), so the SHA-1 only changes when the content
 * does and clients keep their cached copy.
 */
public final class PackTool {

    static final String ZIP = "KaboofCasino-pack.zip";
    static final String LAYOUT = "gui-layout.json";

    private PackTool() {
    }

    /** The result of one build. */
    record Result(byte[] zip, String sha1, byte[] layout, byte[] recipes, int files,
                  List<TexturePadding.Padded> padded, GuidePictures.Result pictures) {
    }

    public static void main(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opts.put(args[i], args[i + 1]);
        }
        Path jar = Path.of(required(opts, "--mod-jar"));
        Path src = Path.of(required(opts, "--src"));
        Path out = Path.of(required(opts, "--out"));
        Result result = build(jar, required(opts, "--mod-sha1"), src);
        ModJar modJar = ModJar.open(jar, required(opts, "--mod-sha1"));
        MusicPack.Result music = MusicPack.build(modJar);
        try {
            Files.createDirectories(out);
            Files.write(out.resolve(ZIP), result.zip());
            Files.writeString(out.resolve(ZIP + ".sha1"), result.sha1() + "\n", StandardCharsets.UTF_8);
            Files.write(out.resolve(LAYOUT), result.layout());
            Files.write(out.resolve(ModRecipes.FILE), result.recipes());
            Files.write(out.resolve(GuideData.FILE), GuideData.build(modJar));
            Files.write(out.resolve(GuidePictures.FILE), result.pictures().manifest());
            Files.write(out.resolve(MusicPack.ZIP), music.zip());
            Files.writeString(out.resolve(MusicPack.ZIP + ".sha1"), music.sha1() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write to " + out, e);
        }
        System.out.println("KaboofCasino pack: " + result.files() + " files, " + result.zip().length + " bytes, sha1 "
                + result.sha1() + (result.padded().isEmpty() ? "" : ", padded " + result.padded()));
        System.out.println("KaboofCasino guide pictures: " + result.pictures().pictures() + " recipes, "
                + result.pictures().glyphs() + " glyphs in " + result.pictures().providers() + " providers, fallbacks "
                + result.pictures().fallbacks());
        System.out.println("KaboofCasino music pack: " + music.files() + " files, " + music.zip().length
                + " bytes, sha1 " + music.sha1());
    }

    private static String required(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    /** The marker of the active empty bet field: a gold 1 px frame on a 16x16 item (item/generated). */
    static void stakeMarker(PackTree tree) {
        java.awt.image.BufferedImage img = Images.blank(16, 16);
        int gold = 0xFFFFD770;
        Images.fill(img, 0, 0, 16, 1, gold);
        Images.fill(img, 0, 15, 16, 1, gold);
        Images.fill(img, 0, 0, 1, 16, gold);
        Images.fill(img, 15, 0, 1, 16, gold);
        tree.put("assets/kaboofcasino/textures/item/gui/stake_active.png", Images.write(img));
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", Json.object("layer0", new com.google.gson.JsonPrimitive(
                "kaboofcasino:item/gui/stake_active")));
        tree.put("assets/kaboofcasino/models/gui/stake_active.json", Json.bytes(model));
        tree.put("assets/kaboofcasino/items/gui/stake_active.json",
                Json.bytes(Json.itemDefinition("kaboofcasino:gui/stake_active")));
    }

    /** Builds the whole pack in memory. */
    static Result build(Path modJar, String modSha1, Path packSrc) {
        ModJar jar = ModJar.open(modJar, modSha1);
        PackTree tree = new PackTree();
        TextGlyphs text = TextGlyphs.load(packSrc.resolve("vanilla"));
        GuiFont font = new GuiFont(tree);

        Credits.add(jar, tree);
        SlotMachinePack.add(jar, tree, packSrc);
        SlotMachineGui.add(jar, tree, font, text);
        WheelPack.add(jar, tree, packSrc);
        WheelGui.add(jar, tree, font, text);
        PlinkoPack.add(jar, tree, packSrc);
        PlinkoGui.add(jar, tree, font, text);
        RoulettePack.add(jar, tree, packSrc);
        RouletteGui.add(jar, tree, font, text);
        BlackjackPack.add(jar, tree, packSrc);
        BlackjackGui.add(jar, tree, font, text);
        PokerPack.add(jar, tree, packSrc);
        PokerGui.add(jar, tree, font, text);
        DerbyPack.add(jar, tree, packSrc);
        DerbyGui.add(jar, tree, font, text);
        CroupierPack.add(jar, tree, packSrc);
        GuidePack.add(jar, tree, packSrc);
        DecoFullPack.add(jar, tree, packSrc);
        DecoDisplayPack.add(jar, tree, packSrc);
        ChipPack.add(jar, tree, packSrc);
        SlotMachineGui.button(tree, text, "collect_low", "ABHOLEN", true, 14, 4.5, 11.5);
        // step 9: the cashier (rig model, item, menu art)
        CashierPack.add(jar, tree, packSrc);
        CashierGui.add(jar, tree, font, text);
        // step 10: the leaderboards (wall board, column post and board, the two placing items)
        LeaderboardPack.add(jar, tree, packSrc);
        stakeMarker(tree);
        List<String> events = new java.util.ArrayList<>(SlotMachinePack.SOUND_EVENTS);
        events.addAll(WheelPack.SOUND_EVENTS);
        events.addAll(PlinkoPack.SOUND_EVENTS);
        events.addAll(RoulettePack.SOUND_EVENTS);
        events.addAll(BlackjackPack.SOUND_EVENTS);
        events.addAll(PokerPack.SOUND_EVENTS);
        events.addAll(DerbyPack.SOUND_EVENTS);
        events.addAll(CroupierPack.SOUND_EVENTS);
        events.addAll(GuidePack.SOUND_EVENTS);
        events.addAll(DecoDisplayPack.SOUND_EVENTS);
        events.addAll(ChipPack.SOUND_EVENTS);
        events.addAll(LeaderboardPack.SOUND_EVENTS);
        Sounds.write(jar, tree, events);
        List<String> prefixes = new java.util.ArrayList<>(SlotMachinePack.LANG_PREFIXES);
        prefixes.addAll(WheelPack.LANG_PREFIXES);
        prefixes.addAll(PlinkoPack.LANG_PREFIXES);
        prefixes.addAll(RoulettePack.LANG_PREFIXES);
        prefixes.addAll(BlackjackPack.LANG_PREFIXES);
        prefixes.addAll(PokerPack.LANG_PREFIXES);
        prefixes.addAll(DerbyPack.LANG_PREFIXES);
        prefixes.addAll(CroupierPack.LANG_PREFIXES);
        prefixes.addAll(GuidePack.LANG_PREFIXES);
        prefixes.addAll(DecoFullPack.LANG_PREFIXES);
        prefixes.addAll(DecoDisplayPack.LANG_PREFIXES);
        prefixes.addAll(ChipPack.LANG_PREFIXES);
        prefixes.addAll(LeaderboardPack.LANG_PREFIXES);
        Lang.filter(jar, tree, "roulettecasino", prefixes);
        // the deco and speaker texts live in the mod's other two namespaces
        for (String ns : List.of("casinodeco", "casinosound")) {
            if (prefixes.stream().anyMatch(p -> p.contains("." + ns + "."))) {
                Lang.filter(jar, tree, ns, prefixes);
            }
        }
        tree.put("assets/kaboofcasino/items/gui/blank.json",
                Json.bytes(Json.object("model", Json.object("type", new com.google.gson.JsonPrimitive(
                        "minecraft:empty")))));
        text.writeTo(tree, font.textRows());
        font.write();
        List<TexturePadding.Padded> padded = TexturePadding.apply(tree);
        // the guide's recipe pictures read the final item definitions and textures
        GuidePictures.Result pictures = GuidePictures.add(jar, tree, text);

        byte[] zip = ZipOut.write(tree.files());
        JsonObject layout = font.manifest(text);
        return new Result(zip, ModJar.sha1Hex(zip), Json.bytes(layout), Json.bytes(ModRecipes.build(jar)), tree.size(),
                padded, pictures);
    }
}
