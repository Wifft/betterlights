package dev.wifft.betterlight.duck;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** Light cells around an item frame (world space), captured at extraction; null for glow frames or with smooth lighting off. */
public interface ItemFrameLightCells {
	int @Nullable [] betterlight$getLightCells();

	BlockPos betterlight$getLightOrigin();

	void betterlight$setLightCells(int @Nullable [] cells, BlockPos origin);
}
