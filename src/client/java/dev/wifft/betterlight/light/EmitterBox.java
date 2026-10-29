package dev.wifft.betterlight.light;

import static dev.wifft.betterlight.light.EmitterField.CENTER;
import static dev.wifft.betterlight.light.EmitterField.RADIUS;
import static dev.wifft.betterlight.light.EmitterField.SIDE;
import static dev.wifft.betterlight.light.EmitterField.VOLUME;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The box of RADIUS around the emitter being filled: block states read lazily in local coordinates (valid where
 * stamp == generation, so starting a fill clears nothing), and the shading of a fill by line of sight through it.
 */
final class EmitterBox {
	// Line of sight is tested with one vertical sheet per corner of the horizontal square, from an edge of a cube spread
	// over the emitter's block to the same edge of a slightly bigger one in the target cell (so the floor is judged from
	// just above it, or above the layer the target holds). Each sheet is BINS rays stacked from the bottom to the top of
	// the source; the fraction that passes is how open the way is. Full blocks and other shapes are tested with the
	// sheet's two edge rays, each one standing for its half; layers (snow, slabs) with every ray, so each snow layer in
	// front of the emitter hides a bit more of it
	private static final int SHEETS = 4;
	private static final int BINS = 16;
	private static final int LOWER_BINS = (1 << BINS / 2) - 1;
	private static final int UPPER_BINS = LOWER_BINS << BINS / 2;
	private static final int ALL_BINS = LOWER_BINS | UPPER_BINS;
	private static final double SOURCE_SPREAD = 0.35;
	// Blocks that glow all over (glowstone, sea lanterns, furnaces...) send light from their whole surface
	private static final double FULL_BLOCK_SOURCE_SPREAD = 0.49;
	private static final double TARGET_SPREAD = 0.4;
	// Light the emitter doesn't see directly loses this much per block
	static final int INDIRECT_STEP = 3;
	private static final Direction[] DIRECTIONS = Direction.values();
	// Cell classes for the rays
	private static final byte CLEAR = 0;
	private static final byte OPAQUE = 1;
	private static final byte SHAPED = 2;
	// One box over the whole footprint, from the bottom (snow layers, bottom slabs) or from the top (top slabs), up to
	// or down from cellHeights
	private static final byte FLOOR = 3;
	private static final byte CEILING = 4;
	private static final double FLUSH = 1.0E-3;

	private final ClientLevel level;
	private final BlockState[] states = new BlockState[VOLUME];
	private final byte[] cellClasses = new byte[VOLUME];
	private final float[] cellHeights = new float[VOLUME];
	private final int[] stamps = new int[VOLUME];
	private int generation;
	// Whether a FLOOR or CEILING cell has been read in this fill: the flood fill reads every lit cell and its neighbours,
	// so without one the sheets' layer walks can be skipped
	private boolean hasLayers;
	private final Map<BlockState, AABB[]> occlusionBoxes = new Reference2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
	private int originX;
	private int originY;
	private int originZ;
	private double sourceSpread;
	private final IntArrayFIFOQueue[] buckets = new IntArrayFIFOQueue[16];

	EmitterBox(final ClientLevel level) {
		this.level = level;
		for (int i = 0; i < this.buckets.length; i++) {
			this.buckets[i] = new IntArrayFIFOQueue();
		}
	}

	void begin(final BlockPos source, final BlockState sourceState) {
		this.originX = source.getX() - RADIUS;
		this.originY = source.getY() - RADIUS;
		this.originZ = source.getZ() - RADIUS;
		this.sourceSpread = sourceState.isSolidRender() ? FULL_BLOCK_SOURCE_SPREAD : SOURCE_SPREAD;
		this.hasLayers = false;
		if (++this.generation == 0) {
			Arrays.fill(this.stamps, 0);
			this.generation = 1;
		}
		this.setState(CENTER, sourceState);
	}

	long node(final int x, final int y, final int z) {
		return BlockPos.asLong(this.originX + x, this.originY + y, this.originZ + z);
	}

	BlockState state(final int x, final int y, final int z) {
		int index = EmitterField.index(x, y, z);
		if (this.stamps[index] != this.generation) {
			this.setState(index, this.level.getBlockState(this.mutablePos.set(this.originX + x, this.originY + y, this.originZ + z)));
		}
		return this.states[index];
	}

