package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.DirectionalLightHolder;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunk data sent again for a loaded chunk (plugins that edit or resend chunks) replaces its blocks in place, with an
 * onChunkLoaded but no unload: the emitter overlays drop the old chunk first, so fields of emitters that are gone don't
 * stay and fills that read the old blocks are redone.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
	@Shadow
	@Final
	private ClientLevel level;

	@Inject(method = "replaceWithPacketData", at = @At("HEAD"))
	private void betterlight$chunkReplaced(int chunkX, int chunkZ, ClientboundLevelChunkPacketData chunkData, CallbackInfoReturnable<LevelChunk> cir) {
		if (((ClientChunkCache) (Object) this).getChunk(chunkX, chunkZ, false) != null) {
			((DirectionalLightHolder) this.level).betterlight$chunkReplaced(new ChunkPos(chunkX, chunkZ));
		}
	}
}
