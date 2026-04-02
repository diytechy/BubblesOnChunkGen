package com.bubbleschunkgen.common;

/**
 * Platform-agnostic interface for reading and writing blocks within a chunk.
 * Local coordinates are 0-15 for x/z, world y for y.
 */
public interface BlockAccess {

    /** Returns a BLOCK_* constant from BubblesConstants. */
    int getBlockType(int localX, int y, int localZ);

    void setBlockType(int localX, int y, int localZ, int type, boolean physics);

    /** Get water level (0 = full source, higher = more shallow). Returns -1 if not water. */
    int getWaterLevel(int localX, int y, int localZ);

    void setWaterLevel(int localX, int y, int localZ, int level, boolean physics);

    /** Check if a block is solid (for chest placement validation). */
    boolean isSolid(int localX, int y, int localZ);

    /** World-coordinate water check for cross-chunk neighbor lookups. */
    boolean isWaterAtWorld(int worldX, int y, int worldZ);

    /** World-coordinate bubble column check for cross-chunk neighbor lookups. */
    boolean isBubbleColumnAtWorld(int worldX, int y, int worldZ);

    /** Get chunk X coordinate in world. */
    int getChunkX();

    /** Get chunk Z coordinate in world. */
    int getChunkZ();
}
