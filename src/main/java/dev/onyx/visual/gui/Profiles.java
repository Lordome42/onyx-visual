package dev.onyx.visual.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.OnyxVisual;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Named setting profiles, one JSON file each in {@code <minecraft folder>/onyx-visual/config/}. A profile keeps
 * the on/off state of every function, the trail style, the zoom strength and the HUD positions, plus a name and a colour.
 */
public final class Profiles {
	public record Info(String name, int color, String file) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final int[] COLORS = {0xFFFF8FB9, 0xFFB79CFF, 0xFF6FE3D0, 0xFFFFC861, 0xFFFF6B6B, 0xFF6FA8FF, 0xFF8BE37A, 0xFFF7F4EC};

	private Profiles() {}

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("onyx-visual").resolve("config");
	}

	static String fileName(String name) {
		String safe = name.trim().replaceAll("[^\\p{L}\\p{N} _-]", "_");
		if (safe.length() > 32) safe = safe.substring(0, 32);
		return (safe.isBlank() ? "config" : safe) + ".json";
	}

	public static List<Info> list() {
		List<Info> out = new ArrayList<>();
		try {
			Files.createDirectories(dir());
			try (var stream = Files.list(dir())) {
				for (Path file : (Iterable<Path>) stream.filter(f -> f.getFileName().toString().endsWith(".json"))::iterator) {
					try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
						String name = o.has("name") ? o.get("name").getAsString() : file.getFileName().toString().replace(".json", "");
						int color = o.has("color") ? o.get("color").getAsInt() : COLORS[0];
						out.add(new Info(name, color | 0xFF000000, file.getFileName().toString()));
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception e) {
			OnyxVisual.LOGGER.warn("Could not list profiles", e);
		}
		out.sort(Comparator.comparing(i -> i.name().toLowerCase(java.util.Locale.ROOT)));
		return out;
	}

	private static boolean included(Field f) {
		if (Modifier.isStatic(f.getModifiers())) return false;
		Class<?> t = f.getType();
		return t == boolean.class || t == OnyxConfig.TrailStyle.class || f.getName().equals("zoomStrength") || f.getName().equals("hudPos");
	}

	/** Writes the current settings under the given name; returns false on failure. */
	public static boolean save(String name, int color, OnyxConfig config) {
		try {
			Files.createDirectories(dir());
			JsonObject o = new JsonObject();
			o.addProperty("name", name.trim());
			o.addProperty("color", color | 0xFF000000);
			JsonObject settings = new JsonObject();
			for (Field f : OnyxConfig.class.getFields()) {
				if (!included(f)) continue;
				settings.add(f.getName(), GSON.toJsonTree(f.get(config)));
			}
			o.add("settings", settings);
			JsonObject look = new JsonObject();
			look.addProperty("theme", config.gui.theme);
			look.addProperty("accent", config.gui.accent);
			o.add("gui", look);
			try (Writer w = Files.newBufferedWriter(dir().resolve(fileName(name)), StandardCharsets.UTF_8)) {
				GSON.toJson(o, w);
			}
			return true;
		} catch (Exception e) {
			OnyxVisual.LOGGER.warn("Could not save profile", e);
			return false;
		}
	}

	/** Applies a profile to the live config and saves it. */
	public static boolean load(Info info, OnyxConfig config) {
		try (Reader reader = Files.newBufferedReader(dir().resolve(info.file()), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject settings = root.getAsJsonObject("settings");
			if (root.has("gui")) {
				JsonObject look = root.getAsJsonObject("gui");
				if (look.has("theme")) config.gui.theme = look.get("theme").getAsInt();
				if (look.has("accent")) config.gui.accent = look.get("accent").getAsInt();
				config.gui.sanitize();
			}
			for (Field f : OnyxConfig.class.getFields()) {
				if (!included(f)) continue;
				JsonElement v = settings.get(f.getName());
				if (v == null) {
					if (f.getType() == boolean.class) f.setBoolean(config, false);
					continue;
				}
				f.set(config, GSON.fromJson(v, f.getGenericType()));
			}
			if (config.hudPos == null) config.hudPos = new java.util.HashMap<>();
			if (config.trailStyle == null) config.trailStyle = OnyxConfig.TrailStyle.RAINBOW;
			if (config.zoomStrength < 1.5f || config.zoomStrength > 10f) config.zoomStrength = 4.3f;
			config.gui.profile = info.name();
			config.save();
			return true;
		} catch (Exception e) {
			OnyxVisual.LOGGER.warn("Could not load profile", e);
			return false;
		}
	}

	public static void delete(Info info) {
		try {
			Files.deleteIfExists(dir().resolve(info.file()));
		} catch (Exception e) {
			OnyxVisual.LOGGER.warn("Could not delete profile", e);
		}
	}
}
