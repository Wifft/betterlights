package dev.wifft.betterlight.duck;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Falling block cells inside a RenderSectionRegion, copied when it's created (see MovingBlockShadows); null with the option off. */
public interface MovingBlockCellsHolder {
	@Nullable Long2ObjectMap<BlockState> betterlight$getMovingBlockCells();

	void betterlight$setMovingBlockCells(@Nullable Long2ObjectMap<BlockState> cells);
}
