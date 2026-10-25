package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.LightFieldHolder;
import dev.wifft.betterlight.light.LightField;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures LightField.current() when the submit is created. Outline and crumbling submits keep none. */
@Mixin(ModelFeatureRenderer.Submit.class)
public abstract class ModelSubmitMixin implements LightFieldHolder {
	@Unique
	private @Nullable LightField betterlight$lightField;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void betterlight$captureLightField(CallbackInfo ci) {
		ModelFeatureRenderer.Submit<?> self = (ModelFeatureRenderer.Submit<?>) (Object) this;
		if (!self.renderType().isOutline() && self.sheetedDecalPose() == null) {
			this.betterlight$lightField = LightField.current();
		}
	}

	@Override
	public @Nullable LightField betterlight$getLightField() {
		return this.betterlight$lightField;
	}

	@Override
	public void betterlight$setLightField(@Nullable LightField field) {
		this.betterlight$lightField = field;
	}
}
