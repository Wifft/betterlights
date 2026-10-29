package dev.wifft.betterlight.light;

import static dev.wifft.betterlight.light.EmitterField.CENTER;
import static dev.wifft.betterlight.light.EmitterField.VOLUME;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

// Client-only block light of front emitters (lit furnaces, jack o'lanterns), which the light engine ignores
// (FrontEmitters.isFrontEmitter). Each one gets its own flood fill that never steps backwards, so it only lights the
// half-space in front of it, shaded by line of sight like DirectionalLightOverlay (EmitterBox.shade); the snapshot
// holds the max of the shaded fills, merged (max) into the engine value.
public final class FrontLightOverlay extends EmitterLightOverlay {
	private static final Direction[] DIRECTIONS = Direction.values();
	// Scratch for fills: light and its shadowed version (EmitterBox.shade) in local coordinates, and the cells they touched
	private final byte[] light = new byte[VOLUME];
	private final byte[] shadowed = new byte[VOLUME];
	private final IntArrayList touched = new IntArrayList();
	private final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();

	public FrontLightOverlay(final ClientLevel level) {
		super(level);
	}

	@Override
	public int getLightValue(final long pos, final int engineLevel) {
		return engineLevel < 15 ? Math.max(engineLevel, this.snapshot.get(pos)) : engineLevel;
	}

	@Override
	protected boolean isEmitter(final BlockState state) {
		return FrontEmitters.isFrontEmitter(state);
	}

	@Override
	protected EmitterField fill(final BlockPos source, final BlockState sourceState) {
		EmitterBox box = this.box;
		box.begin(source, sourceState);
		Direction front = FrontEmitters.frontOf(sourceState);
		Direction back = front.getOpposite();
		this.touched.clear();
		// The emitter's own block stays dark: the light starts in front of it
		this.spread(sourceState, sourceState.getLightEmission(), CENTER, front);

		while (!this.queue.isEmpty()) {
			int from = this.queue.dequeueInt();
			BlockState fromState = box.state(EmitterField.localX(from), EmitterField.localY(from), EmitterField.localZ(from));
			for (Direction direction : DIRECTIONS) {
				if (direction != back) {
					this.spread(fromState, this.light[from], from, direction);
				}
			}
		}

		box.shade(this.light, this.shadowed, this.touched, sourceState.getLightEmission());

		this.touched.sort(null);
		int[] cells = new int[this.touched.size()];
		for (int i = 0; i < cells.length; i++) {
			int cell = this.touched.getInt(i);
			cells[i] = cell << 8 | this.light[cell] << 4 | this.shadowed[cell];
			this.light[cell] = 0;
			this.shadowed[cell] = 0;
		}

		EmitterField field = new EmitterField(source, cells);
		box.addMissingChunks(field);
		return field;
	}

	private void spread(final BlockState fromState, final int fromLevel, final int from, final Direction direction) {
		int tx = EmitterField.localX(from) + direction.getStepX();
		int ty = EmitterField.localY(from) + direction.getStepY();
		int tz = EmitterField.localZ(from) + direction.getStepZ();
		if (!EmitterField.inLocalBox(tx, ty, tz)) {
			return;
		}
		BlockState toState = this.box.state(tx, ty, tz);
		int newLevel = fromLevel - LightEngine.getLightDampeningInto(fromState, toState, direction, Math.max(1, toState.getLightDampening()));
		int to = EmitterField.index(tx, ty, tz);
		if (newLevel > this.light[to]) {
			if (this.light[to] == 0) {
				this.touched.add(to);
			}
			this.light[to] = (byte) newLevel;
			if (newLevel > 1) {
				this.queue.enqueue(to);
			}
		}
	}

	@Override
	protected void mergeSections(final LongSet sections) {
		LongSet candidates = new LongOpenHashSet();
		LongIterator sectionIt = sections.iterator();
		while (sectionIt.hasNext()) {
			this.forEachSourceAround(sectionIt.nextLong(), candidates::add);
		}

		Long2ObjectMap<byte[]> merged = new Long2ObjectOpenHashMap<>();
		LongIterator candidateIt = candidates.iterator();
		while (candidateIt.hasNext()) {
			EmitterField field = this.fields.get(candidateIt.nextLong());
			for (int cell : field.cells) {
				long key = field.cellSection(cell);
				if (sections.contains(key)) {
					byte[] values = merged.computeIfAbsent(key, k -> new byte[4096]);
					int i = SectionLightSnapshot.index(field.cellX(cell), field.cellY(cell), field.cellZ(cell));
					values[i] = (byte) Math.max(values[i], cell & 15);
				}
			}
		}

		LongIterator it = sections.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			this.snapshot.put(key, merged.get(key));
		}
	}
}
