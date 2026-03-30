package com.bubbleschunkgen;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
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
import java.util.UUID;

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

    // Chunks currently being generated — ALL water flow in/out of these chunks is frozen
    // until processNewChunk completes and the chunk key is removed.
    private final Set<Long> pendingNewChunks = new HashSet<>();

    // Cache per-world whether it uses Terra's chunk generator.
    private final Map<UUID, Boolean> terraWorldCache = new HashMap<>();

    public ChunkGenListener(BubblesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns true if the world uses a Terra chunk generator. */
    private boolean isTerraWorld(World world) {
        return terraWorldCache.computeIfAbsent(world.getUID(), uid -> {
            ChunkGenerator gen = world.getGenerator();
            return gen != null
                    && gen.getClass().getName().toLowerCase().contains("terra");
        });
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
        if (!isTerraWorld(chunk.getWorld())) return;

        if (event.isNewChunk()) {
            long ck = chunkKey(chunk.getX(), chunk.getZ());

            if (plugin.isDebug()) {
                plugin.getLogger().info("New chunk detected: [" + chunk.getX() + ", " + chunk.getZ() + "]");
            }

            // Freeze ALL water flow in/out of this chunk immediately.
            // This must happen before any physics tick to prevent water from
            // flowing past bubble column surfaces during chunk generation.
            pendingNewChunks.add(ck);

            // Delay the actual block replacements so all generation stages finish first.
            // Once done, register per-coordinate surface blocks and lift the chunk freeze.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                processNewChunk(chunk);
                // Blue concrete is now soul sand + bedrock — use the same
                // registration logic as existing chunk loads.
                registerSurfacesFromExistingChunk(chunk);
                pendingNewChunks.remove(ck);

                if (plugin.isDebug()) {
                    plugin.getLogger().info("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                            + "] processing complete, chunk-wide freeze lifted");
                }
            }, 5L);
        } else {
            // Existing chunk: scan for barrier+soul_sand pattern immediately
            registerSurfacesFromExistingChunk(chunk);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!isTerraWorld(event.getChunk().getWorld())) return;
        long ck = chunkKey(event.getChunk().getX(), event.getChunk().getZ());
        pendingNewChunks.remove(ck);
        List<Long> coords = blockedByChunk.remove(ck);
        if (coords != null) {
            allBlockedSurfaces.removeAll(coords);
        }
    }

    /**
     * Cancel water flow into or out of any blocked surface coordinate,
     * and blanket-block all water flow involving chunks still being generated.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        if (!isTerraWorld(event.getBlock().getWorld())) return;
        Block from = event.getBlock();
        Block to = event.getToBlock();

        // Blanket freeze: block ALL water flow in/out of chunks still being generated
        if (!pendingNewChunks.isEmpty()) {
            long fromCk = chunkKey(from.getX() >> 4, from.getZ() >> 4);
            long toCk = chunkKey(to.getX() >> 4, to.getZ() >> 4);
            if (pendingNewChunks.contains(fromCk) || pendingNewChunks.contains(toCk)) {
                event.setCancelled(true);
                return;
            }
        }

        // Per-coordinate blocking for processed chunks
        if (!allBlockedSurfaces.isEmpty()) {
            if (allBlockedSurfaces.contains(coordKey(to.getX(), to.getY(), to.getZ()))
                    || allBlockedSurfaces.contains(coordKey(from.getX(), from.getY(), from.getZ()))) {
                event.setCancelled(true);
            }
        }
    }

    // ---- Surface registration ----

    /**
     * For an existing chunk, find the bedrock signature and register
     * the water surface above each column. If soul sand above the bedrock
     * has been removed (griefing), restore it.
     */
    private void registerSurfacesFromExistingChunk(Chunk chunk) {
        long ck = chunkKey(chunk.getX(), chunk.getZ());
        int count = 0;
        int restored = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y - 1; y <= MAX_Y; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.BEDROCK) continue;

                    // Bedrock found — check the block above
                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.SOUL_SAND) {
                        // Soul sand was removed (griefed) — restore it
                        above.setType(Material.SOUL_SAND);
                        restored++;
                    }

                    count += registerSurfaceAbove(chunk, ck, x, y + 1, z);
                }
            }
        }

        if (plugin.isDebug() && (count > 0 || restored > 0)) {
            plugin.getLogger().info("Loaded chunk [" + chunk.getX() + ", " + chunk.getZ()
                    + "]: registered " + count + " surface(s), restored " + restored + " soul sand");
        }
    }

    /**
     * Scans upward from (x, y, z) through water/bubble_column to find the surface,
     * then registers that coordinate as blocked. Returns 1 if registered, 0 otherwise.
     */
    private int registerSurfaceAbove(Chunk chunk, long ck, int x, int startY, int z) {
        int worldX = chunk.getX() * 16 + x;
        int worldZ = chunk.getZ() * 16 + z;
        int count = 0;
        boolean reachedSurface = false;

        for (int y = startY + 1; y <= MAX_Y; y++) {
            Block block = chunk.getBlock(x, y, z);
            Material type = block.getType();

            if (!reachedSurface) {
                // Skip only full source water (level 0) and bubble columns.
                // Partial water (e.g. the level-3 block we place) counts as the surface.
                if (type == Material.BUBBLE_COLUMN) continue;
                if (type == Material.WATER) {
                    Levelled data = (Levelled) block.getBlockData();
                    if (data.getLevel() == 0) continue;
                }
                reachedSurface = true;
            }

            // Check if any horizontal neighbor at this y level is water
            if (!hasAdjacentWater(chunk, x, y, z)) break;

            // Block water flow at this level
            addBlockedSurface(ck, worldX, y, worldZ);
            count++;

            // Place a shallow (level 3) water block for a visual step
            block.setType(Material.WATER, false);
            Levelled waterData = (Levelled) block.getBlockData();
            waterData.setLevel(3);
            block.setBlockData(waterData, false);

            if (plugin.isDebug()) {
                plugin.getLogger().info("  Blocking water flow at [" + worldX
                        + ", " + y + ", " + worldZ + "] (level-3 water placed)");
            }
        }
        return count;
    }

    /** Returns true if any of the 4 horizontal neighbors at (x, y, z) is a water block. */
    private boolean hasAdjacentWater(Chunk chunk, int x, int y, int z) {
        for (int[] offset : SIDE_OFFSETS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            // For blocks at chunk edges, use world lookup
            Block neighbor;
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
                neighbor = chunk.getWorld().getBlockAt(
                        chunk.getX() * 16 + nx, y, chunk.getZ() * 16 + nz);
            } else {
                neighbor = chunk.getBlock(nx, y, nz);
            }
            Material nType = neighbor.getType();
            if (nType == Material.WATER || nType == Material.BUBBLE_COLUMN) return true;
        }
        return false;
    }

    // ---- Block replacement (runs on delayed tick) ----

    /**
     * New chunk: replace blue concrete with soul sand + bedrock below.
     * Surface registration happens after this method in the scheduled task.
     */
    private void processNewChunk(Chunk chunk) {
        int updateCount = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.BLUE_CONCRETE) continue;

                    if (updateCount > 60) break;
                    Block above = chunk.getBlock(x, y + 1, z);
                    if (above.getType() != Material.WATER) continue;

                    // Place bedrock below for future identification, then soul sand
                    Block below = chunk.getBlock(x, y - 1, z);
                    below.setType(Material.BEDROCK);
                    block.setType(Material.SOUL_SAND);
                    updateCount++;

                    // 1 in 1000 chance: place a dedication chest on an adjacent side
                    if (random.nextInt(1000) == 0) {
                        tryPlaceDedicationChest(chunk, x, y, z);
                    }
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
            Material sideType = side.getType();
            // Reject soul sand and blue concrete (other bubble columns)
            if (sideType == Material.SOUL_SAND || sideType == Material.BLUE_CONCRETE) continue;
            // Reject if the block above is solid (chest would be buried)
            Block aboveSide = chunk.getBlock(nx, y + 1, nz);
            if (aboveSide.getType().isSolid()) continue;
            candidates.add(new int[]{nx, nz});
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
