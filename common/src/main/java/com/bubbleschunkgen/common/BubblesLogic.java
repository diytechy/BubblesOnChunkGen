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
                    int worldX = chunk.getChunkX() * 16 + x;
                    int worldZ = chunk.getChunkZ() * 16 + z;
                    for (int y = MIN_Y - 1; y <= MAX_Y; y++) {
                        if (chunk.getBlockType(x, y, z) != BLOCK_SOUL_SAND) continue;


                        flowBlocker.addProtectedSoulSand(ck, worldX, y, worldZ);
                        protectedSoulSand++;
                        frozenWater += freezeFlowingWaterAbove(chunk, ck, x, y, z, worldX, worldZ);
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
     * Walks up the column above the soul sand and freezes every non-source
     * (flowing) water block so it cannot receive or produce flow. The scan passes
     * straight through {@code bubble_column} blocks (soul sand under water turns
     * the submerged column into {@code minecraft:bubble_column}, not water) and
     * through full source water, stopping at the first air/solid block, which is
     * the surface.
     *
     * <p>Water levels are vanilla blockstate values: {@code level=0} is a full
     * source block; {@code level=1..7} are flowing, with {@code 7} the thinnest
     * ("lowest") water.
     */
    private int freezeFlowingWaterAbove(BlockAccess chunk, long ck, int x, int soulSandY, int z, int worldX, int worldZ) {
        int frozen = 0;
        for (int y = soulSandY + 1; y <= MAX_Y; y++) {
            int type = chunk.getBlockType(x, y, z);

            // The submerged column is bubble_column; keep scanning upward through it.
            if (type == BLOCK_BUBBLE_COLUMN) {
                if (bridge.isDebug()) {
                    bridge.log("  column [" + worldX + "," + y + "," + worldZ + "] = bubble_column");
                }
                continue;
            }

            if (type != BLOCK_WATER) {
                if (bridge.isDebug()) {
                    bridge.log("  column [" + worldX + "," + y + "," + worldZ + "] = type " + type + " (surface, stop)");
                }
                break;
            }

            int level = chunk.getWaterLevel(x, y, z);
            if (bridge.isDebug()) {
                bridge.log("  column [" + worldX + "," + y + "," + worldZ + "] = water level " + level);
            }
            if (level != 0) {
                flowBlocker.addFrozenWater(ck, worldX, y, worldZ);
                frozen++;
            }
        }
        return frozen;
    }
}
