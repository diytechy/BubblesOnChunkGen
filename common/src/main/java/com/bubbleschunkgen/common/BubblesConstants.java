package com.bubbleschunkgen.common;

public final class BubblesConstants {

    private BubblesConstants() {}

    public static final int MIN_Y = 50;
    public static final int MAX_Y = 165;

    public static final int MAX_UPDATES_PER_CHUNK = 60;

    public static final int[][] SIDE_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // Block type constants used by BlockAccess
    public static final int BLOCK_AIR = 0;
    public static final int BLOCK_WATER = 1;
    public static final int BLOCK_BUBBLE_COLUMN = 2;
    public static final int BLOCK_BLUE_CONCRETE = 3;
    public static final int BLOCK_SOUL_SAND = 4;
    public static final int BLOCK_BEDROCK = 5;
    public static final int BLOCK_CHEST = 6;
    public static final int BLOCK_OTHER = 99;

    // System properties for conflict detection
    public static final String PROP_TERRA_ADDON = "bubbleschunkgen.terra-addon";
    public static final String PROP_PLUGIN_BUKKIT = "bubbleschunkgen.plugin.bukkit";
    public static final String PROP_PLUGIN_FORGE = "bubbleschunkgen.plugin.forge";
    public static final String PROP_PLUGIN_FABRIC = "bubbleschunkgen.plugin.fabric";

    /** Pack world x/y/z into a single long for fast set lookups. */
    public static long coordKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | (((long) (z & 0x3FFFFFF)) << 12)
                | ((long) (y & 0xFFF));
    }

    /** Pack chunk coordinates into a single long. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
