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
                    if (block.getType() != Material.BLUE_CONCRETE) continue;

                    soulSandCount++;

                    if (updateCount > 40) break;
                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) break;

                    //block.setType(Material.SOUL_SAND, true);
                    block.setType(Material.SOUL_SAND);

                    updateCount++;

                    if (plugin.isDebug()) {
                        plugin.getLogger().info("  Applied replacement  at [" + block.getX() + ", " + y + ", " + block.getZ() + "]");
                    }

                    break; // move to next x/z column
                }
            }
        }

        if (plugin.isDebug()) {
            globalUpdateCount += updateCount;
            plugin.getLogger().info("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                    + "] - blue_concrete found: " + soulSandCount
                    + ", updates triggered: " + updateCount
                    + ", global total: " + globalUpdateCount);
        }
    }
}
