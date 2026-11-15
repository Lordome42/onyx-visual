package dev.onyx.visual.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Small vertex helpers shared by the effects. All coordinates are camera-relative. */
final class Draw {
	private static final int FULL_BRIGHT = 0xF000F0;

	private Draw() {
	}

	static int argb(float alpha, int rgb) {
		return (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f) << 24 | rgb & 0xFFFFFF;
	}

	/** Colour for the additive glow types: alpha is the brightness. */
	static int glow(float strength, int rgb) {
		return argb(strength, rgb);
	}

	static int mix(int a, int b, float t) {
		int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}

	/** Pastel-ish fully lit colour for a hue in turns. */
	static int hue(float h, float white) {
		h = (h % 1.0f + 1.0f) % 1.0f;
		float r = Mth.clamp(Math.abs(h * 6.0f - 3.0f) - 1.0f, 0.0f, 1.0f);
		float g = Mth.clamp(2.0f - Math.abs(h * 6.0f - 2.0f), 0.0f, 1.0f);
		float b = Mth.clamp(2.0f - Math.abs(h * 6.0f - 4.0f), 0.0f, 1.0f);
		int ri = (int) ((white + (1.0f - white) * r) * 255.0f);
		int gi = (int) ((white + (1.0f - white) * g) * 255.0f);
		int bi = (int) ((white + (1.0f - white) * b) * 255.0f);
		return ri << 16 | gi << 8 | bi;
	}

	static void colored(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int argb) {
		buffer.addVertex(pose, x, y, z).setColor(argb);
	}

	static void textured(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int argb) {
		buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0f, 1.0f, 0.0f);
	}

	/** Textured quad from a centre and two half-extent vectors. */
	static void sprite(PoseStack.Pose pose, VertexConsumer buffer, float cx, float cy, float cz,
			float ax, float ay, float az, float bx, float by, float bz, int argb) {
		textured(pose, buffer, cx - ax - bx, cy - ay - by, cz - az - bz, 0, 1, argb);
		textured(pose, buffer, cx + ax - bx, cy + ay - by, cz + az - bz, 1, 1, argb);
		textured(pose, buffer, cx + ax + bx, cy + ay + by, cz + az + bz, 1, 0, argb);
		textured(pose, buffer, cx - ax + bx, cy - ay + by, cz - az + bz, 0, 0, argb);

	}
}
