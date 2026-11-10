package de.tigges.kaboofcasino.jukebox;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.machine.Recipes;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The jukebox's menu (art: the pack tool's {@code JukeboxGui}; texts at the chip shop's receipt anchors):
 * <pre>
 *  row 0     0-8 the nine genres (the shown one glints, its tab is drawn selected)
 *  rows 1-4  9-44 the genre's tracks (a disc each; the playing one glints): a click plays it from its beginning
 *  row 5     45/46 pages, 47 the mode, 48-51 ABSPIELEN / STOPPEN, 52 the next track, 53 the range
 * </pre>
 * The receipt shows the genre, a short help, what plays and the range and mode. The texts are part of the title, so
 * the jukebox reopens the menu of everybody who has it open when the music changes ({@link JukeboxController}).
 */
public final class JukeboxMenu extends GameMenu {

    static final int FIRST_TRACK = 9;
    static final int PAGE_SIZE = 36;
    static final int PREV = 45;
    static final int NEXT = 46;
    static final int MODE = 47;
    static final int[] PLAY = {48, 49, 50, 51};
    static final int PLAY_ART = 49;
    static final int SKIP = 52;
    static final int RANGE = 53;
    static final String GUI = "kaboofcasino:gui/jukebox/";
    static final String BLANK = "kaboofcasino:gui/blank";
    static final TextColor BRAND = TextColor.color(0xEACD83);
    static final TextColor CREAM = TextColor.color(0xF3E7BF);
    static final TextColor MUTED = TextColor.color(0xB5C5A6);
    /** The receipt's text width (the right panel's 105 px less its margins). */
    static final int RECEIPT_W = 90;

    private final JukeboxController controller;
    private final CasinoContext ctx;
    private final int genre;
    private final int page;

    JukeboxMenu(UUID viewer, JukeboxController controller, CasinoContext ctx, int genre, int page) {
        super(viewer, title(ctx.layout(), ctx.messages().get()::raw, controller.service().catalog(), genre,
                controller.state(), controller.track()));
        this.controller = controller;
        this.ctx = ctx;
        this.genre = genre;
        this.page = page;
    }

    int genre() {
        return genre;
    }

    int page() {
        return page;
    }

    @Override
    public MachineController controller() {
        return controller;
    }

    static String mode(Function<Msg, String> raw, JukeboxState.Mode mode) {
        return raw.apply(switch (mode) {
            case ORDER -> Msg.JUKEBOX_MODE_ORDER;
            case SHUFFLE -> Msg.JUKEBOX_MODE_SHUFFLE;
            case REPEAT -> Msg.JUKEBOX_MODE_REPEAT;
        });
    }

