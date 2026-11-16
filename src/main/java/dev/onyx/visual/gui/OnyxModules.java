package dev.onyx.visual.gui;

import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.gui.GuiModel.Category;
import dev.onyx.visual.gui.GuiModel.Choice;
import dev.onyx.visual.gui.GuiModel.ColorValue;
import dev.onyx.visual.gui.GuiModel.Key;
import dev.onyx.visual.gui.GuiModel.Module;
import dev.onyx.visual.gui.GuiModel.Setting;
import dev.onyx.visual.gui.GuiModel.Slider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Maps Onyx Visual's config options onto ClickGUI categories and modules. */
public final class OnyxModules {
	private OnyxModules() {}

	/** All toggleable options by category, in display order. Ids are config field names. */
	public static final String[][] LAYOUT = {
			{"sky", "aurora", "shootingStars", "skyHalos", "sunShafts", "mist", "rainbow", "fluff", "windGusts", "wishes", "fireflies", "pollen", "lightHalos"},
			{"player", "wings", "halo", "magicCircle", "wisps", "trail", "footprints", "shockwave", "levelUp", "slash"},
			{"world", "lootBeams", "outline", "hitFlash", "breakBurst", "oreGlints", "soulBurst", "splashes", "waterSparkles", "puddles", "snowPrints", "coldBreath"},
			{"nether", "netherMotes", "soulWisps", "lavaLife"},
			{"life", "birds", "geese", "butterflies", "snails", "hedgehogs", "crabs", "fish", "groundClutter", "snowPiles", "pebblePocket", "petting", "glowBugs"},
			{"interface", "hud", "hudStats", "hudCompass", "hudLook", "hudSchematic", "hudTitles", "hudCrosshair", "hudKeys", "hudGear", "hudToasts", "hudFlight", "fxRain", "fxFrost", "fxVignette", "fxSpeed", "zoom"},
	};
	private static final int[] ICONS = {0xE430, 0xE7FD, 0xE80B, 0xE80E, 0xE91D, 0xE871};

	public static List<Category> create(OnyxConfig config, Runnable save, Supplier<String> openKeyLabel) {
		List<Category> list = new ArrayList<>();
		for (int c = 0; c < LAYOUT.length; c++) {
			String cat = LAYOUT[c][0];
			List<Module> modules = new ArrayList<>();
			for (int i = 1; i < LAYOUT[c].length; i++) {
				Module m = option(config, save, LAYOUT[c][i]);
				if (m != null) modules.add(m);
			}
			list.add(new Category(cat, Tr.raw("onyxvisual.cat." + cat), Tr.raw("onyxvisual.catdesc." + cat), ICONS[c], modules));
		}
		list.add(menuCategory(config, save, openKeyLabel));
		list.add(new Category("configs", Tr.raw("onyxvisual.cat.configs"), Tr.raw("onyxvisual.catdesc.configs"), 0xE161, List.of()));
		return list;
	}

	private static String key(String field) {
		return field.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
	}

	private static Module option(OnyxConfig config, Runnable save, String fieldName) {
		Field field;
		try {
			field = OnyxConfig.class.getField(fieldName);
		} catch (NoSuchFieldException e) {
			return null;
		}
		List<Setting> settings = new ArrayList<>();
		if (fieldName.equals("trail")) {
			List<String> names = new ArrayList<>();
			for (OnyxConfig.TrailStyle style : OnyxConfig.TrailStyle.values())
				names.add(Tr.raw("onyxvisual.trail." + style.name().toLowerCase(Locale.ROOT)));
			settings.add(new Choice(Tr.get("trail_style"), Tr.get("trail_style.hint"), names,
					() -> config.trailStyle.ordinal(),
					i -> { config.trailStyle = OnyxConfig.TrailStyle.values()[i]; save.run(); }));
		}
		if (fieldName.equals("zoom")) {
			settings.add(new Slider(Tr.get("zoom_strength"), Tr.get("zoom_strength.hint"), 1.5f, 10f, .1f, "x",
					() -> config.zoomStrength, v -> { config.zoomStrength = (float) v; save.run(); }));
		}
		String k = key(fieldName);
		return new Module(fieldName, Tr.raw("onyxvisual.option." + k), Tr.raw("onyxvisual.desc." + k),
				() -> get(field, config),
				v -> { set(field, config, v); save.run(); },
				settings);
	}

