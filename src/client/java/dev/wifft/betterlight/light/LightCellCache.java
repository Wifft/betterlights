package dev.wifft.betterlight.light;

import org.jspecify.annotations.Nullable;

// Light cells captured for one object, reused for the rest of the game tick: extraction runs every frame, but light
// changes at most once per tick in practice. A recapture stores a new array, so render states holding the old one never see it change.
public final class LightCellCache {
	private int @Nullable [] cells;
	private long gameTime;
	private long key;
	private int extra;

	// Cells stored for this tick and key (origin block, plus whatever else shapes the capture), or null
	public int @Nullable [] get(final long gameTime, final long key, final int extra) {
		return this.cells != null && this.gameTime == gameTime && this.key == key && this.extra == extra ? this.cells : null;
	}

	public int[] put(final long gameTime, final long key, final int extra, final int[] cells) {
		this.cells = cells;
		this.gameTime = gameTime;
		this.key = key;
		this.extra = extra;
		return cells;
	}
}
