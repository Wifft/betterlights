package dev.wifft.betterlight.light;

import static dev.wifft.betterlight.light.EmitterField.CENTER;
import static dev.wifft.betterlight.light.EmitterField.VOLUME;

import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.duck.BlockLightOverlayTarget;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.Arrays;
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
import org.jspecify.annotations.Nullable;

// Client-only light of torches and lanterns that only reaches what the emitter can see.
// Each emitter gets a field with two values per cell: V, the vanilla light it casts (the engine's flood fill), and D, its
// shadowed light: V where the cell is in line of sight from the emitter (EmitterBox.clearRays), elsewhere light spread
// from the lit cells that loses INDIRECT_STEP per block, so light leaking through a gap stays a dim glow.
// The engine still holds the vanilla light (the server sends it), so getLightValue takes it back out: if the engine is
// brighter than the strongest V some other source lights the cell and the engine value stands, otherwise the cell shows
// max(D, O), where O is the light of all the other sources, rebuilt from the engine around the fields (mergeSections).
// Fields stop where the engine is already PRUNE_MARGIN brighter than them, like the engine's own flood fill stops at
// brighter cells, so among many emitters each field only covers the cells around it; where that's wrong the cell falls
// back to the vanilla value. The snapshot holds V << 4 | max(D, O).
public final class DirectionalLightOverlay extends EmitterLightOverlay {
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int INDIRECT_STEP = 3;
	private static final int PRUNE_MARGIN = INDIRECT_STEP;
	private final BlockLightOverlayTarget engine;
	// Emitters whose chunk light hasn't arrived yet (the pruning reads it), by chunk
	private final Long2ObjectMap<LongSet> waitingByChunk = new Long2ObjectOpenHashMap<>();
	// O per merged section, only read to seed later merges
	private final Long2ObjectMap<byte[]> others = new Long2ObjectOpenHashMap<>();
	// Scratch for fills: V and D in local coordinates, and the cells they touched
	private final byte[] vanilla = new byte[VOLUME];
	private final byte[] shadowed = new byte[VOLUME];
	private final IntArrayList touched = new IntArrayList();
	private final IntArrayFIFOQueue fillQueue = new IntArrayFIFOQueue();
	private final IntArrayFIFOQueue[] fillBuckets = new IntArrayFIFOQueue[16];
	// Scratch for merges: per-section arrays (zeroed when returned), and the chunk sections read
	private final ArrayDeque<byte[]> sectionArrays = new ArrayDeque<>();
	private final LongArrayFIFOQueue[] mergeBuckets = new LongArrayFIFOQueue[16];
	private final Long2ObjectMap<LevelChunkSection> chunkSections = new Long2ObjectOpenHashMap<>();

