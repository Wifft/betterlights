package dev.wifft.betterlight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wifft.betterlight.light.FrontEmitters;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Front emitters emit nothing in the engine. Covers getEmission and the propagateLightSources lambda;
 * an increase enqueued with emission 0 propagates nothing, so the patch's "if (lightEmission > 0)" isn't needed.
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
	@WrapOperation(
		method = "*",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"),
		require = 2
	)
	private int betterlight$frontEmitterEmission(BlockState state, Operation<Integer> original) {
		return FrontEmitters.isFrontEmitter(state) ? 0 : original.call(state);
	}
}
