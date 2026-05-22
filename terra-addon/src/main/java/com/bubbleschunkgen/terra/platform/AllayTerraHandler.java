package com.bubbleschunkgen.terra.platform;

import com.bubbleschunkgen.common.*;
import org.allaymc.api.block.property.type.BlockPropertyTypes;
import org.allaymc.api.block.type.BlockState;
import org.allaymc.api.block.type.BlockTypes;
import org.allaymc.api.eventbus.event.block.LiquidFlowEvent;
import org.allaymc.api.eventbus.event.world.ChunkLoadEvent;
import org.allaymc.api.eventbus.event.world.ChunkUnloadEvent;
import org.allaymc.api.server.Server;
import org.allaymc.api.world.Dimension;
import org.allaymc.api.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Allay (Bedrock-edition server) listener registration for the Terra addon.
 *
 * EXPERIMENTAL — Bedrock semantics differ from Java in several places:
 *  - Water uses the {@code liquid_depth} property (0 = source, 1-7 = falling),
 *    not Java's {@code level}. The level-3 surface-step visual is approximated
 *    via liquid_depth = 3 and may need tuning.
 *  - Vanilla bubble column physics may behave differently; soul-sand-under-water
 *    placement should still create a column.
 *  - Block IDs differ in casing/namespacing on Bedrock but Allay's BlockTypes
 *    constants normalise them.
 *
 * No CHIMERA-world filter is applied here. The {@code hasBlueConcrete} pre-scan
 * makes non-CHIMERA worlds cheap to ignore.
 */
