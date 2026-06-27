package com.bubbleschunkgen.common;

public final class BubblesConstants {

    private BubblesConstants() {}

    public static final int MIN_Y = 45;
    public static final int MAX_Y = 165;

    /**
     * Tick delay between a chunk load and column processing. Kept at 0 so the per-block
     * freeze is applied before fluid settling converts the transition water (a flowing
     * block beside two sources becomes a source within a tick or two). The chunk-wide
     * freeze set on chunk load covers the brief gap until processing runs.
     */
    public static final long PROCESS_DELAY_TICKS = 0L;

    // Block type constants used by BlockAccess
    public static final int BLOCK_AIR = 0;
    public static final int BLOCK_WATER = 1;
    public static final int BLOCK_BUBBLE_COLUMN = 2;
    public static final int BLOCK_SOUL_SAND = 3;
    public static final int BLOCK_BEDROCK = 4;
    public static final int BLOCK_OTHER = 99;

    // Signal flag for cross-loader coordination on Bukkit. The Terra addon's
    // BukkitTerraHandler sets this on registration so the standalone Bukkit
    // plugin (if also installed) can bow out and avoid double-listening.
    // Fabric/Forge no longer need a property: the Terra-addon JAR is the only
    // bubble-column code on those platforms.
    public static final String PROP_TERRA_ADDON = "bubbleschunkgen.terra-addon";

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
