package dev.wifft.betterlight.duck;

import dev.wifft.betterlight.light.BlockLightOverlay;
import org.jspecify.annotations.Nullable;

/** Added to LightEngine (block engine only) and LevelLightEngine (forwards to its block engine). */
public interface BlockLightOverlayTarget {
	void betterlight$setBlockLightOverlay(@Nullable BlockLightOverlay overlay);
}
