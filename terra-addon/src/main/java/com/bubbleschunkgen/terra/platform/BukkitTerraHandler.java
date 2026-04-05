package com.bubbleschunkgen.terra.platform;

import com.bubbleschunkgen.common.*;
import com.dfsek.terra.bukkit.generator.BukkitChunkGeneratorWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Bukkit-specific listener registration for the Terra addon.
 * Registers with the first available Bukkit plugin (Terra itself).
 */
public class BukkitTerraHandler implements Listener {

    private final FlowBlocker flowBlocker = new FlowBlocker();
    private final Map<UUID, Boolean> chimeraWorldCache = new HashMap<>();
    private BubblesLogic logic;
    private boolean debug = false;
    private Plugin hostPlugin;

    public void register() {
        FlowBlocker.setGlobalInstance(flowBlocker);

        // Find the Terra plugin to register events with
        hostPlugin = Bukkit.getPluginManager().getPlugin("Terra");
        if (hostPlugin == null) {
            // Fallback: use any enabled plugin
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.isEnabled()) {
                    hostPlugin = p;
                    break;
                }
            }
        }

        if (hostPlugin == null) {
            Logger.getLogger("BubblesOnChunkGen").severe(
                    "No host plugin found to register Bukkit events with!");
            return;
        }

        PlatformBridge bridge = new PlatformBridge() {
            @Override public boolean isDebug() { return debug; }
            @Override public void setDebug(boolean d) { debug = d; }

            @Override
            public void log(String message) {
                hostPlugin.getLogger().info("[Bubbles] " + message);
            }

            @Override
            public void warn(String message) {
                hostPlugin.getLogger().warning("[Bubbles] " + message);
            }

            @Override
            public void runDelayed(Runnable task, long ticks) {
                Bukkit.getScheduler().runTaskLater(hostPlugin, task, ticks);
            }

            @Override
            public void fillDedicationChest(BlockAccess chunk, int localX, int y, int localZ) {
                Chunk bukkitChunk = Bukkit.getWorlds().get(0)
                        .getChunkAt(chunk.getChunkX(), chunk.getChunkZ());
                Block chestBlock = bukkitChunk.getBlock(localX, y, localZ);
                if (chestBlock.getType() != Material.CHEST) return;

                Chest chest = (Chest) chestBlock.getState();
                Inventory inv = chest.getInventory();

                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) book.getItemMeta();
                meta.setTitle("CHIMERA");
                meta.setAuthor("CHIMERA");
                meta.addPage("CHIMERA\n\nDedicated to Finnian and Armin");
                book.setItemMeta(meta);

                inv.setItem(0, book);
                inv.setItem(1, new ItemStack(Material.EMERALD, 6));
                inv.setItem(2, new ItemStack(Material.DIAMOND, 7));
            }
        };

        logic = new BubblesLogic(bridge, flowBlocker);
        Bukkit.getPluginManager().registerEvents(this, hostPlugin);
        registerDebugCommand(bridge);
    }

    private void registerDebugCommand(PlatformBridge bridge) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            org.bukkit.command.Command cmd = new org.bukkit.command.Command(
                    "bubblesdebug",
                    "Toggle debug logging for BubblesOnChunkGen",
                    "/bubblesdebug",
                    List.of()) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (!sender.isOp()) {
                        sender.sendMessage("You don't have permission to use this command.");
                        return true;
                    }
                    bridge.setDebug(!bridge.isDebug());
                    sender.sendMessage("BubblesOnChunkGen debug " + (bridge.isDebug() ? "ENABLED" : "DISABLED"));
                    return true;
                }
            };

            commandMap.register("bubbleschunkgen", cmd);
        } catch (Exception e) {
            Logger.getLogger("BubblesOnChunkGen").warning(
                    "Failed to register /bubblesdebug command: " + e.getMessage());
        }
    }

    private boolean isChimeraWorld(World world) {
        return chimeraWorldCache.computeIfAbsent(world.getUID(), uid -> {
            if (!(world.getGenerator() instanceof BukkitChunkGeneratorWrapper wrapper)) return false;
            String fullId = wrapper.getPack().getRegistryKey().toString();
            return fullId.toLowerCase().contains("chimera");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isChimeraWorld(event.getChunk().getWorld())) return;
        BukkitBlockAccess access = new BukkitBlockAccess(event.getChunk());
        if (event.isNewChunk()) {
            logic.onNewChunkLoad(access);
        } else {
            logic.onExistingChunkLoad(access);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!isChimeraWorld(event.getChunk().getWorld())) return;
        Chunk chunk = event.getChunk();
        logic.onChunkUnload(chunk.getX(), chunk.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        if (!isChimeraWorld(event.getBlock().getWorld())) return;
        Block from = event.getBlock();
        Block to = event.getToBlock();
        if (flowBlocker.shouldBlockFlow(
                from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ())) {
            event.setCancelled(true);
        }
    }

    /** Bukkit BlockAccess implementation for Terra addon. */
    private static class BukkitBlockAccess implements BlockAccess {
        private final Chunk chunk;

        BukkitBlockAccess(Chunk chunk) { this.chunk = chunk; }

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
            return ((Levelled) block.getBlockData()).getLevel();
        }

        @Override
        public void setWaterLevel(int localX, int y, int localZ, int level, boolean physics) {
            Block block = chunk.getBlock(localX, y, localZ);
            if (block.getType() != Material.WATER) return;
            Levelled data = (Levelled) block.getBlockData();
            data.setLevel(level);
            block.setBlockData(data, physics);
        }

        @Override
        public boolean isSolid(int localX, int y, int localZ) {
            return chunk.getBlock(localX, y, localZ).getType().isSolid();
        }

        @Override
        public boolean isWaterAtWorld(int worldX, int y, int worldZ) {
            return chunk.getWorld().getBlockAt(worldX, y, worldZ).getType() == Material.WATER;
        }

        @Override
        public boolean isBubbleColumnAtWorld(int worldX, int y, int worldZ) {
            return chunk.getWorld().getBlockAt(worldX, y, worldZ).getType() == Material.BUBBLE_COLUMN;
        }

        @Override public int getChunkX() { return chunk.getX(); }
        @Override public int getChunkZ() { return chunk.getZ(); }

        private static int materialToType(Material mat) {
            return switch (mat) {
                case WATER -> BLOCK_WATER;
                case BUBBLE_COLUMN -> BLOCK_BUBBLE_COLUMN;
                case BLUE_CONCRETE -> BLOCK_BLUE_CONCRETE;
                case SOUL_SAND -> BLOCK_SOUL_SAND;
                case BEDROCK -> BLOCK_BEDROCK;
                case CHEST -> BLOCK_CHEST;
                case AIR, VOID_AIR, CAVE_AIR -> BLOCK_AIR;
                default -> BLOCK_OTHER;
            };
        }

        private static Material typeToMaterial(int type) {
            return switch (type) {
                case BLOCK_WATER -> Material.WATER;
                case BLOCK_BUBBLE_COLUMN -> Material.BUBBLE_COLUMN;
                case BLOCK_BLUE_CONCRETE -> Material.BLUE_CONCRETE;
                case BLOCK_SOUL_SAND -> Material.SOUL_SAND;
                case BLOCK_BEDROCK -> Material.BEDROCK;
                case BLOCK_CHEST -> Material.CHEST;
                default -> Material.AIR;
            };
        }
    }
}
