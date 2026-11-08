package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A leaderboard's look (step 10): the pack's board models as item displays on the barrier carriers, and four text
 * displays on the felt (title, subtitle, the names column, the values column), shared by every viewer. Text changes
 * only when the page or the data changes (at most every few seconds); no animation, no light updates. Coordinates in
 * the machine's south frame from the anchor's bottom centre (x to the viewer's right, z towards him). Thread: MAIN.
 */
final class BoardRig {

    /** One model display: key and where its centre (8, 8, 8) goes. */
    record Part(String model, double x, double y, double z) {
    }

    /**
     * The text layout of one board kind (blocks, south frame): the felt's text area (x from -halfWidth to +halfWidth,
     * the front plane z), where the title, subtitle and row block sit (bottom of their line / top of the rows) and
     * their scales, the pixel widths of the two columns and of a name.
     */
    record Layout(List<Part> parts, float width, float height, double halfWidth, double z, double titleY,
                  float titleScale, double subtitleY, float subtitleScale, double rowsTop, float rowScale, int leftPx,
                  int rightPx, int namePx) {
    }

    /** One text line at scale 1: 9 font pixels of 1/40 block (vanilla text displays). */
    static final double LINE = 9 / 40.0;
    static final double PX = 1 / 40.0;
    /** Rows on a board, plus the spacer line at the bottom of each column. */
    static final int ROWS = 10;

    static final Layout WALL = new Layout(List.of(new Part("kaboofcasino:leaderboard/wall", 0, 0.5, 0)), 3.2f, 2.2f,
            1.30, -0.36, 1.40, 0.6f, 1.30, 0.4f, 1.28, 0.48f, 130, 80, 106);
    /**
     * The column: the post in cell 0, the sign board (model y -8..24 around its origin) from the bottom of cell 1 to
     * the top of cell 2, so its origin sits 2.0 above the anchor; felt text area y 1.125..2.5625, front at z 0.125.
     */
    static final Layout COLUMN = new Layout(List.of(new Part("kaboofcasino:leaderboard/column_post", 0, 0.5, 0),
            new Part("kaboofcasino:leaderboard/column_board", 0, 2.0, 0)), 1.6f, 3.2f, 0.58, 0.135, 2.45, 0.42f,
            2.37, 0.3f, 2.35, 0.36f, 80, 50, 56);

    static Layout layout(MachineType type) {
        return type == MachineType.LEADERBOARD_WALL ? WALL : COLUMN;
    }

    private final Machine machine;
    private final Layout layout;
    private final List<ItemDisplay> models = new ArrayList<>();
    private TextDisplay title;
    private TextDisplay subtitle;
    private TextDisplay names;
    private TextDisplay values;
    private BoardView.Page shown;

    BoardRig(Machine machine) {
        this.machine = machine;
        this.layout = layout(machine.type());
    }

    Layout layout() {
        return layout;
    }

    /** Every model key the rigs use (pack test). */
    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (Layout l : List.of(WALL, COLUMN)) {
            l.parts().forEach(p -> out.add(p.model()));
        }
        return out;
    }

    void spawn(World world) {
        despawn();
        Location at = new Location(world, machine.anchor().x() + 0.5, machine.anchor().y(),
                machine.anchor().z() + 0.5);
        for (Part p : layout.parts()) {
            models.add(Displays.item(world, at, machine.id(), Displays.model(p.model()),
                    RigMath.part(machine.facing(), new Vector3f((float) p.x(), (float) p.y(), (float) p.z())),
                    1.0f, d -> {
                        d.setDisplayWidth(layout.width());
                        d.setDisplayHeight(layout.height());
                    }));
        }
        title = text(world, at, TextDisplay.TextAlignment.CENTER);
        subtitle = text(world, at, TextDisplay.TextAlignment.CENTER);
        names = text(world, at, TextDisplay.TextAlignment.LEFT);
        values = text(world, at, TextDisplay.TextAlignment.RIGHT);
        shown = null;
    }

    private TextDisplay text(World world, Location at, TextDisplay.TextAlignment alignment) {
        return Displays.text(world, at, machine.id(), d -> {
            d.setBillboard(Display.Billboard.FIXED);
            d.setAlignment(alignment);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setLineWidth(4000);
            d.setViewRange(1.0f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setDisplayWidth(layout.width());
            d.setDisplayHeight(layout.height());
            d.text(Component.empty());
        });
    }

    void despawn() {
        models.forEach(Displays::remove);
        models.clear();
        for (TextDisplay d : new TextDisplay[] {title, subtitle, names, values}) {
            Displays.remove(d);
        }
        title = null;
        subtitle = null;
        names = null;
        values = null;
        shown = null;
    }

    boolean spawned() {
        return title != null;
    }

    /** True if every display still exists ({@code /kill}, other plugins). */
    boolean intact() {
        return spawned() && models.size() == layout.parts().size() && models.stream().allMatch(ItemDisplay::isValid)
                && title.isValid() && subtitle.isValid() && names.isValid() && values.isValid();
    }

    /** Shows {@code page}; nothing is sent when it equals what is shown. */
    void show(BoardView.Page page) {
        if (!spawned() || page.equals(shown)) {
            return;
        }
        Facing f = machine.facing();
        float ts = fitScale(page.title(), layout.titleScale());
        float ss = fitScale(page.subtitle(), layout.subtitleScale());
        title.text(page.title());
        title.setTransformation(transform(f, 0, layout.titleY(), ts));
        subtitle.text(page.subtitle());
        subtitle.setTransformation(transform(f, 0, layout.subtitleY(), ss));
        float rs = layout.rowScale();
        double rowsBottom = layout.rowsTop() - (ROWS + 1) * LINE * rs;
        double left = -layout.halfWidth() + layout.leftPx() * PX * rs / 2;
        double right = layout.halfWidth() - layout.rightPx() * PX * rs / 2;
        names.text(page.names());
        names.setTransformation(transform(f, left, rowsBottom, rs));
        values.text(page.values());
        values.setTransformation(transform(f, right, rowsBottom, rs));
        shown = page;
    }

    /** The scale at which a one-line text fits the felt's width (at most {@code max}). */
    float fitScale(Component text, float max) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(text);
        int px = BoardView.width(plain) + 2;
        double fit = 2 * layout.halfWidth() / (px * PX);
        return (float) Math.min(max, fit);
    }

    /** A text display's transformation: its bottom-centre at (x, y, z) of the south frame, turned with the board. */
    Transformation transform(Facing facing, double x, double y, float scale) {
        Quaternionf rf = RigMath.facing(facing);
        Vector3f t = rf.transform(new Vector3f((float) x, (float) y, (float) layout.z()));
        return new Transformation(t, new Quaternionf(rf), new Vector3f(scale, scale, scale), new Quaternionf());
    }
}
