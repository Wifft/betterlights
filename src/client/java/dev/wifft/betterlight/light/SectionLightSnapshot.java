package dev.wifft.betterlight.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;

/**
 * One byte of light per block (0 = none), by section, read from the section compile threads. The main thread edits a
 * copy (put), and publish() swaps it in as an immutable snapshot; sections around the cells that changed are rebuilt
 * (smooth lighting reads one block out).
 */
final class SectionLightSnapshot {
	private final ClientLevel level;
	private volatile Long2ObjectOpenHashMap<byte[]> published = new Long2ObjectOpenHashMap<>();
	private @Nullable Long2ObjectOpenHashMap<byte[]> editing;

	SectionLightSnapshot(final ClientLevel level) {
		this.level = level;
	}

	static int index(final int x, final int y, final int z) {
		return (y & 15) << 8 | (z & 15) << 4 | x & 15;
	}

	// Published value at pos; any thread
	int get(final long pos) {
		Long2ObjectOpenHashMap<byte[]> sections = this.published;
		if (sections.isEmpty()) {
			return 0;
		}
		byte[] section = sections.get(SectionPos.blockToSection(pos));
		return section == null ? 0 : section[index(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos))] & 0xFF;
	}

	// Values of a section as edited so far (main thread); not to be modified
	byte @Nullable [] section(final long sectionKey) {
		return (this.editing != null ? this.editing : this.published).get(sectionKey);
	}

	boolean contains(final long sectionKey) {
		return this.section(sectionKey) != null;
	}

	// Replaces a section's values (null removes it); values must not be modified afterwards
	void put(final long sectionKey, final byte @Nullable [] values) {
		if (values == null && !this.contains(sectionKey)) {
			return;
		}
		if (this.editing == null) {
			this.editing = new Long2ObjectOpenHashMap<>(this.published);
		}
		byte[] old = values != null ? this.editing.put(sectionKey, values) : this.editing.remove(sectionKey);
		this.setChangedDirty(sectionKey, old, values);
	}

	void publish() {
		if (this.editing != null) {
			this.published = this.editing;
			this.editing = null;
		}
	}

	private void setChangedDirty(final long sectionKey, final byte @Nullable [] oldValues, final byte @Nullable [] newValues) {
		int minX = 16;
		int minY = 16;
		int minZ = 16;
		int maxX = -1;
		int maxY = -1;
		int maxZ = -1;
		for (int i = 0; i < 4096; i++) {
			byte oldValue = oldValues != null ? oldValues[i] : 0;
			byte newValue = newValues != null ? newValues[i] : 0;
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
			this.level.setSectionRangeDirty(
				SectionPos.blockToSectionCoord(baseX + minX - 1), SectionPos.blockToSectionCoord(baseY + minY - 1), SectionPos.blockToSectionCoord(baseZ + minZ - 1),
				SectionPos.blockToSectionCoord(baseX + maxX + 1), SectionPos.blockToSectionCoord(baseY + maxY + 1), SectionPos.blockToSectionCoord(baseZ + maxZ + 1)
			);
		}
	}
}
