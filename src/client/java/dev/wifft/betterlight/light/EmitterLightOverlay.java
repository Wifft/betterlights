package dev.wifft.betterlight.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;
import org.jspecify.annotations.Nullable;

/**
 * Client-side block light computed per emitter (FrontLightOverlay, DirectionalLightOverlay). Block and chunk events
 * only mark emitters dirty; update() (once per frame, from ClientLevel.update) refills them on a time budget, then
 * merges the sections they touched into the snapshot the section compile threads read. Fields are indexed by the
 * section of their source: a field never leaves the 3×3×3 sections around it (RADIUS < 16).
 */
abstract class EmitterLightOverlay implements BlockLightOverlay {
	private static final int MERGE_BATCH = 16;
	private static final long BUDGET_NANOS = 3_000_000L;
	protected final ClientLevel level;
	protected final SectionLightSnapshot snapshot;
	protected final EmitterBox box;
	protected final Long2ObjectMap<EmitterField> fields = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<LongSet> sourcesBySection = new Long2ObjectOpenHashMap<>();
	protected final LongLinkedOpenHashSet dirtySources = new LongLinkedOpenHashSet();
	private final LongLinkedOpenHashSet dirtySections = new LongLinkedOpenHashSet();
	// For rescan
	private final LongSet loadedChunks = new LongOpenHashSet();

	protected EmitterLightOverlay(final ClientLevel level) {
		this.level = level;
		this.snapshot = new SectionLightSnapshot(level);
		this.box = new EmitterBox(level);
	}

	protected abstract boolean isEmitter(BlockState state);

	// The field of the emitter at pos, or null to leave it without one for now
	protected abstract @Nullable EmitterField fill(BlockPos pos, BlockState state);

	// Rebuilds the snapshot values of these sections from the fields (snapshot.put)
	protected abstract void mergeSections(LongSet sections);

	// Whether a light property change at pos alters this field
	protected boolean isAffected(final EmitterField field, final BlockPos pos, final BlockState oldState, final BlockState newState) {
		return field.reaches(pos);
	}

	protected void lightPropertiesChanged(final BlockPos pos) {
	}

	// Drops an emitter that is waiting for something other than a fill; true if there was one
	protected boolean forgetPending(final long node) {
		return false;
	}

	protected void chunkForgotten(final long chunkKey) {
	}

	public void blockChanged(final BlockPos pos, final BlockState oldState, final BlockState newState) {
		long node = pos.asLong();
		if (this.isEmitter(newState) || this.fields.containsKey(node) || this.forgetPending(node)) {
			this.dirtySources.add(node);
		}

		if (LightEngine.hasDifferentLightProperties(oldState, newState)) {
			this.forEachSourceAround(SectionPos.blockToSection(node), source -> {
				if (this.isAffected(this.fields.get(source), pos, oldState, newState)) {
					this.dirtySources.add(source);
				}
			});
			this.lightPropertiesChanged(pos);
		}
	}

	public void chunkLoaded(final ChunkPos pos) {
		long chunkKey = pos.pack();
		this.loadedChunks.add(chunkKey);
		this.findEmitters(pos.x(), pos.z());
		this.forEachSourceInColumnsAround(pos, source -> {
			if (this.fields.get(source).isMissing(chunkKey)) {
				this.dirtySources.add(source);
			}
		});
	}

	// Called while the chunk is still in the cache
	public void chunkUnloaded(final ChunkPos pos) {
		for (int sy = this.level.getMinSectionY(); sy <= this.level.getMaxSectionY(); sy++) {
			LongSet sources = this.sourcesBySection.get(SectionPos.asLong(pos.x(), sy, pos.z()));
			if (sources != null) {
				for (long source : sources.toLongArray()) {
					this.markFieldSections(this.removeField(source));
				}
			}
		}

		long chunkKey = pos.pack();
		this.loadedChunks.remove(chunkKey);
		this.chunkForgotten(chunkKey);
		// Fills around it are redone if the chunk comes back
		this.forEachSourceInColumnsAround(pos, source -> this.fields.get(source).addMissingChunk(chunkKey));
	}

	// What counts as an emitter changed (tags reloaded, option toggled): look for emitters in every loaded chunk again,
	// and refill the current ones, which drops those that no longer count
	public void rescan() {
		LongIterator it = this.loadedChunks.iterator();
		while (it.hasNext()) {
			long chunkKey = it.nextLong();
			this.findEmitters(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
		}
		this.dirtySources.addAll(this.fields.keySet());
	}

	private void findEmitters(final int chunkX, final int chunkZ) {
		LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
		if (chunk != null) {
			chunk.findBlockLightSources((lightPos, state) -> {
				if (this.isEmitter(state)) {
					this.dirtySources.add(lightPos.asLong());
				}
			});
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
			EmitterField oldField = this.removeField(node);
			if (oldField != null) {
				this.markFieldSections(oldField);
			}

			BlockPos pos = BlockPos.of(node);
			BlockState state = this.level.getBlockState(pos);
			if (!this.isEmitter(state) || this.level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false) == null) {
				continue;
			}
			EmitterField field = this.fill(pos, state);
			if (field != null) {
				this.fields.put(node, field);
				this.sourcesBySection.computeIfAbsent(SectionPos.blockToSection(node), k -> new LongOpenHashSet()).add(node);
				this.markFieldSections(field);
			}
		}

		// Merge once the fills are done, so sections aren't rebuilt for every step of a big batch
		while (this.dirtySources.isEmpty() && !this.dirtySections.isEmpty() && System.nanoTime() < deadline) {
			LongSet sections = new LongOpenHashSet();
			while (sections.size() < MERGE_BATCH && !this.dirtySections.isEmpty()) {
				sections.add(this.dirtySections.removeFirstLong());
			}
			this.mergeSections(sections);
		}
		this.snapshot.publish();
	}

	protected void markMergedDirty(final long sectionKey) {
		if (this.snapshot.contains(sectionKey)) {
			this.dirtySections.add(sectionKey);
		}
	}

	// Sources whose field can reach the section: those in the 3×3×3 sections around it
	protected void forEachSourceAround(final long sectionKey, final LongConsumer consumer) {
		for (int sx = SectionPos.x(sectionKey) - 1; sx <= SectionPos.x(sectionKey) + 1; sx++) {
			for (int sy = SectionPos.y(sectionKey) - 1; sy <= SectionPos.y(sectionKey) + 1; sy++) {
				for (int sz = SectionPos.z(sectionKey) - 1; sz <= SectionPos.z(sectionKey) + 1; sz++) {
					LongSet sources = this.sourcesBySection.get(SectionPos.asLong(sx, sy, sz));
					if (sources != null) {
						sources.forEach(consumer);
					}
				}
			}
		}
	}

	private void forEachSourceInColumnsAround(final ChunkPos pos, final LongConsumer consumer) {
		for (int cx = pos.x() - 1; cx <= pos.x() + 1; cx++) {
			for (int cz = pos.z() - 1; cz <= pos.z() + 1; cz++) {
				for (int sy = this.level.getMinSectionY(); sy <= this.level.getMaxSectionY(); sy++) {
					LongSet sources = this.sourcesBySection.get(SectionPos.asLong(cx, sy, cz));
					if (sources != null) {
						sources.forEach(consumer);
					}
				}
			}
		}
	}

	private @Nullable EmitterField removeField(final long source) {
		EmitterField field = this.fields.remove(source);
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

	private void markFieldSections(final EmitterField field) {
		for (int cell : field.cells) {
			this.dirtySections.add(field.cellSection(cell));
		}
	}
}
