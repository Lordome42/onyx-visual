package dev.onyx.visual.gui;

import dev.onyx.visual.OnyxVisual;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/**
 * Draws the ClickGUI with plain {@code fill} calls in real framebuffer pixels (the pose is scaled by
 * 1/guiScale), so rounded corners, circles and thin lines get anti-aliased edges instead of the
 * blocky GUI-pixel grid. Text and icons use TrueType fonts shipped in the jar, picked by size bucket.
 */
public final class McCanvas implements UiCanvas {
	/** Fonts exist for every whole pixel size from MIN_PX to MAX_PX: ui_N, bold_N, icons_N (1 font pixel = 1 screen pixel). */
	static final int MIN_PX = 6, MAX_PX = 64;
	private static final float CAP_HEIGHT = 0.7275f; // Inter cap height / em
	private static final int KIND_UI = 0, KIND_BOLD = 1, KIND_ICON = 2;
	private static final String[] KIND_NAME = {"ui_", "bold_", "icons_"};
	private static final Style[][] STYLES = new Style[3][MAX_PX + 1];

	private record Key(int kind, int px, String text) {}
	private static final java.util.HashMap<Key, Component> COMPONENTS = new java.util.HashMap<>();
	private static final java.util.HashMap<Key, Integer> WIDTHS = new java.util.HashMap<>();
	private static final java.util.HashMap<String, String> FITS = new java.util.HashMap<>();

	private final GuiGraphicsExtractor g;
	private final Font font;
	private final float gs;
	private int scissorDepth;

	private static Style style(int kind, int px) {
		Style st = STYLES[kind][px];
		if (st == null) {
			st = Style.EMPTY.withFont(new FontDescription.Resource(OnyxVisual.id(KIND_NAME[kind] + px)));
			STYLES[kind][px] = st;
		}
		return st;
	}

	private static Component component(int kind, int px, String text) {
		if (COMPONENTS.size() > 6000) COMPONENTS.clear();
		return COMPONENTS.computeIfAbsent(new Key(kind, px, text), k -> Component.literal(text).withStyle(style(kind, px)));
	}

	public McCanvas(GuiGraphicsExtractor g, Font font, float guiScale) {
		this.g = g;
		this.font = font;
		this.gs = guiScale;
	}

	public void begin() {
		g.pose().pushMatrix();
		g.pose().scale(1 / gs, 1 / gs);
	}

	public void end() {
		while (scissorDepth > 0) popScissor();
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ primitives (pixel space)

	private void fillPx(int x0, int y0, int x1, int y1, int color) {
		if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) return;
		g.fill(x0, y0, x1, y1, color);
	}

	/** Fills one pixel row between two fractional x positions with coverage-based edge pixels. */
	private void span(int y, float xl, float xr, int color) {
		if (xr - xl <= 0.01f) return;
		int il = (int) Math.ceil(xl), ir = (int) Math.floor(xr);
		if (il > ir) { // span inside a single pixel
			fillPx(ir, y, ir + 1, y + 1, Colors.fade(color, xr - xl));
			return;
		}
		if (il - xl > 0.02f) fillPx(il - 1, y, il, y + 1, Colors.fade(color, il - xl));
		fillPx(il, y, ir, y + 1, color);
		if (xr - ir > 0.02f) fillPx(ir, y, ir + 1, y + 1, Colors.fade(color, xr - ir));
	}

	private static float inset(float r, float dy) {
		// Horizontal inset of a circle of radius r at vertical distance dy from its centre row.
		if (dy >= r) return r;
		return r - (float) Math.sqrt(r * r - dy * dy);
	}

