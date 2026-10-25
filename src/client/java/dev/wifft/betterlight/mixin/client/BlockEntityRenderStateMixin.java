package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.LightFieldHolder;
import dev.wifft.betterlight.light.LightField;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntityRenderState.class)
public abstract class BlockEntityRenderStateMixin implements LightFieldHolder {
	@Unique
	private @Nullable LightField betterlight$lightField;

	@Override
	public @Nullable LightField betterlight$getLightField() {
		return this.betterlight$lightField;
	}

	@Override
	public void betterlight$setLightField(@Nullable LightField field) {
		this.betterlight$lightField = field;
	}
}
