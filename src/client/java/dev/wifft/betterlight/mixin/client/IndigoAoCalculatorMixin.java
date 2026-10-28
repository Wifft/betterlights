package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.light.MovingBlockShadows;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Indigo's smooth light without the directional shade, which the terrain shader now applies from the vertex normal.
 * Neighbour lookups also see moving blocks (MovingBlockShadows), so they cast AO on the terrain around them, and
 * extended piston bases as retracted ones (vanilla only lets full collision shapes cast AO).
 */
@Mixin(value = AoCalculator.class, remap = false)
public abstract class IndigoAoCalculatorMixin {
	@Unique
	private static final String COMPUTE_FACE =
		"computeFace(Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/AoFaceData;Lnet/minecraft/core/Direction;ZLnet/minecraft/core/Direction;)V";

	@Shadow
	private BlockPos pos;

	@ModifyExpressionValue(
		method = COMPUTE_FACE,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CardinalLighting;byFace(Lnet/minecraft/core/Direction;)F")
	)
	private float betterlight$noCpuShade(float shade) {
		return 1.0F;
	}

	@WrapOperation(
		method = COMPUTE_FACE,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState betterlight$aoNeighbor(BlockAndTintGetter level, BlockPos pos, Operation<BlockState> original) {
		BlockState state = MovingBlockShadows.aoState(level, pos, original.call(level, pos), this.pos);
		// Extended piston bases (12/16 deep) occlude like the full block they are when retracted
		if (BetterlightConfig.extendedPistonAo
			&& state.getBlock() instanceof PistonBaseBlock
			&& state.getValue(PistonBaseBlock.EXTENDED)
			&& !pos.equals(this.pos)) {
			return state.setValue(PistonBaseBlock.EXTENDED, false);
		}
		return state;
	}
}
