package dev.wifft.betterlight.light;

/** Extra block light merged (max) into the block light engine's getLightValue, see FrontLightOverlay. */
public interface BlockLightOverlay {
	int getLightValue(long pos);
}
