package com.bubbleschunkgen.common;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Platform-agnostic core logic for bubble column maintenance.
 *
 * <p>World generation (Terra/CHIMERA) places the soul sand and water directly.
 * This class does not place or restore any of those blocks on chunk load;
 * instead it passively records their locations to:
 * <ol>
 *   <li>prevent players from mining the soul sand (anti-griefing); and</li>
 *   <li>freeze level-1 water above protected soul sand, so the river transition
 *       survives fluid ticks.</li>
 * </ol>
 */
public class BubblesLogic {

    private final PlatformBridge bridge;
    private final FlowBlocker flowBlocker;

    public BubblesLogic(PlatformBridge bridge, FlowBlocker flowBlocker) {
        this.bridge = bridge;
        this.flowBlocker = flowBlocker;
    }

    public FlowBlocker getFlowBlocker() {
        return flowBlocker;
    }

    /**
     * Called whenever a chunk is loaded (new or from disk). 
     * Processes its columns after a short delay to allow worldgen to finish settling.
     */
    public void onChunkLoad(BlockAccess chunk) {
        long ck = chunkKey(chunk.getChunkX(), chunk.getChunkZ());
        flowBlocker.freezeChunk(ck);
        bridge.runDelayed(() -> processColumns(chunk, ck), PROCESS_DELAY_TICKS);
    }

    /** Called when a chunk unloads. Cleans up tracking data. */
    public void onChunkUnload(int chunkX, int chunkZ) {
        flowBlocker.removeChunk(chunkKey(chunkX, chunkZ));
    }

    /**
     * Scans each x/z column for soul sand and registers the soul sand and
     * level-1 water above it for passive protection.
     */
    private void processColumns(BlockAccess chunk, long ck) {
        int protectedSoulSand = 0;
        int frozenWater = 0;

        try {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = MIN_Y - 1; y <= MAX_Y; y++) {
                        if (chunk.getBlockType(x, y, z) != BLOCK_SOUL_SAND) continue;

                        int worldX = chunk.getChunkX() * 16 + x;
                        int worldZ = chunk.getChunkZ() * 16 + z;

                        flowBlocker.addProtectedSoulSand(ck, worldX, y, worldZ);
                        protectedSoulSand++;
                        frozenWater += freezeLevelOneWaterAbove(chunk, ck, x, y, z, worldX, worldZ);
                        break;
                    }
                }
            }
        } finally {
            flowBlocker.unfreezeChunk(ck);
        }

        if (bridge.isDebug() && (protectedSoulSand > 0 || frozenWater > 0)) {
            bridge.log("Chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ()
                    + "]: protected " + protectedSoulSand + " soul sand block(s), froze "
                    + frozenWater + " water block(s)");
        }
    }

    /**
     * Walks up from the soul sand until the first non-water block. Level 1 water
     * is frozen so it cannot receive or produce flow.
     */
    private int freezeLevelOneWaterAbove(BlockAccess chunk, long ck, int x, int soulSandY, int z, int worldX, int worldZ) {
        int frozen = 0;
        for (int y = soulSandY + 1; y <= MAX_Y; y++) {
            int type = chunk.getBlockType(x, y, z);
            if (type != BLOCK_WATER) {
                break;
            }

            if (chunk.getWaterLevel(x, y, z) == 1) {
                flowBlocker.addFrozenWater(ck, worldX, y, worldZ);
                frozen++;
            }
        }
        return frozen;
    }
}
