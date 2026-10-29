package dev.wifft.betterlight.light;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lit furnaces only light what is in front of them. A flood fill can't cut light off behind a plane
 * (it always wraps around), so they emit nothing in the light engine and the client computes their light
 * separately (FrontLightOverlay). Same for jack o'lanterns (carved face). Which blocks count is the
 * {@code betterlight:front_emitters} tag (EmitterTags); HorizontalDirectionalBlock.FACING is the lit side.
 */
public final class FrontEmitters {
	private FrontEmitters() {
	}

	public static boolean isFrontEmitter(final BlockState state) {
		return EmitterTags.isFront(state);
	}

	public static Direction frontOf(final BlockState state) {
		return state.getValue(HorizontalDirectionalBlock.FACING);
	}
}
