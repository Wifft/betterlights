package dev.wifft.betterlight.light;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;

// Client-only block light of front emitters (lit furnaces, jack o'lanterns), which the light engine ignores (FrontEmitters.isFrontEmitter).
// Each furnace gets its own flood fill that never steps backwards, so it only lights the half-space in front of it.
// Block and chunk events only mark furnaces dirty; update() (once per frame, from ClientLevel.update) refills them and
// publishes the merged field. Read from section compile threads: the merged field is an immutable snapshot swapped in on the main thread.
public final class FrontLightOverlay implements BlockLightOverlay {
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final Long2ByteOpenHashMap EMPTY = new Long2ByteOpenHashMap();
	private final ClientLevel level;
	private final Long2ObjectMap<Field> fields = new Long2ObjectOpenHashMap<>();
	private final LongSet dirtySources = new LongOpenHashSet();
	private boolean mergedDirty;
	private volatile Long2ByteOpenHashMap merged = new Long2ByteOpenHashMap();

	// Light of one furnace and the chunks its fill read (unloaded ones read as air, so it's redone when they load)
	private record Field(Long2ByteOpenHashMap light, LongSet chunks) {
	}

	public FrontLightOverlay(final ClientLevel level) {
		this.level = level;
	}

	@Override
	public int getLightValue(final long pos, final int engineLevel) {
		return engineLevel < 15 ? Math.max(engineLevel, this.merged.get(pos)) : engineLevel;
	}

	public void blockChanged(final BlockPos pos, final BlockState oldState, final BlockState newState) {
		long node = pos.asLong();
		if (FrontEmitters.isFrontEmitter(newState) || this.fields.containsKey(node)) {
			this.dirtySources.add(node);
		}

		if (LightEngine.hasDifferentLightProperties(oldState, newState)) {
			// Only fills that reached the block or one of its neighbours can change
			for (Long2ObjectMap.Entry<Field> entry : this.fields.long2ObjectEntrySet()) {
				if (reaches(entry.getValue().light(), pos)) {
					this.dirtySources.add(entry.getLongKey());
				}
			}
		}
	}

	public void chunkLoaded(final ChunkPos pos) {
		LevelChunk chunk = this.level.getChunkSource().getChunk(pos.x(), pos.z(), false);
		if (chunk != null) {
			chunk.findBlockLightSources((lightPos, state) -> {
				if (FrontEmitters.isFrontEmitter(state)) {
					this.dirtySources.add(lightPos.asLong());
				}

			});
		}

		long chunkKey = pos.pack();
		for (Long2ObjectMap.Entry<Field> entry : this.fields.long2ObjectEntrySet()) {
			if (entry.getValue().chunks().contains(chunkKey)) {
				this.dirtySources.add(entry.getLongKey());
			}
		}
	}

	public void chunkUnloaded(final ChunkPos pos) {
		// Called while the chunk is still in the cache, so drop its furnaces directly
		if (this.fields.keySet().removeIf((long node) -> SectionPos.blockToSectionCoord(BlockPos.getX(node)) == pos.x() && SectionPos.blockToSectionCoord(BlockPos.getZ(node)) == pos.z())) {
			this.mergedDirty = true;
		}

	}

	public void update() {
		if (this.dirtySources.isEmpty() && !this.mergedDirty) {
			return;
		}

		this.mergedDirty = false;
		LongIterator it = this.dirtySources.iterator();

		while(it.hasNext()) {
			long node = it.nextLong();
			BlockPos pos = BlockPos.of(node);
			BlockState state = this.level.getBlockState(pos);
			Field oldField = this.fields.remove(node);
			Long2ByteOpenHashMap oldLight = oldField != null ? oldField.light() : EMPTY;
			if (FrontEmitters.isFrontEmitter(state) && this.level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false) != null) {
				Field field = this.fill(pos, state);
				this.fields.put(node, field);
				this.setChangedDirty(oldLight, field.light());
			} else {
				this.setChangedDirty(oldLight, EMPTY);
			}
		}

		this.dirtySources.clear();
		Long2ByteOpenHashMap merged = new Long2ByteOpenHashMap();

