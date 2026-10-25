package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Indigo's smooth light without the directional shade, which the terrain shader now applies from the vertex normal. */
@Mixin(value = AoCalculator.class, remap = false)
public abstract class IndigoAoCalculatorMixin {
	@ModifyExpressionValue(
		method = "computeFace(Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/AoFaceData;Lnet/minecraft/core/Direction;ZLnet/minecraft/core/Direction;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CardinalLighting;byFace(Lnet/minecraft/core/Direction;)F")
	)
	private float betterlight$noCpuShade(float shade) {
		return 1.0F;
	}
}
