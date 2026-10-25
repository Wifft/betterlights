package dev.wifft.betterlight.gui;

import com.mojang.serialization.Codec;
import dev.wifft.betterlight.config.BetterlightConfig;
import dev.wifft.betterlight.config.BetterlightConfig.LightModel;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-widget options screen. Values go straight into {@link BetterlightConfig};
 * the file is written and chunks are rebuilt (if a meshing option changed) when the screen closes.
 */
public class BetterlightOptionsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("betterlight.options.title");
	private static final Codec<LightModel> LIGHT_MODEL_CODEC = Codec.STRING.xmap(
		key -> LightModel.parse(key, LightModel.SPLIT), model -> model.key
	);

	private final boolean oldSmoothFluids = BetterlightConfig.smoothFluids;
	private final boolean oldSmoothNoAoModels = BetterlightConfig.smoothNoAoModels;
	private final boolean oldSmoothEmitters = BetterlightConfig.smoothEmitters;

	public BetterlightOptionsScreen(final Screen lastScreen, final Options options) {
		super(lastScreen, options, TITLE);
	}

	@Override
	protected void addOptions() {
		this.list.addBig(new OptionInstance<>(
			"betterlight.options.lightModel",
			model -> Tooltip.create(
				Component.translatable("betterlight.options.lightModel." + model.key + ".tooltip")
			),
			(caption, model) -> Options.genericValueLabel(caption, Component.translatable("betterlight.options.lightModel." + model.key)),
			new OptionInstance.Enum<>(Arrays.asList(LightModel.values()), LIGHT_MODEL_CODEC),
			BetterlightConfig.lightModel,
			model -> BetterlightConfig.lightModel = model
		));
		this.list.addSmall(
			bool("smoothFluids", BetterlightConfig.smoothFluids, v -> BetterlightConfig.smoothFluids = v),
			bool("smoothNoAoModels", BetterlightConfig.smoothNoAoModels, v -> BetterlightConfig.smoothNoAoModels = v),
			bool("smoothEmitters", BetterlightConfig.smoothEmitters, v -> BetterlightConfig.smoothEmitters = v),
			bool("blockEntityLightField", BetterlightConfig.blockEntityLightField, v -> BetterlightConfig.blockEntityLightField = v),
			bool("smoothEntities", BetterlightConfig.smoothEntities, v -> BetterlightConfig.smoothEntities = v),
			bool("smoothPaintingsFrames", BetterlightConfig.smoothPaintingsFrames, v -> BetterlightConfig.smoothPaintingsFrames = v),
			bool("frontEmitters", BetterlightConfig.frontEmitters, v -> BetterlightConfig.frontEmitters = v)
		);
	}

	private static OptionInstance<Boolean> bool(final String key, final boolean value, final Consumer<Boolean> setter) {
		return OptionInstance.createBoolean(
			"betterlight.options." + key,
			OptionInstance.cachedConstantTooltip(Component.translatable("betterlight.options." + key + ".tooltip")),
			value,
			setter::accept
		);
	}

	@Override
	public void removed() {
		BetterlightConfig.save();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			// The light model lives in the Lighting UBO, which vanilla only rewrites on level change
			minecraft.gameRenderer.lighting().updateLevel(minecraft.level.dimensionType().cardinalLightType());
		}
		if (BetterlightConfig.smoothFluids != this.oldSmoothFluids
			|| BetterlightConfig.smoothNoAoModels != this.oldSmoothNoAoModels
			|| BetterlightConfig.smoothEmitters != this.oldSmoothEmitters) {
			LevelExtractor levelExtractor = minecraft.levelExtractor;
			if (levelExtractor != null) {
				levelExtractor.allChanged();
			}
		}
		super.removed();
	}
}
