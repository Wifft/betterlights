package dev.wifft.betterlight.light;

import com.mojang.blaze3d.vertex.VertexConsumer;

// Unified lighting (MC-231102): replaces the per-object light of every vertex with the LightField value at its position
public class LightFieldVertexConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final LightField field;
	private float lastX;
	private float lastY;
	private float lastZ;

	public LightFieldVertexConsumer(final VertexConsumer delegate, final LightField field) {
		this.delegate = delegate;
		this.field = field;
	}

	@Override
	public void addVertex(final float x, final float y, final float z, final int color, final float u, final float v, final int overlayCoords, final int lightCoords, final float nx, final float ny, final float nz) {
		this.delegate.addVertex(x, y, z, color, u, v, overlayCoords, this.field.sample(x, y, z), nx, ny, nz);
	}

	@Override
	public VertexConsumer addVertex(final float x, final float y, final float z) {
		this.lastX = x;
		this.lastY = y;
		this.lastZ = z;
		this.delegate.addVertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setColor(final int r, final int g, final int b, final int a) {
		this.delegate.setColor(r, g, b, a);
		return this;
	}

	@Override
	public VertexConsumer setColor(final int color) {
		this.delegate.setColor(color);
		return this;
	}

	@Override
	public VertexConsumer setUv(final float u, final float v) {
		this.delegate.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(final int u, final int v) {
		this.delegate.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv2(final int u, final int v) {
		int light = this.field.sample(this.lastX, this.lastY, this.lastZ);
		this.delegate.setUv2(light & 0xFFFF, light >> 16 & 0xFFFF);
		return this;
	}

	@Override
	public VertexConsumer setUv3(final float u, final float v) {
		this.delegate.setUv3(u, v);
		return this;
	}

	@Override
	public VertexConsumer setNormal(final float x, final float y, final float z) {
		this.delegate.setNormal(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setLineWidth(final float width) {
		this.delegate.setLineWidth(width);
		return this;
	}
}
