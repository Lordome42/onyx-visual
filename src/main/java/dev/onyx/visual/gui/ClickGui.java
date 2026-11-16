package dev.onyx.visual.gui;

import dev.onyx.visual.gui.GuiModel.Category;
import dev.onyx.visual.gui.GuiModel.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Onyx Visual ClickGUI, ported from CsClickGui. Independent of Minecraft: it draws onto a
 * {@link UiCanvas} and receives input as plain values, so the same code runs in game and in previews.
 */
public final class ClickGui {
	private static final int WHITE = 0xFFF7F4EC, TEXT = 0xFFD9D5C9, MUTED = 0xFF898A85;
	private static final int PANEL = 0xF70F0F0F;
	private static final int LOGO = 0xEAD3;

	private final List<Category> categories;
	private final GuiPrefs prefs;
	private final Runnable save;
	private final Binds binds;
	private int categoryIndex;
	private final Set<Module> expandedModules = new HashSet<>();
	private final SettingsPanel settings = new SettingsPanel();
	private final RubberSegment categorySegment = new RubberSegment();
	private final TechTitle techTitle = new TechTitle();
	private final ShapeWaves shapeWaves = new ShapeWaves();
	private final Anim.AnimatedFloat open = new Anim.AnimatedFloat(0);
	private Module bindingModule;
	private boolean searchFocused;
	private final Set<String> favorites = new HashSet<>();
	private String search = "";
	private float scrollOffset, scrollTarget, maxScroll;
	private float width, height, gs = 2;
	/** Last drawn position of the first card (GUI units); used by in-game tests. */
	public float firstCardX = -1, firstCardY = -1;

	public ClickGui(List<Category> categories, GuiPrefs prefs, Binds binds, Runnable save) {
		this.categories = categories;
		this.prefs = prefs;
		this.binds = binds;
		this.save = save;
		this.categoryIndex = Math.max(0, Math.min(categories.size() - 1, prefs.category));
		favorites.addAll(prefs.favorites);
		open.setTarget(1);
	}

	private int accent() { return prefs.accent | 0xFF000000; }

	public boolean lite() { return prefs.lite; }
	public boolean capturingKeys() { return bindingModule != null || searchFocused; }

	/** One frame: draw everything and react to the mouse. */
	public void frame(UiCanvas ui, float width, float height, float guiScale, int mouseX, int mouseY, boolean down, boolean pressed, boolean rightPressed, float dt) {
		this.width = width;
		this.height = height;
		this.gs = Math.max(1, guiScale);
		for (Category c : categories) for (Module m : c.modules()) m.refresh();
		open.update(dt, 9);
		Layout l = Layout.create(width, height, prefs.scale / 100f, gs);
		boolean popupWasOpen = settings.popupOpen();
		settings.dismissOutside(mouseX, mouseY, pressed);
		boolean basePressed = pressed && !popupWasOpen, baseDown = down && !popupWasOpen, baseRightPressed = rightPressed && !popupWasOpen;
		settings.beginFrame(accent());
		backdrop(ui, l, mouseX, mouseY, down, pressed, dt);
		sidebar(ui, l, mouseX, mouseY, baseDown, basePressed, dt);
		Category category = categories.get(categoryIndex);
		header(ui, l, category, mouseX, mouseY, basePressed);
		moduleGrid(ui, l, category, mouseX, mouseY, baseDown, basePressed, baseRightPressed, dt);
		settings.drawPopups(ui, new SettingsPanel.Bounds(l.x, l.y, l.w, l.h, l.mainX, l.mainW, l.s), mouseX, mouseY, down, pressed);
		if (!down) settings.released();
	}

