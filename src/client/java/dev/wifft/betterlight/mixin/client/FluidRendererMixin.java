package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wifft.betterlight.config.BetterlightConfig;
import java.util.Arrays;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluid faces get their real normal (vanilla writes 0,1,0 everywhere) so the terrain shader can shade them, no CPU
 * directional shade, and optionally smooth light: each face corner blends the four cells around it instead of one light
 * value per face. Right before each addFace call in tesselate the face is described (ordinal 0 = top, 1 = bottom,
 * 2 = sides); addFace then emits the vertices. One FluidRenderer per SectionCompiler, so the fields aren't shared between threads.
 */
@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
	@Unique
	private static final String ADD_FACE = "Lnet/minecraft/client/renderer/block/FluidRenderer;addFace(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFFFFFIIZ)V";

	@Unique
	private final int[] betterlight$lightCache = new int[27];
	@Unique
	private final BlockPos.MutableBlockPos betterlight$samplePos = new BlockPos.MutableBlockPos();
	// Lights of the 4 vertices of the next addFace call, in its vertex order
	@Unique
	private final int[] betterlight$cornerLights = new int[4];
	@Unique
	private Direction betterlight$faceNormal = Direction.UP;
	@Unique
	private boolean betterlight$hasFace;
	@Unique
	private boolean betterlight$useCornerLights;

	@Shadow
	private int getLightCoords(final BlockAndTintGetter level, final BlockPos pos) {
		throw new AssertionError();
	}

	@Inject(method = "tesselate", at = @At("HEAD"))
	private void betterlight$resetLightCache(
		BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci
	) {
		Arrays.fill(this.betterlight$lightCache, -1);
		this.betterlight$hasFace = false;
	}

	// The directional shade (CardinalLighting factors on the tint) moves to the shader
	@WrapOperation(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;scaleRGB(IF)I"), require = 3)
	private int betterlight$noCpuShade(int color, float scale, Operation<Integer> original) {
		return color;
	}

	@Inject(method = "tesselate", at = @At(value = "INVOKE", target = ADD_FACE, ordinal = 0))
	private void betterlight$topCorners(
		BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci
	) {
		if (BetterlightConfig.smoothFluids) {
			this.betterlight$setCorners(
				Direction.UP,
				this.betterlight$cornerLight(level, pos, 0, Direction.NORTH, Direction.WEST),
				this.betterlight$cornerLight(level, pos, 0, Direction.SOUTH, Direction.WEST),
				this.betterlight$cornerLight(level, pos, 0, Direction.SOUTH, Direction.EAST),
				this.betterlight$cornerLight(level, pos, 0, Direction.NORTH, Direction.EAST)
			);
		} else {
			this.betterlight$setFlatFace(Direction.UP);
		}
	}

	@Inject(method = "tesselate", at = @At(value = "INVOKE", target = ADD_FACE, ordinal = 1))
	private void betterlight$bottomCorners(
		BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci
	) {
		if (BetterlightConfig.smoothFluids) {
			this.betterlight$setCorners(
				Direction.DOWN,
				this.betterlight$cornerLight(level, pos, -1, Direction.NORTH, Direction.WEST),
				this.betterlight$cornerLight(level, pos, -1, Direction.NORTH, Direction.EAST),
				this.betterlight$cornerLight(level, pos, -1, Direction.SOUTH, Direction.EAST),
				this.betterlight$cornerLight(level, pos, -1, Direction.SOUTH, Direction.WEST)
			);
		} else {
			this.betterlight$setFlatFace(Direction.DOWN);
		}
	}

	// Side faces: vertices 0/1 are the top edge, 2/3 the bottom edge
	@Inject(method = "tesselate", at = @At(value = "INVOKE", target = ADD_FACE, ordinal = 2))
	private void betterlight$sideCorners(
		BlockAndTintGetter level,
		BlockPos pos,
		FluidRenderer.Output output,
		BlockState blockState,
		FluidState fluidState,
		CallbackInfo ci,
		@Local Direction faceDir
	) {
		if (BetterlightConfig.smoothFluids) {
			Direction side0 = faceDir.getCounterClockWise();
			Direction side1 = faceDir.getClockWise();
			this.betterlight$setCorners(
				faceDir,
				this.betterlight$cornerLight(level, pos, 0, side0, Direction.UP),
				this.betterlight$cornerLight(level, pos, 0, side1, Direction.UP),
				this.betterlight$cornerLight(level, pos, 0, side1, Direction.DOWN),
				this.betterlight$cornerLight(level, pos, 0, side0, Direction.DOWN)
			);
		} else {
			this.betterlight$setFlatFace(faceDir);
		}
	}

	@Inject(method = "addFace", at = @At("HEAD"), cancellable = true)
	private void betterlight$addFace(
		final VertexConsumer builder,
		final float x0,
		final float y0,
		final float z0,
		final float u0,
		final float v0,
		final float x1,
		final float y1,
		final float z1,
		final float u1,
		final float v1,
		final float x2,
		final float y2,
		final float z2,
		final float u2,
		final float v2,
		final float x3,
		final float y3,
		final float z3,
		final float u3,
		final float v3,
		final int color,
		final int lightCoords,
		final boolean addBackFace,
		CallbackInfo ci
	) {
		if (!this.betterlight$hasFace) {
			return;
		}

		this.betterlight$hasFace = false;
		int[] light = this.betterlight$cornerLights;
		if (!this.betterlight$useCornerLights) {
			Arrays.fill(light, lightCoords);
		}

		Direction normal = this.betterlight$faceNormal;
		betterlight$vertex(builder, x0, y0, z0, color, u0, v0, light[0], normal);
		betterlight$vertex(builder, x1, y1, z1, color, u1, v1, light[1], normal);
		betterlight$vertex(builder, x2, y2, z2, color, u2, v2, light[2], normal);
		betterlight$vertex(builder, x3, y3, z3, color, u3, v3, light[3], normal);
		if (addBackFace) {
			// Same normal as the front face: vanilla shades both sides of a fluid face alike
			betterlight$vertex(builder, x0, y0, z0, color, u0, v0, light[0], normal);
			betterlight$vertex(builder, x3, y3, z3, color, u3, v3, light[3], normal);
			betterlight$vertex(builder, x2, y2, z2, color, u2, v2, light[2], normal);
			betterlight$vertex(builder, x1, y1, z1, color, u1, v1, light[1], normal);
		}
		ci.cancel();
	}

	@Unique
	private void betterlight$setFlatFace(Direction normal) {
		this.betterlight$faceNormal = normal;
		this.betterlight$useCornerLights = false;
		this.betterlight$hasFace = true;
	}

	@Unique
	private void betterlight$setCorners(Direction normal, int light0, int light1, int light2, int light3) {
		int[] light = this.betterlight$cornerLights;
		light[0] = light0;
		light[1] = light1;
		light[2] = light2;
		light[3] = light3;
		this.betterlight$faceNormal = normal;
		this.betterlight$useCornerLights = true;
		this.betterlight$hasFace = true;
	}

	@Unique
	private static void betterlight$vertex(
		VertexConsumer builder, float x, float y, float z, int color, float u, float v, int lightCoords, Direction normal
	) {
		builder.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, lightCoords, normal.getStepX(), normal.getStepY(), normal.getStepZ());
	}

	@Unique
	private int betterlight$cachedLight(BlockAndTintGetter level, BlockPos pos, int dx, int dy, int dz) {
		int index = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
		int light = this.betterlight$lightCache[index];
		if (light == -1) {
			light = this.getLightCoords(level, this.betterlight$samplePos.setWithOffset(pos, dx, dy, dz));
			this.betterlight$lightCache[index] = light;
		}
		return light;
	}

	// Smooth light at a face corner: blend of the four cells around it in the plane at height dy (0 = the fluid cell, -1 = below)
	@Unique
	private int betterlight$cornerLight(BlockAndTintGetter level, BlockPos pos, int dy, Direction a, Direction b) {
		int center = this.betterlight$cachedLight(level, pos, 0, dy, 0);
		int lightA = this.betterlight$cachedLight(level, pos, a.getStepX(), dy + a.getStepY(), a.getStepZ());
		int lightB = this.betterlight$cachedLight(level, pos, b.getStepX(), dy + b.getStepY(), b.getStepZ());
		int lightAB = this.betterlight$cachedLight(level, pos, a.getStepX() + b.getStepX(), dy + a.getStepY() + b.getStepY(), a.getStepZ() + b.getStepZ());
		return LightCoordsUtil.smoothBlend(lightA, lightB, lightAB, center);
	}
}
