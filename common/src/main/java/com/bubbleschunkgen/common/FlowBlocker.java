package com.bubbleschunkgen.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Tracks coordinates of soul sand and water columns to prevent source block
 * formation and block breaking (anti-griefing). Thread-safe enough for
 * single-threaded server tick usage.
 */
public class FlowBlocker {

    private final Set<Long> protectedSoulSand = new HashSet<>();
    private final Set<Long> protectedWaterColumns = new HashSet<>();
    private final Map<Long, List<Long>> soulsandByChunk = new HashMap<>();
    private final Map<Long, List<Long>> waterByChunk = new HashMap<>();

    public void addProtectedSoulSand(long chunkKey, int worldX, int y, int worldZ) {
        long bk = coordKey(worldX, y, worldZ);
        protectedSoulSand.add(bk);
        soulsandByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(bk);
    }

    public void addProtectedWaterColumn(long chunkKey, int worldX, int y, int worldZ) {
        long bk = coordKey(worldX, y, worldZ);
        protectedWaterColumns.add(bk);
        waterByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(bk);
    }

    public void removeChunk(long chunkKey) {
        List<Long> ssCoords = soulsandByChunk.remove(chunkKey);
        if (ssCoords != null) {
            protectedSoulSand.removeAll(ssCoords);
        }
        List<Long> waterCoords = waterByChunk.remove(chunkKey);
        if (waterCoords != null) {
            protectedWaterColumns.removeAll(waterCoords);
        }
    }

    /**
     * Checks if the block at the given coordinates is a protected soul sand block.
     */
    public boolean isProtectedSoulSand(int x, int y, int z) {
        return !protectedSoulSand.isEmpty() && protectedSoulSand.contains(coordKey(x, y, z));
    }

    /**
     * Checks if a new source block can form at the given coordinates.
     * Source blocks are prevented from forming adjacent to protected water columns.
     */
    public boolean canFormSource(int x, int y, int z) {
        if (protectedWaterColumns.isEmpty()) return true;

        for (int[] offset : SIDE_OFFSETS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (protectedWaterColumns.contains(coordKey(nx, y, nz))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Static global instance for use by Mixins (Forge/Fabric) that can't
     * easily receive injected dependencies.
     */
    private static FlowBlocker globalInstance;

    public static void setGlobalInstance(FlowBlocker instance) {
        globalInstance = instance;
    }

    public static FlowBlocker getGlobalInstance() {
        return globalInstance;
    }
}