	private static Category menuCategory(OnyxConfig config, Runnable save, Supplier<String> openKeyLabel) {
		GuiPrefs p = config.gui;
		List<Module> modules = new ArrayList<>();
		modules.add(new Module("gui.dim", Tr.get("m.dim"), Tr.get("m.dim.desc"), () -> p.dim, v -> { p.dim = v; save.run(); },
				List.of(new Choice(Tr.get("theme"), Tr.get("theme.hint"), themeNames(), () -> p.theme, i -> { p.theme = i; save.run(); }),
						new Choice(Tr.get("accent_preset"), Tr.get("accent_preset.hint"), accentNames(), () -> accentIndex(p), i -> { p.accent = GuiPrefs.ACCENTS[i]; save.run(); }),
						new ColorValue(Tr.get("accent"), Tr.get("accent.hint"), () -> p.accent, v -> { p.accent = v | 0xFF000000; save.run(); }),
						new Slider(Tr.get("scale"), Tr.get("scale.hint"), 70, 130, 5, "%", () -> p.scale, v -> { p.scale = (float) v; save.run(); }),
						new Key(Tr.get("open_key"), Tr.get("open_key.hint"), openKeyLabel))));
		modules.add(new Module("gui.waves", Tr.get("m.waves"), Tr.get("m.waves.desc"), () -> p.waves, v -> { p.waves = v; save.run(); }, List.of()));
		modules.add(new Module("gui.lite", Tr.get("m.lite"), Tr.get("m.lite.desc"), () -> p.lite, v -> { p.lite = v; save.run(); }, List.of()));
		modules.add(new Module("gui.title", Tr.get("m.title"), Tr.get("m.title.desc"), () -> p.title, v -> { p.title = v; save.run(); }, List.of()));
		return new Category("menu", Tr.raw("onyxvisual.cat.menu"), Tr.raw("onyxvisual.catdesc.menu"), 0xE8B8, modules);
	}

	private static List<String> themeNames() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < Theme.COUNT; i++) names.add(Tr.raw("onyxvisual.gui.theme." + i));
		return names;
	}

	private static List<String> accentNames() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < GuiPrefs.ACCENTS.length; i++) names.add(Tr.raw("onyxvisual.gui.accent." + i));
		return names;
	}

	private static int accentIndex(GuiPrefs p) {
		for (int i = 0; i < GuiPrefs.ACCENTS.length; i++) if ((GuiPrefs.ACCENTS[i] | 0xFF000000) == (p.accent | 0xFF000000)) return i;
		return 0;
	}

	private static boolean get(Field f, Object o) {
		try {
			return f.getBoolean(o);
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	private static void set(Field f, Object o, boolean v) {
		try {
			f.setBoolean(o, v);
		} catch (IllegalAccessException ignored) {
		}
	}

	/** Toggles an option by id (used by key binds while playing). */
	public static boolean toggle(OnyxConfig config, String id) {
		if (id.startsWith("gui.")) {
			GuiPrefs p = config.gui;
			switch (id) {
				case "gui.dim" -> p.dim = !p.dim;
				case "gui.waves" -> p.waves = !p.waves;
				case "gui.title" -> p.title = !p.title;
				case "gui.lite" -> p.lite = !p.lite;
				default -> { return false; }
			}
			return true;
		}
		try {
			Field f = OnyxConfig.class.getField(id);
			if (f.getType() != boolean.class) return false;
			boolean now = !f.getBoolean(config);
			f.setBoolean(config, now);
			return now;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}
}
