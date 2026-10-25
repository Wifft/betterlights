package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.ItemFrameLightCells;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemFrameRenderState.class)
public abstract class ItemFrameRenderStateMixin implements ItemFrameLightCells {
	@Unique
	private int @Nullable [] betterlight$lightCells;
	@Unique
	private BlockPos betterlight$lightOrigin = BlockPos.ZERO;

	@Override
	public int @Nullable [] betterlight$getLightCells() {
		return this.betterlight$lightCells;
	}

	@Override
	public BlockPos betterlight$getLightOrigin() {
		return this.betterlight$lightOrigin;
	}

	@Override
	public void betterlight$setLightCells(int @Nullable [] cells, BlockPos origin) {
		this.betterlight$lightCells = cells;
		this.betterlight$lightOrigin = origin;
	}
}
