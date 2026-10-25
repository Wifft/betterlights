package dev.wifft.betterlight.mixin;

import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import dev.wifft.betterlight.light.BlockLightOverlay;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin implements BlockLightOverlayTarget {
	@Shadow
	@Final
	private @Nullable LightEngine<?, ?> blockEngine;

	@Override
	public void betterlight$setBlockLightOverlay(@Nullable BlockLightOverlay overlay) {
		if (this.blockEngine instanceof BlockLightEngine) {
			((BlockLightOverlayTarget) this.blockEngine).betterlight$setBlockLightOverlay(overlay);
		}
	}
}