	public DirectionalLightOverlay(final ClientLevel level, final BlockLightOverlayTarget engine) {
		super(level);
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
		int packed = this.snapshot.get(pos);
		return packed == 0 || engineLevel > packed >>> 4 ? engineLevel : packed & 15;
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

	@Override
	protected boolean isEmitter(final BlockState state) {
		return isTracked(state);
	}

	// An emission change moves the pruning of every field around it; an opacity change only fills that reach it
	@Override
	protected boolean isAffected(final EmitterField field, final BlockPos pos, final BlockState oldState, final BlockState newState) {
		return oldState.getLightEmission() != newState.getLightEmission() ? field.inBox(pos.getX(), pos.getY(), pos.getZ()) : field.reaches(pos);
	}

	// The light of the other sources (O) can change up to 15 blocks away
	@Override
	protected void lightPropertiesChanged(final BlockPos pos) {
		for (int sx = SectionPos.blockToSectionCoord(pos.getX() - 15); sx <= SectionPos.blockToSectionCoord(pos.getX() + 15); sx++) {
			for (int sy = SectionPos.blockToSectionCoord(pos.getY() - 15); sy <= SectionPos.blockToSectionCoord(pos.getY() + 15); sy++) {
				for (int sz = SectionPos.blockToSectionCoord(pos.getZ() - 15); sz <= SectionPos.blockToSectionCoord(pos.getZ() + 15); sz++) {
					this.markMergedDirty(SectionPos.asLong(sx, sy, sz));
				}
			}
		}
	}

	@Override
	protected boolean forgetPending(final long node) {
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

	@Override
	protected void chunkForgotten(final long chunkKey) {
		this.waitingByChunk.remove(chunkKey);
	}

	@Override
	protected @Nullable EmitterField fill(final BlockPos source, final BlockState sourceState) {
		int emission = sourceState.getLightEmission();
		if (this.engine.betterlight$getRawLightValue(source.asLong()) < emission) {
			this.waitingByChunk.computeIfAbsent(ChunkPos.pack(source), k -> new LongOpenHashSet()).add(source.asLong());
			return null;
		}

		EmitterBox box = this.box;
		box.begin(source, sourceState);
		byte[] vanilla = this.vanilla;
		byte[] shadowed = this.shadowed;
		IntArrayList touched = this.touched;
		touched.clear();

		// Vanilla light, the same flood fill as the engine, stopping where the engine is clearly brighter
		vanilla[CENTER] = (byte) emission;
		touched.add(CENTER);
		IntArrayFIFOQueue queue = this.fillQueue;
		queue.enqueue(CENTER);
		while (!queue.isEmpty()) {
			int from = queue.dequeueInt();
			int fromLevel = vanilla[from];
			int fx = EmitterField.localX(from);
			int fy = EmitterField.localY(from);
			int fz = EmitterField.localZ(from);
			BlockState fromState = box.state(fx, fy, fz);

			for (Direction direction : DIRECTIONS) {
				int tx = fx + direction.getStepX();
				int ty = fy + direction.getStepY();
				int tz = fz + direction.getStepZ();
				if (!EmitterField.inLocalBox(tx, ty, tz)) {
					continue;
				}
				int to = EmitterField.index(tx, ty, tz);
				BlockState toState = box.state(tx, ty, tz);
				int newLevel = fromLevel - LightEngine.getLightDampeningInto(fromState, toState, direction, Math.max(1, toState.getLightDampening()));
				if (newLevel > vanilla[to] && this.engine.betterlight$getRawLightValue(box.node(tx, ty, tz)) - newLevel < PRUNE_MARGIN) {
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
		IntArrayFIFOQueue[] buckets = this.fillBuckets;
		for (IntArrayFIFOQueue bucket : buckets) {
			bucket.clear();
		}
		for (int i = 0; i < touched.size(); i++) {
			int cell = touched.getInt(i);
			int rays = cell == CENTER ? EmitterBox.RAYS : box.clearRays(cell);
			int direct = vanilla[cell] - Math.round((float) ((EmitterBox.RAYS - rays) * emission) / EmitterBox.RAYS);
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

		touched.sort(null);
		int[] cells = new int[touched.size()];
		for (int i = 0; i < cells.length; i++) {
			int cell = touched.getInt(i);
			cells[i] = cell << 8 | vanilla[cell] << 4 | shadowed[cell];
			vanilla[cell] = 0;
			shadowed[cell] = 0;
		}

		EmitterField field = new EmitterField(source, cells);
		box.addMissingChunks(field);
		return field;
	}

	@Override
	protected void mergeSections(final LongSet sections) {
		LongSet candidates = new LongOpenHashSet();
		LongIterator sectionIt = sections.iterator();
		while (sectionIt.hasNext()) {
			this.forEachSourceAround(sectionIt.nextLong(), candidates::add);
		}

		Long2ObjectMap<byte[]> vanilla = new Long2ObjectOpenHashMap<>();
		Long2ObjectMap<byte[]> shadowed = new Long2ObjectOpenHashMap<>();
		LongIterator candidateIt = candidates.iterator();
		while (candidateIt.hasNext()) {
			EmitterField field = this.fields.get(candidateIt.nextLong());
			for (int cell : field.cells) {
				long key = field.cellSection(cell);
				if (sections.contains(key)) {
					byte[] v = vanilla.computeIfAbsent(key, k -> this.takeSectionArray());
					byte[] d = shadowed.computeIfAbsent(key, k -> this.takeSectionArray());
					int i = SectionLightSnapshot.index(field.cellX(cell), field.cellY(cell), field.cellZ(cell));
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
						int known = this.knownOther(neighbor, sections, vanilla);
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
				if (other.get(SectionPos.blockToSection(node))[SectionLightSnapshot.index(fromX, fromY, fromZ)] != bucketLevel) {
					continue;
				}
				BlockState fromState = this.mergeState(fromX, fromY, fromZ);

				for (Direction direction : DIRECTIONS) {
					long toNode = BlockPos.offset(node, direction);
					long toSection = SectionPos.blockToSection(toNode);
					byte[] v = vanilla.get(toSection);
					int toIndex = SectionLightSnapshot.index(BlockPos.getX(toNode), BlockPos.getY(toNode), BlockPos.getZ(toNode));
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

			this.snapshot.put(key, packed);
			byte[] oldOther = packed != null ? this.others.put(key, o) : this.others.remove(key);
			if (oldOther != null) {
				this.returnSectionArray(oldOther);
			}
			if (packed == null && o != null) {
				this.returnSectionArray(o);
			}
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
	private int knownOther(final long node, final LongSet sections, final Long2ObjectMap<byte[]> vanilla) {
		long sectionKey = SectionPos.blockToSection(node);
		int i = SectionLightSnapshot.index(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
		if (sections.contains(sectionKey)) {
			byte[] v = vanilla.get(sectionKey);
			return v != null && v[i] > 0 ? -1 : this.engine.betterlight$getRawLightValue(node);
		}

		byte[] packed = this.snapshot.section(sectionKey);
		if (packed != null && packed[i] != 0) {
			byte[] o = this.others.get(sectionKey);
			return o != null ? o[i] : 0;
		}
		return this.engine.betterlight$getRawLightValue(node);
	}
}
