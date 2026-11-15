package dev.onyx.visual.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Drawing helpers shared by the on-screen widgets: rounded panels, colour maths and free-angle lines. */
final class Ui {
	static final int ACCENT = 0xFF5B7BF5;
	static final int PANEL = 0xB8101012;
	static final int RING = 0x30FFFFFF;
	static final int TEXT = 0xFFEDEDEF;
	static final int DIM = 0xFF9A9AA4;

	private Ui() {
	}

	/** Multiplies a colour's alpha. */
	static int a(int argb, float alpha) {
		int a = (int) ((argb >>> 24) * Mth.clamp(alpha, 0.0f, 1.0f));
		return a << 24 | argb & 0xFFFFFF;
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
				| Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}

	/** A filled rectangle with rounded corners. */
	static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		int r = Math.min(radius, Math.min(w, h) / 2);
		for (int i = 0; i < r; i++) {
			float d = r - i - 0.5f;
			int inset = Math.round(r - (float) Math.sqrt(r * r - d * d));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, argb);
		}
		g.fill(x, y + r, x + w, y + h - r, argb);
	}

	/** The standard dark glass panel with a faint rim. */
	static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		round(g, x - 1, y - 1, w + 2, h + 2, 5, a(RING, alpha));
		round(g, x, y, w, h, 4, a(PANEL, alpha));
	}

	/** A straight line at any angle, drawn as a rotated thin rectangle. */
	static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int thickness, int argb) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		int length = Math.round(Mth.sqrt(dx * dx + dy * dy));
		if (length <= 0 || (argb >>> 24) == 0) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Mth.atan2(dy, dx));
		g.fill(0, -(thickness / 2), length, thickness - thickness / 2, argb);
		g.pose().popMatrix();
	}

	static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	static float ease(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
	}
}
