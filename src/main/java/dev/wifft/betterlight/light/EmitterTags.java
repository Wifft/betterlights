package dev.wifft.betterlight.light;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.wifft.betterlight.Betterlight;
import dev.wifft.betterlight.config.BetterlightConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks get Betterlight's emitter light, from two block tags datapacks can extend or replace:
 * {@code betterlight:shadowed_emitters} (DirectionalLightOverlay) and {@code betterlight:front_emitters} (FrontEmitters).
 * Unlit states (emission 0) never count.
 * Tags come from the server, and a server without the mod doesn't send ours: the client then falls back to the
 * shadowed emitters listed in the mod's own tag file (reloadFallback, on every tag sync). Front emitters need no
 * fallback, since without the mod on the server the engine lights them as usual.
 */
public final class EmitterTags {
	public static final TagKey<Block> SHADOWED = tag("shadowed_emitters");
	public static final TagKey<Block> FRONT = tag("front_emitters");
	private static volatile Set<Block> shadowedFallback = Collections.emptySet();
	private static volatile boolean frontFollowsServer;

	private EmitterTags() {
	}

	private static TagKey<Block> tag(final String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Betterlight.MOD_ID, path));
	}

	public static boolean isShadowed(final BlockState state) {
		return BetterlightConfig.directionalTorches && state.getLightEmission() > 0 && (state.is(SHADOWED) || shadowedFallback.contains(state.getBlock()));
	}

	// Lit by its front only: also needs the facing that tells which side is the front
	public static boolean isFront(final BlockState state) {
		return (BetterlightConfig.frontEmitters || frontFollowsServer)
			&& state.is(FRONT) && state.hasProperty(HorizontalDirectionalBlock.FACING) && state.getLightEmission() > 0;
	}

	// On a remote server with the mod (the only one that sends the front tag) the server's engine already leaves front
	// emitters dark, so the client lights them whatever its own option says; set by the client on every tag sync
	public static void setFrontFollowsServer(final boolean followsServer) {
		frontFollowsServer = followsServer;
	}

	// After tags are synced: the bundled list when the server didn't send the tag, nothing otherwise
	public static void reloadFallback() {
		shadowedFallback = BuiltInRegistries.BLOCK.get(SHADOWED).isPresent() ? Collections.emptySet() : readBundled(SHADOWED);
	}

	// Blocks of the mod's own tag file; entries are block ids, #tags (resolved against the synced tags) or {"id": ...}
	private static Set<Block> readBundled(final TagKey<Block> tag) {
		Set<Block> blocks = new HashSet<>();
		Optional<Path> path = FabricLoader.getInstance().getModContainer(Betterlight.MOD_ID)
			.flatMap(mod -> mod.findPath("data/" + tag.location().getNamespace() + "/tags/block/" + tag.location().getPath() + ".json"));
		if (path.isEmpty()) {
			return blocks;
		}

		try (Reader reader = Files.newBufferedReader(path.get())) {
			for (JsonElement value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
				String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
				if (id.startsWith("#")) {
					BuiltInRegistries.BLOCK.get(TagKey.create(Registries.BLOCK, Identifier.parse(id.substring(1))))
						.ifPresent(holders -> holders.forEach(holder -> blocks.add(holder.value())));
				} else {
					BuiltInRegistries.BLOCK.get(Identifier.parse(id)).map(Holder::value).ifPresent(blocks::add);
				}
			}
		} catch (IOException | RuntimeException e) {
			Betterlight.LOGGER.warn("Failed to read the bundled {} tag", tag.location(), e);
		}
		return blocks;
	}
}
