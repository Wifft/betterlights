package dev.wifft.betterlight.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import dev.wifft.betterlight.light.BlockLightOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Only ever set on the client's block light engine (via LevelLightEngine). */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin implements BlockLightOverlayTarget {
	@Shadow
	@Final
	protected LayerLightSectionStorage<?> storage;

	@Unique
	private volatile @Nullable BlockLightOverlay betterlight$overlay;

	@Override
	public void betterlight$setBlockLightOverlay(@Nullable BlockLightOverlay overlay) {
		this.betterlight$overlay = overlay;
	}

	@Override
	public int betterlight$getRawLightValue(long pos) {
		return ((LayerLightSectionStorageInvoker) this.storage).betterlight$invokeGetLightValue(pos);
	}

	@ModifyReturnValue(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
	private int betterlight$applyOverlay(int level, BlockPos pos) {
		BlockLightOverlay overlay = this.betterlight$overlay;
		return overlay != null ? overlay.getLightValue(pos.asLong(), level) : level;
	}
}
