package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import dev.wifft.betterlight.duck.MovingBlockShadowsHolder;
import dev.wifft.betterlight.light.FrontLightOverlay;
import dev.wifft.betterlight.light.MovingBlockShadows;
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

/**
 * Feeds FrontLightOverlay with block and chunk changes and hooks it into the client light engine.
 * Also owns the level's MovingBlockShadows, updated once per frame.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements MovingBlockShadowsHolder {
	@Shadow
	@Final
	private ClientChunkCache chunkSource;

	@Unique
	private FrontLightOverlay betterlight$frontLight;
	@Unique
	private MovingBlockShadows betterlight$movingBlockShadows;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void betterlight$createFrontLight(CallbackInfo ci) {
		this.betterlight$frontLight = new FrontLightOverlay((ClientLevel) (Object) this);
		((BlockLightOverlayTarget) this.chunkSource.getLightEngine()).betterlight$setBlockLightOverlay(this.betterlight$frontLight);
		this.betterlight$movingBlockShadows = new MovingBlockShadows((ClientLevel) (Object) this);
	}

	@Override
	public MovingBlockShadows betterlight$getMovingBlockShadows() {
		return this.betterlight$movingBlockShadows;
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

	// Once per frame, next to the vanilla light updates: refills the furnaces marked dirty since the last frame
	@Inject(method = "update", at = @At("TAIL"))
	private void betterlight$updateFrontLight(CallbackInfo ci) {
		this.betterlight$frontLight.update();
		this.betterlight$movingBlockShadows.update();
	}
}
