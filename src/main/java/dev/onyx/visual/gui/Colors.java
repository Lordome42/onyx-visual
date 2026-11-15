package dev.onyx.visual.gui;

/** Colour maths without java.awt. */
public final class Colors {
	private Colors() {}

	public static int hsbToRgb(float hue, float saturation, float brightness) {
		int r = 0, g = 0, b = 0;
		if (saturation == 0) {
			r = g = b = (int) (brightness * 255.0f + 0.5f);
		} else {
			float h = (hue - (float) Math.floor(hue)) * 6.0f;
			float f = h - (float) Math.floor(h);
			float p = brightness * (1.0f - saturation);
			float q = brightness * (1.0f - saturation * f);
			float t = brightness * (1.0f - (saturation * (1.0f - f)));
			switch ((int) h) {
				case 0 -> { r = (int) (brightness * 255 + .5f); g = (int) (t * 255 + .5f); b = (int) (p * 255 + .5f); }
				case 1 -> { r = (int) (q * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (p * 255 + .5f); }
				case 2 -> { r = (int) (p * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (t * 255 + .5f); }
				case 3 -> { r = (int) (p * 255 + .5f); g = (int) (q * 255 + .5f); b = (int) (brightness * 255 + .5f); }
				case 4 -> { r = (int) (t * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (brightness * 255 + .5f); }
				default -> { r = (int) (brightness * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (q * 255 + .5f); }
			}
		}
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	public static float[] rgbToHsb(int argb) {
		int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float brightness = max / 255f;
		float saturation = max != 0 ? (max - min) / (float) max : 0;
		float hue;
		if (saturation == 0) {
			hue = 0;
		} else {
			float rc = (max - r) / (float) (max - min), gc = (max - g) / (float) (max - min), bc = (max - b) / (float) (max - min);
			if (r == max) hue = bc - gc;
			else if (g == max) hue = 2.0f + rc - bc;
			else hue = 4.0f + gc - rc;
			hue /= 6.0f;
			if (hue < 0) hue += 1.0f;
		}
		return new float[]{hue, saturation, brightness};
	}

	public static int withAlpha(int color, float alpha) {
		return (color & 0xFFFFFF) | (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24);
	}

	/** Multiplies the colour's own alpha. */
	public static int fade(int color, float alpha) {
		int a = (color >>> 24) & 255;
		return (color & 0xFFFFFF) | (Math.round(a * Math.max(0, Math.min(1, alpha))) << 24);
	}

	public static int mix(int from, int to, float t) {
		t = Math.max(0, Math.min(1, t));
		int a = Math.round(((from >>> 24) & 255) * (1 - t) + ((to >>> 24) & 255) * t);
		int r = Math.round(((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
		int g = Math.round(((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
		int b = Math.round((from & 255) * (1 - t) + (to & 255) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
