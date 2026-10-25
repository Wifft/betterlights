package dev.wifft.betterlight.duck;

import dev.wifft.betterlight.light.LightField;
import org.jspecify.annotations.Nullable;

/** Added to BlockEntityRenderState and to the Submit records of the model, block model, item and custom feature renderers. */
public interface LightFieldHolder {
	@Nullable LightField betterlight$getLightField();

	void betterlight$setLightField(@Nullable LightField field);

	static @Nullable LightField of(Object holder) {
		return ((LightFieldHolder) holder).betterlight$getLightField();
	}
}
