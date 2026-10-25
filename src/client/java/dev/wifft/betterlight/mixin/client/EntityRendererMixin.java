package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.config.BetterlightConfig;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity light interpolated between the 8 cells around the light probe instead of one cell. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@Shadow
	protected abstract int getBlockLightLevel(T entity, BlockPos blockPos);

	@Shadow
	protected abstract int getSkyLightLevel(T entity, BlockPos blockPos);

	@Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
	private void betterlight$smoothLightCoords(T entity, float partialTickTime, CallbackInfoReturnable<Integer> cir) {
		if (!BetterlightConfig.smoothEntities) {
			return;
		}

		Vec3 probe = entity.getLightProbePosition(partialTickTime);
		BlockPos blockPos = BlockPos.containing(probe);
		// Trilinear blend of the 8 cells around the probe.
		// Cells with no light at all (usually solid blocks) are skipped, like LightCoordsUtil.smoothBlend does.
		double px = probe.x - 0.5;
		double py = probe.y - 0.5;
		double pz = probe.z - 0.5;
		int baseX = Mth.floor(px);
		int baseY = Mth.floor(py);
		int baseZ = Mth.floor(pz);
		float fx = (float) (px - baseX);
		float fy = (float) (py - baseY);
		float fz = (float) (pz - baseZ);
		BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
		float blockSum = 0.0F;
		float skySum = 0.0F;
		float weightSum = 0.0F;

		for (int corner = 0; corner < 8; corner++) {
			int dx = corner & 1;
			int dy = corner >> 1 & 1;
			int dz = corner >> 2 & 1;
			float weight = (dx == 1 ? fx : 1.0F - fx) * (dy == 1 ? fy : 1.0F - fy) * (dz == 1 ? fz : 1.0F - fz);
			if (weight > 0.0F) {
				samplePos.set(baseX + dx, baseY + dy, baseZ + dz);
				int block = this.getBlockLightLevel(entity, samplePos);
				int sky = this.getSkyLightLevel(entity, samplePos);
				if (block != 0 || sky != 0) {
					blockSum += block * weight;
					skySum += sky * weight;
					weightSum += weight;
				}
			}
		}

		if (weightSum <= 0.0F) {
			cir.setReturnValue(LightCoordsUtil.pack(this.getBlockLightLevel(entity, blockPos), this.getSkyLightLevel(entity, blockPos)));
		} else {
			int smoothBlock = Math.min(Math.round(blockSum / weightSum * 16.0F), 240);
			int smoothSky = Math.min(Math.round(skySum / weightSum * 16.0F), 240);
			cir.setReturnValue(LightCoordsUtil.smoothPack(smoothBlock, smoothSky));
		}
	}
}
