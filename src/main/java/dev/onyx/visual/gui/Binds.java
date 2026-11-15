package dev.onyx.visual.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Persistent per-module key binds (config/onyx-visual-binds.properties). Key codes are GLFW codes. */
public final class Binds {
	public static final int KEY_UNKNOWN = -1, KEY_ESCAPE = 256, KEY_BACKSPACE = 259, KEY_DELETE = 261, KEY_RIGHT_SHIFT = 344, KEY_LAST = 348;
	private static Binds instance;

	private final Map<String, Integer> bindings = new LinkedHashMap<>();
	private final Map<Integer, Boolean> keyWasDown = new HashMap<>();
	private final Path file;

	public static Binds get() { return instance; }

	public static Binds init(Path file) {
		instance = new Binds(file);
		return instance;
	}

	private Binds(Path file) {
		this.file = file;
		if (file == null || !Files.isRegularFile(file)) return;
		Properties values = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			values.load(in);
			for (String id : values.stringPropertyNames()) {
				try {
					int key = Integer.parseInt(values.getProperty(id).trim());
					if (key > 0 && key <= KEY_LAST) bindings.put(id, key);
				} catch (NumberFormatException ignored) {
				}
			}
		} catch (IOException ignored) {
		}
	}

	public int binding(String id) { return bindings.getOrDefault(id, KEY_UNKNOWN); }

	public void bind(String id, int key) {
		if (key == KEY_ESCAPE || key == KEY_RIGHT_SHIFT) return;
		if (key <= 0 || key > KEY_LAST) {
			bindings.remove(id);
		} else {
			bindings.entrySet().removeIf(entry -> entry.getValue() == key && !entry.getKey().equals(id));
			bindings.put(id, key);
		}
		keyWasDown.clear();
		save();
	}

	/** Polls bound keys; fires toggle for each fresh press. Call once per client tick, only in-game. */
	public void tick(IntPredicate isDown, Consumer<String> toggle) {
		for (Map.Entry<String, Integer> entry : bindings.entrySet()) {
			int key = entry.getValue();
			boolean down = isDown.test(key);
			boolean was = keyWasDown.getOrDefault(key, false);
			keyWasDown.put(key, down);
			if (down && !was) toggle.accept(entry.getKey());
		}
	}

	public void resetPressed(IntPredicate isDown) {
		for (int key : bindings.values()) keyWasDown.put(key, isDown.test(key));
	}

	public String label(String id) { return keyName(binding(id)); }

	public static String keyName(int key) {
		if (key <= 0) return Tr.get("bind");
		if (key >= 65 && key <= 90) return String.valueOf((char) key);
		if (key >= 48 && key <= 57) return String.valueOf((char) key);
		if (key >= 290 && key <= 314) return "F" + (key - 289);
		if (key >= 320 && key <= 329) return "NUM " + (key - 320);
		return switch (key) {
			case 32 -> "SPACE";
			case 39 -> "'";
			case 44 -> ",";
			case 45 -> "-";
			case 46 -> ".";
			case 47 -> "/";
			case 59 -> ";";
			case 61 -> "=";
			case 91 -> "[";
			case 92 -> "\\";
			case 93 -> "]";
			case 96 -> "`";
			case 257 -> "ENTER";
			case 258 -> "TAB";
			case 260 -> "INSERT";
			case 261 -> "DELETE";
			case 262 -> "RIGHT";
			case 263 -> "LEFT";
			case 264 -> "DOWN";
			case 265 -> "UP";
			case 266 -> "PG UP";
			case 267 -> "PG DN";
			case 268 -> "HOME";
			case 269 -> "END";
			case 280 -> "CAPS";
			case 340 -> "L SHIFT";
			case 341 -> "L CTRL";
			case 342 -> "L ALT";
			case 344 -> "R SHIFT";
			case 345 -> "R CTRL";
			case 346 -> "R ALT";
			default -> "KEY " + key;
		};
	}

	private void save() {
		if (file == null) return;
		Properties values = new Properties();
		bindings.forEach((id, key) -> values.setProperty(id, Integer.toString(key)));
		try {
			Files.createDirectories(file.getParent());
			try (OutputStream out = Files.newOutputStream(file)) {
				values.store(out, "Onyx Visual module binds (GLFW key codes)");
			}
		} catch (IOException ignored) {
		}
	}
}
