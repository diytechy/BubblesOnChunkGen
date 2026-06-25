package com.bubbleschunkgen.terra.platform;

import com.bubbleschunkgen.common.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.bubbleschunkgen.common.BubblesConstants.*;

public class ForgeTerraHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");
    private final FlowBlocker flowBlocker = new FlowBlocker();
    private final BubblesLogic logic;
    private final Map<String, Boolean> chimeraWorldCache = new HashMap<>();
    private boolean debug = false;

    private final Queue<DelayedTask> delayedTasks = new ConcurrentLinkedQueue<>();

    public ForgeTerraHandler() {
        FlowBlocker.setGlobalInstance(flowBlocker);

        PlatformBridge bridge = new PlatformBridge() {
            @Override public boolean isDebug() { return debug; }
            @Override public void setDebug(boolean d) { debug = d; }
            @Override public void log(String message) { LOGGER.info(message); }
            @Override public void warn(String message) { LOGGER.warn(message); }

            @Override
            public void runDelayed(Runnable task, long ticks) {
                delayedTasks.add(new DelayedTask(task, ticks));
            }
        };

        logic = new BubblesLogic(bridge, flowBlocker);
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk levelChunk)) return;
        if (!isChimeraWorld(serverLevel)) return;

        logic.onChunkLoad(new ForgeBlockAccess(levelChunk, serverLevel));
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!isChimeraWorld(serverLevel)) return;
        
        if (event.getState().is(Blocks.SOUL_SAND)) {
            BlockPos pos = event.getPos();
            if (flowBlocker.isProtectedSoulSand(pos.getX(), pos.getY(), pos.getZ())) {
                if (!event.getPlayer().hasPermissions(2)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    private void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk levelChunk)) return;
        logic.onChunkUnload(levelChunk.getPos().x(), levelChunk.getPos().z());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        delayedTasks.removeIf(task -> {
            if (--task.ticksRemaining <= 0) {
                task.runnable.run();
                return true;
            }
            return false;
        });
    }

    private boolean isChimeraWorld(ServerLevel level) {
        return chimeraWorldCache.computeIfAbsent(level.dimension().toString(), k -> {
            try {
                ChunkGenerator gen = level.getChunkSource().getGenerator();
                Object pack = gen.getClass().getMethod("getPack").invoke(gen);
                if (pack == null) return false;
                Object key = pack.getClass().getMethod("getRegistryKey").invoke(pack);
                return key != null && key.toString().toLowerCase().contains("chimera");
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static class DelayedTask {
        final Runnable runnable;
        long ticksRemaining;

        DelayedTask(Runnable runnable, long ticks) {
            this.runnable = runnable;
            this.ticksRemaining = ticks;
        }
    }

    /** NeoForge implementation of BlockAccess wrapping a LevelChunk. */
    static class ForgeBlockAccess implements BlockAccess {
        private final LevelChunk chunk;
        private final ServerLevel level;

        ForgeBlockAccess(LevelChunk chunk, ServerLevel level) {
            this.chunk = chunk;
            this.level = level;
        }

        private BlockPos localToWorld(int localX, int y, int localZ) {
            return new BlockPos(chunk.getPos().x() * 16 + localX, y, chunk.getPos().z() * 16 + localZ);
        }

        @Override
        public int getBlockType(int localX, int y, int localZ) {
            BlockState state = level.getBlockState(localToWorld(localX, y, localZ));
            return blockStateToType(state);
        }

        @Override
        public void setBlockType(int localX, int y, int localZ, int type, boolean physics) {
            BlockPos pos = localToWorld(localX, y, localZ);
            BlockState state = typeToBlockState(type);
            int flags = physics ? 3 : 2;
            level.setBlock(pos, state, flags);
        }

        @Override
        public int getWaterLevel(int localX, int y, int localZ) {
            BlockState state = level.getBlockState(localToWorld(localX, y, localZ));
            if (!state.is(Blocks.WATER)) return -1;
            return state.getValue(LiquidBlock.LEVEL);
        }

        @Override
        public void setWaterLevel(int localX, int y, int localZ, int waterLevel, boolean physics) {
            BlockPos pos = localToWorld(localX, y, localZ);
            BlockState state = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, waterLevel);
            int flags = physics ? 3 : 2;
            this.level.setBlock(pos, state, flags);
        }

        @Override
        public int getBlockTypeAtWorld(int worldX, int y, int worldZ) {
            return blockStateToType(level.getBlockState(new BlockPos(worldX, y, worldZ)));
        }

        @Override public int getChunkX() { return chunk.getPos().x(); }
        @Override public int getChunkZ() { return chunk.getPos().z(); }

        private static int blockStateToType(BlockState state) {
            if (state.is(Blocks.WATER)) return BLOCK_WATER;
            if (state.is(Blocks.BUBBLE_COLUMN)) return BLOCK_BUBBLE_COLUMN;
            if (state.is(Blocks.SOUL_SAND)) return BLOCK_SOUL_SAND;
            if (state.is(Blocks.BEDROCK)) return BLOCK_BEDROCK;
            if (state.isAir()) return BLOCK_AIR;
            return BLOCK_OTHER;
        }

        private static BlockState typeToBlockState(int type) {
            return switch (type) {
                case BLOCK_WATER -> Blocks.WATER.defaultBlockState();
                case BLOCK_BUBBLE_COLUMN -> Blocks.BUBBLE_COLUMN.defaultBlockState();
                case BLOCK_SOUL_SAND -> Blocks.SOUL_SAND.defaultBlockState();
                case BLOCK_BEDROCK -> Blocks.BEDROCK.defaultBlockState();
                default -> Blocks.AIR.defaultBlockState();
            };
        }
    }
}
