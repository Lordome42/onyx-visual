package dev.onyx.visual.gui;

import dev.onyx.visual.OnyxConfig;
import java.util.List;

/** The "Configs" page of the ClickGUI: create, save over, load and delete named profiles with a colour tag. */
final class ProfilesPanel {
	private static final int WHITE = 0xFFF7F4EC, TEXT = 0xFFD9D5C9, MUTED = 0xFF898A85;

	private final OnyxConfig config;
	private final Runnable onLoaded;
	private List<Profiles.Info> list;
	private String name = "";
	private int color = Profiles.COLORS[0];
	private boolean focused;
	private String confirmDelete;
	private String status = "";
	private long statusUntil;
	private float scroll, maxScroll;
	private float listTop, listBottom;

	ProfilesPanel(OnyxConfig config, Runnable onLoaded) {
		this.config = config;
		this.onLoaded = onLoaded;
	}

	boolean typing() { return focused; }

	void refresh() { list = Profiles.list(); }

	private void say(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 2600;
	}

	private static boolean hit(float mx, float my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private boolean button(UiCanvas ui, float x, float y, float w, float h, float s, String label, int fill, int textColor, int mx, int my, boolean pressed, boolean[] clicked) {
		boolean over = hit(mx, my, x, y, w, h);
		ui.roundedRect(x, y, w, h, 5 * s, over ? Colors.mix(fill, 0xFFFFFFFF, .08f) : fill)
		  .centeredText(x + w / 2, y + h / 2, 7.6f * s, textColor, ui.fit(label, 7.6f * s, true, w - 6 * s), true);
		if (over && pressed) clicked[0] = true;
		return over && pressed;
	}

	/** Draws the page inside the main area and handles clicks. */
	void draw(UiCanvas ui, float x, float top, float w, float bottom, float s, int accent, int mx, int my, boolean pressed) {
		if (list == null) refresh();
		boolean[] any = {false};
		// -------- creation card
		float ch = 62 * s;
		ui.roundedRect(x, top, w, ch, 6 * s, 0xFF141414).outlineRoundedRect(x, top, w, ch, 6 * s, .6f * s, 0xFF222222);
		ui.strongText(x + 12 * s, top + 14 * s, 8.6f * s, WHITE, Tr.get("cfg.new"));
		float fx = x + 12 * s, fy = top + 26 * s, fw = 210 * s, fh = 24 * s;
		if (pressed) focused = hit(mx, my, fx, fy, fw, fh);
		ui.roundedRect(fx, fy, fw, fh, 5 * s, 0xFF181818)
		  .outlineRoundedRect(fx, fy, fw, fh, 5 * s, .7f * s, focused ? accent : 0xFF2A2A2A);
		String shown = name.isEmpty() ? Tr.get("cfg.name") : name;
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
		ui.text(fx + 9 * s, fy + fh / 2, 8.2f * s, name.isEmpty() ? MUTED : TEXT, ui.fit(shown, 8.2f * s, false, fw - 18 * s));
		float cx = fx + fw + 16 * s;
		for (int c : Profiles.COLORS) {
			float r = 7 * s;
			boolean sel = c == color;
			if (pressed && hit(mx, my, cx - 2 * s, fy, 2 * r + 4 * s, fh)) color = c;
			if (sel) ui.circle(cx + r, fy + fh / 2, r + 3 * s, 0x55FFFFFF);
			ui.circle(cx + r, fy + fh / 2, r, c);
			cx += 2 * r + 9 * s;
		}
		float bw = 96 * s;
		float bx = x + w - bw - 12 * s;
		if (button(ui, bx, fy, bw, fh, s, Tr.get("cfg.create"), accent, 0xFF221B11, mx, my, pressed, any)) {
			String trimmed = name.trim();
			if (trimmed.isEmpty()) say(Tr.get("cfg.need_name"));
			else if (Profiles.save(trimmed, color, config)) {
				config.gui.profile = trimmed;
				config.save();
				name = "";
				focused = false;
				refresh();
				say(Tr.get("cfg.saved"));
			} else say(Tr.get("cfg.error"));
		}
		if (System.currentTimeMillis() < statusUntil)
			ui.rightText(x + w - 14 * s, top + 14 * s, 7.6f * s, accent, status, true);
		else ui.rightText(x + w - 14 * s, top + 14 * s, 7.2f * s, MUTED, Tr.get("cfg.where"), false);

		// -------- list
		listTop = top + ch + 10 * s;
		listBottom = bottom;
		float rowH = 36 * s, gap = 6 * s;
		float contentH = list.size() * (rowH + gap);
		maxScroll = Math.max(0, contentH - (listBottom - listTop));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		if (list.isEmpty()) {
			ui.centeredText(x + w / 2, listTop + 50 * s, 9 * s, MUTED, Tr.get("cfg.empty"), false);
			return;
		}
		ui.pushScissor(x, listTop, w, listBottom - listTop);
		float y = listTop - scroll;
		boolean listHover = hit(mx, my, x, listTop, w, listBottom - listTop);
		Profiles.Info toDelete = null, toLoad = null, toSave = null;
		for (Profiles.Info p : list) {
			if (y + rowH > listTop - 2 && y < listBottom + 2) {
				boolean active = p.name().equals(config.gui.profile);
				ui.roundedRect(x, y, w, rowH, 6 * s, 0xFF141414)
				  .outlineRoundedRect(x, y, w, rowH, 6 * s, .6f * s, active ? Colors.withAlpha(p.color(), .7f) : 0xFF222222)
				  .roundedRect(x + 8 * s, y + 8 * s, 4 * s, rowH - 16 * s, 2 * s, p.color())
				  .circle(x + 28 * s, y + rowH / 2, 6 * s, p.color());
				float tx = x + 42 * s;
				ui.strongText(tx, y + rowH / 2, 9.2f * s, WHITE, ui.fit(p.name(), 9.2f * s, true, 260 * s));
				if (active) {
					float nw = ui.textWidth(p.name(), 9.2f * s, true);
					float tagW = ui.textWidth(Tr.get("cfg.active"), 7 * s, true) + 12 * s;
					ui.roundedRect(tx + Math.min(nw, 260 * s) + 8 * s, y + rowH / 2 - 7 * s, tagW, 14 * s, 4 * s, Colors.mix(0xFF1A1A1A, p.color(), .25f))
					  .centeredText(tx + Math.min(nw, 260 * s) + 8 * s + tagW / 2, y + rowH / 2, 7 * s, p.color(), Tr.get("cfg.active"), true);
				}
				float bh = 24 * s, by = y + (rowH - bh) / 2, b3 = 84 * s;
				float rx = x + w - 10 * s - b3;
				boolean confirming = p.file().equals(confirmDelete);
				boolean click = pressed && listHover;
				boolean[] c = {false};
				if (button(ui, rx, by, b3, bh, s, confirming ? Tr.get("cfg.sure") : Tr.get("cfg.delete"), confirming ? 0xFF7A2E2E : 0xFF1E1E1E, confirming ? WHITE : TEXT, mx, my, click, c)) toDelete = p;
				rx -= b3 + 6 * s;
				if (button(ui, rx, by, b3, bh, s, Tr.get("cfg.save"), 0xFF1E1E1E, TEXT, mx, my, click, c)) toSave = p;
				rx -= b3 + 6 * s;
				if (button(ui, rx, by, b3, bh, s, Tr.get("cfg.load"), Colors.mix(0xFF1E1E1E, p.color(), .35f), WHITE, mx, my, click, c)) toLoad = p;
				if (c[0]) any[0] = true;
			}
			y += rowH + gap;
		}
		ui.popScissor();
		if (toLoad != null) {
			if (Profiles.load(toLoad, config)) {
				onLoaded.run();
				say(Tr.get("cfg.loaded") + " " + toLoad.name());
			} else say(Tr.get("cfg.error"));
		} else if (toSave != null) {
			say(Profiles.save(toSave.name(), toSave.color(), config) ? Tr.get("cfg.saved") : Tr.get("cfg.error"));
			config.gui.profile = toSave.name();
			config.save();
		} else if (toDelete != null) {
			if (toDelete.file().equals(confirmDelete)) {
				Profiles.delete(toDelete);
				if (toDelete.name().equals(config.gui.profile)) config.gui.profile = "";
				confirmDelete = null;
				refresh();
			} else confirmDelete = toDelete.file();
		} else if (pressed && !any[0]) confirmDelete = null;
	}

	boolean scrolled(float amount, float step) {
		if (maxScroll <= 0) return false;
		scroll = Math.max(0, Math.min(maxScroll, scroll - amount * step));
		return true;
	}

	boolean charTyped(String chars) {
		if (!focused || name.length() >= 24) return focused;
		name += chars;
		return true;
	}

	boolean keyPressed(int key) {
		if (!focused) return false;
		if (key == Binds.KEY_BACKSPACE) {
			if (!name.isEmpty()) name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
			return true;
		}
		if (key == 257 || key == 335 || key == Binds.KEY_ESCAPE) {
			focused = false;
			return true;
		}
		return key != Binds.KEY_RIGHT_SHIFT;
	}
}
