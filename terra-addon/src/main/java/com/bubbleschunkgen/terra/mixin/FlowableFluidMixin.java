package com.bubbleschunkgen.terra.mixin;

import com.bubbleschunkgen.common.FlowBlocker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Cancels water spread to or from frozen water coordinates. Safe no-op when no
 * FlowBlocker has been installed.
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
}
