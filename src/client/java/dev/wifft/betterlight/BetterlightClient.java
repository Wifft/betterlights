package dev.wifft.betterlight;

import dev.wifft.betterlight.duck.DirectionalLightHolder;
import dev.wifft.betterlight.light.EmitterTags;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class BetterlightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Tags synced from the server (joining, /reload): the emitter tags may have changed
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
			if (client) {
				EmitterTags.reloadFallback();
				// Singleplayer and LAN hosts share the option with their own server
				EmitterTags.setFrontFollowsServer(!Minecraft.getInstance().isLocalServer());
				rescanEmitters();
			}
		});
	}

	// Before the level exists there's nothing to rescan: its chunks load with the current tags
	public static void rescanEmitters() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			((DirectionalLightHolder) level).betterlight$rescanEmitters();
		}
	}
}
