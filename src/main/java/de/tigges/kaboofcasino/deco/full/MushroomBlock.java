package de.tigges.kaboofcasino.deco.full;

/** The three vanilla huge-mushroom blocks whose {@code down=true} states carry the full-block deco. */
public enum MushroomBlock {
    RED("red_mushroom_block"),
    BROWN("brown_mushroom_block"),
    STEM("mushroom_stem");

    private final String path;

    MushroomBlock(String path) {
        this.path = path;
    }

    /** {@code minecraft:red_mushroom_block} ... */
    public String id() {
        return "minecraft:" + path;
    }

    /** {@code red_mushroom_block} ... (pack file names) */
    public String path() {
        return path;
    }
}
