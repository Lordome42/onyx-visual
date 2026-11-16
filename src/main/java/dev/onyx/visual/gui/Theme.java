package dev.onyx.visual.gui;

/**
 * Menu colour themes. Instead of re-colouring every widget, neutral (grey) colours are tinted on their way to the
 * screen; saturated colours (the accent) are left alone. Index 0 is the original look.
 */
public final class Theme {
	public static final int COUNT = 6;
	// Added to dark surfaces (r, g, b); the last value scales the greys (OLED black).
	private static final int[][] ADD = {{0, 0, 0}, {-6, 6, 30}, {-6, 18, 4}, {18, -2, 20}, {24, 6, -8}, {0, 0, 0}};
	/** 0..5, set by the screen before every frame. */
	static int current;

	private Theme() {}

	public static int apply(int c) {
		if (current <= 0 || current >= COUNT) return c;
		int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
		int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b));
		if (hi - lo > 24) return c; // coloured: accent, status dots...
		float lum = (r + g + b) / 765f;
		if (current == 5) {
			if (lum > .45f) return c;
			float k = .25f + lum * .5f;
			return (c & 0xFF000000) | (Math.round(r * k) << 16) | (Math.round(g * k) << 8) | Math.round(b * k);
		}
		int[] a = ADD[current];
		float k = lum > .5f ? .15f : Math.max(0f, 1f - lum * 1.4f);
		r = clamp(r + Math.round(a[0] * k));
		g = clamp(g + Math.round(a[1] * k));
		b = clamp(b + Math.round(a[2] * k));
		return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
