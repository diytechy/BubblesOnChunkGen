package com.bubbleschunkgen.terra.mixin;

import com.bubbleschunkgen.common.FlowBlocker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps frozen water coordinates fixed. Two injections, mirroring the Bukkit
 * handlers: {@code spreadTo} blocks water flowing into or out of a frozen
 * coordinate; {@code tick} blocks a frozen block from changing in place — the
 * "infinite water" source conversion (a flowing block beside two sources) and the
 * decay of unsupported flowing water both happen during the block's own fluid
 * tick, not via {@code spreadTo}. Safe no-op when no FlowBlocker is installed.
 */
@Mixin(FlowingFluid.class)
public class FlowableFluidMixin {

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void bubbles$onSpreadTo(LevelAccessor level, BlockPos pos, BlockState blockState,
                                    Direction direction, FluidState fluidState, CallbackInfo ci) {
        FlowBlocker blocker = FlowBlocker.getGlobalInstance();
        if (blocker == null) return;
        if (!fluidState.is(FluidTags.WATER)) return;

        BlockPos source = direction == null ? pos : pos.relative(direction.getOpposite());
        if (!blocker.canFlow(source.getX(), source.getY(), source.getZ(), pos.getX(), pos.getY(), pos.getZ())) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bubbles$onTick(ServerLevel level, BlockPos pos, BlockState blockState,
                                FluidState fluidState, CallbackInfo ci) {
        FlowBlocker blocker = FlowBlocker.getGlobalInstance();
        if (blocker == null) return;
        if (!fluidState.is(FluidTags.WATER)) return;

        // A frozen coordinate must not change level in place (infinite-water source
        // conversion or decay). canFormSource() is false for frozen coords/chunks.
        if (!blocker.canFormSource(pos.getX(), pos.getY(), pos.getZ())) {
            ci.cancel();
        }
    }
}