    /** {@code text} cut with "..." so it fits {@code width} px. */
    static String fit(GuiLayout layout, String text, int width) {
        if (layout.width(text) <= width) {
            return text;
        }
        String s = text;
        while (!s.isEmpty() && layout.width(s + "...") > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s.strip() + "...";
    }

    /** Background, the genre's tab, JUKEBOX, the genre, the help lines, what plays, range and mode. */
    public static Component title(GuiLayout layout, Function<Msg, String> raw, JukeboxCatalog catalog, int genre,
                           JukeboxState state, JukeboxCatalog.Track track) {
        TitleBuilder t = new TitleBuilder(layout);
        t.art("jukebox_bg_0");
        t.art("jukebox_bg_1");
        int g = Math.max(0, Math.min(genre, catalog.genres().size() - 1));
        t.art("jukebox_overlay_" + g);
        t.text("chipshop_brand", raw.apply(Msg.JUKEBOX_BRAND), BRAND, true, true);
        t.text("chipshop_head", catalog.genres().get(g).name(), CREAM, false, true);
        String[] help = raw.apply(Msg.JUKEBOX_HELP).split("\n", -1);
        for (int i = 0; i < Math.min(help.length, 5); i++) {
            if (!help[i].isBlank()) {
                t.text("chipshop_help_" + i, help[i].strip(), MUTED, false, false);
            }
        }
        String playing = state.playing() ? raw.apply(Msg.JUKEBOX_LABEL_PLAYING).replace("<title>", track.title())
                : raw.apply(Msg.JUKEBOX_LABEL_STOPPED);
        t.text("chipshop_label_a", fit(layout, playing, RECEIPT_W), CREAM, false, true);
        String range = raw.apply(Msg.JUKEBOX_LABEL_RANGE).replace("<range>", Integer.toString(state.range()))
                .replace("<mode>", mode(raw, state.mode()));
        t.text("chipshop_label_b", fit(layout, range, RECEIPT_W), CREAM, false, true);
        return t.build();
    }

    static String length(long millis) {
        long s = Math.round(millis / 1000.0);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private Messages messages() {
        return ctx.messages().get();
    }

    private ItemStack icon(String id, Component name, List<Component> lore) {
        ItemStack stack;
        if (id.startsWith("minecraft:")) {
            Material m = Material.matchMaterial(id);
            stack = DisplayCopies.of(m == null || !m.isItem() ? Material.MUSIC_DISC_CAT : m, 1, name, lore);
        } else {
            stack = Recipes.casinoStack(id, messages(), 1).map(i -> DisplayCopies.copyOf(i, 1, lore))
                    .orElseGet(() -> DisplayCopies.of(Material.JUKEBOX, 1, name, lore));
            stack.setData(DataComponentTypes.ITEM_NAME, name);
        }
        // the disc's own "Music Disc - C418 - cat" line would contradict the name
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .hiddenComponents(Set.of(DataComponentTypes.JUKEBOX_PLAYABLE)).build());
        return stack;
    }

    private void click(Player p) {
        p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
    }

    @Override
    public void render() {
        clear();
        Messages m = messages();
        JukeboxCatalog catalog = controller.service().catalog();
        JukeboxState state = controller.state();
        JukeboxCatalog.Track current = controller.track();
        for (int i = 0; i < catalog.genres().size() && i < 9; i++) {
            JukeboxCatalog.Genre g = catalog.genres().get(i);
            List<Component> lore = new ArrayList<>(m.lines(Msg.JUKEBOX_GENRE_LORE,
                    Messages.text("count", Integer.toString(catalog.of(g.id()).size()))));
            if (catalog.of(g.id()).stream().anyMatch(JukeboxCatalog.Track::needsMusicPack)) {
                lore.addAll(m.lines(Msg.JUKEBOX_GENRE_MUSIC_PACK));
            }
            ItemStack stack = icon(g.icon(), Component.text(g.name()), lore);
            int index = i;
            if (i == genre) {
                stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                set(i, Button.display(stack));
            } else {
                set(i, Button.of(stack, (p, c) -> {
                    click(p);
                    controller.show(p, index, 0);
                }));
            }
        }
        List<JukeboxCatalog.Track> tracks = catalog.of(catalog.genres().get(Math.max(0, Math.min(genre,
                catalog.genres().size() - 1))).id());
        int pages = Math.max(1, (tracks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        for (int k = 0; k < PAGE_SIZE; k++) {
            int index = shown * PAGE_SIZE + k;
            if (index >= tracks.size()) {
                break;
            }
            JukeboxCatalog.Track t = tracks.get(index);
            boolean now = state.playing() && t.id().equals(current.id());
            List<Component> lore = new ArrayList<>(m.lines(Msg.JUKEBOX_TRACK_LORE,
                    Messages.text("length", length(t.millis()))));
            if (now) {
                lore.addAll(0, m.lines(Msg.JUKEBOX_TRACK_PLAYING));
            }
            if (t.needsMusicPack()) {
                lore.addAll(m.lines(Msg.JUKEBOX_GENRE_MUSIC_PACK));
            }
            ItemStack stack = icon(t.icon(), Component.text(t.title()), lore);
            if (now) {
                stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            set(FIRST_TRACK + k, Button.of(stack, (p, c) -> {
                click(p);
                controller.choose(p, t.id());
            }));
        }
        if (shown > 0) {
            set(PREV, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/prev", m.render(Msg.JUKEBOX_PREV),
                    List.of()), (p, c) -> {
                        click(p);
                        controller.show(p, genre, shown - 1);
                    }));
        }
        if (shown < pages - 1) {
            set(NEXT, Button.of(DisplayCopies.model("kaboofcasino:gui/cashier/next", m.render(Msg.JUKEBOX_NEXT),
                    List.of()), (p, c) -> {
                        click(p);
                        controller.show(p, genre, shown + 1);
                    }));
        }
        set(MODE, Button.of(DisplayCopies.model(GUI + "mode_" + state.mode().id(), m.render(Msg.JUKEBOX_MODE_BUTTON,
                Messages.text("mode", mode(m::raw, state.mode()))), m.lines(Msg.JUKEBOX_MODE_LORE)), (p, c) -> {
                    click(p);
                    controller.cycleMode(p);
                }));
        boolean on = state.playing();
        for (int slot : PLAY) {
            String key = slot == PLAY_ART ? GUI + (on ? "stop" : "play") : BLANK;
            set(slot, Button.of(DisplayCopies.model(key, m.render(on ? Msg.JUKEBOX_STOP : Msg.JUKEBOX_PLAY),
                    m.lines(on ? Msg.JUKEBOX_STOP_LORE : Msg.JUKEBOX_PLAY_LORE, Messages.text("title",
                            current.title()))), (p, c) -> {
                                click(p);
                                controller.togglePlay(p);
                            }));
        }
        ItemStack skip = DisplayCopies.model(GUI + (on ? "skip_on" : "skip_off"), m.render(Msg.JUKEBOX_SKIP),
                m.lines(Msg.JUKEBOX_SKIP_LORE));
        set(SKIP, on ? Button.of(skip, (p, c) -> {
            click(p);
            controller.skip(p);
        }) : Button.display(skip));
        set(RANGE, Button.of(DisplayCopies.model(GUI + "range_" + state.range(), m.render(Msg.JUKEBOX_RANGE,
                Messages.text("range", Integer.toString(state.range()))), m.lines(Msg.JUKEBOX_RANGE_LORE)), (p, c) -> {
                    click(p);
                    controller.cycleRange(p);
                }));
    }
}
