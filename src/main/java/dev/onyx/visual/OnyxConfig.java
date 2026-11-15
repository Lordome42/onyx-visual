package dev.onyx.visual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plain Gson POJO stored in config/onyx-visual.json. */
public final class OnyxConfig {
	public enum TrailStyle {
		RAINBOW, STARDUST, PETALS, SOUL;

		public TrailStyle next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public boolean trail = true;
	public TrailStyle trailStyle = TrailStyle.RAINBOW;
	public boolean fireflies = true;
	public boolean pollen = true;
	public boolean hud = true;
	public boolean zoom = true;
	public boolean aurora = true;
	public boolean shootingStars = true;
	public boolean magicCircle = true;
	public boolean wisps = true;
	public boolean shockwave = true;
	public boolean outline = true;
	public boolean wings = true;
	public boolean lootBeams = true;
	public boolean hitFlash = true;
	public boolean breakBurst = true;
	public boolean levelUp = true;
	public boolean footprints = true;
	public boolean halo = true;
	public boolean slash = true;
	public boolean oreGlints = true;
	public boolean lightHalos = true;
	public boolean soulBurst = true;
	public boolean rainbow = true;
	public boolean birds = true;
	public boolean geese = true;
	public boolean butterflies = true;
	public boolean snails = true;
	public boolean groundClutter = true;
	public boolean snowPiles = true;
	public boolean splashes = true;
	public boolean hedgehogs = true;
	public boolean crabs = true;
	public boolean fish = true;
	public boolean sunShafts = true;
	public boolean skyHalos = true;
	public boolean mist = true;
	public boolean fluff = true;
	public boolean waterSparkles = true;
	public boolean netherMotes = true;
	public boolean soulWisps = true;
	public boolean lavaLife = true;
	public boolean pebblePocket = true;
	public int pebbleCount;
	public int[] pocket = new int[7];
	/** Empty = the free keyless services. Otherwise any OpenAI-compatible chat/completions URL. */
	public String aiUrl = "";
	public String aiModel = "";
	public String aiKey = "";
	public int pocketSelected;
	public boolean snowPrints = true;
	public boolean puddles = true;
	public boolean windGusts = true;
	public boolean petting = true;
	public boolean coldBreath = true;
	public boolean glowBugs = true;
	public boolean wishes = true;
	public boolean hudCompass = true;
	public boolean hudLook = true;
	public boolean hudSchematic = true;
	public boolean hudTitles = true;
	public boolean hudCrosshair = true;
	public boolean hudKeys = true;
	public boolean hudGear = true;
	public boolean hudToasts = true;
	public boolean hudStats = true;
	public boolean hudFlight = true;
	public boolean fxRain = true;
	public boolean fxFrost = true;
	public boolean fxVignette = true;
	public boolean fxSpeed = true;
	public int bestSkips;
	/** Zoom magnification (x). */
	public float zoomStrength = 4.3f;
	/** ClickGUI look and state. */
	public dev.onyx.visual.gui.GuiPrefs gui = new dev.onyx.visual.gui.GuiPrefs();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static OnyxConfig instance;

	public static OnyxConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("onyx-visual.json");
	}

	private static OnyxConfig load() {
		Path file = path();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				OnyxConfig loaded = GSON.fromJson(reader, OnyxConfig.class);
				if (loaded != null) {
					if (loaded.pocket == null || loaded.pocket.length != 7) {
						loaded.pocket = new int[7];
						loaded.pocket[0] = Math.max(0, loaded.pebbleCount);
					}
					if (loaded.gui == null) {
						loaded.gui = new dev.onyx.visual.gui.GuiPrefs();
					}
					loaded.gui.sanitize();
					if (loaded.zoomStrength < 1.5f || loaded.zoomStrength > 10f) {
						loaded.zoomStrength = 4.3f;
					}
					if (loaded.trailStyle == null) {
						loaded.trailStyle = TrailStyle.RAINBOW;
					}
					return loaded;
				}
			} catch (Exception e) {
				OnyxVisual.LOGGER.warn("Could not read onyx-visual.json, using defaults", e);
			}
		}
		return new OnyxConfig();
	}

	public void save() {
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(this, writer);
		} catch (Exception e) {
			OnyxVisual.LOGGER.warn("Could not save onyx-visual.json", e);
		}
	}
}
