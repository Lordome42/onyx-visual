package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Short-lived bursts of light: a star flare, an expanding ring and a spray of sparks. */
final class BurstFx {
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final Identifier FLARE = OnyxVisual.id("textures/fx/flare.png");
	private static final Identifier RING = OnyxVisual.id("textures/fx/ring.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Burst> BURSTS = new ArrayList<>();

	private BurstFx() {
	}

	private static final class Burst {
		Vec3 pos;
		float age;
		float life;
		float scale;
		float spin;
		int rgb;
		boolean flare;
		float gravity;
		float[] sparks; // dx, dy, dz, speed per spark
	}

	private record View(float x, float y, float z, float progress, Burst burst) {
	}

	/** A sharp star flash, used when hitting something. */
	static void impact(Vec3 pos, int rgb) {
		add(pos, rgb, 0.38f, 1.0f, true, 12, 3.2f, 2.0f);
	}

	/** A softer shower of sparks, used when a block breaks. */
	static void shatter(Vec3 pos, int rgb) {
		add(pos, rgb, 0.65f, 0.8f, false, 18, 2.4f, 7.0f);
	}

	/** Pale motes drifting upwards, used when a mob dies. */
	static void soul(Vec3 pos) {
		add(pos, 0xB8F4FF, 1.3f, 1.1f, false, 22, 1.1f, -2.6f);
	}

	/** A lava bubble bursting: an orange ring and a few sparks that arc back down. */
	static void lava(Vec3 pos, float strength) {
		add(pos, 0xFF9A30, 0.55f + strength * 0.5f, 0.35f + strength * 0.9f, false, (int) (4 + strength * 14), 1.6f + strength * 2.6f, 9.0f);
	}

	/** A soft burst of white motes (a snowball breaking). */
	static void snow(Vec3 pos) {
		add(pos, 0xFFFFFF, 0.5f, 0.7f, false, 16, 2.0f, 6.0f);
	}

	private static void add(Vec3 pos, int rgb, float life, float scale, boolean flare, int sparks, float speed, float gravity) {
		if (BURSTS.size() >= 24) {
			return;
		}
		Burst burst = new Burst();
		burst.pos = pos;
		burst.rgb = rgb;
		burst.life = life;
		burst.scale = scale;
		burst.flare = flare;
		burst.gravity = gravity;
		burst.spin = RANDOM.nextFloat() * Mth.TWO_PI;
		burst.sparks = new float[sparks * 4];
		for (int i = 0; i < sparks; i++) {
			float yaw = RANDOM.nextFloat() * Mth.TWO_PI;
			float y = RANDOM.nextFloat() * 1.6f - 0.6f;
			float h = Mth.sqrt(Math.max(0.0f, 1.0f - Math.min(1.0f, y * y)));
			burst.sparks[i * 4] = Mth.cos(yaw) * h;
			burst.sparks[i * 4 + 1] = y;
			burst.sparks[i * 4 + 2] = Mth.sin(yaw) * h;
			burst.sparks[i * 4 + 3] = speed * (0.45f + RANDOM.nextFloat() * 0.75f);
		}
		BURSTS.add(burst);
	}

	static void render(LevelRenderContext context, float dt) {
		if (BURSTS.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<View> views = new ArrayList<>(BURSTS.size());
		for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
			Burst burst = it.next();
			burst.age += dt;
			if (burst.age >= burst.life) {
				it.remove();
				continue;
			}
			views.add(new View((float) (burst.pos.x - camera.x), (float) (burst.pos.y - camera.y),
					(float) (burst.pos.z - camera.z), burst.age / burst.life, burst));
		}
		if (views.isEmpty()) {
			return;
		}
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (View view : views) {
				Burst burst = view.burst;
				float t = burst.age;
				float fade = 1.0f - view.progress;
				float size = 0.07f * burst.scale * (0.4f + 0.6f * fade);
				for (int i = 0; i < burst.sparks.length; i += 4) {
					// Sparks slow down as they fly and fall a little.
					float travel = burst.sparks[i + 3] * t * (1.0f - 0.45f * view.progress);
					float x = view.x + burst.sparks[i] * travel;
					float y = view.y + burst.sparks[i + 1] * travel - 0.5f * burst.gravity * t * t;
					float z = view.z + burst.sparks[i + 2] * travel;
					int color = Draw.glow(fade, i % 8 == 0 ? 0xFFFFFF : burst.rgb);
					Draw.sprite(pose, buffer, x, y, z, right.x * size, right.y * size, right.z * size,
							up.x * size, up.y * size, up.z * size, color);
				}
			}
		});

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(RING), (pose, buffer) -> {
			for (View view : views) {
				float grow = 1.0f - (1.0f - view.progress) * (1.0f - view.progress);
				float size = view.burst.scale * (0.15f + 0.85f * grow);
				float fade = (1.0f - view.progress) * (1.0f - view.progress);
				Draw.sprite(pose, buffer, view.x, view.y, view.z, right.x * size, right.y * size, right.z * size,
						up.x * size, up.y * size, up.z * size, Draw.glow(fade * 0.8f, view.burst.rgb));
			}
		});

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(FLARE), (pose, buffer) -> {
			for (View view : views) {
				if (!view.burst.flare) {
					continue;
				}
				// Pops quickly and then shrinks while turning.
				float pop = Mth.sin(Math.min(1.0f, view.progress * 1.6f) * Mth.PI);
				float size = view.burst.scale * (0.5f + 0.9f * pop);
				float angle = view.burst.spin + view.progress * 1.2f;
				float cos = Mth.cos(angle) * size;
				float sin = Mth.sin(angle) * size;
				Draw.sprite(pose, buffer, view.x, view.y, view.z,
						right.x * cos + up.x * sin, right.y * cos + up.y * sin, right.z * cos + up.z * sin,
						up.x * cos - right.x * sin, up.y * cos - right.y * sin, up.z * cos - right.z * sin,
						Draw.glow(pop, Draw.mix(view.burst.rgb, 0xFFFFFF, 0.5f)));
			}
		});
	}
}
