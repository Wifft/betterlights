package dev.wifft.betterlight.mixin.client;

import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.FlatLighter;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flat-lit blocks: no CPU directional shade, the terrain shader applies it from the vertex normal. */
@Mixin(value = FlatLighter.class, remap = false)
public abstract class IndigoFlatLighterMixin {
	@Inject(method = "applyDirectionalBrightness", at = @At("HEAD"), cancellable = true)
	private void betterlight$noCpuShade(CardinalLighting cardinalLighting, MutableQuadViewImpl quad, boolean vanillaShade, CallbackInfo ci) {
		ci.cancel();
	}
}
