package dev.wifft.betterlight.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The 3×3×3 block states around a moving block (falling block, piston-moved block), captured at extraction so its
 * AO and light blend see the real neighbours instead of air. One block of reach is what Indigo samples with
 * fix-smooth-lighting-offset (its default); further positions stay air.
 */
public final class NeighborStates {
	private NeighborStates() {
	}

	public static BlockState[] capture(final BlockGetter level, final BlockPos center) {
		BlockState[] states = new BlockState[27];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					states[index(dx, dy, dz)] = level.getBlockState(pos.setWithOffset(center, dx, dy, dz));
				}
			}
		}
		return states;
	}

	// State captured at pos, or null if pos is outside the captured box
	public static @Nullable BlockState get(final BlockState[] states, final BlockPos center, final BlockPos pos) {
		int dx = pos.getX() - center.getX();
		int dy = pos.getY() - center.getY();
		int dz = pos.getZ() - center.getZ();
		if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) {
			return null;
		}
		return states[index(dx, dy, dz)];
	}

	private static int index(final int dx, final int dy, final int dz) {
		return (dx + 1) * 9 + (dy + 1) * 3 + dz + 1;
	}
}
