package dev.onyx.visual.gui;

import java.util.function.Function;

/** Translation hook for GUI strings ("onyxvisual.gui.*" keys in the lang files). */
public final class Tr {
	private static Function<String, String> lookup = key -> key;

	private Tr() {}

	public static void set(Function<String, String> function) { lookup = function; }

	public static String get(String key) { return lookup.apply("onyxvisual.gui." + key); }

	public static String raw(String fullKey) { return lookup.apply(fullKey); }
}
