package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.QuadViewImpl;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The face normal written to the vertex buffer honours shadeDirectionOverride, as Indigo's CPU shading did. */
@Mixin(value = QuadViewImpl.class, remap = false)
public abstract class IndigoQuadViewMixin {
	@Shadow
	public abstract @Nullable Direction shadeDirectionOverride();

	@ModifyExpressionValue(
		method = {
			"buffer(ILcom/mojang/blaze3d/vertex/VertexConsumer;)V",
			"buffer(ILcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/QuadViewImpl;faceNormal()Lorg/joml/Vector3fc;"
		)
	)
	private Vector3fc betterlight$shadeNormal(Vector3fc faceNormal) {
		Direction shadeDirection = this.shadeDirectionOverride();
		return shadeDirection != null ? shadeDirection.getUnitVec3f() : faceNormal;
	}
}
