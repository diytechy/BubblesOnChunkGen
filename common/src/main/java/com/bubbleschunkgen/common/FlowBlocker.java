package com.bubbleschunkgen.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Tracks coordinates where water flow should be blocked, and chunks
 * that are pending generation (blanket freeze). Thread-safe enough for
 * single-threaded server tick usage.
 */
public class FlowBlocker {

    private final Set<Long> allBlockedSurfaces = new HashSet<>();
    private final Map<Long, List<Long>> blockedByChunk = new HashMap<>();
    private final Set<Long> pendingNewChunks = new HashSet<>();

    public void addPendingChunk(long chunkKey) {
        pendingNewChunks.add(chunkKey);
    }

    public void removePendingChunk(long chunkKey) {
        pendingNewChunks.remove(chunkKey);
    }

    public void addBlockedSurface(long chunkKey, int worldX, int y, int worldZ) {
        long bk = coordKey(worldX, y, worldZ);
        allBlockedSurfaces.add(bk);
        blockedByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(bk);
    }

    public void removeChunk(long chunkKey) {
        pendingNewChunks.remove(chunkKey);
        List<Long> coords = blockedByChunk.remove(chunkKey);
        if (coords != null) {
            allBlockedSurfaces.removeAll(coords);
        }
    }

    /**
     * Returns true if water flow between the given world coordinates should be blocked.
     * Checks both blanket chunk freezes and per-coordinate blocks.
     */
    public boolean shouldBlockFlow(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        if (!pendingNewChunks.isEmpty()) {
            long fromCk = chunkKey(fromX >> 4, fromZ >> 4);
            long toCk = chunkKey(toX >> 4, toZ >> 4);
            if (pendingNewChunks.contains(fromCk) || pendingNewChunks.contains(toCk)) {
                return true;
            }
        }

        if (!allBlockedSurfaces.isEmpty()) {
            if (allBlockedSurfaces.contains(coordKey(toX, toY, toZ))
                    || allBlockedSurfaces.contains(coordKey(fromX, fromY, fromZ))) {
                return true;
            }
        }

        return false;
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
