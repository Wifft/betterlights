package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.LightCellCacheHolder;
import dev.wifft.betterlight.light.LightCellCache;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Created on first use, so block entities and hanging entities that never render carry only a null field. */
@Mixin({BlockEntity.class, HangingEntity.class})
public abstract class LightCellCacheHolderMixin implements LightCellCacheHolder {
	@Unique
	private @Nullable LightCellCache betterlight$lightCellCache;

	@Override
	public LightCellCache betterlight$lightCellCache() {
		LightCellCache cache = this.betterlight$lightCellCache;
		if (cache == null) {
			cache = new LightCellCache();
			this.betterlight$lightCellCache = cache;
		}
		return cache;
	}
}
