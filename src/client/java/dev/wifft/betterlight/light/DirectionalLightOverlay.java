package dev.wifft.betterlight.light;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.AABB;

// Client-only light of torches and lanterns that only reaches what the emitter can see.
// Each emitter gets a field with two values per cell: V, the vanilla light it casts (the engine's flood fill), and D, its
// shadowed light: V where the cell is in line of sight from the emitter, elsewhere light spread from the lit cells that
// loses INDIRECT_STEP per block, so light leaking through a gap stays a dim glow.
// The engine still holds the vanilla light (the server sends it), so getLightValue takes it back out: if the engine is
// brighter than the strongest V some other source lights the cell and the engine value stands, otherwise the cell shows
// max(D, O), where O is the light of all the other sources, rebuilt from the engine around the fields (mergeSections).
// Fields stop where the engine is already PRUNE_MARGIN brighter than them, like the engine's own flood fill stops at
// brighter cells, so among many emitters each field only covers the cells around it; where that's wrong the cell falls
// back to the vanilla value. Same event flow as FrontLightOverlay, with fills and merges on a per-frame time budget;
// the merged result is kept per section and swapped in as an immutable snapshot for the section compile threads.
public final class DirectionalLightOverlay implements BlockLightOverlay {
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int RADIUS = 14;
	private static final int SIDE = 2 * RADIUS + 1;
	private static final int VOLUME = SIDE * SIDE * SIDE;
	private static final int CENTER = index(RADIUS, RADIUS, RADIUS);
	private static final int INDIRECT_STEP = 3;
	private static final int PRUNE_MARGIN = INDIRECT_STEP;
	// Line of sight is tested with one ray per octant, from a corner of a cube spread over the emitter's block to the
	// same corner of a slightly bigger one in the target cell (so the floor is judged from just above it). The fraction
	// that passes is how open the way is: half a slab across a gap stops about half the rays
	private static final int RAYS = 8;
	private static final double SOURCE_SPREAD = 0.35;
	private static final double TARGET_SPREAD = 0.4;
	// Cell classes for the rays
	private static final byte CLEAR = 0;
	private static final byte OPAQUE = 1;
	private static final byte SHAPED = 2;
	private static final int MERGE_BATCH = 16;
	private static final long BUDGET_NANOS = 3_000_000L;
	private final ClientLevel level;
	private final BlockLightOverlayTarget engine;
	private final Long2ObjectMap<Field> fields = new Long2ObjectOpenHashMap<>();
	// Field sources by the section they're in
	private final Long2ObjectMap<LongSet> sourcesBySection = new Long2ObjectOpenHashMap<>();
	private final LongLinkedOpenHashSet dirtySources = new LongLinkedOpenHashSet();
	// Emitters whose chunk light hasn't arrived yet (the pruning reads it), by chunk
	private final Long2ObjectMap<LongSet> waitingByChunk = new Long2ObjectOpenHashMap<>();
	private final LongLinkedOpenHashSet dirtySections = new LongLinkedOpenHashSet();
	// O per merged section, only read to seed later merges
	private final Long2ObjectMap<byte[]> others = new Long2ObjectOpenHashMap<>();
	// V << 4 | max(D, O) per cell (sectionIndex), 0 where no field reaches
	private volatile Long2ObjectOpenHashMap<byte[]> merged = new Long2ObjectOpenHashMap<>();
	// Scratch for fills: block states and their ray class read lazily (valid where stamp == generation), V and D, and
	// the cells they touched
	private final BlockState[] states = new BlockState[VOLUME];
	private final byte[] cellClasses = new byte[VOLUME];
	private final int[] stateStamps = new int[VOLUME];
	private int generation;
	private final byte[] vanilla = new byte[VOLUME];
	private final byte[] shadowed = new byte[VOLUME];
	private final IntArrayList touched = new IntArrayList();
	private final IntArrayFIFOQueue fillQueue = new IntArrayFIFOQueue();
	private final IntArrayFIFOQueue[] fillBuckets = new IntArrayFIFOQueue[16];
	private final Map<BlockState, AABB[]> occlusionBoxes = new Reference2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
	private int originX;
	private int originY;
	private int originZ;
	// Scratch for merges: per-section arrays (zeroed when returned), and the chunk sections read
	private final ArrayDeque<byte[]> sectionArrays = new ArrayDeque<>();
	private final LongArrayFIFOQueue[] mergeBuckets = new LongArrayFIFOQueue[16];
	private final Long2ObjectMap<LevelChunkSection> chunkSections = new Long2ObjectOpenHashMap<>();

