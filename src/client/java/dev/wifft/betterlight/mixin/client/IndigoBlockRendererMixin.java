package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.light.FrontEmitters;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.QuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API redirects terrain (and moving block) meshing to Indigo, so the block part of Betterlight hooks Indigo
 * instead of vanilla's ModelBlockRenderer. Sends more quads through the smooth (AO) path than Indigo does:
 * - front emitters (lit furnaces, jack o'lanterns): normal AO, emission floor on the front face only;
 * - models with "ambientocclusion": false (signs, rails, plants...) and light emitters (nether portal, torches...):
 *   smooth light without AO darkening, block light kept at least at the emission level (MC-231102, MC-1531).
 * One renderer per section compile thread, so the per-quad context fields aren't shared between threads.
 */
@Mixin(value = AltModelBlockRendererImpl.class, remap = false)
public abstract class IndigoBlockRendererMixin {
	@Unique
	private static final String SHADE_QUAD =
		"Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AltModelBlockRendererImpl;shadeQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;ZZZ)V";

	@Shadow
	@Final
	private boolean ambientOcclusion;

	@Shadow
	@Final
	private AoCalculator aoCalc;

	@Shadow
	private BlockState blockState;

	// Context for the quad being shaded, set by betterlight$smoothInsteadOfFlat
	@Unique
	private boolean betterlight$applyShade = true;
	@Unique
	private int betterlight$minBlockLight;
	@Unique
	private @Nullable Direction betterlight$minLightFace;

	@ModifyArg(method = "transform", at = @At(value = "INVOKE", target = SHADE_QUAD), index = 1)
	private boolean betterlight$smoothInsteadOfFlat(MutableQuadViewImpl quad, boolean ao, boolean emissive, boolean vanillaShade) {
		this.betterlight$applyShade = true;
		this.betterlight$minBlockLight = 0;
		this.betterlight$minLightFace = null;
		if (ao || !this.ambientOcclusion || emissive) {
			return ao;
		}

		BlockState state = this.blockState;
		int emission = state.getLightEmission();
		if (FrontEmitters.isFrontEmitter(state) && quad.ambientOcclusion() != TriState.FALSE) {
			this.betterlight$minBlockLight = emission;
			this.betterlight$minLightFace = FrontEmitters.frontOf(state);
			return true;
		}

		if (!state.emissiveRendering() && (emission > 0 ? BetterlightConfig.smoothEmitters : BetterlightConfig.smoothNoAoModels)) {
			this.betterlight$applyShade = false;
			this.betterlight$minBlockLight = emission;
			return true;
		}

		return false;
	}

	@Inject(
		method = "shadeQuad",
		at = @At(
			value = "INVOKE",
			target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/AoCalculator;compute(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/QuadViewImpl;Z)V",
			shift = At.Shift.AFTER
		)
	)
	private void betterlight$applyContext(MutableQuadViewImpl quad, boolean ao, boolean emissive, boolean vanillaShade, CallbackInfo ci) {
		AoCalculator aoCalc = this.aoCalc;
		if (!this.betterlight$applyShade) {
			// No AO darkening (the directional shade is applied by the terrain shader)
			for (int i = 0; i < 4; i++) {
				aoCalc.ao[i] = 1.0F;
			}
		}

		int minBlockLight = this.betterlight$minBlockLight;
		if (minBlockLight > 0 && (this.betterlight$minLightFace == null || quad.nominalFace() == this.betterlight$minLightFace)) {
			int minSmoothBlock = minBlockLight * 16;
			for (int i = 0; i < 4; i++) {
				int lightCoords = aoCalc.light[i];
				if (LightCoordsUtil.smoothBlock(lightCoords) < minSmoothBlock) {
					aoCalc.light[i] = LightCoordsUtil.smoothPack(minSmoothBlock, LightCoordsUtil.smoothSky(lightCoords));
				}
			}
		}
	}

	// Front emitters on the flat path: only the front face is floored at the emission level (other faces sampled without emission)
	@ModifyArg(
		method = "shadeQuad",
		at = @At(
			value = "INVOKE",
			target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/FlatLighter;light(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/QuadViewImpl;)I"
		),
		index = 1
	)
	private BlockState betterlight$frontEmitterFlatLight(BlockAndTintGetter level, BlockState state, BlockPos pos, QuadViewImpl quad) {
		return FrontEmitters.isFrontEmitter(state) && quad.nominalFace() != FrontEmitters.frontOf(state) ? Blocks.AIR.defaultBlockState() : state;
	}
}
