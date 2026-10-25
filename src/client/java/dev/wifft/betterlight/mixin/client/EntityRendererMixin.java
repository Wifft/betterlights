package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.light.LightBlend;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

		// Trilinear blend of the 8 cells around the probe (LightBlend); falls back to vanilla's single cell when all are unlit
		Vec3 probe = entity.getLightProbePosition(partialTickTime);
		double px = probe.x - 0.5;
		double py = probe.y - 0.5;
		double pz = probe.z - 0.5;
		int baseX = Mth.floor(px);
		int baseY = Mth.floor(py);
		int baseZ = Mth.floor(pz);
		BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
		int light = LightBlend.trilinear(
			this.betterlight$light(entity, samplePos.set(baseX, baseY, baseZ)),
			this.betterlight$light(entity, samplePos.set(baseX + 1, baseY, baseZ)),
			this.betterlight$light(entity, samplePos.set(baseX, baseY + 1, baseZ)),
			this.betterlight$light(entity, samplePos.set(baseX + 1, baseY + 1, baseZ)),
			this.betterlight$light(entity, samplePos.set(baseX, baseY, baseZ + 1)),
			this.betterlight$light(entity, samplePos.set(baseX + 1, baseY, baseZ + 1)),
			this.betterlight$light(entity, samplePos.set(baseX, baseY + 1, baseZ + 1)),
			this.betterlight$light(entity, samplePos.set(baseX + 1, baseY + 1, baseZ + 1)),
			(float) (px - baseX), (float) (py - baseY), (float) (pz - baseZ), -1
		);
		cir.setReturnValue(light != -1 ? light : this.betterlight$light(entity, samplePos.set(BlockPos.containing(probe))));
	}

	@Unique
	private int betterlight$light(T entity, BlockPos pos) {
		return LightCoordsUtil.pack(this.getBlockLightLevel(entity, pos), this.getSkyLightLevel(entity, pos));
	}
}
