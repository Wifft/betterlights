package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.MovingBlockCellsHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin implements MovingBlockCellsHolder {
	@Unique
	private @Nullable Long2ObjectMap<BlockState> betterlight$movingBlockCells;

	@Override
	public @Nullable Long2ObjectMap<BlockState> betterlight$getMovingBlockCells() {
		return this.betterlight$movingBlockCells;
	}

	@Override
	public void betterlight$setMovingBlockCells(@Nullable Long2ObjectMap<BlockState> cells) {
		this.betterlight$movingBlockCells = cells;
	}
}
