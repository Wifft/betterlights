package dev.wifft.betterlight.light;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;

/**
 * Light of one emitter, within RADIUS of it (an emission of 15 reaches 14 blocks). cells are
 * {@code localIndex << 8 | V << 4 | D}, sorted, where V is the vanilla-like light and D the one the overlay shows
 * (the same for front emitters). missingChunks are the unloaded chunks the fill read as air, so it's redone when they load.
 */
final class EmitterField {
	static final int RADIUS = 14;
	static final int SIDE = 2 * RADIUS + 1;
	static final int VOLUME = SIDE * SIDE * SIDE;
	static final int CENTER = index(RADIUS, RADIUS, RADIUS);
	private static final Direction[] DIRECTIONS = Direction.values();

	final int x;
	final int y;
	final int z;
	final int[] cells;
	// Shared empty set until a chunk is missing
	private LongSet missingChunks = LongSets.EMPTY_SET;

	EmitterField(final BlockPos source, final int[] cells) {
		this.x = source.getX();
		this.y = source.getY();
		this.z = source.getZ();
		this.cells = cells;
	}

	static int index(final int x, final int y, final int z) {
		return (y * SIDE + z) * SIDE + x;
	}

	static boolean inLocalBox(final int x, final int y, final int z) {
		return x >= 0 && x < SIDE && y >= 0 && y < SIDE && z >= 0 && z < SIDE;
	}

	static int localX(final int local) {
		return local % SIDE;
	}

	static int localY(final int local) {
		return local / (SIDE * SIDE);
	}

	static int localZ(final int local) {
		return local / SIDE % SIDE;
	}

	int cellX(final int cell) {
		return this.x - RADIUS + localX(cell >>> 8);
	}

	int cellY(final int cell) {
		return this.y - RADIUS + localY(cell >>> 8);
	}

	int cellZ(final int cell) {
		return this.z - RADIUS + localZ(cell >>> 8);
	}

	long cellSection(final int cell) {
		return SectionPos.asLong(SectionPos.blockToSectionCoord(this.cellX(cell)), SectionPos.blockToSectionCoord(this.cellY(cell)), SectionPos.blockToSectionCoord(this.cellZ(cell)));
	}

	void addMissingChunk(final long chunkKey) {
		if (this.missingChunks == LongSets.EMPTY_SET) {
			this.missingChunks = new LongOpenHashSet();
		}
		this.missingChunks.add(chunkKey);
	}

	boolean isMissing(final long chunkKey) {
		return this.missingChunks.contains(chunkKey);
	}

	boolean inBox(final int x, final int y, final int z) {
		return this.nearBox(x, y, z, 0);
	}

	// Within margin blocks of the box
	boolean nearBox(final int x, final int y, final int z, final int margin) {
		int reach = RADIUS + margin;
		return Math.abs(x - this.x) <= reach && Math.abs(y - this.y) <= reach && Math.abs(z - this.z) <= reach;
	}

	// Whether the fill reached pos or one of its neighbours, the only fills a block change there can alter. The
	// source's neighbours always count: a front emitter's own block stays dark, so a fill walled in at its front has
	// no cells at all
	boolean reaches(final BlockPos pos) {
		if (Math.abs(pos.getX() - this.x) + Math.abs(pos.getY() - this.y) + Math.abs(pos.getZ() - this.z) <= 1
			|| this.vanillaAt(pos.getX(), pos.getY(), pos.getZ()) > 0) {
			return true;
		}

		for (Direction direction : DIRECTIONS) {
			if (this.vanillaAt(pos.getX() + direction.getStepX(), pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ()) > 0) {
				return true;
			}
		}

		return false;
	}

	int vanillaAt(final int x, final int y, final int z) {
		if (!this.inBox(x, y, z)) {
			return 0;
		}
		int local = index(x - this.x + RADIUS, y - this.y + RADIUS, z - this.z + RADIUS);
		int low = 0;
		int high = this.cells.length - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int midLocal = this.cells[mid] >>> 8;
			if (midLocal < local) {
				low = mid + 1;
			} else if (midLocal > local) {
				high = mid - 1;
			} else {
				return this.cells[mid] >>> 4 & 15;
			}
		}
		return 0;
	}
}
