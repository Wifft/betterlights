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
// Read from section compile threads: the merged field is an immutable snapshot swapped in on the main thread.
public final class FrontLightOverlay implements BlockLightOverlay {
	private static final int RANGE = 15;
	private static final Direction[] DIRECTIONS = Direction.values();
	private final ClientLevel level;
	private final Long2ObjectMap<Long2ByteOpenHashMap> fields = new Long2ObjectOpenHashMap<>();
	private final LongSet dirtySources = new LongOpenHashSet();
	private boolean mergedDirty;
	private volatile Long2ByteOpenHashMap merged = new Long2ByteOpenHashMap();

	public FrontLightOverlay(final ClientLevel level) {
		this.level = level;
	}

	@Override
	public int getLightValue(final long pos) {
		return this.merged.get(pos);
	}

	public void blockChanged(final BlockPos pos, final BlockState oldState, final BlockState newState) {
		long node = pos.asLong();
		if (FrontEmitters.isFrontEmitter(newState) || this.fields.containsKey(node)) {
			this.dirtySources.add(node);
		}

		if (LightEngine.hasDifferentLightProperties(oldState, newState)) {
			this.markSourcesNear(pos.getX() - RANGE, pos.getY() - RANGE, pos.getZ() - RANGE, pos.getX() + RANGE, pos.getY() + RANGE, pos.getZ() + RANGE);
		}

		this.update();
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

		// Fills of nearby furnaces ran through this chunk while it was missing (read as air)
		this.markSourcesNear(pos.getMinBlockX() - RANGE, Integer.MIN_VALUE, pos.getMinBlockZ() - RANGE, pos.getMaxBlockX() + RANGE, Integer.MAX_VALUE, pos.getMaxBlockZ() + RANGE);
		this.update();
	}

	public void chunkUnloaded(final ChunkPos pos) {
		// Called while the chunk is still in the cache, so drop its furnaces directly
		if (this.fields.keySet().removeIf((long node) -> SectionPos.blockToSectionCoord(BlockPos.getX(node)) == pos.x() && SectionPos.blockToSectionCoord(BlockPos.getZ(node)) == pos.z())) {
			this.mergedDirty = true;
			this.update();
		}

	}

	private void markSourcesNear(final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {
		LongIterator it = this.fields.keySet().iterator();

		while(it.hasNext()) {
			long node = it.nextLong();
			int x = BlockPos.getX(node);
			int y = BlockPos.getY(node);
			int z = BlockPos.getZ(node);
			if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
				this.dirtySources.add(node);
			}
		}

	}

	private void update() {
		if (!this.dirtySources.isEmpty() || this.mergedDirty) {
			this.mergedDirty = false;
			LongIterator it = this.dirtySources.iterator();

			while(it.hasNext()) {
				long node = it.nextLong();
				BlockPos pos = BlockPos.of(node);
				BlockState state = this.level.getBlockState(pos);
				Long2ByteOpenHashMap oldField = this.fields.remove(node);
				if (FrontEmitters.isFrontEmitter(state) && this.level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false) != null) {
					Long2ByteOpenHashMap field = this.fill(pos, state);
					this.fields.put(node, field);
					if (!field.equals(oldField)) {
						this.setDirty(pos);
					}
				} else if (oldField != null) {
					this.setDirty(pos);
				}
			}

			this.dirtySources.clear();
			Long2ByteOpenHashMap merged = new Long2ByteOpenHashMap();

			for(Long2ByteOpenHashMap field : this.fields.values()) {
				for(Long2ByteMap.Entry entry : field.long2ByteEntrySet()) {
					byte value = entry.getByteValue();
					if (value > merged.get(entry.getLongKey())) {
						merged.put(entry.getLongKey(), value);
					}
				}
			}

			merged.trim();
			this.merged = merged;
		}
	}

	private Long2ByteOpenHashMap fill(final BlockPos source, final BlockState sourceState) {
		Direction front = FrontEmitters.frontOf(sourceState);
		Direction back = front.getOpposite();
		Long2ByteOpenHashMap field = new Long2ByteOpenHashMap();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		BlockPos.MutableBlockPos fromPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos toPos = new BlockPos.MutableBlockPos();
		int emission = sourceState.getLightEmission();
		this.spread(field, queue, sourceState, emission, toPos.setWithOffset(source, front), front);

		while(!queue.isEmpty()) {
			long node = queue.dequeueLong();
			int fromLevel = field.get(node);
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

	private void spread(final Long2ByteOpenHashMap field, final LongArrayFIFOQueue queue, final BlockState fromState, final int fromLevel, final BlockPos toPos, final Direction direction) {
		BlockState toState = this.level.getBlockState(toPos);
		int newLevel = fromLevel - LightEngine.getLightDampeningInto(fromState, toState, direction, Math.max(1, toState.getLightDampening()));
		long toNode = toPos.asLong();
		if (newLevel > field.get(toNode)) {
			field.put(toNode, (byte)newLevel);
			if (newLevel > 1) {
				queue.enqueue(toNode);
			}
		}

	}

	private void setDirty(final BlockPos pos) {
		this.level.setSectionRangeDirty(SectionPos.blockToSectionCoord(pos.getX() - RANGE), SectionPos.blockToSectionCoord(pos.getY() - RANGE), SectionPos.blockToSectionCoord(pos.getZ() - RANGE), SectionPos.blockToSectionCoord(pos.getX() + RANGE), SectionPos.blockToSectionCoord(pos.getY() + RANGE), SectionPos.blockToSectionCoord(pos.getZ() + RANGE));
	}
}
