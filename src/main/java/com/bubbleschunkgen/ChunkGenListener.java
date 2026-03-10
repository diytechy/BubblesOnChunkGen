package com.bubbleschunkgen;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkGenListener implements Listener {

    private final BubblesPlugin plugin;

    // Scan range for soul_sand - covers river bottoms near ocean level
    private static final int MIN_Y = 30;
    private static final int MAX_Y = 80;

    // Global counter for total blocks changed across all chunks (while debug is on)
    private int globalUpdateCount = 0;

    // Strategy counter - cycles through strategies per block
    private int strategyCounter = 0;
    private static final int STRATEGY_COUNT = 5;

    public ChunkGenListener(BubblesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        Chunk chunk = event.getChunk();

        if (plugin.isDebug()) {
            plugin.getLogger().info("New chunk detected: [" + chunk.getX() + ", " + chunk.getZ() + "]");
        }

        // Delay by a few ticks to ensure all generation stages are complete
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            triggerBubbleColumns(chunk);
        }, 10L);
    }

    private void triggerBubbleColumns(Chunk chunk) {
        int updateCount = 0;
        int soulSandCount = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Iterate top-to-bottom; break on first soul_sand match per column
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.SOUL_SAND) continue;

                    soulSandCount++;

                    if (updateCount > 16) break;
                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) break;

                    int strategy = strategyCounter % STRATEGY_COUNT;
                    strategyCounter++;

                    switch (strategy) {
                        case 0:
                            // Strategy 1: Re-set the BlockData to trigger neighbor update
                            block.setBlockData(block.getBlockData(), true);
                            break;
                        case 1:
                            // Strategy 2: Use BlockState.update(force, applyPhysics)
                            BlockState state = block.getState();
                            state.update(true, true);
                            break;
                        case 2:
                            // Strategy 3: Replace soul_sand with magma block (visual test)
                            block.setType(Material.MAGMA_BLOCK);
                            break;
                        case 3:
                            // Strategy 4: Manually place BUBBLE_COLUMN in water above
                            for (int by = y + 1; by <= MAX_Y; by++) {
                                Block waterBlock = chunk.getBlock(x, by, z);
                                if (waterBlock.getType() != Material.WATER) break;
                                waterBlock.setType(Material.BUBBLE_COLUMN, true);
                            }
                            break;
                        case 4:
                            // Strategy 5: Remove soul_sand then re-place it to force
                            // a full block change event (not just data update)
                            block.setType(Material.STONE, false);
                            block.setType(Material.SOUL_SAND, true);
                            break;
                    }

                    updateCount++;

                    if (plugin.isDebug()) {
                        plugin.getLogger().info("  Applied strategy " + strategy
                                + " at [" + block.getX() + ", " + y + ", " + block.getZ() + "]");
                    }

                    break; // move to next x/z column
                }
            }
        }

        if (plugin.isDebug()) {
            globalUpdateCount += updateCount;
            plugin.getLogger().info("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                    + "] - soul_sand found: " + soulSandCount
                    + ", updates triggered: " + updateCount
                    + ", global total: " + globalUpdateCount);
        }
    }
}
