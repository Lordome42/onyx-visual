package dev.onyx.visual.gui;

import dev.onyx.visual.gui.GuiModel.Module;

/**
 * Module card: star (favourite), name, settings button, animated switch, description,
 * status line and the bind chip. Written for Onyx Visual in the style of the original ClickGUI
 * (its card renderer was not part of the supplied files).
 */
final class CardRenderer {
	private static final int WHITE = 0xFFF7F4EC, TEXT = 0xFFD9D5C9, MUTED = 0xFF898A85;
	private static final int CARD = 0xFF141414, CARD_HOVER = 0xFF181818, RING = 0xFF222222;

	private CardRenderer() {}

	static float baseHeight(Module m, float s) { return 64 * s; }

	static void draw(UiCanvas ui, Module m, float x, float y, float w, float h, float s, int accent,
			boolean favorite, boolean expanded, boolean hover, String bind, boolean binding, float dt) {
		float p = m.animation.update(dt, 14);
		ui.roundedRect(x, y, w, h, 6 * s, hover ? CARD_HOVER : CARD)
		  .outlineRoundedRect(x, y, w, h, 6 * s, .6f * s, Colors.mix(RING, Colors.withAlpha(accent, .55f), p * .8f));
		if (p > .01f) ui.roundedRect(x + 1.5f * s, y + 9 * s, 2 * s, 15 * s, s, Colors.fade(accent, p));

		// header row
		float cy = y + 16 * s;
		ui.icon(x + 16.5f * s, cy, 10.5f * s, favorite ? accent : 0xFF4A4A4A, favorite ? 0xE838 : 0xE83A);
		boolean hasSettings = !m.settings.isEmpty();
		float nameRight = x + w - (hasSettings ? 50 : 32) * s;
		ui.strongText(x + 28 * s, cy, 8.4f * s, Colors.mix(TEXT, WHITE, p), ui.fit(m.name, 8.4f * s, true, nameRight - x - 28 * s));
		if (hasSettings) {
			float bx = x + w - 45 * s;
			if (expanded) ui.roundedRect(bx, y + 5 * s, 17 * s, 22 * s, 4 * s, 0xFF1F1F1F);
			ui.icon(bx + 8.5f * s, cy, 9.5f * s, expanded ? accent : MUTED, 0xE429);
		}
		// switch
		float tw = 19 * s, th = 11 * s, tx = x + w - 25 * s, ty = cy - th / 2;
		ui.roundedRect(tx, ty, tw, th, th / 2, Colors.mix(0xFF2A2A2A, accent, p));
		float knob = th / 2 - 1.6f * s;
		ui.circle(tx + th / 2 + (tw - th) * p, cy, knob, Colors.mix(0xFF8A8A8A, 0xFF291923, p));

		// description
		ui.text(x + 10 * s, y + 32 * s, 6.9f * s, MUTED, ui.fit(m.description, 6.9f * s, false, w - 20 * s));

		// status + bind chip
		float by = y + 42 * s, bh = 15 * s, bcy = by + bh / 2;
		ui.circle(x + 12.5f * s, bcy, 2.2f * s, Colors.mix(0xFF4A4A4A, accent, p));
		ui.text(x + 18 * s, bcy, 6.4f * s, Colors.mix(MUTED, TEXT, p), m.enabled ? Tr.get("enabled") : Tr.get("disabled"));
		float bw = 34 * s, bx = x + w - 44 * s;
		String label = binding ? "..." : bind;
		ui.roundedRect(bx, by, bw, bh, 4 * s, binding ? Colors.withAlpha(accent, .25f) : 0xFF1E1E1E)
		  .outlineRoundedRect(bx, by, bw, bh, 4 * s, .5f * s, binding ? accent : 0xFF2A2A2A)
		  .centeredText(bx + bw / 2, bcy, 6 * s, binding ? WHITE : (bind.equals(Tr.get("bind")) ? MUTED : TEXT),
				  ui.fit(label, 6 * s, true, bw - 4 * s), true);
	}
}
