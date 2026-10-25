package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The normal written for a block quad honours shadeDirectionOverride, as the CPU shading did (now done in the shader). */
@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin {
	@ModifyExpressionValue(
		method = "putBlockBakedQuad",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad;direction()Lnet/minecraft/core/Direction;")
	)
	private Direction betterlight$shadeDirection(Direction direction, @Local(argsOnly = true) BakedQuad quad) {
		Direction shadeDirection = quad.materialInfo().shadeDirectionOverride();
		return shadeDirection != null ? shadeDirection : direction;
	}
}
