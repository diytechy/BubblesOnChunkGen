package com.bubbleschunkgen.common;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Platform-agnostic core logic for bubble column maintenance.
 *
 * <p>World generation (Terra/CHIMERA) places the soul sand directly, with a
 * bedrock block one below it as a persistent signature. This class no longer
 * places or restores any of those blocks on chunk load; instead it passively records
 * their locations to:
 * <ol>
 *   <li>prevent players from mining the soul sand (anti-griefing); and</li>
 *   <li>prevent water columns from forming adjacent source blocks, so the
 *       river naturally cascades but does not permanently widen.</li>
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
        bridge.runDelayed(() -> processColumns(chunk), PROCESS_DELAY_TICKS);
    }

    /** Called when a chunk unloads. Cleans up tracking data. */
    public void onChunkUnload(int chunkX, int chunkZ) {
        flowBlocker.removeChunk(chunkKey(chunkX, chunkZ));
    }

    /**
     * Scans the chunk for bedrock signatures and registers the soul sand
     * and water columns for passive protection.
     */
    private void processColumns(BlockAccess chunk) {
        long ck = chunkKey(chunk.getChunkX(), chunk.getChunkZ());
        int protectedCols = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y - 1; y <= MAX_Y; y++) {
                    if (chunk.getBlockType(x, y, z) != BLOCK_BEDROCK) continue;

                    int soulSandY = y + 1;
                    int worldX = chunk.getChunkX() * 16 + x;
                    int worldZ = chunk.getChunkZ() * 16 + z;

                    flowBlocker.addProtectedSoulSand(ck, worldX, soulSandY, worldZ);
                    
                    // Register the water column above it
                    trackColumnAbove(chunk, ck, x, soulSandY, z, worldX, worldZ);
                    protectedCols++;
                }
            }
        }

        if (bridge.isDebug() && protectedCols > 0) {
            bridge.log("Chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ()
                    + "]: protected " + protectedCols + " bubble column(s)");
        }
    }

    /**
     * Walks up from the soul sand, registering every full-source-water or 
     * bubble-column block as a protected water column, so they cannot form
     * adjacent source blocks.
     */
    private void trackColumnAbove(BlockAccess chunk, long ck, int x, int soulSandY, int z, int worldX, int worldZ) {
        for (int y = soulSandY + 1; y <= MAX_Y; y++) {
            int type = chunk.getBlockType(x, y, z);

            boolean columnWater = type == BLOCK_BUBBLE_COLUMN
                    || (type == BLOCK_WATER && chunk.getWaterLevel(x, y, z) == 0);

            if (columnWater) {
                flowBlocker.addProtectedWaterColumn(ck, worldX, y, worldZ);
            } else {
                break;
            }
        }
    }
}
