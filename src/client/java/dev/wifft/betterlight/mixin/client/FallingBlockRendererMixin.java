package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.MovingBlockNeighbors;
import dev.wifft.betterlight.light.NeighborStates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Falling blocks (sand, gravel, anvils...) keep their AO while falling instead of losing it until they land. */
@Mixin(FallingBlockRenderer.class)
public abstract class FallingBlockRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/item/FallingBlockEntity;Lnet/minecraft/client/renderer/entity/state/FallingBlockRenderState;F)V",
		at = @At("TAIL")
	)
	private void betterlight$captureNeighbors(FallingBlockEntity entity, FallingBlockRenderState state, float partialTicks, CallbackInfo ci) {
		// The render state is reused every frame, and so is its neighbour array; cleared when the option is off
		MovingBlockRenderState movingBlock = state.movingBlockRenderState;
		MovingBlockNeighbors neighbors = (MovingBlockNeighbors) movingBlock;
		if (BetterlightConfig.movingBlockAo && entity.level() instanceof ClientLevel level) {
			BlockState[] states = neighbors.betterlight$getNeighbors();
			neighbors.betterlight$setNeighbors(NeighborStates.capture(level, movingBlock.blockPos, states != null ? states : new BlockState[27]));
		} else {
			neighbors.betterlight$setNeighbors(null);
		}
	}
}
