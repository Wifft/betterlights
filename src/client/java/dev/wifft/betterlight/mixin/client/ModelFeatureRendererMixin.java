package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wifft.betterlight.light.LightFieldVertexConsumer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wraps the buffer in a LightFieldVertexConsumer when the submit carries a light field. */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
	@ModifyExpressionValue(
		method = "prepareModel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
	)
	private VertexConsumer betterlight$wrapLightField(VertexConsumer buffer, @Local(argsOnly = true) ModelFeatureRenderer.Submit<?> submit) {
		return LightFieldVertexConsumer.wrap(buffer, submit);
	}
}
