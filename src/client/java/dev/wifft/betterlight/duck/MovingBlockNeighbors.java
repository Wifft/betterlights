package dev.wifft.betterlight.duck;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Neighbour states around a MovingBlockRenderState's block (see NeighborStates); null keeps vanilla's air around it. */
public interface MovingBlockNeighbors {
	BlockState @Nullable [] betterlight$getNeighbors();

	void betterlight$setNeighbors(BlockState @Nullable [] neighbors);
}
