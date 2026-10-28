package dev.wifft.betterlight.light;

import static dev.wifft.betterlight.light.EmitterField.CENTER;
import static dev.wifft.betterlight.light.EmitterField.RADIUS;
import static dev.wifft.betterlight.light.EmitterField.SIDE;
import static dev.wifft.betterlight.light.EmitterField.VOLUME;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The box of RADIUS around the emitter being filled: block states read lazily in local coordinates (valid where
 * stamp == generation, so starting a fill clears nothing), and line of sight through it for DirectionalLightOverlay.
 */
final class EmitterBox {
	// Line of sight is tested with one ray per octant, from a corner of a cube spread over the emitter's block to the
	// same corner of a slightly bigger one in the target cell (so the floor is judged from just above it). The fraction
	// that passes is how open the way is: half a slab across a gap stops about half the rays
	static final int RAYS = 8;
	private static final double SOURCE_SPREAD = 0.35;
	private static final double TARGET_SPREAD = 0.4;
	// Cell classes for the rays
	private static final byte CLEAR = 0;
	private static final byte OPAQUE = 1;
	private static final byte SHAPED = 2;

	private final ClientLevel level;
	private final BlockState[] states = new BlockState[VOLUME];
	private final byte[] cellClasses = new byte[VOLUME];
	private final int[] stamps = new int[VOLUME];
	private int generation;
	private final Map<BlockState, AABB[]> occlusionBoxes = new Reference2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
	private int originX;
	private int originY;
	private int originZ;

	EmitterBox(final ClientLevel level) {
		this.level = level;
	}

	void begin(final BlockPos source, final BlockState sourceState) {
		this.originX = source.getX() - RADIUS;
		this.originY = source.getY() - RADIUS;
		this.originZ = source.getZ() - RADIUS;
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

	// How many of the RAYS from the emitter's block reach the target cell, skipping the first and last cell of each
	int clearRays(final int target) {
		int x = EmitterField.localX(target);
		int y = EmitterField.localY(target);
		int z = EmitterField.localZ(target);
		int count = 0;
		for (int ray = 0; ray < RAYS; ray++) {
			double ox = (ray & 1) == 0 ? -1 : 1;
			double oy = (ray & 2) == 0 ? -1 : 1;
			double oz = (ray & 4) == 0 ? -1 : 1;
			if (this.segmentClear(
				RADIUS + 0.5 + ox * SOURCE_SPREAD, RADIUS + 0.5 + oy * SOURCE_SPREAD, RADIUS + 0.5 + oz * SOURCE_SPREAD,
				x + 0.5 + ox * TARGET_SPREAD, y + 0.5 + oy * TARGET_SPREAD, z + 0.5 + oz * TARGET_SPREAD
			)) {
				count++;
			}
		}
		return count;
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

	// Whether the segment crosses the occlusion shape of a SHAPED cell (slabs, stairs...: the blocks the engine occludes
	// light with by shape). The boxes are shrunk a little so a segment grazing a face passes
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
		this.cellClasses[index] = state.getLightDampening() >= 15 ? OPAQUE : state.canOcclude() && state.useShapeForLightOcclusion() ? SHAPED : CLEAR;
		this.stamps[index] = this.generation;
	}
}
