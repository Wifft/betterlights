package dev.wifft.betterlight.mixin.client;

import net.minecraft.client.renderer.block.BlockModelLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla block lighter without the CPU directional shade (the shaders apply it from the vertex normal).
 * With Fabric API terrain goes through Indigo; this covers Indigo's "vanilla" AO mode and anything else using the vanilla lighter.
 */
@Mixin(BlockModelLighter.class)
public abstract class BlockModelLighterMixin {
	@ModifyArg(
		method = "prepareQuadAmbientOcclusion",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/QuadInstance;scaleColor(F)V")
	)
	private float betterlight$noAoShade(float scale) {
		return 1.0F;
	}

	@ModifyArg(
		method = "prepareQuadFlat",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V")
	)
	private int betterlight$noFlatShade(int color) {
		return -1;
	}
}
