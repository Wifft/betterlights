package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.gui.BetterlightOptionsScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "Betterlight..." button at the end of Video Settings. */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
	private VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void betterlight$addButton(CallbackInfo ci) {
		this.list.addHeader(Component.translatable("betterlight.options.header"));
		this.list.addBig(Button.builder(
			Component.translatable("betterlight.options.button"),
			button -> this.minecraft.gui.setScreen(new BetterlightOptionsScreen(this, this.options))
		).build());
	}
}
