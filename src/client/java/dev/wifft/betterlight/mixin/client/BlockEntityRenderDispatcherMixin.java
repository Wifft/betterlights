package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.LightFieldHolder;
import dev.wifft.betterlight.light.LightField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Captures a LightField per block entity at extraction and makes it current while the renderer submits (MC-231102). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Shadow
	private Vec3 cameraPos;

	@ModifyReturnValue(method = "tryExtractRenderState", at = @At("RETURN"))
	private @Nullable BlockEntityRenderState betterlight$captureLightField(
		@Nullable BlockEntityRenderState state, @Local(argsOnly = true) BlockEntity blockEntity
	) {
		if (state != null && BetterlightConfig.blockEntityLightField && Minecraft.getInstance().options.ambientOcclusion().get()) {
			((LightFieldHolder) state).betterlight$setLightField(
				LightField.capture(blockEntity.getLevel(), blockEntity.getBlockPos(), this.cameraPos, state.lightCoords)
			);
		}
		return state;
	}

	@WrapOperation(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
		)
	)
	private void betterlight$submitWithLightField(
		BlockEntityRenderer<?, ?> renderer,
		BlockEntityRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState camera,
		Operation<Void> original
	) {
		LightField.setCurrent(LightFieldHolder.of(state));
		try {
			original.call(renderer, state, poseStack, submitNodeCollector, camera);
		} finally {
			LightField.setCurrent(null);
		}
	}
}
