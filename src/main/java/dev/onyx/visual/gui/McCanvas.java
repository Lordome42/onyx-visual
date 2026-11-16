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

	/** Per-frame statistics for the perf log (reset by the screen each frame). */
	public static int statFills, statTexts, statBlits;
	private final GuiGraphicsExtractor g;
	private final Font font;
	private final float gs;
	private final boolean lite;
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

	public McCanvas(GuiGraphicsExtractor g, Font font, float guiScale, boolean lite) {
		this.g = g;
		this.font = font;
		this.gs = guiScale;
		this.lite = lite;
	}

	@Override
	public boolean lite() { return lite; }

	@Override
	public float pixelScale() { return gs; }

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
		statFills++;
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

	/** Number of steps used to approximate one rounded corner (every step is a single fill). */
	private int cornerBands(int rr) {
		return Math.max(1, Math.min(rr, lite ? 1 : Math.min(6, 2 + rr / 5)));
	}

	/** Group boundaries along an axis of n pixels: corner steps at both ends plus optional uniform steps. */
	private int[] bounds(int n, int rr, int steps) {
		java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
		set.add(0);
		set.add(n);
		int nb = cornerBands(rr);
		for (int k = 1; k <= nb && rr > 0; k++) {
			int c = Math.round(rr * k / (float) nb);
			if (c > 0 && c < n) set.add(c);
			if (n - c > 0 && n - c < n) set.add(n - c);
		}
		for (int k = 1; k < steps; k++) set.add(Math.round(n * k / (float) steps));
		int[] out = new int[set.size()];
		int i = 0;
		for (int v : set) out[i++] = v;
		return out;
	}

	private void roundedPx(float x, float y, float w, float h, float r, int color) {
		int top = Math.round(y), bottom = Math.round(y + h);
		int n = bottom - top;
		if (n <= 0 || w <= 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, n) / 2f));
		int rr = Math.min((int) Math.ceil(r), n / 2);
		int ix0 = Math.round(x), ix1 = Math.round(x + w);
		if (rr == 0) { fillPx(ix0, top, ix1, bottom, color); return; }
		int[] b = bounds(n, rr, 0);
		for (int i = 0; i + 1 < b.length; i++) {
			float c = (b[i] + b[i + 1]) / 2f;
			float d = Math.min(c, n - c);
			float in = d < r ? inset(r, r - d) : 0;
			fillPx(Math.round(x + in), top + b[i], Math.round(x + w - in), top + b[i + 1], color);
		}
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
		int n = bottom - top;
		if (n <= 0 || W <= 0) return this;
		R = Math.max(0, Math.min(R, Math.min(W, n) / 2f));
		float iR = Math.max(0, R - S);
		int is = Math.max(1, Math.round(S));
		if (lite) { // four plain strips
			int x0 = Math.round(X), x1 = Math.round(X + W);
			fillPx(x0, top, x1, top + is, color);
			fillPx(x0, bottom - is, x1, bottom, color);
			fillPx(x0, top + is, x0 + is, bottom - is, color);
			fillPx(x1 - is, top + is, x1, bottom - is, color);
			return this;
		}
		int rr = Math.min((int) Math.ceil(R), n / 2);
		int T = Math.min(Math.max(rr, is), n / 2);
		int sx = Math.max(1, Math.round(S));
		java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
		set.add(0);
		set.add(T);
		int nb = cornerBands(rr);
		for (int k = 1; k <= nb && rr > 0; k++) set.add(Math.min(T, Math.round(rr * k / (float) nb)));
		set.add(Math.min(T, is));
		Integer[] b = set.toArray(new Integer[0]);
		for (int i = 0; i + 1 < b.length; i++) {
			int a0 = b[i], a1 = b[i + 1];
			if (a1 <= a0) continue;
			float c = (a0 + a1) / 2f;
			float out = c < R ? inset(R, R - c) : 0;
			float xl = X + out, xr = X + W - out;
			boolean full = a1 <= is;
			float in = 0;
			if (!full) {
				float di = c - S;
				in = di < iR ? inset(iR, iR - di) : 0;
			}
			for (int side = 0; side < 2; side++) {
				int y0 = side == 0 ? top + a0 : bottom - a1, y1 = side == 0 ? top + a1 : bottom - a0;
				if (full) fillPx(Math.round(xl), y0, Math.round(xr), y1, color);
				else {
					fillPx(Math.round(xl), y0, Math.round(X + S + in), y1, color);
					fillPx(Math.round(X + W - S - in), y0, Math.round(xr), y1, color);
				}
			}
		}
		if (n - 2 * T > 0) {
			fillPx(Math.round(X), top + T, Math.round(X) + sx, bottom - T, color);
			fillPx(Math.round(X + W) - sx, top + T, Math.round(X + W), bottom - T, color);
		}
		return this;
	}

	@Override
	public UiCanvas gradientRoundedRect(float x, float y, float w, float h, float r, int from, int to, boolean horizontal) {
		float X = x * gs, Y = y * gs, W = w * gs, H = h * gs, R = Math.min(r * gs, Math.min(W, H) / 2f);
		if (W <= 0 || H <= 0) return this;
		int steps = lite ? 3 : 12;
		if (!horizontal) {
			int top = Math.round(Y), bottom = Math.round(Y + H), n = bottom - top;
			if (n <= 0) return this;
			int[] b = bounds(n, Math.min((int) Math.ceil(R), n / 2), steps);
			for (int i = 0; i + 1 < b.length; i++) {
				float c = (b[i] + b[i + 1]) / 2f;
				float d = Math.min(c, n - c);
				float in = d < R ? inset(R, R - d) : 0;
				fillPx(Math.round(X + in), top + b[i], Math.round(X + W - in), top + b[i + 1], Colors.mix(from, to, c / n));
			}
		} else {
			int left = Math.round(X), right = Math.round(X + W), n = right - left;
			if (n <= 0) return this;
			int[] b = bounds(n, Math.min((int) Math.ceil(R), n / 2), steps);
			for (int i = 0; i + 1 < b.length; i++) {
				float c = (b[i] + b[i + 1]) / 2f;
				float d = Math.min(c, n - c);
				float in = d < R ? inset(R, R - d) : 0;
				fillPx(left + b[i], Math.round(Y + in), left + b[i + 1], Math.round(Y + H - in), Colors.mix(from, to, c / n));
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
		int n = bottom - top;
		if (n <= 0) return this;
		int groups = Math.min(n, lite ? 3 : 6);
		for (int gi = 0; gi < groups; gi++) {
			int r0 = top + Math.round(n * gi / (float) groups), r1 = top + Math.round(n * (gi + 1) / (float) groups);
			if (r1 <= r0) continue;
			float yy = (r0 + r1) / 2f;
			float xa = edge(p[0], p[2], yy);
			float xb = yy < p[1][1] ? edge(p[0], p[1], yy) : edge(p[1], p[2], yy);
			fillPx(Math.round(Math.min(xa, xb)), r0, Math.round(Math.max(xa, xb)), r1, color);
		}
		return this;
	}

	private static float edge(float[] a, float[] b, float y) {
		if (Math.abs(b[1] - a[1]) < 1e-4f) return a[0];
		return a[0] + (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]);
	}

	// ------------------------------------------------------------------ text

	/** Font sizes that exist in the jar: every pixel up to 24, then a few larger steps (keeps the font count low). */
	static final int[] SIZES = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56, 64};

	/** Letters are drawn a bit larger than the design size: the menu was too small to read comfortably. */
	static final float TEXT_BOOST = 1.22f;

	private static int pixelSize(float guiSize, float gs) { return pixelSize(guiSize, gs, true); }

	private static int pixelSize(float guiSize, float gs, boolean text) {
		int want = Math.max(MIN_PX, Math.min(MAX_PX, Math.round(guiSize * gs * (text ? TEXT_BOOST : 1f))));
		int best = SIZES[0];
		for (int s : SIZES) if (Math.abs(s - want) < Math.abs(best - want)) best = s;
		return best;
	}

	private void drawText(int kind, float x, float cy, float size, int color, String text) {
		if (text.isEmpty() || (color >>> 24) < 5) return;
		boolean icon = kind == KIND_ICON;
		int P = pixelSize(size, gs, !icon);
		float baseline = icon ? cy * gs + P / 2f : cy * gs + P * CAP_HEIGHT / 2f;
		// Whole pixels only: fractional text positions are what made the text blurry.
		int px = Math.round(icon ? x * gs : x * gs);
		int py = Math.round(baseline) - 7; // the font baseline sits 7 units below a glyph's top
		statTexts++;
		g.text(font, component(kind, P, text), px, py, color, false);
	}

	@Override
	public UiCanvas icon(float cx, float cy, float size, int color, int codepoint) {
		int P = pixelSize(size, gs, false);
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

	// ------------------------------------------------------------------ pixel buffer

	private static final net.minecraft.resources.Identifier BG_ID = OnyxVisual.id("background");
	private static BgTexture bgTex;
	private static int bgW, bgH;
	private static boolean bgFailed;

	/** Dynamic texture with a smooth (linear) sampler so the low-resolution backdrop is stretched softly. */
	private static final class BgTexture extends net.minecraft.client.renderer.texture.DynamicTexture {
		BgTexture(int w, int h) {
			super("onyxvisual_background", w, h, true);
			this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
		}
	}

	@Override
	public boolean blitPixels(int[] argb, int tw, int th, boolean changed, float x, float y, float w, float h) {
		if (bgFailed) return false;
		try {
			if (bgTex == null || bgW != tw || bgH != th) {
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				if (bgTex != null) mc.getTextureManager().release(BG_ID);
				bgTex = new BgTexture(tw, th);
				bgW = tw;
				bgH = th;
				mc.getTextureManager().register(BG_ID, bgTex);
				changed = true;
			}
			if (changed) {
				com.mojang.blaze3d.platform.NativeImage img = bgTex.getPixels();
				java.nio.IntBuffer dst = org.lwjgl.system.MemoryUtil.memIntBuffer(img.getPointer(), tw * th);
				for (int i = 0, n = tw * th; i < n; i++) {
					int v = argb[i];
					dst.put(i, (v & 0xFF00FF00) | ((v >> 16) & 0xFF) | ((v & 0xFF) << 16)); // ARGB -> ABGR
				}
				bgTex.upload();
			}
			statBlits++;
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BG_ID, Math.round(x * gs), Math.round(y * gs), 0f, 0f,
					Math.round(w * gs), Math.round(h * gs), tw, th, tw, th);
			return true;
		} catch (Throwable t) {
			OnyxVisual.LOGGER.warn("Background texture failed, using flat backdrop", t);
			bgTex = null;
			bgFailed = true;
			return false;
		}
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
