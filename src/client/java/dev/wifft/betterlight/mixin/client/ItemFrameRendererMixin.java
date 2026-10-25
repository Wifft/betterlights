package dev.wifft.betterlight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.ItemFrameLightCells;
import dev.wifft.betterlight.light.LightField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame model, map and item get a LightField around the frame (MC-1531). Glow frames are left alone. */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin {
	@Unique
	private static final String SUBMIT =
		"submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
		at = @At("TAIL")
	)
	private void betterlight$captureLightCells(ItemFrame entity, ItemFrameRenderState state, float partialTicks, CallbackInfo ci) {
		ItemFrameLightCells cells = (ItemFrameLightCells) state;
		if (!state.isGlowFrame && BetterlightConfig.smoothPaintingsFrames && Minecraft.getInstance().options.ambientOcclusion().get()) {
			cells.betterlight$setLightCells(LightField.captureCells(entity.level(), entity.blockPosition()), entity.blockPosition());
		} else {
			cells.betterlight$setLightCells(null, cells.betterlight$getLightOrigin());
		}
	}

	// Same span as the patch: everything submitted after super.submit
	@Inject(
		method = SUBMIT,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			shift = At.Shift.AFTER
		)
	)
	private void betterlight$setLightField(
		ItemFrameRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci
	) {
		ItemFrameLightCells cells = (ItemFrameLightCells) state;
		int[] lightCells = cells.betterlight$getLightCells();
		LightField.setCurrent(lightCells != null ? LightField.of(lightCells, cells.betterlight$getLightOrigin(), camera.pos, state.lightCoords) : null);
	}

	@Inject(method = SUBMIT, at = @At("TAIL"))
	private void betterlight$clearLightField(
		ItemFrameRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci
	) {
		LightField.setCurrent(null);
	}
}
