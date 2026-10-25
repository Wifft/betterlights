package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.LightFieldHolder;
import dev.wifft.betterlight.light.LightField;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures LightField.current() when a submit is created; the matching *FeatureRendererMixin applies it.
 * Outline submits, and the crumbling (sheeted decal) submits of models and block models, keep none.
 */
@Mixin({ModelFeatureRenderer.Submit.class, BlockModelFeatureRenderer.Submit.class, CustomFeatureRenderer.Submit.class, ItemFeatureRenderer.Submit.class})
public abstract class SubmitLightFieldMixin implements LightFieldHolder {
	@Unique
	private @Nullable LightField betterlight$lightField;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void betterlight$captureLightField(CallbackInfo ci) {
		if (betterlight$acceptsLightField(this)) {
			this.betterlight$lightField = LightField.current();
		}
	}

	@Unique
	private static boolean betterlight$acceptsLightField(Object submit) {
		return switch (submit) {
			case ModelFeatureRenderer.Submit<?> model -> !model.renderType().isOutline() && model.sheetedDecalPose() == null;
			case BlockModelFeatureRenderer.Submit blockModel -> !blockModel.renderType().isOutline() && blockModel.sheetedDecalPose() == null;
			case CustomFeatureRenderer.Submit custom -> !custom.renderType().isOutline();
			case ItemFeatureRenderer.Submit item -> item.outlineColor() == 0;
			default -> false;
		};
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
