package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.wifft.betterlight.render.BetterlightFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Terrain and moving-block pipelines get a Normal attribute and the Lighting UBO, so the shaders can do the directional shading.
 * Targets the builder lambdas of terrain(...) and block(...) by their synthetic names: fragile, check with javap when porting.
 */
@Mixin(RenderPipelines.class)
public abstract class RenderPipelinesMixin {
	@WrapOperation(
		method = {"lambda$terrain$0", "lambda$block$0"},
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withVertexBinding(ILcom/mojang/blaze3d/vertex/VertexFormat;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"
		),
		require = 3
	)
	private static RenderPipeline.Builder betterlight$blockWithNormal(
		RenderPipeline.Builder builder, int bindingIndex, VertexFormat format, Operation<RenderPipeline.Builder> original
	) {
		if (format != DefaultVertexFormat.BLOCK) {
			return original.call(builder, bindingIndex, format);
		}
		return original.call(builder, bindingIndex, BetterlightFormats.BLOCK_WITH_NORMAL).withBindGroupLayout(BindGroupLayouts.LIGHTING);
	}
}
