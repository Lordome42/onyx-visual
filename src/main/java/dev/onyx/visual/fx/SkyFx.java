package dev.onyx.visual.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Northern lights and shooting stars, drawn on a dome that follows the camera. */
final class SkyFx {
	private static final int RIBBONS = 3;
	private static final int SEGMENTS = 110;
	// Vertical gradient of a curtain: position, colour, strength.
	private static final float[] STOP_POS = {0.0f, 0.10f, 0.40f, 0.72f, 1.0f};
	private static final int[] STOP_COLOR = {0x30FF90, 0x48FFA0, 0x30E0D0, 0x9060FF, 0xD060FF};
	private static final float[] STOP_STRENGTH = {0.0f, 1.0f, 0.7f, 0.4f, 0.0f};

	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Meteor> METEORS = new ArrayList<>();
	private static long lastWish;

	private SkyFx() {
	}

	private static final class Meteor {
		final Vector3f start = new Vector3f();
		final Vector3f travel = new Vector3f();
		float age;
		float life;
		int color;
	}

	static void render(LevelRenderContext context, float time, float dt, float night) {
		OnyxConfig config = OnyxConfig.get();
		// The effects are fogged like any other geometry, so they have to sit inside the fog start distance.
		float fogStart = context.levelState().cameraRenderState.fogData.renderDistanceStart;
		float radius = Mth.clamp(fogStart * 0.62f, 30.0f, 220.0f);
		if (config.aurora && night > 0.01f) {
			aurora(context, time, night, radius);
		}
		if (config.shootingStars) {
			meteors(context, dt, night, radius);
		} else {
			METEORS.clear();
		}
	}

	// --------------------------------------------------------------- aurora

