package com.bubbleschunkgen;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChunkGenListener implements Listener {

    private final BubblesPlugin plugin;

    // Scan range for soul_sand - covers river bottoms near ocean level
    private static final int MIN_Y = 50;
    private static final int MAX_Y = 165;

    // Global counter for total blocks changed across all chunks (while debug is on)
    private int globalUpdateCount = 0;

    private final Random random = new Random();

    // Offsets for the 4 horizontal adjacent blocks
    private static final int[][] SIDE_OFFSETS = {{1,0},{-1,0},{0,1},{0,-1}};

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

                    // 1 in 500 chance: place a dedication chest on an adjacent side
                    if (random.nextInt(500) == 0) {
                        tryPlaceDedicationChest(chunk, x, y, z);
                    }

                    break; // move to next x/z column
                }
            }
        }
        if (plugin.isDebug()) {
            globalUpdateCount += updateCount;
            plugin.getLogger().info("Total sandstone added: " + globalUpdateCount);
        }
    }

    /**
     * Attempts to place a dedication chest on one of the 4 horizontal sides of the
     * soul_sand block at (x, y, z), choosing randomly among sides that are still
     * inside the chunk (0-15) and are air or replaceable.
     */
    private void tryPlaceDedicationChest(Chunk chunk, int x, int y, int z) {
        // Collect valid candidate side positions inside the chunk
        List<int[]> candidates = new ArrayList<>();
        for (int[] offset : SIDE_OFFSETS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            Block side = chunk.getBlock(nx, y, nz);
            if (side.getType() == Material.AIR || side.getType() == Material.WATER
                    || side.getType() == Material.CAVE_AIR) {
                candidates.add(new int[]{nx, nz});
            }
        }

        if (candidates.isEmpty()) return;

        int[] chosen = candidates.get(random.nextInt(candidates.size()));
        Block chestBlock = chunk.getBlock(chosen[0], y, chosen[1]);
        chestBlock.setType(Material.CHEST);

        Chest chest = (Chest) chestBlock.getState();
        Inventory inv = chest.getInventory();

        // --- Dedication note ---
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("CHIMERA");
        meta.setAuthor("CHIMERA");
        meta.addPage("CHIMERA\n\nDedicated to Finnian and Armin");
        book.setItemMeta(meta);

        inv.setItem(0, book);
        inv.setItem(1, new ItemStack(Material.EMERALD, 6));
        inv.setItem(2, new ItemStack(Material.DIAMOND, 7));

        chest.update();

        if (plugin.isDebug()) {
            plugin.getLogger().info("[CHIMERA DEDICATION] Chest placed at world ["
                    + chestBlock.getX() + ", " + y + ", " + chestBlock.getZ()
                    + "] adjacent to soul_sand in chunk ["
                    + chunk.getX() + ", " + chunk.getZ() + "]");
        }
    }
}
