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
