package com.bubbleschunkgen.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Tracks coordinates of soul sand and frozen water to prevent flow and block
 * breaking (anti-griefing). Thread-safe enough for single-threaded server tick
 * usage.
 */
public class FlowBlocker {

    private final Set<Long> protectedSoulSand = new HashSet<>();
    private final Set<Long> frozenWater = new HashSet<>();
    private final Set<Long> frozenChunks = new HashSet<>();
    private final Map<Long, List<Long>> soulsandByChunk = new HashMap<>();
    private final Map<Long, List<Long>> waterByChunk = new HashMap<>();

    public void freezeChunk(long chunkKey) {
        frozenChunks.add(chunkKey);
    }

    public void unfreezeChunk(long chunkKey) {
        frozenChunks.remove(chunkKey);
    }

    public void addProtectedSoulSand(long chunkKey, int worldX, int y, int worldZ) {
        long bk = coordKey(worldX, y, worldZ);
        protectedSoulSand.add(bk);
        soulsandByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(bk);
    }

    public void addFrozenWater(long chunkKey, int worldX, int y, int worldZ) {
        long bk = coordKey(worldX, y, worldZ);
        frozenWater.add(bk);
        waterByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(bk);
    }

    public void removeChunk(long chunkKey) {
        frozenChunks.remove(chunkKey);
        List<Long> ssCoords = soulsandByChunk.remove(chunkKey);
        if (ssCoords != null) {
            protectedSoulSand.removeAll(ssCoords);
        }
        List<Long> waterCoords = waterByChunk.remove(chunkKey);
        if (waterCoords != null) {
            frozenWater.removeAll(waterCoords);
        }
    }

    /**
     * Checks if the block at the given coordinates is a protected soul sand block.
     */
    public boolean isProtectedSoulSand(int x, int y, int z) {
        return !protectedSoulSand.isEmpty() && protectedSoulSand.contains(coordKey(x, y, z));
    }

    /**
     * Checks if water is allowed to flow between two coordinates.
     * Flow is blocked while either chunk is being scanned, or when either side of
     * the flow is a frozen water block.
     */
    public boolean canFlow(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        if (!frozenChunks.isEmpty()) {
            if (frozenChunks.contains(chunkKey(fromX >> 4, fromZ >> 4))
                    || frozenChunks.contains(chunkKey(toX >> 4, toZ >> 4))) {
                return false;
            }
        }

        if (frozenWater.isEmpty()) return true;

        return !frozenWater.contains(coordKey(fromX, fromY, fromZ))
                && !frozenWater.contains(coordKey(toX, toY, toZ));
    }

    /**
     * Compatibility check for source-form events on platforms that expose them.
     */
    public boolean canFormSource(int x, int y, int z) {
        return !frozenChunks.contains(chunkKey(x >> 4, z >> 4))
                && !frozenWater.contains(coordKey(x, y, z));
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
