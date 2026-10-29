package dev.wifft.betterlight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.LightCellCacheHolder;
import dev.wifft.betterlight.light.LightCellCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-corner smooth light on paintings (MC-1531). With smooth lighting on, lightCoordsPerBlock holds
 * (width + 2) x (height + 2) cells (one extra ring around the painting, for corner blending) instead of
 * vanilla's width x height; renderPainting tells the two layouts apart by the array length.
 */
@Mixin(PaintingRenderer.class)
public abstract class PaintingRendererMixin {
	@Shadow
	private static void vertex(
		PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, float z, int nx, int ny, int nz, int lightCoords
	) {
		throw new AssertionError();
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/decoration/painting/Painting;Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;F)V",
		at = @At("TAIL")
	)
	private void betterlight$captureLightRing(Painting entity, PaintingRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.variant == null || !BetterlightConfig.smoothPaintingsFrames || !Minecraft.getInstance().options.ambientOcclusion().get()) {
			return;
		}

		PaintingVariant variant = state.variant;
		Direction direction = state.direction;
		int width = variant.width();
		int height = variant.height();
		Level level = entity.level();
		// The ring depends on the block, the facing and the size; reused for the rest of the tick
		LightCellCache cache = LightCellCacheHolder.of(entity);
		long gameTime = LightCellCache.tick(level);
		long key = entity.blockPosition().asLong();
		int extra = direction.ordinal() << 16 | width << 8 | height;
		int[] cached = cache.get(gameTime, key, extra);
		if (cached != null) {
			state.lightCoordsPerBlock = cached;
			return;
		}

		int[] cells = new int[(width + 2) * (height + 2)];
		float offsetX = -width / 2.0F;
		float offsetY = -height / 2.0F;
		BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

		// Same cell positions as vanilla, plus the ring of cells around the painting
		for (int segmentY = -1; segmentY <= height; segmentY++) {
			for (int segmentX = -1; segmentX <= width; segmentX++) {
				float segmentOffsetX = segmentX + offsetX + 0.5F;
				float segmentOffsetY = segmentY + offsetY + 0.5F;
				int x = entity.getBlockX();
				int y = Mth.floor(entity.getY() + segmentOffsetY);
				int z = entity.getBlockZ();
				switch (direction) {
					case NORTH -> x = Mth.floor(entity.getX() + segmentOffsetX);
					case WEST -> z = Mth.floor(entity.getZ() - segmentOffsetX);
					case SOUTH -> x = Mth.floor(entity.getX() - segmentOffsetX);
					case EAST -> z = Mth.floor(entity.getZ() + segmentOffsetX);
					default -> {
					}
				}

				cells[segmentX + 1 + (segmentY + 1) * (width + 2)] = LightCoordsUtil.getLightCoords(level, samplePos.set(x, y, z));
			}
		}

		state.lightCoordsPerBlock = cache.put(gameTime, key, extra, cells);
	}

	@Unique
	private static int betterlight$cellLight(final int[] lightCoordsMap, final int width, final int segmentX, final int segmentY) {
		return lightCoordsMap[segmentX + 1 + (segmentY + 1) * (width + 2)];
	}

	// Light at segment corner (cornerX, cornerY) seen from segment (segmentX, segmentY): blend of the 4 cells sharing that corner,
	// with the segment's own cell as center (same rule as terrain smooth lighting, LightCoordsUtil.smoothBlend)
	@Unique
	private static int betterlight$cornerLight(
		final int[] lightCoordsMap, final int width, final int segmentX, final int segmentY, final int cornerX, final int cornerY
	) {
		int otherX = cornerX == segmentX ? segmentX - 1 : segmentX + 1;
		int otherY = cornerY == segmentY ? segmentY - 1 : segmentY + 1;
		return LightCoordsUtil.smoothBlend(
			betterlight$cellLight(lightCoordsMap, width, otherX, segmentY),
			betterlight$cellLight(lightCoordsMap, width, segmentX, otherY),
			betterlight$cellLight(lightCoordsMap, width, otherX, otherY),
			betterlight$cellLight(lightCoordsMap, width, segmentX, segmentY)
		);
	}

	/**
	 * @author Wifft
	 * @reason Light per segment corner instead of per segment. Vanilla copy in docs/vanilla/PaintingRenderer.renderPainting.java;
	 * with the vanilla array layout this does exactly what vanilla does.
	 */
	@Overwrite
	private void renderPainting(
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final RenderType renderType,
		final int[] lightCoordsMap,
		final int width,
		final int height,
		final TextureAtlasSprite front,
		final TextureAtlasSprite back
	) {
		final boolean smooth = lightCoordsMap.length != width * height;
		submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
			float offsetX = -width / 2.0F;
			float offsetY = -height / 2.0F;
			float backU0 = back.getU0();
			float backU1 = back.getU1();
			float backV0 = back.getV0();
			float backV1 = back.getV1();
			float topBottomU0 = back.getU0();
			float topBottomU1 = back.getU1();
			float topBottomV0 = back.getV0();
			float topBottomV1 = back.getV(0.0625F);
			float leftRightU0 = back.getU0();
			float leftRightU1 = back.getU(0.0625F);
			float leftRightV0 = back.getV0();
			float leftRightV1 = back.getV1();
			double deltaU = 1.0 / width;
			double deltaV = 1.0 / height;

			for (int segmentX = 0; segmentX < width; segmentX++) {
				for (int segmentY = 0; segmentY < height; segmentY++) {
					float x0 = offsetX + (segmentX + 1);
					float x1 = offsetX + segmentX;
					float y0 = offsetY + (segmentY + 1);
					float y1 = offsetY + segmentY;
					// x0/y0 are the segment's far edges (corner index + 1), x1/y1 its near edges
					int lightX0Y0;
					int lightX0Y1;
					int lightX1Y0;
					int lightX1Y1;
					if (smooth) {
						lightX0Y0 = betterlight$cornerLight(lightCoordsMap, width, segmentX, segmentY, segmentX + 1, segmentY + 1);
						lightX0Y1 = betterlight$cornerLight(lightCoordsMap, width, segmentX, segmentY, segmentX + 1, segmentY);
						lightX1Y0 = betterlight$cornerLight(lightCoordsMap, width, segmentX, segmentY, segmentX, segmentY + 1);
						lightX1Y1 = betterlight$cornerLight(lightCoordsMap, width, segmentX, segmentY, segmentX, segmentY);
					} else {
						int lightCoords = lightCoordsMap[segmentX + segmentY * width];
						lightX0Y0 = lightCoords;
						lightX0Y1 = lightCoords;
						lightX1Y0 = lightCoords;
						lightX1Y1 = lightCoords;
					}

					float frontU0 = front.getU((float) (deltaU * (width - segmentX)));
					float frontU1 = front.getU((float) (deltaU * (width - (segmentX + 1))));
					float frontV0 = front.getV((float) (deltaV * (height - segmentY)));
					float frontV1 = front.getV((float) (deltaV * (height - (segmentY + 1))));
					vertex(pose, buffer, x0, y1, frontU1, frontV0, -0.03125F, 0, 0, -1, lightX0Y1);
					vertex(pose, buffer, x1, y1, frontU0, frontV0, -0.03125F, 0, 0, -1, lightX1Y1);
					vertex(pose, buffer, x1, y0, frontU0, frontV1, -0.03125F, 0, 0, -1, lightX1Y0);
					vertex(pose, buffer, x0, y0, frontU1, frontV1, -0.03125F, 0, 0, -1, lightX0Y0);
					vertex(pose, buffer, x0, y0, backU1, backV0, 0.03125F, 0, 0, 1, lightX0Y0);
					vertex(pose, buffer, x1, y0, backU0, backV0, 0.03125F, 0, 0, 1, lightX1Y0);
					vertex(pose, buffer, x1, y1, backU0, backV1, 0.03125F, 0, 0, 1, lightX1Y1);
					vertex(pose, buffer, x0, y1, backU1, backV1, 0.03125F, 0, 0, 1, lightX0Y1);
					if (segmentY == height - 1) {
						vertex(pose, buffer, x0, y0, topBottomU0, topBottomV0, -0.03125F, 0, 1, 0, lightX0Y0);
						vertex(pose, buffer, x1, y0, topBottomU1, topBottomV0, -0.03125F, 0, 1, 0, lightX1Y0);
						vertex(pose, buffer, x1, y0, topBottomU1, topBottomV1, 0.03125F, 0, 1, 0, lightX1Y0);
						vertex(pose, buffer, x0, y0, topBottomU0, topBottomV1, 0.03125F, 0, 1, 0, lightX0Y0);
					}

					if (segmentY == 0) {
						vertex(pose, buffer, x0, y1, topBottomU0, topBottomV0, 0.03125F, 0, -1, 0, lightX0Y1);
						vertex(pose, buffer, x1, y1, topBottomU1, topBottomV0, 0.03125F, 0, -1, 0, lightX1Y1);
						vertex(pose, buffer, x1, y1, topBottomU1, topBottomV1, -0.03125F, 0, -1, 0, lightX1Y1);
						vertex(pose, buffer, x0, y1, topBottomU0, topBottomV1, -0.03125F, 0, -1, 0, lightX0Y1);
					}

					if (segmentX == width - 1) {
						vertex(pose, buffer, x0, y0, leftRightU1, leftRightV0, 0.03125F, -1, 0, 0, lightX0Y0);
						vertex(pose, buffer, x0, y1, leftRightU1, leftRightV1, 0.03125F, -1, 0, 0, lightX0Y1);
						vertex(pose, buffer, x0, y1, leftRightU0, leftRightV1, -0.03125F, -1, 0, 0, lightX0Y1);
						vertex(pose, buffer, x0, y0, leftRightU0, leftRightV0, -0.03125F, -1, 0, 0, lightX0Y0);
					}

					if (segmentX == 0) {
						vertex(pose, buffer, x1, y0, leftRightU1, leftRightV0, -0.03125F, 1, 0, 0, lightX1Y0);
						vertex(pose, buffer, x1, y1, leftRightU1, leftRightV1, -0.03125F, 1, 0, 0, lightX1Y1);
						vertex(pose, buffer, x1, y1, leftRightU0, leftRightV1, 0.03125F, 1, 0, 0, lightX1Y1);
						vertex(pose, buffer, x1, y0, leftRightU0, leftRightV0, 0.03125F, 1, 0, 0, lightX1Y0);
					}
				}
			}
		});
	}
}
