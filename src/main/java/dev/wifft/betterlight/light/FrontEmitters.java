package dev.wifft.betterlight.light;

import dev.wifft.betterlight.config.BetterlightConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lit furnaces only light what is in front of them. A flood fill can't cut light off behind a plane
 * (it always wraps around), so they emit nothing in the light engine and the client computes their light
 * separately (FrontLightOverlay). Same for jack o'lanterns (carved face). Both use
 * HorizontalDirectionalBlock.FACING for the lit side.
 */
public final class FrontEmitters {
	private FrontEmitters() {
	}

	public static boolean isFrontEmitter(final BlockState state) {
		if (!BetterlightConfig.frontEmitters) {
			return false;
		}
		Block block = state.getBlock();
		if (block instanceof AbstractFurnaceBlock) {
			return state.getValue(AbstractFurnaceBlock.LIT);
		}
		return block instanceof CarvedPumpkinBlock && state.getLightEmission() > 0;
	}

	public static Direction frontOf(final BlockState state) {
		return state.getValue(HorizontalDirectionalBlock.FACING);
	}
}
