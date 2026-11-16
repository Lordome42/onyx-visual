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

	public boolean trail;
	public TrailStyle trailStyle = TrailStyle.RAINBOW;
	public boolean fireflies;
	public boolean pollen;
	public boolean hud;
	public boolean zoom;
	public boolean aurora;
	public boolean shootingStars;
	public boolean magicCircle;
	public boolean wisps;
	public boolean shockwave;
	public boolean outline;
	public boolean wings;
	public boolean lootBeams;
	public boolean hitFlash;
	public boolean breakBurst;
	public boolean levelUp;
	public boolean footprints;
	public boolean halo;
	public boolean slash;
	public boolean oreGlints;
	public boolean lightHalos;
	public boolean soulBurst;
	public boolean rainbow;
	public boolean birds;
	public boolean geese;
	public boolean butterflies;
	public boolean snails;
	public boolean groundClutter;
	public boolean snowPiles;
	public boolean splashes;
	public boolean hedgehogs;
	public boolean crabs;
	public boolean fish;
	public boolean sunShafts;
	public boolean skyHalos;
	public boolean mist;
	public boolean fluff;
	public boolean waterSparkles;
	public boolean netherMotes;
	public boolean soulWisps;
	public boolean lavaLife;
	public boolean pebblePocket;
	public int pebbleCount;
	public int[] pocket = new int[7];
	/** Empty = the free keyless services. Otherwise any OpenAI-compatible chat/completions URL. */
	public String aiUrl = "";
	public String aiModel = "";
	public String aiKey = "";
	public int pocketSelected;
	public boolean snowPrints;
	public boolean puddles;
	public boolean windGusts;
	public boolean petting;
	public boolean coldBreath;
	public boolean glowBugs;
	public boolean wishes;
	public boolean hudCompass;
	public boolean hudLook;
	public boolean hudSchematic;
	public boolean hudTitles;
	public boolean hudCrosshair;
	public boolean hudKeys;
	public boolean hudGear;
	public boolean hudToasts;
	public boolean hudStats;
	public boolean hudFlight;
	public boolean fxRain;
	public boolean fxFrost;
	public boolean fxVignette;
	public boolean fxSpeed;
	public int bestSkips;
	/** Config layout version: files older than 2 had every effect switched on, they are reset to "all off" once. */
	public int schema;
	/** HUD widget offsets (GUI pixels from the default place), edited by dragging in the chat screen. */
	public java.util.Map<String, int[]> hudPos = new java.util.HashMap<>();
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
					if (loaded.schema < 2) {
						for (java.lang.reflect.Field f : OnyxConfig.class.getFields()) {
							if (f.getType() == boolean.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
								try {
									f.setBoolean(loaded, false);
								} catch (IllegalAccessException ignored) {
								}
							}
						}
						loaded.schema = 2;
						try {
							Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve("onyx-visual-binds.properties"));
						} catch (Exception ignored) {
						}
					}
					if (loaded.hudPos == null) {
						loaded.hudPos = new java.util.HashMap<>();
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
		OnyxConfig fresh = new OnyxConfig();
		fresh.schema = 2;
		return fresh;
	}

	public void save() {
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(this, writer);
		} catch (Exception e) {
			OnyxVisual.LOGGER.warn("Could not save onyx-visual.json", e);
		}
	}
}
