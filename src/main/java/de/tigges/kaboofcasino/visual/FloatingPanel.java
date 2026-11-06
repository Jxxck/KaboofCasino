package de.tigges.kaboofcasino.visual;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The floating bet/result panel above a machine (every renderer's {@code panel}): one billboarded TextDisplay,
 * scale 0.44, dark translucent background, full bright, visible within 10 blocks (the mod fades it between 10 and
 * 7.5; a display only has a hard cut-off). Thread: MAIN.
 */
public final class FloatingPanel {

    private static final Display.Brightness FULL = new Display.Brightness(15, 15);
    private final UUID machine;
    private TextDisplay panel;

    public FloatingPanel(UUID machine) {
        this.machine = machine;
    }

    public void show(World world, Location at, Component text) {
        if (panel == null || !panel.isValid()) {
            panel = Displays.text(world, at, machine, d -> {
                d.setViewRange(10f / 64f);
                d.setBackgroundColor(Color.fromARGB(-266188488));
                d.setShadowed(true);
                d.setLineWidth(400);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.44f, 0.44f, 0.44f), new Quaternionf()));
                d.setBrightness(FULL);
                d.text(text);
            });
        } else {
            panel.text(text);
        }
    }

    public void hide() {
        Displays.remove(panel);
        panel = null;
    }

    /** True if the panel entity vanished while it should be shown. */
    public boolean broken() {
        return panel != null && !panel.isValid();
    }
}
