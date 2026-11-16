package dev.onyx.visual.gui;

/**
 * Immediate-mode drawing surface used by the Onyx ClickGUI. Coordinates are GUI units (floats),
 * "y" of every text call is the vertical centre of the line, colours are ARGB.
 * The in-game backend is {@link McCanvas}; a Java2D backend exists only for offline previews.
 */
public interface UiCanvas {
	UiCanvas rect(float x, float y, float w, float h, int color);
	UiCanvas roundedRect(float x, float y, float w, float h, float r, int color);
	UiCanvas outlineRoundedRect(float x, float y, float w, float h, float r, float stroke, int color);
	/** Two-colour gradient clipped to a rounded rect; horizontal = left to right, else top to bottom. */
	UiCanvas gradientRoundedRect(float x, float y, float w, float h, float r, int from, int to, boolean horizontal);
	UiCanvas line(float x1, float y1, float x2, float y2, float stroke, int color);
	UiCanvas circle(float cx, float cy, float r, int color);
	UiCanvas triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color);

	/** Material Icons glyph centred on (cx, cy). */
	UiCanvas icon(float cx, float cy, float size, int color, int codepoint);
	UiCanvas text(float x, float cy, float size, int color, String text);
	UiCanvas strongText(float x, float cy, float size, int color, String text);
	default UiCanvas centeredText(float cx, float cy, float size, int color, String text, boolean strong) {
		float w = textWidth(text, size, strong);
		return strong ? strongText(cx - w / 2, cy, size, color, text) : text(cx - w / 2, cy, size, color, text);
	}
	default UiCanvas rightText(float rx, float cy, float size, int color, String text, boolean strong) {
		float w = textWidth(text, size, strong);
		return strong ? strongText(rx - w, cy, size, color, text) : text(rx - w, cy, size, color, text);
	}
	float textWidth(String text, float size, boolean strong);

	/** Cheap low-detail shape (0 square, 1 circle, 2 triangle) for the many dim background cells. */
	default UiCanvas coarseShape(int shape, float cx, float cy, float r, int color) {
		if (shape == 0) return rect(cx - r, cy - r, r * 2, r * 2, color);
		if (shape == 1) return circle(cx, cy, r, color);
		return triangle(cx, cy - r, cx - r, cy + r, cx + r, cy + r, color);
	}

	/** True when the player (or the frame-time guard) asked for the cheap drawing mode. */
	default boolean lite() { return false; }
	/** Framebuffer pixels per GUI unit. */
	default float pixelScale() { return 1; }

	/**
	 * Stretches an ARGB pixel buffer (tw x th) over the GUI rect in a single draw call.
	 * {@code changed} says the pixels differ from the previous call. Returns false when unsupported.
	 */
	default boolean blitPixels(int[] argb, int tw, int th, boolean changed, float x, float y, float w, float h) { return false; }

	void pushScissor(float x, float y, float w, float h);
	void popScissor();

	/** Shortens text with "..." so it fits into maxWidth. */
	default String fit(String text, float size, boolean strong, float maxWidth) {
		if (textWidth(text, size, strong) <= maxWidth) return text;
		String s = text;
		while (s.length() > 1 && textWidth(s + "...", size, strong) > maxWidth) s = s.substring(0, s.length() - 1);
		return s.trim() + "...";
	}
}
