// Source: net/minecraft/client/renderer/entity/PaintingRenderer.java :: private void renderPainting(
// Vanilla 26.4-snapshot-5 (Loom genSources). Overwritten by PaintingRendererMixin.
	private void renderPainting(
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final RenderType renderType,
		final int[] lightCoordsMap,
		final int width,
		final int height,
		final TextureAtlasSprite front,
		final TextureAtlasSprite back
	) {
		submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
			float offsetX = -width / 2.0F;
			float offsetY = -height / 2.0F;
			float edgeHalfWidth = 0.03125F;
			float backU0 = back.getU0();
			float backU1 = back.getU1();
			float backV0 = back.getV0();
			float backV1 = back.getV1();
			float topBottomU0 = back.getU0();
			float topBottomU1 = back.getU1();
			float topBottomV0 = back.getV0();
			float topBottomV1 = back.getV(0.0625F);
			float leftRightU0 = back.getU0();
			float leftRightU1 = back.getU(0.0625F);
			float leftRightV0 = back.getV0();
			float leftRightV1 = back.getV1();
			double deltaU = 1.0 / width;
			double deltaV = 1.0 / height;

			for (int segmentX = 0; segmentX < width; segmentX++) {
				for (int segmentY = 0; segmentY < height; segmentY++) {
					float x0 = offsetX + (segmentX + 1);
					float x1 = offsetX + segmentX;
					float y0 = offsetY + (segmentY + 1);
					float y1 = offsetY + segmentY;
					int lightCoords = lightCoordsMap[segmentX + segmentY * width];
					float frontU0 = front.getU((float)(deltaU * (width - segmentX)));
					float frontU1 = front.getU((float)(deltaU * (width - (segmentX + 1))));
					float frontV0 = front.getV((float)(deltaV * (height - segmentY)));
					float frontV1 = front.getV((float)(deltaV * (height - (segmentY + 1))));
					vertex(pose, buffer, x0, y1, frontU1, frontV0, -0.03125F, 0, 0, -1, lightCoords);
					vertex(pose, buffer, x1, y1, frontU0, frontV0, -0.03125F, 0, 0, -1, lightCoords);
					vertex(pose, buffer, x1, y0, frontU0, frontV1, -0.03125F, 0, 0, -1, lightCoords);
					vertex(pose, buffer, x0, y0, frontU1, frontV1, -0.03125F, 0, 0, -1, lightCoords);
					vertex(pose, buffer, x0, y0, backU1, backV0, 0.03125F, 0, 0, 1, lightCoords);
					vertex(pose, buffer, x1, y0, backU0, backV0, 0.03125F, 0, 0, 1, lightCoords);
					vertex(pose, buffer, x1, y1, backU0, backV1, 0.03125F, 0, 0, 1, lightCoords);
					vertex(pose, buffer, x0, y1, backU1, backV1, 0.03125F, 0, 0, 1, lightCoords);
					if (segmentY == height - 1) {
						vertex(pose, buffer, x0, y0, topBottomU0, topBottomV0, -0.03125F, 0, 1, 0, lightCoords);
						vertex(pose, buffer, x1, y0, topBottomU1, topBottomV0, -0.03125F, 0, 1, 0, lightCoords);
						vertex(pose, buffer, x1, y0, topBottomU1, topBottomV1, 0.03125F, 0, 1, 0, lightCoords);
						vertex(pose, buffer, x0, y0, topBottomU0, topBottomV1, 0.03125F, 0, 1, 0, lightCoords);
					}

					if (segmentY == 0) {
						vertex(pose, buffer, x0, y1, topBottomU0, topBottomV0, 0.03125F, 0, -1, 0, lightCoords);
						vertex(pose, buffer, x1, y1, topBottomU1, topBottomV0, 0.03125F, 0, -1, 0, lightCoords);
						vertex(pose, buffer, x1, y1, topBottomU1, topBottomV1, -0.03125F, 0, -1, 0, lightCoords);
						vertex(pose, buffer, x0, y1, topBottomU0, topBottomV1, -0.03125F, 0, -1, 0, lightCoords);
					}

					if (segmentX == width - 1) {
						vertex(pose, buffer, x0, y0, leftRightU1, leftRightV0, 0.03125F, -1, 0, 0, lightCoords);
						vertex(pose, buffer, x0, y1, leftRightU1, leftRightV1, 0.03125F, -1, 0, 0, lightCoords);
						vertex(pose, buffer, x0, y1, leftRightU0, leftRightV1, -0.03125F, -1, 0, 0, lightCoords);
						vertex(pose, buffer, x0, y0, leftRightU0, leftRightV0, -0.03125F, -1, 0, 0, lightCoords);
					}

					if (segmentX == 0) {
						vertex(pose, buffer, x1, y0, leftRightU1, leftRightV0, -0.03125F, 1, 0, 0, lightCoords);
						vertex(pose, buffer, x1, y1, leftRightU1, leftRightV1, -0.03125F, 1, 0, 0, lightCoords);
						vertex(pose, buffer, x1, y1, leftRightU0, leftRightV1, 0.03125F, 1, 0, 0, lightCoords);
						vertex(pose, buffer, x1, y0, leftRightU0, leftRightV0, 0.03125F, 1, 0, 0, lightCoords);
					}
				}
			}
		});
	}
