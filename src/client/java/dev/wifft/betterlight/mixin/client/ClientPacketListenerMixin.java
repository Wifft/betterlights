package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.DirectionalLightHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server light for a chunk (on load and on updates): DirectionalLightOverlay rebuilds the other sources' light from it. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "applyLightData", at = @At("TAIL"))
	private void betterlight$lightDataApplied(int x, int z, ClientboundLightUpdatePacketData lightData, boolean scheduleRebuild, CallbackInfo ci) {
		((DirectionalLightHolder) this.level).betterlight$getDirectionalLight().lightDataApplied(x, z);
	}
}
