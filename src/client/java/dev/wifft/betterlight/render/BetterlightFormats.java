package dev.wifft.betterlight.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.renderpearl.api.GpuFormat;

public final class BetterlightFormats {
	/** DefaultVertexFormat.BLOCK plus a Normal, for directional shading in the terrain and block shaders (same formats as DefaultVertexFormat). */
	public static final VertexFormat BLOCK_WITH_NORMAL = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("UV2", GpuFormat.RG16_SINT)
		.addAttribute("Normal", GpuFormat.RGBA8_SNORM)
		.build();

	private BetterlightFormats() {
	}
}