	// Records the unloaded chunks under the box, which the fill read as air
	void addMissingChunks(final EmitterField field) {
		for (int cx = SectionPos.blockToSectionCoord(this.originX); cx <= SectionPos.blockToSectionCoord(this.originX + SIDE - 1); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(this.originZ); cz <= SectionPos.blockToSectionCoord(this.originZ + SIDE - 1); cz++) {
				if (this.level.getChunkSource().getChunk(cx, cz, false) == null) {
					field.addMissingChunk(ChunkPos.pack(cx, cz));
				}
			}
		}
	}

	// D from V (both in local coordinates) for the cells a fill touched: cells the emitter fully sees keep V; through a
	// partly blocked way the emitter acts as a weaker one, losing the blocked fraction of its emission (half a slab
	// halves its reach, and every snow layer piled in front shortens it a bit more); elsewhere light spreads from the
	// lit cells (brightest first), never above V
	void shade(final byte[] vanilla, final byte[] shadowed, final IntArrayList touched, final int emission) {
		IntArrayFIFOQueue[] buckets = this.buckets;
		for (IntArrayFIFOQueue bucket : buckets) {
			bucket.clear();
		}
		for (int i = 0; i < touched.size(); i++) {
			int cell = touched.getInt(i);
			float openness = cell == CENTER ? 1.0F : this.openness(cell);
			int direct = vanilla[cell] - Math.round((1.0F - openness) * emission);
			if (direct > 0) {
				shadowed[cell] = (byte) direct;
				buckets[direct].enqueue(cell);
			}
		}

		for (int bucketLevel = 15; bucketLevel > INDIRECT_STEP; bucketLevel--) {
			IntArrayFIFOQueue bucket = buckets[bucketLevel];
			while (!bucket.isEmpty()) {
				int from = bucket.dequeueInt();
				if (shadowed[from] != bucketLevel) {
					continue;
				}
				int fx = EmitterField.localX(from);
				int fy = EmitterField.localY(from);
				int fz = EmitterField.localZ(from);

				for (Direction direction : DIRECTIONS) {
					int tx = fx + direction.getStepX();
					int ty = fy + direction.getStepY();
					int tz = fz + direction.getStepZ();
					if (!EmitterField.inLocalBox(tx, ty, tz)) {
						continue;
					}
					int to = EmitterField.index(tx, ty, tz);
					int newLevel = Math.min(vanilla[to], bucketLevel - INDIRECT_STEP);
					if (newLevel > shadowed[to]) {
						shadowed[to] = (byte) newLevel;
						buckets[newLevel].enqueue(to);
					}
				}
			}
		}
	}

	// The fraction of the sheets' rays from the emitter's block that reach the target cell, skipping the first and last
	// cell of each
	private float openness(final int target) {
		int x = EmitterField.localX(target);
		int y = EmitterField.localY(target);
		int z = EmitterField.localZ(target);
		double sourceLow = RADIUS + 0.5 - this.sourceSpread;
		double sourceHigh = RADIUS + 0.5 + this.sourceSpread;
		double targetLow = y + 0.5 - TARGET_SPREAD;
		double targetHigh = y + 0.5 + TARGET_SPREAD;
		// A layer in the target is judged from its surface, which is what its light shows
		byte targetClass = this.cellClass(x, y, z);
		if (targetClass == FLOOR) {
			targetLow = Math.min(y + 1 - (1 - this.cellHeights[target]) * (0.5 + TARGET_SPREAD), targetHigh);
		} else if (targetClass == CEILING) {
			targetHigh = Math.max(y + this.cellHeights[target] * (0.5 + TARGET_SPREAD), targetLow);
		}

		int open = 0;
		for (int sheet = 0; sheet < SHEETS; sheet++) {
			double ox = (sheet & 1) == 0 ? -1 : 1;
			double oz = (sheet & 2) == 0 ? -1 : 1;
			double fromX = RADIUS + 0.5 + ox * this.sourceSpread;
			double fromZ = RADIUS + 0.5 + oz * this.sourceSpread;
			double toX = x + 0.5 + ox * TARGET_SPREAD;
			double toZ = z + 0.5 + oz * TARGET_SPREAD;
			int blocked = 0;
			if (!this.segmentClear(fromX, sourceLow, fromZ, toX, targetLow, toZ)) {
				blocked |= LOWER_BINS;
			}
			if (!this.segmentClear(fromX, sourceHigh, fromZ, toX, targetHigh, toZ)) {
				blocked |= UPPER_BINS;
			}
			if (blocked != ALL_BINS && this.hasLayers) {
				blocked = this.layerBins(blocked, fromX, fromZ, toX, toZ, sourceLow, sourceHigh, targetLow, targetHigh, x, z);
			}
			open += BINS - Integer.bitCount(blocked);
		}
		return (float) open / (SHEETS * BINS);
	}

	// Adds the rays of a sheet that cross FLOOR or CEILING layers, walking the columns under the sheet (skipping the
	// emitter's and the target's)
	private int layerBins(
		int blocked, final double fromX, final double fromZ, final double toX, final double toZ,
		final double sourceLow, final double sourceHigh, final double targetLow, final double targetHigh, final int endX, final int endZ
	) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		int x = (int) Math.floor(fromX);
		int z = (int) Math.floor(fromZ);
		int stepX = dx > 0 ? 1 : -1;
		int stepZ = dz > 0 ? 1 : -1;
		double deltaX = dx != 0 ? Math.abs(1 / dx) : Double.POSITIVE_INFINITY;
		double deltaZ = dz != 0 ? Math.abs(1 / dz) : Double.POSITIVE_INFINITY;
		double nextX = dx != 0 ? (dx > 0 ? x + 1 - fromX : fromX - x) * deltaX : Double.POSITIVE_INFINITY;
		double nextZ = dz != 0 ? (dz > 0 ? z + 1 - fromZ : fromZ - z) * deltaZ : Double.POSITIVE_INFINITY;

		double tIn = 0.0;
		for (int steps = 0; steps < 2 * SIDE; steps++) {
			double tOut = Math.min(Math.min(nextX, nextZ), 1.0);
			if ((x != RADIUS || z != RADIUS) && (x != endX || z != endZ)) {
				blocked = this.columnBins(
					blocked, x, z,
					sourceLow + (targetLow - sourceLow) * tIn, sourceHigh + (targetHigh - sourceHigh) * tIn,
					sourceLow + (targetLow - sourceLow) * tOut, sourceHigh + (targetHigh - sourceHigh) * tOut
				);
				if (blocked == ALL_BINS) {
					return blocked;
				}
			}
			if (tOut >= 1.0) {
				return blocked;
			}

			if (nextX <= nextZ) {
				x += stepX;
				nextX += deltaX;
			} else {
				z += stepZ;
				nextZ += deltaZ;
			}
			if (!EmitterField.inLocalBox(x, RADIUS, z)) {
				return blocked;
			}
			tIn = tOut;
		}
		return blocked;
	}

	// Adds the rays of a sheet that cross a layer in one column, given the heights of the sheet's lower and upper edge
	// where it enters and leaves it. Rays are straight lines, so within a column each spans the heights between its
	// entry and exit
	private int columnBins(int blocked, final int x, final int z, final double lowIn, final double highIn, final double lowOut, final double highOut) {
		int minY = Math.max((int) Math.floor(Math.min(lowIn, lowOut)), 0);
		int maxY = Math.min((int) Math.floor(Math.max(highIn, highOut)), SIDE - 1);
		for (int y = minY; y <= maxY; y++) {
			byte cellClass = this.cellClass(x, y, z);
			if (cellClass != FLOOR && cellClass != CEILING) {
				continue;
			}
			float height = this.cellHeights[EmitterField.index(x, y, z)];
			double bottom = cellClass == FLOOR ? y : y + height;
			double top = cellClass == FLOOR ? y + height : y + 1;
			for (int bin = 0; bin < BINS; bin++) {
				double u = (bin + 0.5) / BINS;
				double in = lowIn + (highIn - lowIn) * u;
				double out = lowOut + (highOut - lowOut) * u;
				if (Math.min(in, out) < top && Math.max(in, out) > bottom) {
					blocked |= 1 << bin;
				}
			}
		}
		return blocked;
	}

	// Walks the cells crossed by the segment, skipping the first and last cell
	private boolean segmentClear(final double fromX, final double fromY, final double fromZ, final double toX, final double toY, final double toZ) {
		double dx = toX - fromX;
		double dy = toY - fromY;
		double dz = toZ - fromZ;
		int x = (int) Math.floor(fromX);
		int y = (int) Math.floor(fromY);
		int z = (int) Math.floor(fromZ);
		int endX = (int) Math.floor(toX);
		int endY = (int) Math.floor(toY);
		int endZ = (int) Math.floor(toZ);
		int stepX = dx > 0 ? 1 : -1;
		int stepY = dy > 0 ? 1 : -1;
		int stepZ = dz > 0 ? 1 : -1;
		// t (0 at the start, 1 at the end) of the next boundary on each axis, and between boundaries
		double deltaX = dx != 0 ? Math.abs(1 / dx) : Double.POSITIVE_INFINITY;
		double deltaY = dy != 0 ? Math.abs(1 / dy) : Double.POSITIVE_INFINITY;
		double deltaZ = dz != 0 ? Math.abs(1 / dz) : Double.POSITIVE_INFINITY;
		double nextX = dx != 0 ? (dx > 0 ? x + 1 - fromX : fromX - x) * deltaX : Double.POSITIVE_INFINITY;
		double nextY = dy != 0 ? (dy > 0 ? y + 1 - fromY : fromY - y) * deltaY : Double.POSITIVE_INFINITY;
		double nextZ = dz != 0 ? (dz > 0 ? z + 1 - fromZ : fromZ - z) * deltaZ : Double.POSITIVE_INFINITY;

		for (int steps = 0; steps < 3 * SIDE; steps++) {
			if (nextX <= nextY && nextX <= nextZ) {
				x += stepX;
				nextX += deltaX;
			} else if (nextY <= nextZ) {
				y += stepY;
				nextY += deltaY;
			} else {
				z += stepZ;
				nextZ += deltaZ;
			}

			if (x == endX && y == endY && z == endZ || !EmitterField.inLocalBox(x, y, z)) {
				return true;
			}
			byte cellClass = this.cellClass(x, y, z);
			if (cellClass == OPAQUE || cellClass == SHAPED && this.crossesShape(x, y, z, fromX, fromY, fromZ, dx, dy, dz)) {
				return false;
			}
		}

		return true;
	}

	// Whether the segment crosses the occlusion shape of a SHAPED cell (stairs...: the blocks the engine occludes light
	// with by shape, other than layers). The boxes are shrunk a little so a segment grazing a face passes
	private boolean crossesShape(final int x, final int y, final int z, final double fromX, final double fromY, final double fromZ, final double dx, final double dy, final double dz) {
		double px = fromX - x;
		double py = fromY - y;
		double pz = fromZ - z;
		for (AABB box : this.occlusionBoxes.computeIfAbsent(this.state(x, y, z), EmitterBox::shrunkOcclusionBoxes)) {
			// Slab test: the segment's t range inside the box on each axis, intersected
			double tMin = 0.0;
			double tMax = 1.0;
			if (Math.abs(dx) < 1.0E-9) {
				if (px < box.minX || px > box.maxX) {
					continue;
				}
			} else {
				double t1 = (box.minX - px) / dx;
				double t2 = (box.maxX - px) / dx;
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
			}
			if (Math.abs(dy) < 1.0E-9) {
				if (py < box.minY || py > box.maxY) {
					continue;
				}
			} else {
				double t1 = (box.minY - py) / dy;
				double t2 = (box.maxY - py) / dy;
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
			}
			if (Math.abs(dz) < 1.0E-9) {
				if (pz < box.minZ || pz > box.maxZ) {
					continue;
				}
			} else {
				double t1 = (box.minZ - pz) / dz;
				double t2 = (box.maxZ - pz) / dz;
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
			}
			if (tMin <= tMax) {
				return true;
			}
		}
		return false;
	}

	private static AABB[] shrunkOcclusionBoxes(final BlockState state) {
		return state.getOcclusionShape().toAabbs().stream().map(box -> box.deflate(1.0E-4)).toArray(AABB[]::new);
	}

	private byte cellClass(final int x, final int y, final int z) {
		int index = EmitterField.index(x, y, z);
		if (this.stamps[index] != this.generation) {
			this.state(x, y, z);
		}
		return this.cellClasses[index];
	}

	private void setState(final int index, final BlockState state) {
		this.states[index] = state;
		byte cellClass = CLEAR;
		float height = 0.0F;
		if (state.getLightDampening() >= 15) {
			cellClass = OPAQUE;
		} else if (state.canOcclude() && state.useShapeForLightOcclusion()) {
			cellClass = SHAPED;
			AABB[] boxes = this.occlusionBoxes.computeIfAbsent(state, EmitterBox::shrunkOcclusionBoxes);
			if (boxes.length == 1) {
				AABB box = boxes[0];
				if (box.minX < FLUSH && box.maxX > 1 - FLUSH && box.minZ < FLUSH && box.maxZ > 1 - FLUSH) {
					if (box.minY < FLUSH) {
						cellClass = FLOOR;
						height = (float) box.maxY;
					} else if (box.maxY > 1 - FLUSH) {
						cellClass = CEILING;
						height = (float) box.minY;
					}
				}
			}
		}
		this.cellClasses[index] = cellClass;
		this.cellHeights[index] = height;
		this.hasLayers |= cellClass == FLOOR || cellClass == CEILING;
		this.stamps[index] = this.generation;
	}
}
