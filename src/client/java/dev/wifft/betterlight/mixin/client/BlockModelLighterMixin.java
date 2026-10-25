package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla block lighter without the CPU directional shade (the shaders apply it from the vertex normal), and without the
 * smooth lighting offset (MC-148689, MC-12558), as Indigo's fix-smooth-lighting-offset does.
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

	// The side checks that pick the corner sample look one block further out than the side's light and shade
	// (pos + side + face direction), so tight spaces darken corners and lights behind corners leak through.
	// Only those four moves go along the face direction; the diagonal moves are perpendicular to it.
	@WrapOperation(
		method = "prepareQuadAmbientOcclusion",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;move(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos$MutableBlockPos;")
	)
	private BlockPos.MutableBlockPos betterlight$noSideOffset(
		BlockPos.MutableBlockPos pos, Direction direction, Operation<BlockPos.MutableBlockPos> original, @Local(argsOnly = true) BakedQuad quad
	) {
		return direction == quad.direction() ? pos : original.call(pos, direction);
	}

	@ModifyArg(
		method = "prepareQuadFlat",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V")
	)
	private int betterlight$noFlatShade(int color) {
		return -1;
	}
}
