package dev.wifft.betterlight.duck;

import dev.wifft.betterlight.light.DirectionalLightOverlay;
import net.minecraft.world.level.ChunkPos;

/** The ClientLevel's emitter overlays. */
public interface DirectionalLightHolder {
	DirectionalLightOverlay betterlight$getDirectionalLight();

	// What counts as an emitter changed: both overlays look again (EmitterLightOverlay.rescan)
	void betterlight$rescanEmitters();

	// A loaded chunk is about to be replaced by new data from the server: both overlays drop it as if unloaded, and
	// pick it up again from the onChunkLoaded that follows
	void betterlight$chunkReplaced(ChunkPos pos);
}
