package dev.wifft.betterlight.light;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.MovingBlockCellsHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * AO that moving blocks cast on the terrain around them. While a block falls (or is pushed by a piston) the world has
 * air (or moving_piston) in its cell, so neighbouring terrain is meshed as if it weren't there.
 * Falling blocks: once per frame the cells they occupy are collected here, sections around a cell are marked dirty when
 * it changes, and each RenderSectionRegion gets an immutable copy of the cells inside it when it's created (main thread).
 * Piston-moved blocks need no tracking: moving_piston is a real block (placing and removing it remeshes), its moved
 * state is read from the region's block entity copy.
 * Only the AO calculator sees these blocks (IndigoAoCalculatorMixin), so face culling is unchanged.
 */
public final class MovingBlockShadows {
	// Nearest falling blocks that cast AO; the rest (big avalanches) don't
	private static final int MAX_BLOCKS = 64;
	private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

	private final ClientLevel level;
	private Long2ObjectMap<BlockState> cells = Long2ObjectMaps.emptyMap();

	public MovingBlockShadows(final ClientLevel level) {
		this.level = level;
	}

	public void update() {
		Long2ObjectMap<BlockState> next = BetterlightConfig.movingBlockShadows ? this.collect() : Long2ObjectMaps.emptyMap();
		Long2ObjectMap<BlockState> previous = this.cells;
		if (next.isEmpty() && previous.isEmpty()) {
			return;
		}

		for (Long2ObjectMap.Entry<BlockState> entry : Long2ObjectMaps.fastIterable(previous)) {
			if (next.get(entry.getLongKey()) != entry.getValue()) {
				this.setDirtyAround(entry.getLongKey());
			}
		}
		for (Long2ObjectMap.Entry<BlockState> entry : Long2ObjectMaps.fastIterable(next)) {
			if (previous.get(entry.getLongKey()) != entry.getValue()) {
				this.setDirtyAround(entry.getLongKey());
			}
		}
		this.cells = next;
	}

	private Long2ObjectMap<BlockState> collect() {
		Entity viewer = Minecraft.getInstance().getCameraEntity();
		if (viewer == null) {
			return Long2ObjectMaps.emptyMap();
		}

		List<FallingBlockEntity> blocks = null;
		for (Entity entity : this.level.entitiesForRendering()) {
			if (entity instanceof FallingBlockEntity falling && !falling.isRemoved() && falling.distanceToSqr(viewer) < MAX_DISTANCE_SQ) {
				if (blocks == null) {
					blocks = new ArrayList<>();
				}
				blocks.add(falling);
			}
		}
		if (blocks == null) {
			return Long2ObjectMaps.emptyMap();
		}
		if (blocks.size() > MAX_BLOCKS) {
			blocks.sort(Comparator.comparingDouble(falling -> falling.distanceToSqr(viewer)));
			blocks = blocks.subList(0, MAX_BLOCKS);
		}

		Long2ObjectMap<BlockState> cells = new Long2ObjectOpenHashMap<>(blocks.size());
		for (FallingBlockEntity falling : blocks) {
			// The cell holding the block's centre
			cells.put(BlockPos.asLong(Mth.floor(falling.getX()), Mth.floor(falling.getY() + 0.5), Mth.floor(falling.getZ())), falling.getBlockState());
		}
		return cells;
	}

	// AO of every block within one block of the cell changes
	private void setDirtyAround(final long cell) {
		int x = BlockPos.getX(cell);
		int y = BlockPos.getY(cell);
		int z = BlockPos.getZ(cell);
		this.level.setSectionRangeDirty(
			SectionPos.blockToSectionCoord(x - 1), SectionPos.blockToSectionCoord(y - 1), SectionPos.blockToSectionCoord(z - 1),
			SectionPos.blockToSectionCoord(x + 1), SectionPos.blockToSectionCoord(y + 1), SectionPos.blockToSectionCoord(z + 1)
		);
	}

	// Cells for a region spanning these sections (3 per axis); null with the option off
	public @Nullable Long2ObjectMap<BlockState> forRegion(final int minSectionX, final int minSectionY, final int minSectionZ) {
		if (!BetterlightConfig.movingBlockShadows) {
			return null;
		}

		Long2ObjectMap<BlockState> cells = this.cells;
		if (cells.isEmpty()) {
			return Long2ObjectMaps.emptyMap();
		}

		Long2ObjectMap<BlockState> inside = null;
		for (Long2ObjectMap.Entry<BlockState> entry : Long2ObjectMaps.fastIterable(cells)) {
			long cell = entry.getLongKey();
			int sectionX = SectionPos.blockToSectionCoord(BlockPos.getX(cell)) - minSectionX;
			int sectionY = SectionPos.blockToSectionCoord(BlockPos.getY(cell)) - minSectionY;
			int sectionZ = SectionPos.blockToSectionCoord(BlockPos.getZ(cell)) - minSectionZ;
			if (sectionX >= 0 && sectionX < 3 && sectionY >= 0 && sectionY < 3 && sectionZ >= 0 && sectionZ < 3) {
				if (inside == null) {
					inside = new Long2ObjectOpenHashMap<>();
				}
				inside.put(cell, entry.getValue());
			}
		}
		return inside == null ? Long2ObjectMaps.emptyMap() : inside;
	}

	// State the AO calculator should see at pos while meshing the block at meshedPos (section compile threads)
	public static BlockState aoState(final BlockAndTintGetter level, final BlockPos pos, final BlockState state, final BlockPos meshedPos) {
		if (!(level instanceof MovingBlockCellsHolder holder)) {
			return state;
		}
		Long2ObjectMap<BlockState> cells = holder.betterlight$getMovingBlockCells();
		if (cells == null || pos.equals(meshedPos)) {
			return state;
		}

		if (state.is(Blocks.MOVING_PISTON)) {
			return level.getBlockEntity(pos) instanceof PistonMovingBlockEntity piston ? piston.getMovedState() : state;
		}
		if (!cells.isEmpty() && state.canBeReplaced()) {
			BlockState moving = cells.get(pos.asLong());
			if (moving != null) {
				return moving;
			}
		}
		return state;
	}
}