	private void backdrop(UiCanvas ui, Layout l, int mx, int my, boolean down, boolean pressed, float dt) {
		int bg = prefs.dim ? 0xF8120F17 : 0xB8120F17;
		shapeWaves.draw(ui, width, height, mx, my, dt, prefs.waves && !ui.lite(), Colors.fade(bg, .35f + .65f * open.value()));
		for (int i = ui.lite() ? 0 : 3; i > 0; i--) {
			float spread = i * 2.2f * l.s;
			ui.roundedRect(l.x - spread, l.y - spread + 3 * l.s, l.w + spread * 2, l.h + spread * 2, 13 * l.s, 0x07000000);
		}
		ui.roundedRect(l.x, l.y, l.w, l.h, 9 * l.s, PANEL)
		  .outlineRoundedRect(l.x, l.y, l.w, l.h, 9 * l.s, .7f * l.s, 0xFF202020)
		  .line(l.x + 12 * l.s, l.y + 52 * l.s, l.x + l.w - 12 * l.s, l.y + 52 * l.s, .65f * l.s, 0xFF242424);
		float cx = l.x + l.w / 2;
		if (l.y - 60 * l.s - 23 * l.s > 0) ui.icon(cx, l.y - 60 * l.s, 46 * l.s, accent(), LOGO);
		if (prefs.title && !ui.lite() && l.y + l.h + 54 * l.s < height - 2 * l.s)
			techTitle.draw(ui, cx, l.y + l.h + 54 * l.s, 31 * l.s, l.s, mx, my, down, pressed, dt);
		float ax = l.x - 5 * l.s, ay = l.y - 5 * l.s, bx = l.x + l.w + 5 * l.s, by = l.y + l.h + 5 * l.s;
		ui.line(ax, ay, ax + 14 * l.s, ay, .6f * l.s, 0x458C7C5E)
		  .line(ax, ay, ax, ay + 14 * l.s, .6f * l.s, 0x458C7C5E)
		  .line(bx - 14 * l.s, ay, bx, ay, .6f * l.s, 0x458C7C5E)
		  .line(bx, ay, bx, ay + 14 * l.s, .6f * l.s, 0x458C7C5E)
		  .line(ax, by - 14 * l.s, ax, by, .6f * l.s, 0x458C7C5E)
		  .line(ax, by, ax + 14 * l.s, by, .6f * l.s, 0x458C7C5E)
		  .line(bx - 14 * l.s, by, bx, by, .6f * l.s, 0x458C7C5E)
		  .line(bx, by - 14 * l.s, bx, by, .6f * l.s, 0x458C7C5E);
	}

	private void sidebar(UiCanvas ui, Layout l, int mx, int my, boolean down, boolean pressed, float dt) {
		float cy = l.y + 27 * l.s;
		float logoX = l.x + 14 * l.s;
		ui.roundedRect(logoX, l.y + 13 * l.s, 27 * l.s, 27 * l.s, 6 * l.s, accent())
		  .icon(logoX + 13.5f * l.s, cy, 15 * l.s, 0xFF291923, LOGO);

		float right = l.x + l.w - 14 * l.s;
		float themeX = right - 27 * l.s;
		float quickX = themeX - 88 * l.s;
		float availableLeft = logoX + 36 * l.s;
		float availableRight = quickX - 11 * l.s;
		float trackWidth = RubberSegment.width(categories.size(), l.s);
		float trackX = availableLeft + Math.max(0, availableRight - availableLeft - trackWidth) * .5f;
		float innerX = trackX + 2.5f * l.s;
		int next = categorySegment.draw(ui, categories, categoryIndex, innerX, cy, l.s, accent(), mx, my, down, pressed, dt);
		if (next != categoryIndex) {
			categoryIndex = next;
			prefs.category = next;
			settings.released();
			settings.closePopups();
			scrollOffset = scrollTarget = 0;
			save.run();
		}

		if (pressed && hit(mx, my, themeX, cy - 13 * l.s, 27 * l.s, 27 * l.s)) {
			int index = 0;
			for (int i = 0; i < GuiPrefs.ACCENTS.length; i++) if (GuiPrefs.ACCENTS[i] == accent()) index = i + 1;
			prefs.accent = GuiPrefs.ACCENTS[index % GuiPrefs.ACCENTS.length];
			save.run();
		}
		ui.roundedRect(themeX, cy - 13 * l.s, 27 * l.s, 27 * l.s, 6 * l.s, accent())
		  .icon(themeX + 13.5f * l.s, cy, 11 * l.s, 0xFF291923, 0xE40A);
		for (int i = 0; i < 3; i++)
			ui.roundedRect(quickX + i * 28 * l.s, cy - 13 * l.s, 25 * l.s, 27 * l.s, 5 * l.s, 0xFF171717)
			  .outlineRoundedRect(quickX + i * 28 * l.s, cy - 13 * l.s, 25 * l.s, 27 * l.s, 5 * l.s, .5f * l.s, 0xFF242424);
		if (pressed && hit(mx, my, quickX, cy - 13 * l.s, 25 * l.s, 27 * l.s)) toggleFavoritesOnly();
		if (pressed && hit(mx, my, quickX + 28 * l.s, cy - 13 * l.s, 25 * l.s, 27 * l.s)) { prefs.compact = !prefs.compact; save.run(); }
		if (pressed && hit(mx, my, quickX + 56 * l.s, cy - 13 * l.s, 25 * l.s, 27 * l.s)) searchFocused = true;
		ui.icon(quickX + 12.5f * l.s, cy, 10 * l.s, favoritesOnly ? accent() : MUTED, 0xE838)
		  .icon(quickX + 40.5f * l.s, cy, 10 * l.s, prefs.compact ? accent() : MUTED, 0xE871)
		  .icon(quickX + 68.5f * l.s, cy, 10 * l.s, searchFocused ? accent() : MUTED, 0xE8B6);
	}

