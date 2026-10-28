package dev.wifft.betterlight.mixin;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageInvoker {
	@Invoker("getLightValue")
	int betterlight$invokeGetLightValue(long blockNode);
}
