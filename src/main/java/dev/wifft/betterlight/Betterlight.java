package dev.wifft.betterlight;

import dev.wifft.betterlight.config.BetterlightConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Betterlight implements ModInitializer {
	public static final String MOD_ID = "betterlight";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BetterlightConfig.load();
	}
}
