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
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.SOUL_SAND) continue;

                    soulSandCount++;

                    if (updateCount > 16) continue;
                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) continue;

                    // Force a block update by re-setting the soul_sand block
                    // This triggers Minecraft's neighbor update logic which
                    // creates bubble columns in the water above
                    BlockData data = block.getBlockData();
                    block.setBlockData(data, true);
                    updateCount++;
                }
            }
        }

        if (plugin.isDebug()) {
            plugin.getLogger().info("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                    + "] - soul_sand found: " + soulSandCount
                    + ", updates triggered: " + updateCount);
        }
    }
}