	private void roundedPx(float x, float y, float w, float h, float r, int color) {
		int top = Math.round(y), bottom = Math.round(y + h);
		if (bottom <= top || w <= 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, bottom - top) / 2f));
		int rr = (int) Math.ceil(r);
		if (rr == 0) { span0(top, bottom, x, x + w, color); return; }
		for (int j = 0; j < rr && top + j < bottom - j; j++) {
			float dy = r - (j + 0.5f);
			float in = dy > 0 ? inset(r, dy) : 0;
			span(top + j, x + in, x + w - in, color);
			if (bottom - 1 - j != top + j) span(bottom - 1 - j, x + in, x + w - in, color);
		}
		if (top + rr < bottom - rr) span0(top + rr, bottom - rr, x, x + w, color);
	}

	/** Several identical rows at once. */
	private void span0(int y0, int y1, float xl, float xr, int color) {
		int il = (int) Math.ceil(xl), ir = (int) Math.floor(xr);
		if (il > ir) { fillPx(ir, y0, ir + 1, y1, Colors.fade(color, xr - xl)); return; }
		if (il - xl > 0.02f) fillPx(il - 1, y0, il, y1, Colors.fade(color, il - xl));
		fillPx(il, y0, ir, y1, color);
		if (xr - ir > 0.02f) fillPx(ir, y0, ir + 1, y1, Colors.fade(color, xr - ir));
	}

	@Override
	public UiCanvas rect(float x, float y, float w, float h, int color) {
		span0(Math.round(y * gs), Math.round((y + h) * gs), x * gs, (x + w) * gs, color);
		return this;
	}

	@Override
	public UiCanvas roundedRect(float x, float y, float w, float h, float r, int color) {
		roundedPx(x * gs, y * gs, w * gs, h * gs, r * gs, color);
		return this;
	}

	@Override
	public UiCanvas outlineRoundedRect(float x, float y, float w, float h, float r, float stroke, int color) {
		float X = x * gs, Y = y * gs, W = w * gs, H = h * gs, R = r * gs, S = stroke * gs;
		if (S < 1) { color = Colors.fade(color, S); S = 1; }
		int top = Math.round(Y), bottom = Math.round(Y + H);
		if (bottom <= top) return this;
		R = Math.max(0, Math.min(R, Math.min(W, bottom - top) / 2f));
		float iR = Math.max(0, R - S);
		int is = Math.max(1, Math.round(S));
		int rr = (int) Math.ceil(R);
		int mid0 = top + Math.max(rr, is), mid1 = bottom - Math.max(rr, is);
		for (int row = top; row < bottom; row++) {
			if (row >= mid0 && row < mid1) {
				// straight section: both side strips in one call each, for all remaining rows
				span0(row, mid1, X, X + S, color);
				span0(row, mid1, X + W - S, X + W, color);
				row = mid1 - 1;
				continue;
			}
			float cyTop = row + 0.5f - top, cyBot = bottom - (row + 0.5f);
			float d = Math.min(cyTop, cyBot);
			float out = d < R ? inset(R, R - d) : 0;
			float ol = X + out, or = X + W - out;
			if (row < top + is || row >= bottom - is) {
				span(row, ol, or, color);
				continue;
			}
			float di = d - S;
			float in = di < iR ? inset(iR, iR - di) : 0;
			span(row, ol, X + S + in, color);
			span(row, X + W - S - in, or, color);
		}
		return this;
	}

	@Override
	public UiCanvas gradientRoundedRect(float x, float y, float w, float h, float r, int from, int to, boolean horizontal) {
		float X = x * gs, Y = y * gs, W = w * gs, H = h * gs, R = Math.min(r * gs, Math.min(W, H) / 2f);
		if (W <= 0 || H <= 0) return this;
		if (!horizontal) {
			int top = Math.round(Y), bottom = Math.round(Y + H);
			for (int row = top; row < bottom; row++) {
				float t = (row + 0.5f - top) / Math.max(1, bottom - top);
				float d = Math.min(row + 0.5f - top, bottom - (row + 0.5f));
				float in = d < R ? inset(R, R - d) : 0;
				span(row, X + in, X + W - in, Colors.mix(from, to, t));
			}
		} else {
			int left = (int) Math.floor(X), right = (int) Math.ceil(X + W);
			for (int col = left; col < right; col++) {
				float t = (col + 0.5f - X) / W;
				float d = Math.min(col + 0.5f - X, X + W - (col + 0.5f));
				float in = d < R ? inset(R, R - Math.max(0, d)) : 0;
				float cover = Math.min(col + 1, X + W) - Math.max(col, X);
				int c = Colors.mix(from, to, t);
				if (cover < 0.98f) c = Colors.fade(c, cover);
				fillPx(col, Math.round(Y + in), col + 1, Math.round(Y + H - in), c);
			}
		}
		return this;
	}

	@Override
	public UiCanvas line(float x1, float y1, float x2, float y2, float stroke, int color) {
		float S = stroke * gs;
		if (S < 1) { color = Colors.fade(color, S); S = 1; }
		float X1 = x1 * gs, Y1 = y1 * gs, X2 = x2 * gs, Y2 = y2 * gs;
		if (Math.abs(Y1 - Y2) < 0.01f) {
			int yy = Math.round(Y1 - S / 2);
			span0(yy, yy + Math.max(1, Math.round(S)), Math.min(X1, X2), Math.max(X1, X2), color);
		} else if (Math.abs(X1 - X2) < 0.01f) {
			float xl = X1 - S / 2;
			span0(Math.round(Math.min(Y1, Y2)), Math.round(Math.max(Y1, Y2)), xl, xl + S, color);
		} else {
			float len = (float) Math.hypot(X2 - X1, Y2 - Y1);
			g.pose().pushMatrix();
			g.pose().translate(X1, Y1);
			g.pose().rotate((float) Math.atan2(Y2 - Y1, X2 - X1));
			int half = Math.max(1, Math.round(S)) ;
			fillPx(0, -half / 2, Math.round(len), -half / 2 + half, color);
			g.pose().popMatrix();
		}
		return this;
	}

	@Override
	public UiCanvas circle(float cx, float cy, float r, int color) {
		float R = r * gs;
		if (R <= 0) return this;
		if (R < 0.8f) {
			fillPx(Math.round(cx * gs - .5f), Math.round(cy * gs - .5f), Math.round(cx * gs + .5f), Math.round(cy * gs + .5f), Colors.fade(color, R / .8f));
			return this;
		}
		return roundedRectPx(cx * gs - R, cy * gs - R, 2 * R, 2 * R, R, color);
	}

	private UiCanvas roundedRectPx(float x, float y, float w, float h, float r, int color) {
		roundedPx(x, y, w, h, r, color);
		return this;
	}

	@Override
	public UiCanvas triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
		float[][] p = {{x1 * gs, y1 * gs}, {x2 * gs, y2 * gs}, {x3 * gs, y3 * gs}};
		java.util.Arrays.sort(p, (a, b) -> Float.compare(a[1], b[1]));
		int top = Math.round(p[0][1]), bottom = Math.round(p[2][1]);
		for (int row = top; row < bottom; row++) {
			float yy = row + 0.5f;
			float xa = edge(p[0], p[2], yy);
			float xb = yy < p[1][1] ? edge(p[0], p[1], yy) : edge(p[1], p[2], yy);
			span(row, Math.min(xa, xb), Math.max(xa, xb), color);
		}
		return this;
	}

	private static float edge(float[] a, float[] b, float y) {
		if (Math.abs(b[1] - a[1]) < 1e-4f) return a[0];
		return a[0] + (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]);
	}

	// ------------------------------------------------------------------ text

	private static int pixelSize(float guiSize, float gs) {
		return Math.max(MIN_PX, Math.min(MAX_PX, Math.round(guiSize * gs)));
	}

	private void drawText(int kind, float x, float cy, float size, int color, String text) {
		if (text.isEmpty() || (color >>> 24) < 5) return;
		int P = pixelSize(size, gs);
		boolean icon = kind == KIND_ICON;
		float baseline = icon ? cy * gs + P / 2f : cy * gs + P * CAP_HEIGHT / 2f;
		// Whole pixels only: fractional text positions are what made the text blurry.
		int px = Math.round(icon ? x * gs : x * gs);
		int py = Math.round(baseline) - 7; // the font baseline sits 7 units below a glyph's top
		g.text(font, component(kind, P, text), px, py, color, false);
	}

	@Override
	public UiCanvas icon(float cx, float cy, float size, int color, int codepoint) {
		int P = pixelSize(size, gs);
		drawText(KIND_ICON, cx - (P / gs) / 2f, cy, size, color, new String(Character.toChars(codepoint)));
		return this;
	}

	@Override
	public UiCanvas text(float x, float cy, float size, int color, String text) {
		drawText(KIND_UI, x, cy, size, color, text);
		return this;
	}

	@Override
	public UiCanvas strongText(float x, float cy, float size, int color, String text) {
		drawText(KIND_BOLD, x, cy, size, color, text);
		return this;
	}

	@Override
	public float textWidth(String text, float size, boolean strong) {
		if (text.isEmpty()) return 0;
		int P = pixelSize(size, gs);
		int kind = strong ? KIND_BOLD : KIND_UI;
		if (WIDTHS.size() > 6000) WIDTHS.clear();
		Key key = new Key(kind, P, text);
		Integer w = WIDTHS.get(key);
		if (w == null) {
			w = font.width(component(kind, P, text));
			WIDTHS.put(key, w);
		}
		return w / gs;
	}

	@Override
	public String fit(String text, float size, boolean strong, float maxWidth) {
		String k = pixelSize(size, gs) + (strong ? "b" : "u") + Math.round(maxWidth * gs) + ":" + text;
		String hit = FITS.get(k);
		if (hit != null) return hit;
		if (FITS.size() > 3000) FITS.clear();
		String out = UiCanvas.super.fit(text, size, strong, maxWidth);
		FITS.put(k, out);
		return out;
	}

	@Override
	public UiCanvas coarseShape(int shape, float cx, float cy, float r, int color) {
		float X = cx * gs, Y = cy * gs, R = r * gs;
		switch (shape) {
			case 0 -> fillPx(Math.round(X - R), Math.round(Y - R), Math.round(X + R), Math.round(Y + R), color);
			case 1 -> {
				fillPx(Math.round(X - R), Math.round(Y - R * .5f), Math.round(X + R), Math.round(Y + R * .5f), color);
				fillPx(Math.round(X - R * .6f), Math.round(Y - R), Math.round(X + R * .6f), Math.round(Y - R * .5f), color);
				fillPx(Math.round(X - R * .6f), Math.round(Y + R * .5f), Math.round(X + R * .6f), Math.round(Y + R), color);
			}
			default -> {
				fillPx(Math.round(X - R * .25f), Math.round(Y - R), Math.round(X + R * .25f), Math.round(Y - R * .33f), color);
				fillPx(Math.round(X - R * .62f), Math.round(Y - R * .33f), Math.round(X + R * .62f), Math.round(Y + R * .33f), color);
				fillPx(Math.round(X - R), Math.round(Y + R * .33f), Math.round(X + R), Math.round(Y + R), color);
			}
		}
		return this;
	}

	// ------------------------------------------------------------------ scissor

	@Override
	public void pushScissor(float x, float y, float w, float h) {
		g.pose().popMatrix();
		g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
		g.pose().pushMatrix();
		g.pose().scale(1 / gs, 1 / gs);
		scissorDepth++;
	}

	@Override
	public void popScissor() {
		if (scissorDepth == 0) return;
		g.pose().popMatrix();
		g.disableScissor();
		g.pose().pushMatrix();
		g.pose().scale(1 / gs, 1 / gs);
		scissorDepth--;
	}
}
