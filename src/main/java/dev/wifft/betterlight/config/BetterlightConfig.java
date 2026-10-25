package dev.wifft.betterlight.config;

import dev.wifft.betterlight.Betterlight;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod options, read once at startup from {@code config/betterlight.properties}.
 * Fields are volatile because the section compile threads and the server thread read them.
 */
public final class BetterlightConfig {
	private static final String FILE_NAME = "betterlight.properties";

	public static volatile LightModel lightModel = LightModel.SPLIT;
	public static volatile boolean smoothFluids = true;
	public static volatile boolean smoothNoAoModels = true;
	public static volatile boolean smoothEmitters = true;
	public static volatile boolean blockEntityLightField = true;
	public static volatile boolean smoothEntities = true;
	public static volatile boolean smoothPaintingsFrames = true;
	public static volatile boolean frontEmitters = true;

	private BetterlightConfig() {
	}

	public enum LightModel {
		SPLIT(0, "split"),
		CARDINAL(1, "cardinal"),
		TWO_LIGHT(2, "twolight");

		/** Value of {@code MINECRAFT_LIGHT_MODEL_*} in {@code include/light.glsl}. */
		public final int shaderId;
		public final String key;

		LightModel(int shaderId, String key) {
			this.shaderId = shaderId;
			this.key = key;
		}

		public static LightModel parse(String value, LightModel fallback) {
			if (value == null) {
				return fallback;
			}
			for (LightModel model : values()) {
				if (model.key.equals(value.trim().toLowerCase(Locale.ROOT))) {
					return model;
				}
			}
			return fallback;
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Properties props = new Properties();
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				props.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				Betterlight.LOGGER.warn("Failed to read {}, using defaults", path, e);
			}
		}

		LightModel model = LightModel.parse(props.getProperty("lightModel"), LightModel.SPLIT);
		// The JVM property of the patched jar still wins, so old launcher profiles keep working
		lightModel = LightModel.parse(System.getProperty("betterlight.model"), model);
		smoothFluids = bool(props, "smoothFluids", true);
		smoothNoAoModels = bool(props, "smoothNoAoModels", true);
		smoothEmitters = bool(props, "smoothEmitters", true);
		blockEntityLightField = bool(props, "blockEntityLightField", true);
		smoothEntities = bool(props, "smoothEntities", true);
		smoothPaintingsFrames = bool(props, "smoothPaintingsFrames", true);
		frontEmitters = bool(props, "frontEmitters", true);

		if (!Files.exists(path)) {
			save();
		}
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("lightModel", lightModel.key);
		props.setProperty("smoothFluids", Boolean.toString(smoothFluids));
		props.setProperty("smoothNoAoModels", Boolean.toString(smoothNoAoModels));
		props.setProperty("smoothEmitters", Boolean.toString(smoothEmitters));
		props.setProperty("blockEntityLightField", Boolean.toString(blockEntityLightField));
		props.setProperty("smoothEntities", Boolean.toString(smoothEntities));
		props.setProperty("smoothPaintingsFrames", Boolean.toString(smoothPaintingsFrames));
		props.setProperty("frontEmitters", Boolean.toString(frontEmitters));

		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				props.store(writer, "Betterlight options (lightModel: split, cardinal or twolight)");
			}
		} catch (IOException e) {
			Betterlight.LOGGER.warn("Failed to write {}", path, e);
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}
}