	private boolean favoritesOnly;

	private void toggleFavoritesOnly() { favoritesOnly = !favoritesOnly; scrollOffset = scrollTarget = 0; }

	private void header(UiCanvas ui, Layout l, Category category, int mx, int my, boolean pressed) {
		float cy = l.y + 79 * l.s;
		ui.icon(l.mainX + 12 * l.s, cy, 21 * l.s, accent(), category.icon());
		ui.strongText(l.mainX + 31 * l.s, cy - 2 * l.s, 11 * l.s, WHITE, category.name());
		long enabled = category.modules().stream().filter(m -> m.enabled).count();
		float badgeX = l.mainX + 31 * l.s + ui.textWidth(category.name(), 11 * l.s, true) + 8 * l.s;
		String active = enabled + " " + Tr.get("active"), total = category.modules().size() + " " + Tr.get("total");
		float aw = ui.textWidth(active, 7 * l.s, true) + 12 * l.s, tw = ui.textWidth(total, 7 * l.s, false) + 12 * l.s;
		ui.roundedRect(badgeX, cy - 10 * l.s, aw, 13 * l.s, 4 * l.s, Colors.mix(0xFF1A1A1A, accent(), .16f))
		  .centeredText(badgeX + aw / 2, cy - 3.5f * l.s, 7 * l.s, accent(), active, true);
		ui.roundedRect(badgeX + aw + 3 * l.s, cy - 10 * l.s, tw, 13 * l.s, 4 * l.s, 0xFF242424)
		  .centeredText(badgeX + aw + 3 * l.s + tw / 2, cy - 3.5f * l.s, 7 * l.s, TEXT, total, false);
		float searchW = 160 * l.s, searchX = l.x + l.w - 16 * l.s - searchW, searchY = l.y + 80 * l.s;
		float sortX = searchX - 104 * l.s, sortW = 98 * l.s;
		float allX = sortX - 59 * l.s;
		ui.text(l.mainX + 31 * l.s, cy + 12 * l.s, 7.6f * l.s, MUTED, ui.fit(category.description(), 7.6f * l.s, false, allX - l.mainX - 40 * l.s));
		if (pressed && my > l.y + 52 * l.s) searchFocused = hit(mx, my, searchX, searchY - 12 * l.s, searchW, 24 * l.s);
		ui.roundedRect(searchX, searchY - 12 * l.s, searchW, 24 * l.s, 5 * l.s, 0xFF181818)
		  .outlineRoundedRect(searchX, searchY - 12 * l.s, searchW, 24 * l.s, 5 * l.s, .6f * l.s, searchFocused ? accent() : 0xFF272727)
		  .icon(searchX + searchW - 11 * l.s, searchY, 10 * l.s, MUTED, 0xE8B6);
		String shown = search.isEmpty() ? Tr.get("search") : search;
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
		ui.text(searchX + 9 * l.s, searchY, 7.7f * l.s, search.isEmpty() ? MUTED : TEXT, ui.fit(shown, 7.7f * l.s, false, searchW - 26 * l.s));
		if (pressed && hit(mx, my, sortX, searchY - 12 * l.s, sortW, 24 * l.s)) { prefs.activeFirst = !prefs.activeFirst; save.run(); }
		ui.roundedRect(sortX, searchY - 12 * l.s, sortW, 24 * l.s, 5 * l.s, 0xFF181818)
		  .outlineRoundedRect(sortX, searchY - 12 * l.s, sortW, 24 * l.s, 5 * l.s, .6f * l.s, 0xFF272727)
		  .text(sortX + 9 * l.s, searchY, 7.7f * l.s, TEXT, ui.fit(prefs.activeFirst ? Tr.get("active_first") : Tr.get("default_order"), 7.7f * l.s, false, sortW - 26 * l.s))
		  .icon(sortX + sortW - 10 * l.s, searchY, 8 * l.s, MUTED, 0xE5CF);
		if (pressed && hit(mx, my, allX, searchY - 12 * l.s, 24 * l.s, 24 * l.s)) {
			boolean anyCollapsed = category.modules().stream().anyMatch(m -> !m.settings.isEmpty() && !expandedModules.contains(m));
			for (Module m : category.modules()) if (!m.settings.isEmpty()) {
				if (anyCollapsed) expandedModules.add(m);
				else expandedModules.remove(m);
			}
			settings.closePopups();
		}
		if (pressed && hit(mx, my, allX + 29 * l.s, searchY - 12 * l.s, 24 * l.s, 24 * l.s)) toggleFavoritesOnly();
		ui.roundedRect(allX, searchY - 12 * l.s, 24 * l.s, 24 * l.s, 5 * l.s, accent())
		  .icon(allX + 12 * l.s, searchY, 10 * l.s, 0xFF221B11, 0xE8B8)
		  .roundedRect(allX + 29 * l.s, searchY - 12 * l.s, 24 * l.s, 24 * l.s, 5 * l.s, 0xFF181818)
		  .outlineRoundedRect(allX + 29 * l.s, searchY - 12 * l.s, 24 * l.s, 24 * l.s, 5 * l.s, .6f * l.s, 0xFF272727)
		  .icon(allX + 41 * l.s, searchY, 10 * l.s, favoritesOnly ? accent() : MUTED, 0xE838);
	}

