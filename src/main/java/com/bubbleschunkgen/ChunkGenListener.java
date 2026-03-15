package com.bubbleschunkgen;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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

    // Water flow blocking: coordinates where BlockFromToEvent should be cancelled.
    // allBlockedSurfaces provides O(1) lookup; blockedByChunk enables cleanup on unload.
    private final Set<Long> allBlockedSurfaces = new HashSet<>();
    private final Map<Long, List<Long>> blockedByChunk = new HashMap<>();

    public ChunkGenListener(BubblesPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Coordinate packing utilities ----

    /** Pack world x/y/z into a single long for fast set lookups. */
    private static long coordKey(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38)
             | (((long)(z & 0x3FFFFFF)) << 12)
             | ((long)(y & 0xFFF));
    }

    /** Pack chunk coordinates into a single long. */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private void addBlockedSurface(long ck, int worldX, int y, int worldZ) {
        long bk = coordKey(worldX, y, worldZ);
        allBlockedSurfaces.add(bk);
        blockedByChunk.computeIfAbsent(ck, k -> new ArrayList<>()).add(bk);
    }

    // ---- Event handlers ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        if (event.isNewChunk()) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("New chunk detected: [" + chunk.getX() + ", " + chunk.getZ() + "]");
            }

            // Register blocked surfaces immediately from blue concrete markers,
            // before generation is fully complete — this blocks water flow right away.
            registerSurfacesFromNewChunk(chunk);

            // Delay the actual block replacements so all generation stages finish first.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                processNewChunk(chunk);
            }, 5L);
        } else {
            // Existing chunk: scan for barrier+soul_sand pattern immediately
            registerSurfacesFromExistingChunk(chunk);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        long ck = chunkKey(event.getChunk().getX(), event.getChunk().getZ());
        List<Long> coords = blockedByChunk.remove(ck);
        if (coords != null) {
            allBlockedSurfaces.removeAll(coords);
        }
    }

    /**
     * Cancel water flow into or out of any blocked surface coordinate.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        if (allBlockedSurfaces.isEmpty()) return;

        Block from = event.getBlock();
        Block to = event.getToBlock();

        if (allBlockedSurfaces.contains(coordKey(to.getX(), to.getY(), to.getZ()))
                || allBlockedSurfaces.contains(coordKey(from.getX(), from.getY(), from.getZ()))) {
            event.setCancelled(true);
        }
    }

    // ---- Surface registration (runs immediately, no tick delay) ----

    /**
     * For a new chunk, find blue concrete markers and register the water surface
     * above each one. This runs synchronously in the chunk load event so water
     * flow is blocked before physics can tick.
     */
    private void registerSurfacesFromNewChunk(Chunk chunk) {
        long ck = chunkKey(chunk.getX(), chunk.getZ());
        int count = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.BLUE_CONCRETE) continue;

                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) break;

                    count += registerSurfaceAbove(chunk, ck, x, y, z);
                    break;
                }
            }
        }

        if (plugin.isDebug() && count > 0) {
            plugin.getLogger().info("  Registered " + count + " surface block(s) for water flow prevention");
        }
    }

    /**
     * For an existing chunk, find the barrier+soul_sand signature and register
     * the water surface above each column.
     */
    private void registerSurfacesFromExistingChunk(Chunk chunk) {
        long ck = chunkKey(chunk.getX(), chunk.getZ());
        int count = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.SOUL_SAND) continue;

                    Block below = chunk.getBlock(x, y - 1, z);
                    if (below.getType() != Material.BARRIER) continue;

                    count += registerSurfaceAbove(chunk, ck, x, y, z);
                    break;
                }
            }
        }

        if (plugin.isDebug() && count > 0) {
            plugin.getLogger().info("Loaded chunk [" + chunk.getX() + ", " + chunk.getZ()
                    + "]: registered " + count + " surface block(s) for water flow prevention");
        }
    }

    /**
     * Scans upward from (x, y, z) through water/bubble_column to find the surface,
     * then registers that coordinate as blocked. Returns 1 if registered, 0 otherwise.
     */
    private int registerSurfaceAbove(Chunk chunk, long ck, int x, int startY, int z) {
        for (int y = startY + 1; y <= MAX_Y; y++) {
            Block block = chunk.getBlock(x, y, z);
            Material type = block.getType();
            if (type == Material.WATER || type == Material.BUBBLE_COLUMN) continue;

            // This is the surface — block water flow here
            addBlockedSurface(ck, block.getX(), y, block.getZ());

            if (plugin.isDebug()) {
                plugin.getLogger().info("  Blocking water flow at [" + block.getX()
                        + ", " + y + ", " + block.getZ() + "]");
            }
            return 1;
        }
        return 0;
    }

    // ---- Block replacement (runs on delayed tick) ----

    /**
     * New chunk: replace blue concrete with soul sand + barrier below.
     * Surface registration has already happened in the chunk load event.
     */
    private void processNewChunk(Chunk chunk) {
        int updateCount = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.BLUE_CONCRETE) continue;

                    if (updateCount > 60) break;
                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) break;

                    // Place barrier below for future identification, then soul sand
                    Block below = chunk.getBlock(x, y - 1, z);
                    below.setType(Material.BARRIER);
                    block.setType(Material.SOUL_SAND);
                    updateCount++;

                    // 1 in 1000 chance: place a dedication chest on an adjacent side
                    if (random.nextInt(1000) == 0) {
                        tryPlaceDedicationChest(chunk, x, y, z);
                    }

                    break; // move to next x/z column
                }
            }
        }

        if (plugin.isDebug()) {
            globalUpdateCount += updateCount;
            plugin.getLogger().info("New chunk: " + updateCount + " soul sand placed. Total: " + globalUpdateCount);
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

        // Delay 1 tick so the chest's tile entity is fully initialized before
        // we populate it — writing to the inventory immediately after setType()
        // can silently fail because the TileEntityChest hasn't been registered yet.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (chestBlock.getType() != Material.CHEST) return; // safety check

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
        }, 1L);

        if (plugin.isDebug()) {
            plugin.getLogger().info("[CHIMERA DEDICATION] Chest placed at world ["
                    + chestBlock.getX() + ", " + y + ", " + chestBlock.getZ()
                    + "] adjacent to soul_sand in chunk ["
                    + chunk.getX() + ", " + chunk.getZ() + "]");
        }
    }
}
