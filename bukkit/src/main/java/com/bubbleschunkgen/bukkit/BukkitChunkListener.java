package com.bubbleschunkgen.bukkit;

import com.bubbleschunkgen.common.*;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.ChunkGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.bubbleschunkgen.common.BubblesConstants.*;

public class BukkitChunkListener implements Listener {

    private final BukkitPlugin plugin;
    private final BubblesLogic logic;
    private final FlowBlocker flowBlocker;
    private final Map<UUID, Boolean> terraWorldCache = new HashMap<>();

    public BukkitChunkListener(BukkitPlugin plugin) {
        this.plugin = plugin;
        this.flowBlocker = new FlowBlocker();
        FlowBlocker.setGlobalInstance(this.flowBlocker);

        PlatformBridge bridge = new PlatformBridge() {
            @Override
            public boolean isDebug() { return plugin.isDebug(); }

            @Override
            public void setDebug(boolean debug) { plugin.setDebug(debug); }

            @Override
            public void log(String message) { plugin.getLogger().info(message); }

            @Override
            public void warn(String message) { plugin.getLogger().warning(message); }

            @Override
            public void runDelayed(Runnable task, long ticks) {
                plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
            }
        };

        this.logic = new BubblesLogic(bridge, flowBlocker);
    }

    private boolean isTerraWorld(World world) {
        return terraWorldCache.computeIfAbsent(world.getUID(), uid -> {
            ChunkGenerator gen = world.getGenerator();
            return gen != null
                    && gen.getClass().getName().toLowerCase().contains("terra");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (!isTerraWorld(chunk.getWorld())) return;

        logic.onChunkLoad(new BukkitBlockAccess(chunk));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!isTerraWorld(event.getChunk().getWorld())) return;
        Chunk chunk = event.getChunk();
        logic.onChunkUnload(chunk.getX(), chunk.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        if (!isTerraWorld(event.getBlock().getWorld())) return;
        Block from = event.getBlock();
        Block to = event.getToBlock();

        if (flowBlocker.shouldBlockFlow(
                from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ())) {
            event.setCancelled(true);
        }
    }

    /** Bukkit implementation of BlockAccess wrapping a Chunk. */
    private static class BukkitBlockAccess implements BlockAccess {
        private final Chunk chunk;

        BukkitBlockAccess(Chunk chunk) {
            this.chunk = chunk;
        }

        @Override
        public int getBlockType(int localX, int y, int localZ) {
            return materialToType(chunk.getBlock(localX, y, localZ).getType());
        }

        @Override
        public void setBlockType(int localX, int y, int localZ, int type, boolean physics) {
            chunk.getBlock(localX, y, localZ).setType(typeToMaterial(type), physics);
        }

        @Override
        public int getWaterLevel(int localX, int y, int localZ) {
            Block block = chunk.getBlock(localX, y, localZ);
            if (block.getType() != Material.WATER) return -1;
            Levelled data = (Levelled) block.getBlockData();
            return data.getLevel();
        }

        @Override
        public void setWaterLevel(int localX, int y, int localZ, int level, boolean physics) {
            Block block = chunk.getBlock(localX, y, localZ);
            if (block.getType() != Material.WATER) return;
            Levelled waterData = (Levelled) block.getBlockData();
            waterData.setLevel(level);
            block.setBlockData(waterData, physics);
        }

        @Override
        public int getBlockTypeAtWorld(int worldX, int y, int worldZ) {
            return materialToType(chunk.getWorld().getBlockAt(worldX, y, worldZ).getType());
        }

        @Override
        public int getChunkX() { return chunk.getX(); }

        @Override
        public int getChunkZ() { return chunk.getZ(); }

        private static int materialToType(Material mat) {
            return switch (mat) {
                case WATER -> BLOCK_WATER;
                case BUBBLE_COLUMN -> BLOCK_BUBBLE_COLUMN;
                case SOUL_SAND -> BLOCK_SOUL_SAND;
                case BEDROCK -> BLOCK_BEDROCK;
                case AIR, VOID_AIR, CAVE_AIR -> BLOCK_AIR;
                default -> BLOCK_OTHER;
            };
        }

        private static Material typeToMaterial(int type) {
            return switch (type) {
                case BLOCK_WATER -> Material.WATER;
                case BLOCK_BUBBLE_COLUMN -> Material.BUBBLE_COLUMN;
                case BLOCK_SOUL_SAND -> Material.SOUL_SAND;
                case BLOCK_BEDROCK -> Material.BEDROCK;
                default -> Material.AIR;
            };
        }
    }
}
