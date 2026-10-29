package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.BetterlightClient;
import dev.wifft.betterlight.config.BetterlightConfig;
import java.util.function.Consumer;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Betterlight options at the end of Video Settings. Values go straight into {@link BetterlightConfig};
 * the file is written and chunks are rebuilt (if a meshing option changed) when the screen closes.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
	@Unique
	private boolean betterlight$oldSmoothFluids;
	@Unique
	private boolean betterlight$oldSmoothNoAoModels;
	@Unique
	private boolean betterlight$oldSmoothEmitters;
	@Unique
	private boolean betterlight$oldExtendedPistonAo;
	@Unique
	private boolean betterlight$oldFrontEmitters;
	@Unique
	private boolean betterlight$oldDirectionalTorches;

	private VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	// Not in addOptions: rebuildWidgets (monitor change) runs it again
	@Inject(method = "<init>", at = @At("TAIL"))
	private void betterlight$rememberMeshingOptions(CallbackInfo ci) {
		this.betterlight$oldSmoothFluids = BetterlightConfig.smoothFluids;
		this.betterlight$oldSmoothNoAoModels = BetterlightConfig.smoothNoAoModels;
		this.betterlight$oldSmoothEmitters = BetterlightConfig.smoothEmitters;
		this.betterlight$oldExtendedPistonAo = BetterlightConfig.extendedPistonAo;
		this.betterlight$oldFrontEmitters = BetterlightConfig.frontEmitters;
		this.betterlight$oldDirectionalTorches = BetterlightConfig.directionalTorches;
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void betterlight$addOptions(CallbackInfo ci) {
		this.list.addHeader(Component.translatable("betterlight.options.header"));
		this.list.addSmall(
			betterlight$bool("smoothFluids", BetterlightConfig.smoothFluids, v -> BetterlightConfig.smoothFluids = v),
			betterlight$bool("smoothNoAoModels", BetterlightConfig.smoothNoAoModels, v -> BetterlightConfig.smoothNoAoModels = v),
			betterlight$bool("smoothEmitters", BetterlightConfig.smoothEmitters, v -> BetterlightConfig.smoothEmitters = v),
			betterlight$bool("blockEntityLightField", BetterlightConfig.blockEntityLightField, v -> BetterlightConfig.blockEntityLightField = v),
			betterlight$bool("smoothEntities", BetterlightConfig.smoothEntities, v -> BetterlightConfig.smoothEntities = v),
			betterlight$bool("smoothPaintingsFrames", BetterlightConfig.smoothPaintingsFrames, v -> BetterlightConfig.smoothPaintingsFrames = v),
			betterlight$bool("movingBlockAo", BetterlightConfig.movingBlockAo, v -> BetterlightConfig.movingBlockAo = v),
			betterlight$bool("movingBlockShadows", BetterlightConfig.movingBlockShadows, v -> BetterlightConfig.movingBlockShadows = v),
			betterlight$bool("extendedPistonAo", BetterlightConfig.extendedPistonAo, v -> BetterlightConfig.extendedPistonAo = v),
			betterlight$bool("frontEmitters", BetterlightConfig.frontEmitters, v -> BetterlightConfig.frontEmitters = v),
			betterlight$bool("directionalTorches", BetterlightConfig.directionalTorches, v -> BetterlightConfig.directionalTorches = v)
		);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void betterlight$saveOptions(CallbackInfo ci) {
		BetterlightConfig.save();
		if (BetterlightConfig.smoothFluids != this.betterlight$oldSmoothFluids
			|| BetterlightConfig.smoothNoAoModels != this.betterlight$oldSmoothNoAoModels
			|| BetterlightConfig.smoothEmitters != this.betterlight$oldSmoothEmitters
			|| BetterlightConfig.extendedPistonAo != this.betterlight$oldExtendedPistonAo) {
			LevelExtractor levelExtractor = this.minecraft.levelExtractor;
			if (levelExtractor != null) {
				levelExtractor.allChanged();
			}
		}
		if (BetterlightConfig.frontEmitters != this.betterlight$oldFrontEmitters
			|| BetterlightConfig.directionalTorches != this.betterlight$oldDirectionalTorches) {
			BetterlightClient.rescanEmitters();
		}
	}

	@Unique
	private static OptionInstance<Boolean> betterlight$bool(final String key, final boolean value, final Consumer<Boolean> setter) {
		return OptionInstance.createBoolean(
			"betterlight.options." + key,
			OptionInstance.cachedConstantTooltip(Component.translatable("betterlight.options." + key + ".tooltip")),
			value,
			setter::accept
		);
	}
}