	// cells are localIndex << 8 | V << 4 | D, sorted, for the box of RADIUS around (x, y, z);
	// missingChunks are the unloaded chunks the fill read as air (shared empty set when none)
	private static final class Field {
		final int x;
		final int y;
		final int z;
		final int[] cells;
		LongSet missingChunks;

		Field(final int x, final int y, final int z, final int[] cells, final LongSet missingChunks) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.cells = cells;
			this.missingChunks = missingChunks;
		}

		void addMissingChunk(final long chunkKey) {
			if (this.missingChunks == LongSets.EMPTY_SET) {
				this.missingChunks = new LongOpenHashSet();
			}
			this.missingChunks.add(chunkKey);
		}
	}

	public DirectionalLightOverlay(final ClientLevel level, final BlockLightOverlayTarget engine) {
		this.level = level;
		this.engine = engine;
		for (int i = 0; i < 16; i++) {
			this.fillBuckets[i] = new IntArrayFIFOQueue();
			this.mergeBuckets[i] = new LongArrayFIFOQueue();
		}
	}

	public static boolean isTracked(final BlockState state) {
		if (!BetterlightConfig.directionalTorches) {
			return false;
		}
		Block block = state.getBlock();
		return (block instanceof BaseTorchBlock || block instanceof LanternBlock) && state.getLightEmission() > 0;
	}

	@Override
	public int getLightValue(final long pos, final int engineLevel) {
		Long2ObjectOpenHashMap<byte[]> merged = this.merged;
		if (merged.isEmpty()) {
			return engineLevel;
		}
		byte[] section = merged.get(SectionPos.blockToSection(pos));
		if (section == null) {
			return engineLevel;
		}
		int packed = section[sectionIndex(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos))] & 0xFF;
		return packed == 0 || engineLevel > packed >>> 4 ? engineLevel : packed & 15;
	}

	public void blockChanged(final BlockPos pos, final BlockState oldState, final BlockState newState) {
		long node = pos.asLong();
		if (isTracked(newState) || this.fields.containsKey(node) || this.removeWaiting(node)) {
			this.dirtySources.add(node);
		}

		if (LightEngine.hasDifferentLightProperties(oldState, newState)) {
			// An emission change moves the pruning of every field around it; an opacity change only fills that reach it
			boolean emissionChanged = oldState.getLightEmission() != newState.getLightEmission();
			int sectionX = SectionPos.blockToSectionCoord(pos.getX());
			int sectionY = SectionPos.blockToSectionCoord(pos.getY());
			int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
			for (int sx = sectionX - 1; sx <= sectionX + 1; sx++) {
				for (int sy = sectionY - 1; sy <= sectionY + 1; sy++) {
					for (int sz = sectionZ - 1; sz <= sectionZ + 1; sz++) {
						LongSet sources = this.sourcesBySection.get(SectionPos.asLong(sx, sy, sz));
						if (sources == null) {
							continue;
						}
						LongIterator it = sources.iterator();
						while (it.hasNext()) {
							long source = it.nextLong();
							Field field = this.fields.get(source);
							if (emissionChanged ? inBox(field, pos.getX(), pos.getY(), pos.getZ()) : reaches(field, pos)) {
								this.dirtySources.add(source);
							}
						}
					}
				}
			}

			// The light of the other sources (O) can change up to 15 blocks away
			for (int sx = SectionPos.blockToSectionCoord(pos.getX() - 15); sx <= SectionPos.blockToSectionCoord(pos.getX() + 15); sx++) {
				for (int sy = SectionPos.blockToSectionCoord(pos.getY() - 15); sy <= SectionPos.blockToSectionCoord(pos.getY() + 15); sy++) {
					for (int sz = SectionPos.blockToSectionCoord(pos.getZ() - 15); sz <= SectionPos.blockToSectionCoord(pos.getZ() + 15); sz++) {
						this.markMergedDirty(SectionPos.asLong(sx, sy, sz));
					}
				}
			}
		}
	}

	public void chunkLoaded(final ChunkPos pos) {
		LevelChunk chunk = this.level.getChunkSource().getChunk(pos.x(), pos.z(), false);
		if (chunk != null) {
			chunk.findBlockLightSources((lightPos, state) -> {
				if (isTracked(state)) {
					this.dirtySources.add(lightPos.asLong());
				}
			});
		}

		long chunkKey = pos.pack();
		for (int cx = pos.x() - 1; cx <= pos.x() + 1; cx++) {
			for (int cz = pos.z() - 1; cz <= pos.z() + 1; cz++) {
				for (int sy = this.level.getMinSectionY(); sy <= this.level.getMaxSectionY(); sy++) {
					LongSet sources = this.sourcesBySection.get(SectionPos.asLong(cx, sy, cz));
					if (sources == null) {
						continue;
					}
					LongIterator it = sources.iterator();
					while (it.hasNext()) {
						long source = it.nextLong();
						if (this.fields.get(source).missingChunks.contains(chunkKey)) {
							this.dirtySources.add(source);
						}
					}
				}
			}
		}
	}

	public void chunkUnloaded(final ChunkPos pos) {
		long chunkKey = pos.pack();
		for (int sy = this.level.getMinSectionY(); sy <= this.level.getMaxSectionY(); sy++) {
			LongSet sources = this.sourcesBySection.get(SectionPos.asLong(pos.x(), sy, pos.z()));
			if (sources != null) {
				for (long source : sources.toLongArray()) {
					this.markFieldSections(this.removeField(source));
				}
			}
		}
		this.waitingByChunk.remove(chunkKey);

		// Fills around it are redone if the chunk comes back
		for (int cx = pos.x() - 1; cx <= pos.x() + 1; cx++) {
			for (int cz = pos.z() - 1; cz <= pos.z() + 1; cz++) {
				for (int sy = this.level.getMinSectionY(); sy <= this.level.getMaxSectionY(); sy++) {
					LongSet sources = this.sourcesBySection.get(SectionPos.asLong(cx, sy, cz));
					if (sources == null) {
						continue;
					}
					LongIterator it = sources.iterator();
					while (it.hasNext()) {
						this.fields.get(it.nextLong()).addMissingChunk(chunkKey);
					}
				}
			}
		}
	}

	// Server light arrived for a chunk: O is rebuilt from it, and the emitters that were waiting for it are filled
	public void lightDataApplied(final int chunkX, final int chunkZ) {
		for (int sy = this.level.getMinSectionY() - 1; sy <= this.level.getMaxSectionY() + 1; sy++) {
			this.markMergedDirty(SectionPos.asLong(chunkX, sy, chunkZ));
		}

		LongSet waiting = this.waitingByChunk.remove(ChunkPos.pack(chunkX, chunkZ));
		if (waiting != null) {
			this.dirtySources.addAll(waiting);
		}
	}

	public void update() {
		if (this.dirtySources.isEmpty() && this.dirtySections.isEmpty()) {
			return;
		}

		// Big batches (chunk loads, many emitters) are spread over several frames
		long deadline = System.nanoTime() + BUDGET_NANOS;
		while (!this.dirtySources.isEmpty() && System.nanoTime() < deadline) {
			long node = this.dirtySources.removeFirstLong();
			Field oldField = this.removeField(node);
			if (oldField != null) {
				this.markFieldSections(oldField);
			}

			BlockPos pos = BlockPos.of(node);
			BlockState state = this.level.getBlockState(pos);
			if (!isTracked(state) || this.level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false) == null) {
				continue;
			}
			if (this.engine.betterlight$getRawLightValue(node) < state.getLightEmission()) {
				this.waitingByChunk.computeIfAbsent(ChunkPos.pack(pos), k -> new LongOpenHashSet()).add(node);
				continue;
			}

			Field field = this.fill(pos, state);
			this.fields.put(node, field);
			this.sourcesBySection.computeIfAbsent(SectionPos.blockToSection(node), k -> new LongOpenHashSet()).add(node);
			this.markFieldSections(field);
		}

		// Merge once the fills are done, so sections aren't rebuilt for every step of a big batch; all the batches of a
		// frame go into one copy of the snapshot
		Long2ObjectOpenHashMap<byte[]> published = null;
		while (this.dirtySources.isEmpty() && !this.dirtySections.isEmpty() && System.nanoTime() < deadline) {
			if (published == null) {
				published = new Long2ObjectOpenHashMap<>(this.merged);
			}
			this.mergeSections(published);
		}
		if (published != null) {
			this.merged = published;
		}
	}

	private boolean removeWaiting(final long node) {
		long chunkKey = ChunkPos.pack(SectionPos.blockToSectionCoord(BlockPos.getX(node)), SectionPos.blockToSectionCoord(BlockPos.getZ(node)));
		LongSet waiting = this.waitingByChunk.get(chunkKey);
		if (waiting == null || !waiting.remove(node)) {
			return false;
		}
		if (waiting.isEmpty()) {
			this.waitingByChunk.remove(chunkKey);
		}
		return true;
	}

	private Field removeField(final long source) {
		Field field = this.fields.remove(source);
		if (field != null) {
			long sectionKey = SectionPos.blockToSection(source);
			LongSet sources = this.sourcesBySection.get(sectionKey);
			sources.remove(source);
			if (sources.isEmpty()) {
				this.sourcesBySection.remove(sectionKey);
			}
		}
		return field;
	}

	private void markMergedDirty(final long sectionKey) {
		if (this.merged.containsKey(sectionKey)) {
			this.dirtySections.add(sectionKey);
		}
	}

	private void markFieldSections(final Field field) {
		for (int cell : field.cells) {
			int local = cell >>> 8;
			this.dirtySections.add(SectionPos.asLong(
				SectionPos.blockToSectionCoord(field.x - RADIUS + local % SIDE),
				SectionPos.blockToSectionCoord(field.y - RADIUS + local / (SIDE * SIDE)),
				SectionPos.blockToSectionCoord(field.z - RADIUS + local / SIDE % SIDE)
			));
		}
	}

	private static boolean reaches(final Field field, final BlockPos pos) {
		if (vanillaAt(field, pos.getX(), pos.getY(), pos.getZ()) > 0) {
			return true;
		}

		for (Direction direction : DIRECTIONS) {
			if (vanillaAt(field, pos.getX() + direction.getStepX(), pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ()) > 0) {
				return true;
			}
		}

		return false;
	}

	private static boolean inBox(final Field field, final int x, final int y, final int z) {
		return Math.abs(x - field.x) <= RADIUS && Math.abs(y - field.y) <= RADIUS && Math.abs(z - field.z) <= RADIUS;
	}

	private static int vanillaAt(final Field field, final int x, final int y, final int z) {
		if (!inBox(field, x, y, z)) {
			return 0;
		}
		int local = index(x - field.x + RADIUS, y - field.y + RADIUS, z - field.z + RADIUS);
		int[] cells = field.cells;
		int low = 0;
		int high = cells.length - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int midLocal = cells[mid] >>> 8;
			if (midLocal < local) {
				low = mid + 1;
			} else if (midLocal > local) {
				high = mid - 1;
			} else {
				return cells[mid] >>> 4 & 15;
			}
		}
		return 0;
	}

	private Field fill(final BlockPos source, final BlockState sourceState) {
		this.originX = source.getX() - RADIUS;
		this.originY = source.getY() - RADIUS;
		this.originZ = source.getZ() - RADIUS;
		if (++this.generation == 0) {
			Arrays.fill(this.stateStamps, 0);
			this.generation = 1;
		}
		this.setState(CENTER, sourceState);
		byte[] vanilla = this.vanilla;
		byte[] shadowed = this.shadowed;
		IntArrayList touched = this.touched;
		touched.clear();

		// Vanilla light, the same flood fill as the engine, stopping where the engine is clearly brighter
		vanilla[CENTER] = (byte) sourceState.getLightEmission();
		touched.add(CENTER);
		IntArrayFIFOQueue queue = this.fillQueue;
		queue.enqueue(CENTER);
		while (!queue.isEmpty()) {
			int from = queue.dequeueInt();
			int fromLevel = vanilla[from];
			int fx = from % SIDE;
			int fz = from / SIDE % SIDE;
			int fy = from / (SIDE * SIDE);
			BlockState fromState = this.state(fx, fy, fz);

			for (Direction direction : DIRECTIONS) {
				int tx = fx + direction.getStepX();
				int ty = fy + direction.getStepY();
				int tz = fz + direction.getStepZ();
				if (!inBox(tx, ty, tz)) {
					continue;
				}
				int to = index(tx, ty, tz);
				BlockState toState = this.state(tx, ty, tz);
				int newLevel = fromLevel - LightEngine.getLightDampeningInto(fromState, toState, direction, Math.max(1, toState.getLightDampening()));
				if (newLevel > vanilla[to]
					&& this.engine.betterlight$getRawLightValue(BlockPos.asLong(this.originX + tx, this.originY + ty, this.originZ + tz)) - newLevel < PRUNE_MARGIN) {
					if (vanilla[to] == 0) {
						touched.add(to);
					}
					vanilla[to] = (byte) newLevel;
					if (newLevel > 1) {
						queue.enqueue(to);
					}
				}
			}
		}

		// Direct light: cells the emitter fully sees keep the vanilla value; through a partly blocked way the emitter acts
		// as a weaker one, losing the blocked fraction of its emission (half a slab halves its reach)
		int emission = sourceState.getLightEmission();
		IntArrayFIFOQueue[] buckets = this.fillBuckets;
		for (IntArrayFIFOQueue bucket : buckets) {
			bucket.clear();
		}
		for (int i = 0; i < touched.size(); i++) {
			int cell = touched.getInt(i);
			int rays = cell == CENTER ? RAYS : this.clearRays(cell);
			int direct = vanilla[cell] - Math.round((float) ((RAYS - rays) * emission) / RAYS);
			if (direct > 0) {
				shadowed[cell] = (byte) direct;
				buckets[direct].enqueue(cell);
			}
		}

		// Indirect light: spreads from the lit cells (brightest first), never above the vanilla value
		for (int bucketLevel = 15; bucketLevel > INDIRECT_STEP; bucketLevel--) {
			IntArrayFIFOQueue bucket = buckets[bucketLevel];
			while (!bucket.isEmpty()) {
				int from = bucket.dequeueInt();
				if (shadowed[from] != bucketLevel) {
					continue;
				}
				int fx = from % SIDE;
				int fz = from / SIDE % SIDE;
				int fy = from / (SIDE * SIDE);

				for (Direction direction : DIRECTIONS) {
					int tx = fx + direction.getStepX();
					int ty = fy + direction.getStepY();
					int tz = fz + direction.getStepZ();
					if (!inBox(tx, ty, tz)) {
						continue;
					}
					int to = index(tx, ty, tz);
					int newLevel = Math.min(vanilla[to], bucketLevel - INDIRECT_STEP);
					if (newLevel > shadowed[to]) {
						shadowed[to] = (byte) newLevel;
						buckets[newLevel].enqueue(to);
					}
				}
			}
		}

		touched.sort(null);
		int[] cells = new int[touched.size()];
		for (int i = 0; i < cells.length; i++) {
			int cell = touched.getInt(i);
			cells[i] = cell << 8 | vanilla[cell] << 4 | shadowed[cell];
			vanilla[cell] = 0;
			shadowed[cell] = 0;
		}

		Field field = new Field(source.getX(), source.getY(), source.getZ(), cells, LongSets.EMPTY_SET);
		for (int cx = SectionPos.blockToSectionCoord(this.originX); cx <= SectionPos.blockToSectionCoord(this.originX + SIDE - 1); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(this.originZ); cz <= SectionPos.blockToSectionCoord(this.originZ + SIDE - 1); cz++) {
				if (this.level.getChunkSource().getChunk(cx, cz, false) == null) {
					field.addMissingChunk(ChunkPos.pack(cx, cz));
				}
			}
		}
		return field;
	}

	// How many of the RAYS from the emitter's block reach the target cell, skipping the first and last cell of each
	private int clearRays(final int target) {
		int x = target % SIDE;
		int z = target / SIDE % SIDE;
		int y = target / (SIDE * SIDE);
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

			if (x == endX && y == endY && z == endZ || !inBox(x, y, z)) {
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
		for (AABB box : this.occlusionBoxes.computeIfAbsent(this.state(x, y, z), DirectionalLightOverlay::shrunkOcclusionBoxes)) {
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

	private BlockState state(final int x, final int y, final int z) {
		int index = index(x, y, z);
		if (this.stateStamps[index] != this.generation) {
			this.setState(index, this.level.getBlockState(this.mutablePos.set(this.originX + x, this.originY + y, this.originZ + z)));
		}
		return this.states[index];
	}

	private byte cellClass(final int x, final int y, final int z) {
		int index = index(x, y, z);
		if (this.stateStamps[index] != this.generation) {
			this.state(x, y, z);
		}
		return this.cellClasses[index];
	}

	private void setState(final int index, final BlockState state) {
		this.states[index] = state;
		this.cellClasses[index] = state.getLightDampening() >= 15 ? OPAQUE : state.canOcclude() && state.useShapeForLightOcclusion() ? SHAPED : CLEAR;
		this.stateStamps[index] = this.generation;
	}

	// Rebuilds the merged values of a batch of dirty sections from the fields and the engine into published
	private void mergeSections(final Long2ObjectOpenHashMap<byte[]> published) {
		LongSet sections = new LongOpenHashSet();
		while (sections.size() < MERGE_BATCH && !this.dirtySections.isEmpty()) {
			sections.add(this.dirtySections.removeFirstLong());
		}

		// Fields are at most RADIUS < 16 from their source, so only sources in the neighbouring sections can reach
		LongSet candidates = new LongOpenHashSet();
		LongIterator sectionIt = sections.iterator();
		while (sectionIt.hasNext()) {
			long key = sectionIt.nextLong();
			for (int sx = SectionPos.x(key) - 1; sx <= SectionPos.x(key) + 1; sx++) {
				for (int sy = SectionPos.y(key) - 1; sy <= SectionPos.y(key) + 1; sy++) {
					for (int sz = SectionPos.z(key) - 1; sz <= SectionPos.z(key) + 1; sz++) {
						LongSet sources = this.sourcesBySection.get(SectionPos.asLong(sx, sy, sz));
						if (sources != null) {
							candidates.addAll(sources);
						}
					}
				}
			}
		}

		Long2ObjectMap<byte[]> vanilla = new Long2ObjectOpenHashMap<>();
		Long2ObjectMap<byte[]> shadowed = new Long2ObjectOpenHashMap<>();
		LongIterator candidateIt = candidates.iterator();
		while (candidateIt.hasNext()) {
			Field field = this.fields.get(candidateIt.nextLong());
			for (int cell : field.cells) {
				int local = cell >>> 8;
				int x = field.x - RADIUS + local % SIDE;
				int y = field.y - RADIUS + local / (SIDE * SIDE);
				int z = field.z - RADIUS + local / SIDE % SIDE;
				long key = SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z));
				if (sections.contains(key)) {
					byte[] v = vanilla.computeIfAbsent(key, k -> this.takeSectionArray());
					byte[] d = shadowed.computeIfAbsent(key, k -> this.takeSectionArray());
					int i = sectionIndex(x, y, z);
					v[i] = (byte) Math.max(v[i], cell >>> 4 & 15);
					d[i] = (byte) Math.max(d[i], cell & 15);
				}
			}
		}

		// O: exact where the engine is brighter than V; elsewhere spread (like the engine) from those cells, from the
		// other emitters and from the known values around the merged cells
		Long2ObjectMap<byte[]> other = new Long2ObjectOpenHashMap<>();
		LongArrayFIFOQueue[] buckets = this.mergeBuckets;
		this.chunkSections.clear();

		for (Long2ObjectMap.Entry<byte[]> entry : vanilla.long2ObjectEntrySet()) {
			byte[] v = entry.getValue();
			byte[] o = this.takeSectionArray();
			other.put(entry.getLongKey(), o);
			int minX = SectionPos.sectionToBlockCoord(SectionPos.x(entry.getLongKey()));
			int minY = SectionPos.sectionToBlockCoord(SectionPos.y(entry.getLongKey()));
			int minZ = SectionPos.sectionToBlockCoord(SectionPos.z(entry.getLongKey()));

			for (int i = 0; i < 4096; i++) {
				if (v[i] == 0) {
					continue;
				}
				int x = minX + (i & 15);
				int y = minY + (i >> 8);
				int z = minZ + (i >> 4 & 15);
				long node = BlockPos.asLong(x, y, z);
				int engineLevel = this.engine.betterlight$getRawLightValue(node);
				int otherLevel;
				if (engineLevel > v[i]) {
					otherLevel = engineLevel;
				} else {
					BlockState state = this.mergeState(x, y, z);
					otherLevel = isTracked(state) || FrontEmitters.isFrontEmitter(state) ? 0 : state.getLightEmission();
					for (Direction direction : DIRECTIONS) {
						long neighbor = BlockPos.offset(node, direction);
						int known = this.knownOther(neighbor, sections, vanilla, published);
						if (known - 1 > otherLevel) {
							BlockState neighborState = this.mergeState(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
							otherLevel = Math.max(otherLevel, known - LightEngine.getLightDampeningInto(neighborState, state, direction.getOpposite(), Math.max(1, state.getLightDampening())));
						}
					}
				}

				o[i] = (byte) otherLevel;
				if (otherLevel > 1) {
					buckets[otherLevel].enqueue(node);
				}
			}
		}

		for (int bucketLevel = 15; bucketLevel > 1; bucketLevel--) {
			LongArrayFIFOQueue bucket = buckets[bucketLevel];
			while (!bucket.isEmpty()) {
				long node = bucket.dequeueLong();
				int fromX = BlockPos.getX(node);
				int fromY = BlockPos.getY(node);
				int fromZ = BlockPos.getZ(node);
				if (other.get(SectionPos.blockToSection(node))[sectionIndex(fromX, fromY, fromZ)] != bucketLevel) {
					continue;
				}
				BlockState fromState = this.mergeState(fromX, fromY, fromZ);

				for (Direction direction : DIRECTIONS) {
					long toNode = BlockPos.offset(node, direction);
					long toSection = SectionPos.blockToSection(toNode);
					byte[] v = vanilla.get(toSection);
					int toIndex = sectionIndex(BlockPos.getX(toNode), BlockPos.getY(toNode), BlockPos.getZ(toNode));
					if (v == null || v[toIndex] == 0) {
						continue;
					}
					byte[] o = other.get(toSection);
					BlockState toState = this.mergeState(fromX + direction.getStepX(), fromY + direction.getStepY(), fromZ + direction.getStepZ());
					int newLevel = bucketLevel - LightEngine.getLightDampeningInto(fromState, toState, direction, Math.max(1, toState.getLightDampening()));
					if (newLevel > o[toIndex]) {
						o[toIndex] = (byte) newLevel;
						if (newLevel > 1) {
							buckets[newLevel].enqueue(toNode);
						}
					}
				}
			}
		}

		LongIterator it = sections.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			byte[] packed = null;
			byte[] v = vanilla.get(key);
			byte[] o = other.get(key);
			if (v != null) {
				byte[] d = shadowed.get(key);
				for (int i = 0; i < 4096; i++) {
					if (v[i] > 0) {
						if (packed == null) {
							packed = new byte[4096];
						}
						packed[i] = (byte) (v[i] << 4 | Math.max(d[i], o[i]));
					}
				}
				this.returnSectionArray(v);
				this.returnSectionArray(d);
			}

			byte[] oldPacked = packed != null ? published.put(key, packed) : published.remove(key);
			byte[] oldOther = packed != null ? this.others.put(key, o) : this.others.remove(key);
			if (oldOther != null) {
				this.returnSectionArray(oldOther);
			}
			if (packed == null && o != null) {
				this.returnSectionArray(o);
			}
			this.setChangedDirty(key, oldPacked, packed);
		}
	}

	private byte[] takeSectionArray() {
		byte[] array = this.sectionArrays.poll();
		return array != null ? array : new byte[4096];
	}

	private void returnSectionArray(final byte[] array) {
		Arrays.fill(array, (byte) 0);
		this.sectionArrays.push(array);
	}

	// Block states for the merge, straight from the chunk sections (cached for the merge)
	private BlockState mergeState(final int x, final int y, final int z) {
		long sectionKey = SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z));
		LevelChunkSection section = this.chunkSections.get(sectionKey);
		if (section == null && !this.chunkSections.containsKey(sectionKey)) {
			LevelChunk chunk = this.level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z), false);
			int sectionIndex = this.level.getSectionIndex(y);
			section = chunk != null && sectionIndex >= 0 && sectionIndex < chunk.getSections().length ? chunk.getSection(sectionIndex) : null;
			this.chunkSections.put(sectionKey, section);
		}
		return section != null ? section.getBlockState(x & 15, y & 15, z & 15) : Blocks.AIR.defaultBlockState();
	}

	// O of a cell next to the merged ones, or -1 if it's merged too (the spread computes it)
	private int knownOther(final long node, final LongSet sections, final Long2ObjectMap<byte[]> vanilla, final Long2ObjectOpenHashMap<byte[]> published) {
		long sectionKey = SectionPos.blockToSection(node);
		int i = sectionIndex(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
		if (sections.contains(sectionKey)) {
			byte[] v = vanilla.get(sectionKey);
			return v != null && v[i] > 0 ? -1 : this.engine.betterlight$getRawLightValue(node);
		}

		byte[] packed = published.get(sectionKey);
		if (packed != null && packed[i] != 0) {
			byte[] o = this.others.get(sectionKey);
			return o != null ? o[i] : 0;
		}
		return this.engine.betterlight$getRawLightValue(node);
	}

	// Rebuild only around the cells whose value changed (smooth lighting reads one block out)
	private void setChangedDirty(final long sectionKey, final byte[] oldPacked, final byte[] newPacked) {
		int minX = 16;
		int minY = 16;
		int minZ = 16;
		int maxX = -1;
		int maxY = -1;
		int maxZ = -1;
		for (int i = 0; i < 4096; i++) {
			byte oldValue = oldPacked != null ? oldPacked[i] : 0;
			byte newValue = newPacked != null ? newPacked[i] : 0;
			if (oldValue != newValue) {
				int x = i & 15;
				int y = i >> 8;
				int z = i >> 4 & 15;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
			}
		}

		if (maxX >= 0) {
			int baseX = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey));
			int baseY = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey));
			int baseZ = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey));
			this.level.setSectionRangeDirty(SectionPos.blockToSectionCoord(baseX + minX - 1), SectionPos.blockToSectionCoord(baseY + minY - 1), SectionPos.blockToSectionCoord(baseZ + minZ - 1), SectionPos.blockToSectionCoord(baseX + maxX + 1), SectionPos.blockToSectionCoord(baseY + maxY + 1), SectionPos.blockToSectionCoord(baseZ + maxZ + 1));
		}
	}

	private static boolean inBox(final int x, final int y, final int z) {
		return x >= 0 && x < SIDE && y >= 0 && y < SIDE && z >= 0 && z < SIDE;
	}

	private static int index(final int x, final int y, final int z) {
		return (y * SIDE + z) * SIDE + x;
	}

	private static int sectionIndex(final int x, final int y, final int z) {
		return (y & 15) << 8 | (z & 15) << 4 | x & 15;
	}
}
