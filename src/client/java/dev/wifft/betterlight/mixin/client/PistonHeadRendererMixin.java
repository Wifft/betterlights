package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.MovingBlockNeighbors;
import dev.wifft.betterlight.light.NeighborStates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks moved by pistons (and the piston head) keep their AO while moving. */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererMixin {
	@Inject(method = "createMovingBlock", at = @At("RETURN"))
	private static void betterlight$captureNeighbors(
		BlockPos pos, BlockState blockState, Holder<Biome> biome, ClientLevel level, CallbackInfoReturnable<MovingBlockRenderState> cir
	) {
		if (BetterlightConfig.movingBlockAo) {
			((MovingBlockNeighbors) cir.getReturnValue()).betterlight$setNeighbors(NeighborStates.capture(level, pos));
		}
	}
}
