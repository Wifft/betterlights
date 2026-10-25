package dev.wifft.betterlight.duck;

import dev.wifft.betterlight.light.LightCellCache;

/** Added to BlockEntity and HangingEntity (item frames, paintings): their light cells, reused within a game tick. */
public interface LightCellCacheHolder {
	LightCellCache betterlight$lightCellCache();

	static LightCellCache of(Object holder) {
		return ((LightCellCacheHolder) holder).betterlight$lightCellCache();
	}
}
