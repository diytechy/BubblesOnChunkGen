package com.bubbleschunkgen.terra.platform;

import com.bubbleschunkgen.common.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Minestom-specific listener registration for the Terra addon.
 *
 * Note: Minestom does not run vanilla fluid physics by default, so there is no
 * water-spread mixin needed and FlowBlocker is informational only. The level-3
 * water visual at the surface still works, and bubble columns generated via
 * placed soul sand under water will function once a player triggers nearby
 * physics by interacting with neighbouring blocks. If true vanilla behaviour
 * is required, the host application must enable fluid ticking on the Instance.
 */
public class MinestomTerraHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    private final FlowBlocker flowBlocker = new FlowBlocker();
    private final BubblesLogic logic;
    private final Map<UUID, Boolean> chimeraWorldCache = new HashMap<>();
    private final Map<UUID, Set<Long>> seenChunks = new ConcurrentHashMap<>();
    private boolean debug = false;

    public MinestomTerraHandler() {
        FlowBlocker.setGlobalInstance(flowBlocker);

        PlatformBridge bridge = new PlatformBridge() {
            @Override public boolean isDebug() { return debug; }
            @Override public void setDebug(boolean d) { debug = d; }
            @Override public void log(String message) { LOGGER.info(message); }
            @Override public void warn(String message) { LOGGER.warn(message); }

            @Override
            public void runDelayed(Runnable task, long ticks) {
                MinecraftServer.getSchedulerManager()
                        .buildTask(task)
                        .delay(TaskSchedule.tick((int) ticks))
                        .schedule();
            }

            @Override
            public void fillDedicationChest(BlockAccess chunk, int localX, int y, int localZ) {
                // Minestom doesn't have a built-in chest inventory tied to a placed block
                // the way Bukkit/Fabric/Forge do. Placing the chest is enough for the
                // marker; populating it would require the host app to attach an inventory
                // handler. Left as a no-op rather than a half-implementation.
                if (debug) {
                    LOGGER.info("[Bubbles] Dedication chest placed at local [{},{},{}] in chunk [{},{}] (contents not populated on Minestom)",
                            localX, y, localZ, chunk.getChunkX(), chunk.getChunkZ());
                }
            }
        };

        logic = new BubblesLogic(bridge, flowBlocker);
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(InstanceChunkLoadEvent.class, event -> {
            Instance instance = event.getInstance();
            if (!isChimeraInstance(instance)) return;

            int cx = event.getChunkX();
            int cz = event.getChunkZ();
            long ck = chunkKey(cx, cz);
            boolean firstSeen = seenChunks
                    .computeIfAbsent(instance.getUuid(), u -> ConcurrentHashMap.newKeySet())
                    .add(ck);

            MinestomBlockAccess access = new MinestomBlockAccess(instance, cx, cz);

            if (firstSeen && hasBlueConcrete(access)) {
                logic.onNewChunkLoad(access);
            } else {
                logic.onExistingChunkLoad(access);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(InstanceChunkUnloadEvent.class, event -> {
            if (!isChimeraInstance(event.getInstance())) return;
            logic.onChunkUnload(event.getChunkX(), event.getChunkZ());
        });
    }

    private boolean isChimeraInstance(Instance instance) {
        return chimeraWorldCache.computeIfAbsent(instance.getUuid(), uid -> {
            try {
                Object generator = instance.generator();
                if (generator == null) return false;
                Object pack = generator.getClass().getMethod("getPack").invoke(generator);
                if (pack == null) return false;
                Object key = pack.getClass().getMethod("getRegistryKey").invoke(pack);
                return key != null && key.toString().toLowerCase().contains("chimera");
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static boolean hasBlueConcrete(BlockAccess chunk) {
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (chunk.getBlockType(x, y, z) == BLOCK_BLUE_CONCRETE) return true;
                }
            }
        }
        return false;
    }

    /** Minestom implementation of BlockAccess - reads/writes blocks directly via the Instance. */
    static class MinestomBlockAccess implements BlockAccess {
        private final Instance instance;
        private final int chunkX;
        private final int chunkZ;

        MinestomBlockAccess(Instance instance, int chunkX, int chunkZ) {
            this.instance = instance;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private int worldX(int localX) { return chunkX * 16 + localX; }
        private int worldZ(int localZ) { return chunkZ * 16 + localZ; }

        @Override
        public int getBlockType(int localX, int y, int localZ) {
            return blockToType(instance.getBlock(worldX(localX), y, worldZ(localZ)));
        }

        @Override
        public void setBlockType(int localX, int y, int localZ, int type, boolean physics) {
            // Minestom's Instance#setBlock doesn't run vanilla physics; the 'physics' flag is
            // ignored. Neighbour updates will only fire if the host app installs handlers.
            instance.setBlock(worldX(localX), y, worldZ(localZ), typeToBlock(type));
        }

        @Override
        public int getWaterLevel(int localX, int y, int localZ) {
            Block b = instance.getBlock(worldX(localX), y, worldZ(localZ));
            if (!b.compare(Block.WATER)) return -1;
            String level = b.getProperty("level");
            if (level == null) return 0;
            try { return Integer.parseInt(level); } catch (NumberFormatException e) { return 0; }
        }

        @Override
        public void setWaterLevel(int localX, int y, int localZ, int level, boolean physics) {
            instance.setBlock(worldX(localX), y, worldZ(localZ),
                    Block.WATER.withProperty("level", Integer.toString(level)));
        }

        @Override
        public boolean isSolid(int localX, int y, int localZ) {
            return instance.getBlock(worldX(localX), y, worldZ(localZ)).isSolid();
        }

        @Override
        public boolean isWaterAtWorld(int worldX, int y, int worldZ) {
            return instance.getBlock(worldX, y, worldZ).compare(Block.WATER);
        }

        @Override
        public boolean isBubbleColumnAtWorld(int worldX, int y, int worldZ) {
            return instance.getBlock(worldX, y, worldZ).compare(Block.BUBBLE_COLUMN);
        }

        @Override public int getChunkX() { return chunkX; }
        @Override public int getChunkZ() { return chunkZ; }

        private static int blockToType(Block block) {
            if (block.compare(Block.WATER)) return BLOCK_WATER;
            if (block.compare(Block.BUBBLE_COLUMN)) return BLOCK_BUBBLE_COLUMN;
            if (block.compare(Block.BLUE_CONCRETE)) return BLOCK_BLUE_CONCRETE;
            if (block.compare(Block.SOUL_SAND)) return BLOCK_SOUL_SAND;
            if (block.compare(Block.BEDROCK)) return BLOCK_BEDROCK;
            if (block.compare(Block.CHEST)) return BLOCK_CHEST;
            if (block.isAir()) return BLOCK_AIR;
            return BLOCK_OTHER;
        }

        private static Block typeToBlock(int type) {
            return switch (type) {
                case BLOCK_WATER -> Block.WATER;
                case BLOCK_BUBBLE_COLUMN -> Block.BUBBLE_COLUMN;
                case BLOCK_BLUE_CONCRETE -> Block.BLUE_CONCRETE;
                case BLOCK_SOUL_SAND -> Block.SOUL_SAND;
                case BLOCK_BEDROCK -> Block.BEDROCK;
                case BLOCK_CHEST -> Block.CHEST;
                default -> Block.AIR;
            };
        }
    }
}
