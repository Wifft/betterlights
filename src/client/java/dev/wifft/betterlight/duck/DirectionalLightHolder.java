package dev.wifft.betterlight.duck;

import dev.wifft.betterlight.light.DirectionalLightOverlay;

/** The ClientLevel's emitter overlays. */
public interface DirectionalLightHolder {
	DirectionalLightOverlay betterlight$getDirectionalLight();

	// What counts as an emitter changed: both overlays look again (EmitterLightOverlay.rescan)
	void betterlight$rescanEmitters();
}
