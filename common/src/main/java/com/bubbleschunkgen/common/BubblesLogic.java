package com.bubbleschunkgen.common;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Platform-agnostic core logic for bubble column maintenance.
 *
 * <p>World generation (Terra/CHIMERA) now places the soul sand directly, with a
 * bedrock block one below it as a persistent signature. This class no longer
 * places any of those blocks; on every chunk load it simply:
 * <ol>
 *   <li>finds each bedrock signature and restores the soul sand above it if a
 *       player has removed it (anti-grief);</li>
 *   <li>freezes every water/bubble-column block above the soul sand that is
 *       adjacent to air, walking up until air above the column is reached, so
 *       the bubble column cannot spill sideways and grow the river; and</li>
 *   <li>caps the surface with a thin (level-7) water step.</li>
 * </ol>
 *
 * <p>Every step is idempotent, so the same path runs for freshly generated and
 * disk-loaded chunks alike — no new-vs-existing distinction is needed.
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
     * Called whenever a chunk is loaded (new or from disk). Blanket-freezes the
     * chunk while it settles, then processes its columns after a short delay and
     * lifts the blanket freeze.
     */
    public void onChunkLoad(BlockAccess chunk) {
        long ck = chunkKey(chunk.getChunkX(), chunk.getChunkZ());
        flowBlocker.addPendingChunk(ck);

        bridge.runDelayed(() -> {
            processColumns(chunk);
            flowBlocker.removePendingChunk(ck);
        }, PROCESS_DELAY_TICKS);
    }

    /** Called when a chunk unloads. Cleans up flow-blocking data. */
    public void onChunkUnload(int chunkX, int chunkZ) {
        flowBlocker.removeChunk(chunkKey(chunkX, chunkZ));
    }

    /**
     * Scans the chunk for bedrock signatures, restores griefed soul sand, and
     * freezes the water column above each.
     */
    private void processColumns(BlockAccess chunk) {
        long ck = chunkKey(chunk.getChunkX(), chunk.getChunkZ());
        int frozen = 0;
        int restored = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y - 1; y <= MAX_Y; y++) {
                    if (chunk.getBlockType(x, y, z) != BLOCK_BEDROCK) continue;

                    int soulSandY = y + 1;
                    if (chunk.getBlockType(x, soulSandY, z) != BLOCK_SOUL_SAND) {
                        // Soul sand removed (griefed) - restore it above the signature.
                        chunk.setBlockType(x, soulSandY, z, BLOCK_SOUL_SAND, true);
                        restored++;
                    }

                    frozen += freezeColumnAbove(chunk, ck, x, soulSandY, z);
                }
            }
        }

        if (bridge.isDebug() && (frozen > 0 || restored > 0)) {
            bridge.log("Chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ()
                    + "]: froze " + frozen + " water block(s), restored " + restored + " soul sand");
        }
    }

    /**
     * Walks up from the soul sand. Every full-source-water / bubble-column block
     * that touches air horizontally is registered as a blocked surface, so it
     * cannot spread. The walk stops at the first block above the column; if that
     * block is air or flowing water it is capped with a thin (level-7) frozen
     * water step. Returns the number of coordinates frozen.
     */
    private int freezeColumnAbove(BlockAccess chunk, long ck, int x, int soulSandY, int z) {
        int worldX = chunk.getChunkX() * 16 + x;
        int worldZ = chunk.getChunkZ() * 16 + z;
        int count = 0;

        for (int y = soulSandY + 1; y <= MAX_Y; y++) {
            int type = chunk.getBlockType(x, y, z);

            boolean columnWater = type == BLOCK_BUBBLE_COLUMN
                    || (type == BLOCK_WATER && chunk.getWaterLevel(x, y, z) == 0);

            if (columnWater) {
                if (hasAdjacentAir(chunk, x, y, z)) {
                    flowBlocker.addBlockedSurface(ck, worldX, y, worldZ);
                    count++;
                }
                continue;
            }

            // First non-column block above the soul sand is the surface. Cap an
            // air / flowing-water surface with a thin frozen water step.
            if (type == BLOCK_AIR || type == BLOCK_WATER) {
                boolean alreadyCapped = type == BLOCK_WATER
                        && chunk.getWaterLevel(x, y, z) == SURFACE_WATER_LEVEL;
                if (!alreadyCapped) {
                    chunk.setBlockType(x, y, z, BLOCK_WATER, false);
                    chunk.setWaterLevel(x, y, z, SURFACE_WATER_LEVEL, false);
                }
                flowBlocker.addBlockedSurface(ck, worldX, y, worldZ);
                count++;

                if (bridge.isDebug()) {
                    bridge.log("  Surface step at [" + worldX + ", " + y + ", " + worldZ
                            + "] (level-" + SURFACE_WATER_LEVEL + " water, flow frozen)");
                }
            }
            break;
        }
        return count;
    }

    /** Returns true if any of the 4 horizontal neighbors is air. */
    private boolean hasAdjacentAir(BlockAccess chunk, int x, int y, int z) {
        for (int[] offset : SIDE_OFFSETS) {
            int nx = x + offset[0];
            int nz = z + offset[1];

            int type;
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
                int worldX = chunk.getChunkX() * 16 + nx;
                int worldZ = chunk.getChunkZ() * 16 + nz;
                type = chunk.getBlockTypeAtWorld(worldX, y, worldZ);
            } else {
                type = chunk.getBlockType(nx, y, nz);
            }

            if (type == BLOCK_AIR) return true;
        }
        return false;
    }
}
