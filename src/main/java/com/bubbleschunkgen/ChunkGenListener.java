package com.bubbleschunkgen;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

public class ChunkGenListener implements Listener {

    private final BubblesPlugin plugin;

    // Scan range for soul_sand - covers river bottoms near ocean level
    private static final int MIN_Y = 30;
    private static final int MAX_Y = 80;

    public ChunkGenListener(BubblesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        Chunk chunk = event.getChunk();

        // Delay by a few ticks to ensure all generation stages are complete
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            triggerBubbleColumns(chunk);
        }, 5L);
    }

    private void triggerBubbleColumns(Chunk chunk) {
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.SOUL_SAND) continue;

                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) continue;

                    // Trigger a block state update on the water block to
                    // cause Minecraft to recognize the bubble column
                    BlockState state = above.getState();
                    state.update(true, true);
                }
            }
        }
    }
}
