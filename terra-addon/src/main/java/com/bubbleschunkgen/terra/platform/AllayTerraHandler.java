package com.bubbleschunkgen.terra.platform;

import com.bubbleschunkgen.common.*;
import org.allaymc.api.block.property.type.BlockPropertyTypes;
import org.allaymc.api.block.type.BlockState;
import org.allaymc.api.block.type.BlockTypes;
import org.allaymc.api.eventbus.event.block.LiquidDecayEvent;
import org.allaymc.api.eventbus.event.block.LiquidFlowEvent;
import org.allaymc.api.eventbus.event.world.ChunkLoadEvent;
import org.allaymc.api.eventbus.event.world.ChunkUnloadEvent;
import org.allaymc.api.server.Server;
import org.allaymc.api.world.Dimension;
import org.allaymc.api.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bubbleschunkgen.common.BubblesConstants.*;

/**
 * Allay (Bedrock-edition server) listener registration for the Terra addon.
 *
 * EXPERIMENTAL — Bedrock semantics differ from Java in several places:
 *  - Water uses the {@code liquid_depth} property (0 = source, 1-7 = falling),
 *    not Java's {@code level}. The thin surface-step visual is approximated
 *    via liquid_depth and may need tuning.
 *  - Vanilla bubble column physics may behave differently; soul-sand-under-water
 *    placement should still create a column.
 *  - Block IDs differ in casing/namespacing on Bedrock but Allay's BlockTypes
 *    constants normalise them.
 *
 * No CHIMERA-world filter is applied here; chunks with no bedrock signature in
 * the scan range simply yield no columns, so non-CHIMERA worlds are a cheap no-op.
 */
public class AllayTerraHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    private final FlowBlocker flowBlocker = new FlowBlocker();
    private final BubblesLogic logic;
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
        };

        logic = new BubblesLogic(bridge, flowBlocker);
    }

    public void register() {
        var bus = Server.getInstance().getEventBus();

        bus.registerListenerFor(ChunkLoadEvent.class, this::onChunkLoad);
        bus.registerListenerFor(ChunkUnloadEvent.class, this::onChunkUnload);
        bus.registerListenerFor(LiquidFlowEvent.class, this::onLiquidFlow);
        bus.registerListenerFor(LiquidDecayEvent.class, this::onLiquidDecay);
    }

    private void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        logic.onChunkLoad(new AllayBlockAccess(event.getDimension(), chunk.getX(), chunk.getZ()));
    }

    private void onChunkUnload(ChunkUnloadEvent event) {
        logic.onChunkUnload(event.getChunk().getX(), event.getChunk().getZ());
    }

    private void onLiquidFlow(LiquidFlowEvent event) {
        var from = event.getBlock().getPosition();
        var into = event.getInto();
        if (!flowBlocker.canFlow(from.x(), from.y(), from.z(), into.x(), into.y(), into.z())) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps a frozen liquid from decaying away (the Bedrock analog of Java water
     * dissipating). Bedrock has no dedicated in-place source-conversion event, and
     * its fluid mechanics differ from Java, so the full parity of the other
     * platforms is not guaranteed here — verify on a real Allay server.
     */
    private void onLiquidDecay(LiquidDecayEvent event) {
        var pos = event.getBlock().getPosition();
        if (!flowBlocker.canFormSource(pos.x(), pos.y(), pos.z())) {
            event.setCancelled(true);
        }
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
        public int getBlockTypeAtWorld(int worldX, int y, int worldZ) {
            return blockStateToType(dimension.getBlockState(worldX, y, worldZ));
        }

        @Override public int getChunkX() { return chunkX; }
        @Override public int getChunkZ() { return chunkZ; }

        private static int blockStateToType(BlockState state) {
            var t = state.getBlockType();
            if (t == BlockTypes.WATER) return BLOCK_WATER;
            if (t == BlockTypes.BUBBLE_COLUMN) return BLOCK_BUBBLE_COLUMN;
            if (t == BlockTypes.SOUL_SAND) return BLOCK_SOUL_SAND;
            if (t == BlockTypes.BEDROCK) return BLOCK_BEDROCK;
            if (t == BlockTypes.AIR) return BLOCK_AIR;
            return BLOCK_OTHER;
        }

        private static BlockState typeToBlockState(int type) {
            return switch (type) {
                case BLOCK_WATER -> BlockTypes.WATER.getDefaultState();
                case BLOCK_BUBBLE_COLUMN -> BlockTypes.BUBBLE_COLUMN.getDefaultState();
                case BLOCK_SOUL_SAND -> BlockTypes.SOUL_SAND.getDefaultState();
                case BLOCK_BEDROCK -> BlockTypes.BEDROCK.getDefaultState();
                default -> BlockTypes.AIR.getDefaultState();
            };
        }
    }
}
