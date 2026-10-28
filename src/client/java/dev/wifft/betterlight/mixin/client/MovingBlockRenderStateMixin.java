package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.MovingBlockNeighbors;
import dev.wifft.betterlight.light.NeighborStates;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's moving block level is air everywhere but the block itself, so falling and piston-moved blocks get no AO
 * from their surroundings. With captured neighbours, the AO calculator sees the real blocks around it.
 */
@Mixin(MovingBlockRenderState.class)
public abstract class MovingBlockRenderStateMixin implements MovingBlockNeighbors {
	@Shadow
	public BlockPos blockPos;

	@Unique
	private BlockState @Nullable [] betterlight$neighbors;
	@Override
	public void betterlight$setNeighbors(BlockState @Nullable [] neighbors) {
		this.betterlight$neighbors = neighbors;
	}

	@Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
	private void betterlight$neighborState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		BlockState[] neighbors = this.betterlight$neighbors;
		if (neighbors != null && !pos.equals(this.blockPos)) {
			BlockState state = NeighborStates.get(neighbors, this.blockPos, pos);
			if (state != null) {
				cir.setReturnValue(state);
			}
		}
	}
}
