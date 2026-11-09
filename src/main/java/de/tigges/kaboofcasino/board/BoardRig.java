package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** One model display: key, where its centre (8, 8, 8) goes, its scale (2: a big board's half-size model). */
    record Part(String model, double x, double y, double z, float scale) {

        Part(String model, double x, double y, double z) {
            this(model, x, y, z, 1.0f);
        }
    }

    /**
     * The text layout of one board kind (blocks, south frame): the felt's text area (x from -halfWidth to +halfWidth,
     * the front plane z), where the title, subtitle and row block sit (bottom of their line / top of the rows) and
     * their scales, the pixel widths of the two columns and of a name, the board's middle (an even width has none on
     * the anchor column).
     */
    record Layout(List<Part> parts, float width, float height, double halfWidth, double z, double titleY,
                  float titleScale, double subtitleY, float subtitleScale, double rowsTop, float rowScale, int leftPx,
                  int rightPx, int namePx, boolean compact, double centerX) {
    }

    /**
     * One text line at scale 1: the client's {@code TextDisplayRenderer} steps {@code font.lineHeight + 1} = 10 font
     * pixels of 1/40 block per line, and the whole block rises by lines * 10 above the entity's point.
     */
    static final double LINE = 10 / 40.0;
    static final double PX = 1 / 40.0;
    /** Rows on a board, plus the spacer line at the bottom of each column. */
    static final int ROWS = 10;

    static final Layout WALL = new Layout(List.of(new Part("kaboofcasino:leaderboard/wall", 0, 0.5, 0)), 3.2f, 2.2f,
            1.30, -0.36, 1.40, 0.6f, 1.30, 0.4f, 1.28, 0.45f, 130, 80, 106, false, 0);
    /**
     * The column: the post in cell 0, the sign board (model y -8..24 around its origin) from the bottom of cell 1 to
     * the top of cell 2, so its origin sits 2.0 above the anchor; felt text area y 1.125..2.5625, front at z 0.125.
     */
    static final Layout COLUMN = new Layout(List.of(new Part("kaboofcasino:leaderboard/column_post", 0, 0.5, 0),
            new Part("kaboofcasino:leaderboard/column_board", 0, 2.0, 0)), 1.6f, 3.2f, 0.58, 0.135, 2.45, 0.42f,
            2.37, 0.3f, 2.35, 0.32f, 100, 44, 80, true, 0);

    /** Between the felt's edge and the text, sideways. */
    private static final double SIDE_MARGIN = 0.075;
    /** The frame below the felt ({@code LeaderboardPack.BORDER}). */
    private static final double FELT_BOTTOM = 2 / 16.0;
    private static final double MIN_GAP = 0.03;
    /** Title and subtitle relative to the rows, as on the 3 x 2. */
    private static final double TITLE_RATIO = 4.0 / 3;
    private static final double SUBTITLE_RATIO = 8.0 / 9;

    private static final Map<MachineType, Layout> LAYOUTS = layouts();

    /** The header heights in px are {@code LeaderboardPack.WALL_SIZES}': the top of the felt the text may use. */
    private static Map<MachineType, Layout> layouts() {
        Map<MachineType, Layout> out = new EnumMap<>(MachineType.class);
        out.put(MachineType.LEADERBOARD_WALL, WALL);
        out.put(MachineType.LEADERBOARD_COLUMN, COLUMN);
        out.put(MachineType.LEADERBOARD_WALL_2X2, sized(MachineType.LEADERBOARD_WALL_2X2, 7, 100, 44, 80, true));
        out.put(MachineType.LEADERBOARD_WALL_3X3, sized(MachineType.LEADERBOARD_WALL_3X3, 7, 116, 44, 96, true));
        out.put(MachineType.LEADERBOARD_WALL_4X3, sized(MachineType.LEADERBOARD_WALL_4X3, 9.25, 130, 80, 106, false));
        out.put(MachineType.LEADERBOARD_WALL_4X4, sized(MachineType.LEADERBOARD_WALL_4X4, 9.25, 116, 44, 96, true));
        out.put(MachineType.LEADERBOARD_WALL_5X3, sized(MachineType.LEADERBOARD_WALL_5X3, 11.5, 130, 80, 106, false));
        out.put(MachineType.LEADERBOARD_WALL_5X4, sized(MachineType.LEADERBOARD_WALL_5X4, 11.5, 130, 80, 106, false));
        out.put(MachineType.LEADERBOARD_WALL_6X4, sized(MachineType.LEADERBOARD_WALL_6X4, 13.75, 130, 80, 106, false));
        return out;
    }

    /**
     * A wall board's layout from its size: the biggest rows that fit the felt's width and height, the spare height
     * shared by the gaps above the title, under the subtitle and below the rows. The rows' spacer line may hang into
     * the frame, it is blank. A board wider than 3 or taller than 3 blocks is a half-size model at scale 2, the
     * client's model space only spans 3 blocks.
     */
    private static Layout sized(MachineType type, double headerPx, int leftPx, int rightPx, int namePx,
                                boolean compact) {
        int columns = type.wallColumns();
        int rows = type.wallRows();
        double centerX = columns % 2 == 0 ? 0.5 : 0;
        double halfWidth = columns / 2.0 - FELT_BOTTOM - SIDE_MARGIN;
        double top = rows - headerPx / 16;
        double lines = ROWS + TITLE_RATIO + SUBTITLE_RATIO;
        double byWidth = 2 * halfWidth / ((leftPx + rightPx) * PX);
        double byHeight = (top - FELT_BOTTOM - 3 * MIN_GAP) / (lines * LINE);
        float scale = (float) (Math.floor(Math.min(byWidth, byHeight) * 100) / 100);
        float title = (float) (scale * TITLE_RATIO);
        float subtitle = (float) (scale * SUBTITLE_RATIO);
        double gap = (top - FELT_BOTTOM - (ROWS * scale + title + subtitle) * LINE) / 3;
        double titleY = top - gap - LINE * title;
        double subtitleY = titleY - LINE * subtitle;
        // the culling box stands on the anchor: its half width reaches the far edge of an even board
        float width = (columns % 2 == 0 ? columns + 1 : columns) + 0.2f;
        return new Layout(List.of(new Part("kaboofcasino:leaderboard/wall_" + columns + "x" + rows, centerX, rows / 2.0,
                0, Math.max(columns, rows) > 3 ? 2.0f : 1.0f)), width, rows + 0.2f, halfWidth, -0.36, titleY, title,
                subtitleY, subtitle, subtitleY - gap, scale, leftPx, rightPx, namePx, compact, centerX);
    }

    static Layout layout(MachineType type) {
        return LAYOUTS.get(type);
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
        for (Layout l : LAYOUTS.values()) {
            l.parts().forEach(p -> out.add(p.model()));
        }
        return out;
    }

    void spawn(World world) {
        despawn();
        Location at = new Location(world, machine.anchor().x() + 0.5, machine.anchor().y(),
                machine.anchor().z() + 0.5);
        for (Part p : layout.parts()) {
            Transformation t = RigMath.part(machine.facing(), new Vector3f((float) p.x(), (float) p.y(),
                    (float) p.z()));
            models.add(Displays.item(world, at, machine.id(), Displays.model(p.model()), new Transformation(
                    t.getTranslation(), t.getLeftRotation(), new Vector3f(p.scale()), t.getRightRotation()),
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
        title.setTransformation(transform(f, layout.centerX(), layout.titleY(), ts));
        subtitle.text(page.subtitle());
        subtitle.setTransformation(transform(f, layout.centerX(), layout.subtitleY(), ss));
        float rs = layout.rowScale();
        double rowsBottom = layout.rowsTop() - (ROWS + 1) * LINE * rs;
        double left = layout.centerX() - layout.halfWidth() + layout.leftPx() * PX * rs / 2;
        double right = layout.centerX() + layout.halfWidth() - layout.rightPx() * PX * rs / 2;
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
