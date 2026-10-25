package dev.wifft.betterlight.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import dev.wifft.betterlight.light.BlockLightOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Only ever set on the client's block light engine (via LevelLightEngine). */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin implements BlockLightOverlayTarget {
	@Unique
	private volatile @Nullable BlockLightOverlay betterlight$overlay;

	@Override
	public void betterlight$setBlockLightOverlay(@Nullable BlockLightOverlay overlay) {
		this.betterlight$overlay = overlay;
	}

	@ModifyReturnValue(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
	private int betterlight$mergeOverlay(int level, BlockPos pos) {
		BlockLightOverlay overlay = this.betterlight$overlay;
		return overlay != null && level < 15 ? Math.max(level, overlay.getLightValue(pos.asLong())) : level;
	}
}
