package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wifft.betterlight.duck.LightFieldHolder;
import dev.wifft.betterlight.light.LightField;
import dev.wifft.betterlight.light.LightFieldVertexConsumer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wraps the buffer in a LightFieldVertexConsumer when the submit carries a light field. */
@Mixin(CustomFeatureRenderer.class)
public abstract class CustomFeatureRendererMixin {
	@ModifyExpressionValue(
		method = "buildGroup",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/CustomFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
	)
	private VertexConsumer betterlight$wrapLightField(VertexConsumer buffer, @Local CustomFeatureRenderer.Submit submit) {
		LightField field = LightFieldHolder.of(submit);
		return field != null ? new LightFieldVertexConsumer(buffer, field) : buffer;
	}
}