public class AllayTerraHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    private final FlowBlocker flowBlocker = new FlowBlocker();
    private final BubblesLogic logic;
    private final Map<String, Set<Long>> seenChunks = new ConcurrentHashMap<>();
    private boolean debug = false;

    public AllayTerraHandler() {
        FlowBlocker.setGlobalInstance(flowBlocker);

        PlatformBridge bridge = new PlatformBridge() {
            @Override public boolean isDebug() { return debug; }
            @Override public void setDebug(boolean d) { debug = d; }
            @Override public void log(String message) { LOGGER.info(message); }
            @Override public void warn(String message) { LOGGER.warn(message); }

            @Override
            public void runDelayed(Runnable task, long ticks) {
                Server server = Server.getInstance();
                server.getScheduler().scheduleDelayed(server, task, (int) ticks);
            }

            @Override
            public void fillDedicationChest(BlockAccess chunk, int localX, int y, int localZ) {
                // Bedrock chest inventory population would need Allay's container/block-entity API,
                // which differs significantly from Bukkit/Fabric/Forge. Left as a marker placement
                // for now; the chest is placed but not filled.
                if (debug) {
                    LOGGER.info("[Bubbles] Dedication chest placed at local [{},{},{}] in chunk [{},{}] (contents not populated on Allay)",
                            localX, y, localZ, chunk.getChunkX(), chunk.getChunkZ());
                }
            }
        };

        logic = new BubblesLogic(bridge, flowBlocker);
    }

    public void register() {
        var bus = Server.getInstance().getEventBus();

        bus.registerListenerFor(ChunkLoadEvent.class, this::onChunkLoad);
        bus.registerListenerFor(ChunkUnloadEvent.class, this::onChunkUnload);
        bus.registerListenerFor(LiquidFlowEvent.class, this::onLiquidFlow);
    }

    private void onChunkLoad(ChunkLoadEvent event) {
        Dimension dimension = event.getDimension();
        Chunk chunk = event.getChunk();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        String dimKey = dimensionKey(dimension);
        long ck = chunkKey(cx, cz);
        boolean firstSeen = seenChunks
                .computeIfAbsent(dimKey, k -> ConcurrentHashMap.newKeySet())
                .add(ck);

        AllayBlockAccess access = new AllayBlockAccess(dimension, cx, cz);

        if (firstSeen && hasBlueConcrete(access)) {
            logic.onNewChunkLoad(access);
        } else {
            logic.onExistingChunkLoad(access);
        }
    }

    private void onChunkUnload(ChunkUnloadEvent event) {
        logic.onChunkUnload(event.getChunk().getX(), event.getChunk().getZ());
    }

    private void onLiquidFlow(LiquidFlowEvent event) {
        var into = event.getInto();
        if (flowBlocker.shouldBlockFlow(
                into.x(), into.y(), into.z(),
                into.x(), into.y(), into.z())) {
            event.setCancelled(true);
        }
    }

    private static String dimensionKey(Dimension dimension) {
        return dimension.getWorld().getWorldData().getDisplayName()
                + "/"
                + dimension.getDimensionInfo().dimensionId();
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

    /** Allay implementation of BlockAccess wrapping a Dimension. */
    static class AllayBlockAccess implements BlockAccess {
        private final Dimension dimension;
        private final int chunkX;
        private final int chunkZ;

        AllayBlockAccess(Dimension dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private int worldX(int localX) { return chunkX * 16 + localX; }
        private int worldZ(int localZ) { return chunkZ * 16 + localZ; }

        @Override
        public int getBlockType(int localX, int y, int localZ) {
            return blockStateToType(dimension.getBlockState(worldX(localX), y, worldZ(localZ)));
        }

        @Override
        public void setBlockType(int localX, int y, int localZ, int type, boolean physics) {
            dimension.setBlockState(worldX(localX), y, worldZ(localZ), typeToBlockState(type));
        }

        @Override
        public int getWaterLevel(int localX, int y, int localZ) {
            BlockState state = dimension.getBlockState(worldX(localX), y, worldZ(localZ));
            if (state.getBlockType() != BlockTypes.WATER) return -1;
            try {
                return state.getPropertyValue(BlockPropertyTypes.LIQUID_DEPTH);
            } catch (Exception e) {
                return 0;
            }
        }

        @Override
        public void setWaterLevel(int localX, int y, int localZ, int level, boolean physics) {
            BlockState state = BlockTypes.WATER.ofState(
                    BlockPropertyTypes.LIQUID_DEPTH.createValue(level));
            dimension.setBlockState(worldX(localX), y, worldZ(localZ), state);
        }

        @Override
        public boolean isSolid(int localX, int y, int localZ) {
            // Allay has no SOLID block tag. Best-effort: anything other than air/water/cave_air
            // is treated as solid for chest-placement validation.
            BlockState state = dimension.getBlockState(worldX(localX), y, worldZ(localZ));
            var type = state.getBlockType();
            return type != BlockTypes.AIR && type != BlockTypes.WATER;
        }

        @Override
        public boolean isWaterAtWorld(int worldX, int y, int worldZ) {
            return dimension.getBlockState(worldX, y, worldZ).getBlockType() == BlockTypes.WATER;
        }

        @Override
        public boolean isBubbleColumnAtWorld(int worldX, int y, int worldZ) {
            return dimension.getBlockState(worldX, y, worldZ).getBlockType() == BlockTypes.BUBBLE_COLUMN;
        }

        @Override public int getChunkX() { return chunkX; }
        @Override public int getChunkZ() { return chunkZ; }

        private static int blockStateToType(BlockState state) {
            var t = state.getBlockType();
            if (t == BlockTypes.WATER) return BLOCK_WATER;
            if (t == BlockTypes.BUBBLE_COLUMN) return BLOCK_BUBBLE_COLUMN;
            if (t == BlockTypes.BLUE_CONCRETE) return BLOCK_BLUE_CONCRETE;
            if (t == BlockTypes.SOUL_SAND) return BLOCK_SOUL_SAND;
            if (t == BlockTypes.BEDROCK) return BLOCK_BEDROCK;
            if (t == BlockTypes.CHEST) return BLOCK_CHEST;
            if (t == BlockTypes.AIR) return BLOCK_AIR;
            return BLOCK_OTHER;
        }

        private static BlockState typeToBlockState(int type) {
            return switch (type) {
                case BLOCK_WATER -> BlockTypes.WATER.getDefaultState();
                case BLOCK_BUBBLE_COLUMN -> BlockTypes.BUBBLE_COLUMN.getDefaultState();
                case BLOCK_BLUE_CONCRETE -> BlockTypes.BLUE_CONCRETE.getDefaultState();
                case BLOCK_SOUL_SAND -> BlockTypes.SOUL_SAND.getDefaultState();
                case BLOCK_BEDROCK -> BlockTypes.BEDROCK.getDefaultState();
                case BLOCK_CHEST -> BlockTypes.CHEST.getDefaultState();
                default -> BlockTypes.AIR.getDefaultState();
            };
        }
    }
}