		for(Field field : this.fields.values()) {
			for(Long2ByteMap.Entry entry : field.light().long2ByteEntrySet()) {
				byte value = entry.getByteValue();
				if (value > merged.get(entry.getLongKey())) {
					merged.put(entry.getLongKey(), value);
				}
			}
		}

		merged.trim();
		this.merged = merged;
	}

	private static boolean reaches(final Long2ByteOpenHashMap light, final BlockPos pos) {
		if (light.containsKey(pos.asLong())) {
			return true;
		}

		for(Direction direction : DIRECTIONS) {
			if (light.containsKey(BlockPos.offset(pos.asLong(), direction))) {
				return true;
			}
		}

		return false;
	}

	private Field fill(final BlockPos source, final BlockState sourceState) {
		Direction front = FrontEmitters.frontOf(sourceState);
		Direction back = front.getOpposite();
		Field field = new Field(new Long2ByteOpenHashMap(), new LongOpenHashSet());
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		BlockPos.MutableBlockPos fromPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos toPos = new BlockPos.MutableBlockPos();
		int emission = sourceState.getLightEmission();
		field.chunks().add(ChunkPos.pack(source));
		this.spread(field, queue, sourceState, emission, toPos.setWithOffset(source, front), front);

		while(!queue.isEmpty()) {
			long node = queue.dequeueLong();
			int fromLevel = field.light().get(node);
			fromPos.set(node);
			BlockState fromState = this.level.getBlockState(fromPos);

			for(Direction direction : DIRECTIONS) {
				if (direction != back) {
					this.spread(field, queue, fromState, fromLevel, toPos.setWithOffset(fromPos, direction), direction);
				}
			}
		}

		return field;
	}

	private void spread(final Field field, final LongArrayFIFOQueue queue, final BlockState fromState, final int fromLevel, final BlockPos toPos, final Direction direction) {
		if (direction.getAxis().isHorizontal()) {
			// Vertical steps stay in the same chunk column
			field.chunks().add(ChunkPos.pack(toPos));
		}

		BlockState toState = this.level.getBlockState(toPos);
		int newLevel = fromLevel - LightEngine.getLightDampeningInto(fromState, toState, direction, Math.max(1, toState.getLightDampening()));
		long toNode = toPos.asLong();
		if (newLevel > field.light().get(toNode)) {
			field.light().put(toNode, (byte)newLevel);
			if (newLevel > 1) {
				queue.enqueue(toNode);
			}
		}

	}

	// Rebuild only the sections around blocks whose light changed (smooth lighting reads one block out)
	private void setChangedDirty(final Long2ByteOpenHashMap oldLight, final Long2ByteOpenHashMap newLight) {
		Bounds bounds = new Bounds();
		addChanged(bounds, oldLight, newLight);
		addChanged(bounds, newLight, oldLight);
		if (!bounds.isEmpty()) {
			this.level.setSectionRangeDirty(SectionPos.blockToSectionCoord(bounds.minX - 1), SectionPos.blockToSectionCoord(bounds.minY - 1), SectionPos.blockToSectionCoord(bounds.minZ - 1), SectionPos.blockToSectionCoord(bounds.maxX + 1), SectionPos.blockToSectionCoord(bounds.maxY + 1), SectionPos.blockToSectionCoord(bounds.maxZ + 1));
		}
	}

	private static void addChanged(final Bounds bounds, final Long2ByteOpenHashMap light, final Long2ByteOpenHashMap other) {
		for(Long2ByteMap.Entry entry : light.long2ByteEntrySet()) {
			long node = entry.getLongKey();
			if (other.get(node) != entry.getByteValue()) {
				bounds.add(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
			}
		}
	}

	private static final class Bounds {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		void add(final int x, final int y, final int z) {
			this.minX = Math.min(this.minX, x);
			this.minY = Math.min(this.minY, y);
			this.minZ = Math.min(this.minZ, z);
			this.maxX = Math.max(this.maxX, x);
			this.maxY = Math.max(this.maxY, y);
			this.maxZ = Math.max(this.maxZ, z);
		}

		boolean isEmpty() {
			return this.minX > this.maxX;
		}
	}
}
