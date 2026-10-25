package dev.wifft.betterlight.light;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// Unified lighting (MC-231102): light levels of the SIZE^3 cells around a block entity, so its model can be lit per vertex
// instead of with one value for the whole object. Cells go from blockPos - 1 to blockPos + 2; the origin is camera-relative,
// the same space as the vertex positions written by ModelPart.
public record LightField(int[] cells, float originX, float originY, float originZ, int fallback) {
	public static final int SIZE = 4;
	// Set by BlockEntityRenderDispatcher around renderer.submit(...); submission happens on the render thread only
	private static @Nullable LightField current;

	public static @Nullable LightField current() {
		return current;
	}

	public static void setCurrent(final @Nullable LightField field) {
		current = field;
	}

	// Cells around pos, captured at most once per game tick for the object owning the cache
	public static int[] cachedCells(final Level level, final BlockPos pos, final LightCellCache cache) {
		long gameTime = level.getGameTime();
		long key = pos.asLong();
		int[] cells = cache.get(gameTime, key, 0);
		return cells != null ? cells : cache.put(gameTime, key, 0, captureCells(level, pos));
	}

	// Cells captured during extraction (world space), turned into a camera-relative field at submit time with of(...)
	public static LightField of(final int[] cells, final BlockPos pos, final Vec3 cameraPos, final int fallback) {
		return new LightField(cells, (float)((double)(pos.getX() - 1) - cameraPos.x), (float)((double)(pos.getY() - 1) - cameraPos.y), (float)((double)(pos.getZ() - 1) - cameraPos.z), fallback);
	}

	public static int[] captureCells(final BlockAndLightGetter level, final BlockPos pos) {
		int[] cells = new int[SIZE * SIZE * SIZE];
		BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

		for (int x = 0; x < SIZE; ++x) {
			for (int y = 0; y < SIZE; ++y) {
				for (int z = 0; z < SIZE; ++z) {
					samplePos.set(pos.getX() - 1 + x, pos.getY() - 1 + y, pos.getZ() - 1 + z);
					cells[(x * SIZE + y) * SIZE + z] = LightCoordsUtil.getLightCoords(level, samplePos);
				}
			}
		}

		return cells;
	}

	// Trilinear blend between cell centers (LightBlend)
	public int sample(final float x, final float y, final float z) {
		float gx = Math.clamp(x - this.originX - 0.5F, 0.0F, (float)(SIZE - 1));
		float gy = Math.clamp(y - this.originY - 0.5F, 0.0F, (float)(SIZE - 1));
		float gz = Math.clamp(z - this.originZ - 0.5F, 0.0F, (float)(SIZE - 1));
		int baseX = Math.min(Mth.floor(gx), SIZE - 2);
		int baseY = Math.min(Mth.floor(gy), SIZE - 2);
		int baseZ = Math.min(Mth.floor(gz), SIZE - 2);
		int[] cells = this.cells;
		int i = (baseX * SIZE + baseY) * SIZE + baseZ;
		final int dx = SIZE * SIZE;
		final int dy = SIZE;
		return LightBlend.trilinear(
			cells[i], cells[i + dx], cells[i + dy], cells[i + dx + dy], cells[i + 1], cells[i + dx + 1], cells[i + dy + 1], cells[i + dx + dy + 1],
			gx - (float)baseX, gy - (float)baseY, gz - (float)baseZ, this.fallback
		);
	}
}
