package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import dev.wifft.betterlight.duck.DirectionalLightHolder;
import dev.wifft.betterlight.duck.MovingBlockShadowsHolder;
import dev.wifft.betterlight.light.DirectionalLightOverlay;
import dev.wifft.betterlight.light.FrontLightOverlay;
import dev.wifft.betterlight.light.MovingBlockShadows;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
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
 * Feeds FrontLightOverlay and DirectionalLightOverlay with block and chunk changes and hooks them into the client
 * light engine. Also owns the level's MovingBlockShadows, updated once per frame.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements MovingBlockShadowsHolder, DirectionalLightHolder {
	@Shadow
	@Final
	private ClientChunkCache chunkSource;

	@Unique
	private FrontLightOverlay betterlight$frontLight;
	@Unique
	private DirectionalLightOverlay betterlight$directionalLight;
	@Unique
	private MovingBlockShadows betterlight$movingBlockShadows;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void betterlight$createFrontLight(CallbackInfo ci) {
		BlockLightOverlayTarget engine = (BlockLightOverlayTarget) this.chunkSource.getLightEngine();
		FrontLightOverlay frontLight = new FrontLightOverlay((ClientLevel) (Object) this);
		DirectionalLightOverlay directionalLight = new DirectionalLightOverlay((ClientLevel) (Object) this, engine);
		this.betterlight$frontLight = frontLight;
		this.betterlight$directionalLight = directionalLight;
		// Directional first: it takes the vanilla torch light out of the engine value, front emitters add on top
		engine.betterlight$setBlockLightOverlay((pos, level) -> frontLight.getLightValue(pos, directionalLight.getLightValue(pos, level)));
		this.betterlight$movingBlockShadows = new MovingBlockShadows((ClientLevel) (Object) this);
	}

	@Override
	public MovingBlockShadows betterlight$getMovingBlockShadows() {
		return this.betterlight$movingBlockShadows;
	}

	@Override
	public DirectionalLightOverlay betterlight$getDirectionalLight() {
		return this.betterlight$directionalLight;
	}

	@Inject(method = "unload", at = @At("TAIL"))
	private void betterlight$chunkUnloaded(LevelChunk levelChunk, CallbackInfo ci) {
		this.betterlight$frontLight.chunkUnloaded(levelChunk.getPos());
		this.betterlight$directionalLight.chunkUnloaded(levelChunk.getPos());
	}

	@Inject(method = "onChunkLoaded", at = @At("TAIL"))
	private void betterlight$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
		this.betterlight$frontLight.chunkLoaded(pos);
		this.betterlight$directionalLight.chunkLoaded(pos);
	}

	@Inject(method = "setBlocksDirty", at = @At("TAIL"))
	private void betterlight$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		this.betterlight$frontLight.blockChanged(pos, oldState, newState);
		this.betterlight$directionalLight.blockChanged(pos, oldState, newState);
	}

	// Once per frame, after the vanilla light updates: refills the emitters marked dirty since the last frame
	@Inject(method = "update", at = @At("TAIL"))
	private void betterlight$updateFrontLight(CallbackInfo ci) {
		ProfilerFiller profiler = Profiler.get();
		profiler.push("betterlight_front");
		this.betterlight$frontLight.update();
		profiler.popPush("betterlight_directional");
		this.betterlight$directionalLight.update();
		profiler.popPush("betterlight_movingShadows");
		this.betterlight$movingBlockShadows.update();
		profiler.pop();
	}
}
