package com.bubbleschunkgen.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Platform-agnostic core logic for bubble column generation.
 * All block access and scheduling is done through interfaces.
 */
public class BubblesLogic {

    private final PlatformBridge bridge;
    private final FlowBlocker flowBlocker;
    private final Random random = new Random();

    private int globalUpdateCount = 0;

    public BubblesLogic(PlatformBridge bridge, FlowBlocker flowBlocker) {
        this.bridge = bridge;
        this.flowBlocker = flowBlocker;
    }

    public FlowBlocker getFlowBlocker() {
        return flowBlocker;
    }

    /**
     * Called when a new chunk is loaded. Freezes water flow, schedules
     * delayed processing, then registers surfaces.
     */
    public void onNewChunkLoad(BlockAccess chunk) {
        long ck = chunkKey(chunk.getChunkX(), chunk.getChunkZ());

        if (bridge.isDebug()) {
            bridge.log("New chunk detected: [" + chunk.getChunkX() + ", " + chunk.getChunkZ() + "]");
        }

        flowBlocker.addPendingChunk(ck);

        bridge.runDelayed(() -> {
            processNewChunk(chunk);
            if (PROCESS_EXISTING_CHUNKS) registerSurfacesFromExistingChunk(chunk);
            flowBlocker.removePendingChunk(ck);

            if (bridge.isDebug()) {
                bridge.log("Chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ()
                        + "] processing complete, chunk-wide freeze lifted");
            }
        }, 5L);
    }

    /**
     * Called when an existing (previously generated) chunk is loaded.
     * Scans for bedrock signatures and registers water surfaces.
     */
    public void onExistingChunkLoad(BlockAccess chunk) {
        if (!PROCESS_EXISTING_CHUNKS) return;
        registerSurfacesFromExistingChunk(chunk);
    }

    /**
     * Called when a chunk unloads. Cleans up flow blocking data.
     */
    public void onChunkUnload(int chunkX, int chunkZ) {
        long ck = chunkKey(chunkX, chunkZ);
        flowBlocker.removeChunk(ck);
    }

