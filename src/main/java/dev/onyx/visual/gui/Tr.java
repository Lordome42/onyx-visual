package dev.onyx.visual.gui;

import java.util.function.Function;

/** Translation hook for GUI strings ("onyxvisual.gui.*" keys in the lang files). */
public final class Tr {
	private static Function<String, String> lookup = key -> key;
	private static final java.util.HashMap<String, String> CACHE = new java.util.HashMap<>();

	private Tr() {}

	public static void set(Function<String, String> function) { lookup = function; CACHE.clear(); }

	public static String get(String key) { return raw("onyxvisual.gui." + key); }

	public static String raw(String fullKey) { return CACHE.computeIfAbsent(fullKey, lookup); }
}
