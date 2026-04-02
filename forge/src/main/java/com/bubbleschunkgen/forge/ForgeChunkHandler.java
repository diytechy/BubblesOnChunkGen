package com.bubbleschunkgen.forge;

import com.bubbleschunkgen.common.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.bubbleschunkgen.common.BubblesConstants.*;

public class ForgeChunkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen");
    private final FlowBlocker flowBlocker = new FlowBlocker();
    private final BubblesLogic logic;
    private boolean debug = false;

    // Delayed task queue - processed each server tick
    private final Queue<DelayedTask> delayedTasks = new ConcurrentLinkedQueue<>();

    public ForgeChunkHandler() {
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

            @Override
            public void fillDedicationChest(BlockAccess chunk, int localX, int y, int localZ) {
                // Forge chest filling handled via block entity
                if (chunk instanceof ForgeBlockAccess fba) {
                    BlockPos pos = new BlockPos(
                            fba.getChunkX() * 16 + localX, y, fba.getChunkZ() * 16 + localZ);
                    if (fba.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                        book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                                new WrittenBookContent(
                                        Filterable.passThrough("CHIMERA"),
                                        "CHIMERA",
                                        0,
                                        List.of(Filterable.passThrough(
                                                Component.literal("CHIMERA\n\nDedicated to Finnian and Armin"))),
                                        true));
                        chest.setItem(0, book);
                        chest.setItem(1, new ItemStack(Items.EMERALD, 6));
                        chest.setItem(2, new ItemStack(Items.DIAMOND, 7));
                    }
                }
            }
        };

        logic = new BubblesLogic(bridge, flowBlocker);
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk levelChunk)) return;

        // TODO: Add Terra world detection for Forge
        ForgeBlockAccess access = new ForgeBlockAccess(levelChunk, serverLevel);

        // NeoForge doesn't distinguish new vs existing chunks in the event.
        // We detect new chunks by checking if soul sand markers or bedrock signatures exist.
        // For now, treat all chunks as existing (scan for signatures).
        logic.onExistingChunkLoad(access);
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

        ServerLevel getLevel() { return level; }

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
            int flags = physics ? 3 : 2; // 3 = notify + update, 2 = notify only
            level.setBlock(pos, state, flags);
        }

        @Override
        public int getWaterLevel(int localX, int y, int localZ) {
            BlockState state = level.getBlockState(localToWorld(localX, y, localZ));
            if (!state.is(Blocks.WATER)) return -1;
            return state.getValue(LiquidBlock.LEVEL);
        }

        @Override
        public void setWaterLevel(int localX, int y, int localZ, int level, boolean physics) {
            BlockPos pos = localToWorld(localX, y, localZ);
            BlockState state = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
            int flags = physics ? 3 : 2;
            this.level.setBlock(pos, state, flags);
        }

        @Override
        public boolean isSolid(int localX, int y, int localZ) {
            return level.getBlockState(localToWorld(localX, y, localZ)).isSolid();
        }

        @Override
        public boolean isWaterAtWorld(int worldX, int y, int worldZ) {
            return level.getBlockState(new BlockPos(worldX, y, worldZ)).is(Blocks.WATER);
        }

        @Override
        public boolean isBubbleColumnAtWorld(int worldX, int y, int worldZ) {
            return level.getBlockState(new BlockPos(worldX, y, worldZ)).is(Blocks.BUBBLE_COLUMN);
        }

        @Override public int getChunkX() { return chunk.getPos().x(); }
        @Override public int getChunkZ() { return chunk.getPos().z(); }

        private static int blockStateToType(BlockState state) {
            if (state.is(Blocks.WATER)) return BLOCK_WATER;
            if (state.is(Blocks.BUBBLE_COLUMN)) return BLOCK_BUBBLE_COLUMN;
            if (state.is(Blocks.BLUE_CONCRETE)) return BLOCK_BLUE_CONCRETE;
            if (state.is(Blocks.SOUL_SAND)) return BLOCK_SOUL_SAND;
            if (state.is(Blocks.BEDROCK)) return BLOCK_BEDROCK;
            if (state.is(Blocks.CHEST)) return BLOCK_CHEST;
            if (state.isAir()) return BLOCK_AIR;
            return BLOCK_OTHER;
        }

        private static BlockState typeToBlockState(int type) {
            return switch (type) {
                case BLOCK_WATER -> Blocks.WATER.defaultBlockState();
                case BLOCK_BUBBLE_COLUMN -> Blocks.BUBBLE_COLUMN.defaultBlockState();
                case BLOCK_BLUE_CONCRETE -> Blocks.BLUE_CONCRETE.defaultBlockState();
                case BLOCK_SOUL_SAND -> Blocks.SOUL_SAND.defaultBlockState();
                case BLOCK_BEDROCK -> Blocks.BEDROCK.defaultBlockState();
                case BLOCK_CHEST -> Blocks.CHEST.defaultBlockState();
                default -> Blocks.AIR.defaultBlockState();
            };
        }
    }
}