    /**
     * Replace blue concrete markers with soul sand (+ optional bedrock signature).
     * Collects all placed positions then attempts a single dedication chest at the end.
     */
    private void processNewChunk(BlockAccess chunk) {
        int updateCount = 0;
        List<int[]> soulSandPlaced = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    if (chunk.getBlockType(x, y, z) != BLOCK_BLUE_CONCRETE) continue;
                    if (updateCount > MAX_UPDATES_PER_CHUNK) break;
                    if (chunk.getBlockType(x, y + 1, z) != BLOCK_WATER) continue;

                    if (PLACE_BEDROCK_SIGNATURE) {
                        chunk.setBlockType(x, y - 1, z, BLOCK_BEDROCK, false);
                    }
                    chunk.setBlockType(x, y, z, BLOCK_SOUL_SAND, true);
                    updateCount++;
                    soulSandPlaced.add(new int[]{x, y, z});
                }
            }
        }

        // 1 in 100 chance per chunk to place a single dedication chest.
        // Shuffle so every column gets a fair shot before we give up.
        if (!soulSandPlaced.isEmpty() && random.nextInt(100) == 0) {
            Collections.shuffle(soulSandPlaced, random);
            for (int[] pos : soulSandPlaced) {
                if (tryPlaceDedicationChest(chunk, pos[0], pos[1], pos[2])) break;
            }
        }

        if (bridge.isDebug()) {
            globalUpdateCount += updateCount;
            bridge.log("New chunk: " + updateCount + " soul sand placed. Total: " + globalUpdateCount);
        }
    }

    /**
     * For an existing chunk, find the bedrock signature and register
     * the water surface above each column. Restores griefed soul sand.
     */
    private void registerSurfacesFromExistingChunk(BlockAccess chunk) {
        long ck = chunkKey(chunk.getChunkX(), chunk.getChunkZ());
        int count = 0;
        int restored = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y - 1; y <= MAX_Y; y++) {
                    if (chunk.getBlockType(x, y, z) != BLOCK_BEDROCK) continue;

                    // Bedrock found - check the block above
                    if (chunk.getBlockType(x, y + 1, z) != BLOCK_SOUL_SAND) {
                        // Soul sand was removed (griefed) - restore it
                        chunk.setBlockType(x, y + 1, z, BLOCK_SOUL_SAND, true);
                        restored++;
                    }

                    count += registerSurfaceAbove(chunk, ck, x, y + 1, z);
                }
            }
        }

        if (bridge.isDebug() && (count > 0 || restored > 0)) {
            bridge.log("Loaded chunk [" + chunk.getChunkX() + ", " + chunk.getChunkZ()
                    + "]: registered " + count + " surface(s), restored " + restored + " soul sand");
        }
    }

    /**
     * Scans upward from (x, startY, z) through water/bubble_column to find the surface,
     * then registers that coordinate as blocked. Returns number of surfaces registered.
     */
    private int registerSurfaceAbove(BlockAccess chunk, long ck, int x, int startY, int z) {
        int worldX = chunk.getChunkX() * 16 + x;
        int worldZ = chunk.getChunkZ() * 16 + z;
        int count = 0;
        boolean reachedSurface = false;

        for (int y = startY + 1; y <= MAX_Y; y++) {
            int blockType = chunk.getBlockType(x, y, z);

            if (!reachedSurface) {
                if (blockType == BLOCK_BUBBLE_COLUMN) continue;
                if (blockType == BLOCK_WATER) {
                    int level = chunk.getWaterLevel(x, y, z);
                    if (level == 0) continue;
                }
                reachedSurface = true;
            }

            if (!hasAdjacentWater(chunk, x, y, z)) break;

            flowBlocker.addBlockedSurface(ck, worldX, y, worldZ);
            count++;

            // Place a shallow (level 3) water block for a visual step
            chunk.setBlockType(x, y, z, BLOCK_WATER, false);
            chunk.setWaterLevel(x, y, z, 3, false);

            if (bridge.isDebug()) {
                bridge.log("  Blocking water flow at [" + worldX
                        + ", " + y + ", " + worldZ + "] (level-3 water placed)");
            }
        }
        return count;
    }

    /** Returns true if any of the 4 horizontal neighbors is a water or bubble column block. */
    private boolean hasAdjacentWater(BlockAccess chunk, int x, int y, int z) {
        for (int[] offset : SIDE_OFFSETS) {
            int nx = x + offset[0];
            int nz = z + offset[1];

            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
                // Cross-chunk: use world coordinates
                int worldX = chunk.getChunkX() * 16 + nx;
                int worldZ = chunk.getChunkZ() * 16 + nz;
                if (chunk.isWaterAtWorld(worldX, y, worldZ)
                        || chunk.isBubbleColumnAtWorld(worldX, y, worldZ)) {
                    return true;
                }
            } else {
                int type = chunk.getBlockType(nx, y, nz);
                if (type == BLOCK_WATER || type == BLOCK_BUBBLE_COLUMN) return true;
            }
        }
        return false;
    }

    /**
     * Attempts to place a dedication chest on one of the 4 horizontal sides of the
     * given soul sand position. Any adjacent block except soul sand and bedrock is
     * a valid candidate. Returns true if a chest was placed.
     */
    private boolean tryPlaceDedicationChest(BlockAccess chunk, int x, int y, int z) {
        List<int[]> candidates = new ArrayList<>();
        for (int[] offset : SIDE_OFFSETS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            int sideType = chunk.getBlockType(nx, y, nz);
            if (sideType == BLOCK_SOUL_SAND || sideType == BLOCK_BEDROCK) continue;
            candidates.add(new int[]{nx, nz});
        }

        if (candidates.isEmpty()) return false;

        int[] chosen = candidates.get(random.nextInt(candidates.size()));
        chunk.setBlockType(chosen[0], y, chosen[1], BLOCK_CHEST, false);

        // Delay 1 tick so the chest's tile entity is fully initialized
        bridge.runDelayed(() -> {
            if (chunk.getBlockType(chosen[0], y, chosen[1]) != BLOCK_CHEST) return;
            bridge.fillDedicationChest(chunk, chosen[0], y, chosen[1]);
        }, 1L);

        if (bridge.isDebug()) {
            int worldX = chunk.getChunkX() * 16 + chosen[0];
            int worldZ = chunk.getChunkZ() * 16 + chosen[1];
            bridge.log("[CHIMERA DEDICATION] Chest placed at world ["
                    + worldX + ", " + y + ", " + worldZ
                    + "] adjacent to soul_sand in chunk ["
                    + chunk.getChunkX() + ", " + chunk.getChunkZ() + "]");
        }
        return true;
    }
}