	private void moduleGrid(UiCanvas ui, Layout l, Category category, int mx, int my, boolean down, boolean pressed, boolean rightPressed, float dt) {
		List<Module> visible = new ArrayList<>();
		String query = search.toLowerCase(Locale.ROOT).trim();
		if (query.isEmpty()) visible.addAll(category.modules());
		else for (Category source : categories) for (Module m : source.modules())
			if (m.name.toLowerCase(Locale.ROOT).contains(query) || m.description.toLowerCase(Locale.ROOT).contains(query)) visible.add(m);
		if (favoritesOnly) visible.removeIf(m -> !favorites.contains(m.id));
		if (prefs.activeFirst) visible.sort((a, b) -> Boolean.compare(b.enabled, a.enabled));
		int columns = prefs.compact ? 2 : 3;
		float gap = 7 * l.s, columnW = (l.mainW - gap * (columns - 1)) / columns;
		scrollOffset += (scrollTarget - scrollOffset) * Math.min(1, dt * 18);
		float viewportTop = l.y + 119 * l.s, viewportBottom = l.y + l.h - 12 * l.s;
		float[] ys = new float[columns];
		for (int i = 0; i < columns; i++) ys[i] = viewportTop + 5 * l.s - scrollOffset;
		ui.pushScissor(l.mainX, l.y + 119 * l.s, l.mainW, l.h - 131 * l.s);
		SettingsPanel.Bounds bounds = new SettingsPanel.Bounds(l.x, l.y, l.w, l.h, l.mainX, l.mainW, l.s);
		boolean pointerInView = hit(mx, my, l.mainX, viewportTop, l.mainW, viewportBottom - viewportTop);
		for (int i = 0; i < visible.size(); i++) {
			Module m = visible.get(i);
			int column = i % columns;
			if (i == 0) {
				firstCardX = l.mainX + 40 * l.s;
				firstCardY = ys[0] + 16 * l.s;
			}
			float x = l.mainX + column * (columnW + gap), y = ys[column];
			float baseH = CardRenderer.baseHeight(m, l.s), settingsH = expandedModules.contains(m) ? SettingsPanel.height(m, l.s) : 0;
			float cardH = baseH + settingsH;
			boolean onScreen = y + cardH > viewportTop - 2 && y < viewportBottom + 2;
			boolean over = pointerInView && hit(mx, my, x, y, columnW, 33 * l.s);
			boolean star = pressed && over && hit(mx, my, x + 7 * l.s, y + 6 * l.s, 19 * l.s, 20 * l.s);
			boolean settingsButton = pressed && over && !m.settings.isEmpty() && hit(mx, my, x + columnW - 45 * l.s, y + 5 * l.s, 17 * l.s, 22 * l.s);
			boolean bindButton = pressed && pointerInView && hit(mx, my, x + columnW - 44 * l.s, y + 42 * l.s, 34 * l.s, 15 * l.s);
			if (bindButton) {
				bindingModule = bindingModule == m ? null : m;
				searchFocused = false;
			}
			if (star) {
				if (!favorites.add(m.id)) favorites.remove(m.id);
				prefs.favorites = new ArrayList<>(favorites);
				save.run();
			} else if (settingsButton) {
				if (!expandedModules.add(m)) expandedModules.remove(m);
				settings.closePopups();
			} else if (pressed && over) {
				m.toggle();
				save.run();
			}
			if (rightPressed && over && !m.settings.isEmpty()) {
				if (!expandedModules.add(m)) expandedModules.remove(m);
				settings.closePopups();
			}
			if (onScreen || expandedModules.contains(m)) {
				CardRenderer.draw(ui, m, x, y, columnW, cardH, l.s, accent(), favorites.contains(m.id), expandedModules.contains(m), over,
						binds.label(m.id()), bindingModule == m, dt);
				if (expandedModules.contains(m))
					settings.drawSettings(ui, bounds, m, x + 10 * l.s, y + baseH + 4 * l.s, columnW - 20 * l.s,
							pointerInView ? mx : -100000, pointerInView ? my : -100000, down && pointerInView, pressed && pointerInView, dt);
			} else {
				m.animation.update(dt, 14);
			}
			ys[column] += cardH + 7 * l.s;
		}
		float contentBottom = ys[0];
		for (int i = 1; i < columns; i++) contentBottom = Math.max(contentBottom, ys[i]);
		maxScroll = Math.max(0, contentBottom + scrollOffset - viewportBottom);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		if (visible.isEmpty()) ui.centeredText(l.mainX + l.mainW / 2, l.y + l.h / 2 + 50 * l.s, 10 * l.s, MUTED,
				favoritesOnly ? Tr.get("no_starred") : Tr.get("no_modules"), false);
		ui.popScissor();
		if (maxScroll > 1) {
			float trackH = viewportBottom - viewportTop;
			float thumbH = Math.max(18 * l.s, trackH * trackH / (trackH + maxScroll));
			float barX = l.mainX + l.mainW + 5 * l.s;
			ui.roundedRect(barX, viewportTop, 2.3f * l.s, trackH, 1.15f * l.s, 0xFF313333);
			ui.roundedRect(barX, viewportTop + (trackH - thumbH) * scrollOffset / maxScroll, 2.3f * l.s, thumbH, 1.15f * l.s, 0xFFB4B7B6);
		}
	}

