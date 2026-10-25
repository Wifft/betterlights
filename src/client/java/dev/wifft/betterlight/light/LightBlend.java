package dev.wifft.betterlight.light;

import net.minecraft.util.LightCoordsUtil;

// Trilinear blend of packed light between 8 cell centers. Cells without any light (usually solid blocks) are skipped,
// like LightCoordsUtil.smoothBlend; returns fallback when every cell with weight is unlit. Allocation-free: runs per vertex.
public final class LightBlend {
	private LightBlend() {
	}

	// cXYZ: the corner at +X when X = 1, and so on; fx/fy/fz in [0, 1] from the 000 corner
	public static int trilinear(
		final int c000, final int c100, final int c010, final int c110, final int c001, final int c101, final int c011, final int c111,
		final float fx, final float fy, final float fz, final int fallback
	) {
		float gx = 1.0F - fx;
		float gy = 1.0F - fy;
		float gz = 1.0F - fz;
		float w000 = weight(c000, gx * gy * gz);
		float w100 = weight(c100, fx * gy * gz);
		float w010 = weight(c010, gx * fy * gz);
		float w110 = weight(c110, fx * fy * gz);
		float w001 = weight(c001, gx * gy * fz);
		float w101 = weight(c101, fx * gy * fz);
		float w011 = weight(c011, gx * fy * fz);
		float w111 = weight(c111, fx * fy * fz);
		float weightSum = w000 + w100 + w010 + w110 + w001 + w101 + w011 + w111;
		if (weightSum <= 0.0F) {
			return fallback;
		}

		float blockSum = LightCoordsUtil.block(c000) * w000 + LightCoordsUtil.block(c100) * w100 + LightCoordsUtil.block(c010) * w010 + LightCoordsUtil.block(c110) * w110
			+ LightCoordsUtil.block(c001) * w001 + LightCoordsUtil.block(c101) * w101 + LightCoordsUtil.block(c011) * w011 + LightCoordsUtil.block(c111) * w111;
		float skySum = LightCoordsUtil.sky(c000) * w000 + LightCoordsUtil.sky(c100) * w100 + LightCoordsUtil.sky(c010) * w010 + LightCoordsUtil.sky(c110) * w110
			+ LightCoordsUtil.sky(c001) * w001 + LightCoordsUtil.sky(c101) * w101 + LightCoordsUtil.sky(c011) * w011 + LightCoordsUtil.sky(c111) * w111;
		int block = Math.min(Math.round(blockSum / weightSum * 16.0F), 240);
		int sky = Math.min(Math.round(skySum / weightSum * 16.0F), 240);
		return LightCoordsUtil.smoothPack(block, sky);
	}

	// Unlit cells don't count
	private static float weight(final int light, final float weight) {
		return light != 0 ? weight : 0.0F;
	}
}