	private static void aurora(LevelRenderContext context, float time, float night, float radius) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.TRIANGLES, (pose, buffer) -> {
			float[] x = new float[SEGMENTS + 1];
			float[] z = new float[SEGMENTS + 1];
			float[] bottom = new float[SEGMENTS + 1];
			float[] height = new float[SEGMENTS + 1];
			float[] power = new float[SEGMENTS + 1];
			for (int ribbon = 0; ribbon < RIBBONS; ribbon++) {
				float phase = ribbon * 2.1f;
				for (int i = 0; i <= SEGMENTS; i++) {
					float s = i / (float) SEGMENTS * 2.0f - 1.0f;
					// The curtains sweep in a wide arc around the north.
					float angle = s * 1.45f + 0.25f * ribbon - 0.2f;
					float r = radius * (0.80f + 0.13f * ribbon
							+ 0.09f * Mth.sin(s * 3.1f + time * 0.07f + phase)
							+ 0.035f * Mth.sin(s * 9.3f - time * 0.13f + phase * 2.0f));
					x[i] = r * Mth.sin(angle);
					z[i] = -r * Mth.cos(angle);
					bottom[i] = radius * (0.26f + 0.07f * ribbon + 0.035f * Mth.sin(s * 5.0f + time * 0.21f + phase));
					height[i] = radius * (0.34f + 0.10f * Mth.sin(s * 2.7f + time * 0.11f + phase * 1.7f));
					// Vertical "rays" drifting along the curtain.
					float rays = 0.55f + 0.45f * Mth.sin(s * 31.0f + time * 0.55f + phase * 4.0f)
							* Mth.sin(s * 11.7f - time * 0.31f + phase);
					float slow = 0.6f + 0.4f * Mth.sin(s * 2.3f + time * 0.09f + phase * 3.0f);
					float edge = Mth.clamp((1.0f - Math.abs(s)) * 5.0f, 0.0f, 1.0f);
					power[i] = rays * slow * edge * night * 0.9f;
				}
				for (int i = 0; i < SEGMENTS; i++) {
					for (int stop = 0; stop < STOP_POS.length - 1; stop++) {
						float y0a = bottom[i] + height[i] * STOP_POS[stop];
						float y1a = bottom[i] + height[i] * STOP_POS[stop + 1];
						float y0b = bottom[i + 1] + height[i + 1] * STOP_POS[stop];
						float y1b = bottom[i + 1] + height[i + 1] * STOP_POS[stop + 1];
						int c0a = Draw.glow(power[i] * STOP_STRENGTH[stop], STOP_COLOR[stop]);
						int c1a = Draw.glow(power[i] * STOP_STRENGTH[stop + 1], STOP_COLOR[stop + 1]);
						int c0b = Draw.glow(power[i + 1] * STOP_STRENGTH[stop], STOP_COLOR[stop]);
						int c1b = Draw.glow(power[i + 1] * STOP_STRENGTH[stop + 1], STOP_COLOR[stop + 1]);
						tri(pose, buffer, x[i], y0a, z[i], c0a, x[i + 1], y0b, z[i + 1], c0b, x[i + 1], y1b, z[i + 1], c1b);
						tri(pose, buffer, x[i], y0a, z[i], c0a, x[i + 1], y1b, z[i + 1], c1b, x[i], y1a, z[i], c1a);
					}
				}
			}
		});
	}

	// -------------------------------------------------------------- meteors

	private static void meteors(LevelRenderContext context, float dt, float night, float radius) {
		if (night > 0.5f && METEORS.size() < 4 && RANDOM.nextFloat() < dt * 0.4f) {
			Meteor meteor = spawn();
			METEORS.add(meteor);
			// If you happen to be looking that way, you get to make a wish.
			Vector3f forward = context.levelState().cameraRenderState.orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
			net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
			if (player != null && OnyxConfig.get().wishes && forward.dot(meteor.start) > 0.75f && System.nanoTime() - lastWish > 60_000_000_000L) {
				lastWish = System.nanoTime();
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("onyxvisual.wish"));
			}
		}
		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
			Meteor meteor = it.next();
			meteor.age += dt;
			if (meteor.age >= meteor.life) {
				it.remove();
			}
		}
		if (METEORS.isEmpty() || night <= 0.05f) {
			return;
		}
		List<Meteor> snapshot = List.copyOf(METEORS);
		float dome = radius * 1.4f;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.TRIANGLES, (pose, buffer) -> {
			Vector3f head = new Vector3f();
			Vector3f tail = new Vector3f();
			Vector3f side = new Vector3f();
			for (Meteor meteor : snapshot) {
				float progress = meteor.age / meteor.life;
				float fade = Mth.sin(progress * Mth.PI) * night;
				head.set(meteor.travel).mul(progress).add(meteor.start).normalize().mul(dome);
				tail.set(meteor.travel).mul(Math.max(0.0f, progress - 0.28f)).add(meteor.start).normalize().mul(dome);
				side.set(head).sub(tail).cross(head).normalize();
				streak(pose, buffer, head, tail, side, dome * 0.0075f, Draw.glow(fade * 0.5f, meteor.color));
				streak(pose, buffer, head, tail, side, dome * 0.0022f, Draw.glow(fade, 0xFFFFFF));
			}
		});
	}

	private static void streak(PoseStack.Pose pose, VertexConsumer buffer, Vector3f head, Vector3f tail, Vector3f side, float width, int color) {
		float sx = side.x * width;
		float sy = side.y * width;
		float sz = side.z * width;
		tri(pose, buffer, tail.x, tail.y, tail.z, 0,
				head.x - sx, head.y - sy, head.z - sz, color,
				head.x + sx, head.y + sy, head.z + sz, color);
	}

	private static void tri(PoseStack.Pose pose, VertexConsumer buffer,
			float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
		Draw.colored(pose, buffer, x1, y1, z1, c1);
		Draw.colored(pose, buffer, x2, y2, z2, c2);
		Draw.colored(pose, buffer, x3, y3, z3, c3);
	}

	private static Meteor spawn() {
		Meteor meteor = new Meteor();
		float azimuth = RANDOM.nextFloat() * Mth.TWO_PI;
		float elevation = 0.45f + RANDOM.nextFloat() * 0.75f;
		meteor.start.set(Mth.cos(elevation) * Mth.cos(azimuth), Mth.sin(elevation), Mth.cos(elevation) * Mth.sin(azimuth));
		// Travel sideways and down across the sky.
		float turn = azimuth + Mth.HALF_PI * (RANDOM.nextBoolean() ? 1.0f : -1.0f) + (RANDOM.nextFloat() - 0.5f);
		float length = 0.45f + RANDOM.nextFloat() * 0.5f;
		meteor.travel.set(Mth.cos(turn) * length, -0.25f - RANDOM.nextFloat() * 0.3f, Mth.sin(turn) * length);
		meteor.life = 0.7f + RANDOM.nextFloat() * 0.7f;
		meteor.color = RANDOM.nextInt(3) == 0 ? 0xFFD9A0 : 0xA0D0FF;
		return meteor;
	}
}