	private static boolean hit(float mx, float my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	// ------------------------------------------------------------------ input

	public boolean charTyped(String chars) {
		if (searchFocused && search.length() < 28) {
			search += chars;
			scrollOffset = scrollTarget = 0;
			return true;
		}
		return false;
	}

	/** @return true when the key was consumed. */
	public boolean keyPressed(int key) {
		if (bindingModule != null) {
			Module target = bindingModule;
			bindingModule = null;
			if (key == Binds.KEY_ESCAPE) return true;
			binds.bind(target.id(), key == Binds.KEY_BACKSPACE || key == Binds.KEY_DELETE ? Binds.KEY_UNKNOWN : key);
			return true;
		}
		if (searchFocused) {
			if (key == Binds.KEY_BACKSPACE) {
				if (!search.isEmpty()) search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
				scrollOffset = scrollTarget = 0;
				return true;
			}
			if (key == 257 || key == 335 || key == Binds.KEY_ESCAPE) {
				searchFocused = false;
				return true;
			}
			return key != Binds.KEY_RIGHT_SHIFT; // typing goes to the search box, not to game keys
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		Layout l = Layout.create(width, height, prefs.scale / 100f, gs);
		if (hit((float) mouseX, (float) mouseY, l.mainX, l.y + 119 * l.s, l.mainW, l.h - 131 * l.s)) {
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) amount * 36 * l.s));
			settings.closePopups();
			return true;
		}
		return false;
	}

	private record Layout(float x, float y, float w, float h, float s, float side, float mainX, float mainW) {
		/** The panel is sized in real screen pixels: up to 1.45 px per design unit, so text stays readable at any GUI scale. */
		static Layout create(float sw, float sh, float userScale, float gs) {
			float designW = 900, designH = 550, margin = 14;
			float fitScale = Math.min((sw - margin * 2) / designW, (sh - margin * 2) / designH);
			float s = Math.max(.10f, Math.min(1.45f * userScale / gs, fitScale));
			float w = designW * s, h = designH * s;
			float x = (sw - w) / 2, y = (sh - h) / 2, side = 0, mainX = x + 17 * s;
			return new Layout(x, y, w, h, s, side, mainX, x + w - mainX - 17 * s);
		}
	}
}
