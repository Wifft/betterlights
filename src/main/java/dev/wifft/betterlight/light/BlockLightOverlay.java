package dev.wifft.betterlight.light;

/** Replaces the block light engine's getLightValue on the client, see FrontLightOverlay and DirectionalLightOverlay. */
public interface BlockLightOverlay {
	int getLightValue(long pos, int engineLevel);
}
