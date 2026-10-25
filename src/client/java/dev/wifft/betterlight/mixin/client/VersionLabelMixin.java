package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryVersion;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** " Betterlight by Wifft" after the displayed game version: window title, title screen and F3 (one call per target). */
@Mixin({Minecraft.class, TitleScreen.class, DebugEntryVersion.class})
public abstract class VersionLabelMixin {
	@Unique
	private static final String BETTERLIGHT_LABEL = " Betterlight by Wifft";

	@ModifyExpressionValue(
		method = {"createTitle", "extractRenderState", "display"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/WorldVersion;name()Ljava/lang/String;")
	)
	private String betterlight$appendLabel(String name) {
		return name + BETTERLIGHT_LABEL;
	}
}
