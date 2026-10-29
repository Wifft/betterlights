package dev.wifft.betterlight.duck;

/** Added to ClientLevel: its client ticks, which keep counting while the game time is frozen (/tick freeze). */
public interface ClientTickCounter {
	long betterlight$clientTicks();
}
