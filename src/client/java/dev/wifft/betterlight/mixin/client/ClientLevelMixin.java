package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import dev.wifft.betterlight.light.FrontLightOverlay;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds FrontLightOverlay with block and chunk changes and hooks it into the client light engine. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Shadow
	@Final
	private ClientChunkCache chunkSource;

	@Unique
	private FrontLightOverlay betterlight$frontLight;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void betterlight$createFrontLight(CallbackInfo ci) {
		this.betterlight$frontLight = new FrontLightOverlay((ClientLevel) (Object) this);
		((BlockLightOverlayTarget) this.chunkSource.getLightEngine()).betterlight$setBlockLightOverlay(this.betterlight$frontLight);
	}

	@Inject(method = "unload", at = @At("TAIL"))
	private void betterlight$chunkUnloaded(LevelChunk levelChunk, CallbackInfo ci) {
		this.betterlight$frontLight.chunkUnloaded(levelChunk.getPos());
	}

	@Inject(method = "onChunkLoaded", at = @At("TAIL"))
	private void betterlight$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
		this.betterlight$frontLight.chunkLoaded(pos);
	}

	@Inject(method = "setBlocksDirty", at = @At("TAIL"))
	private void betterlight$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		this.betterlight$frontLight.blockChanged(pos, oldState, newState);
	}
}
