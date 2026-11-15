package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Things that live around the player: a rune circle, orbiting wisps and landing shockwaves. */
final class AuraFx {
	private static final Identifier CIRCLE_OUTER = OnyxVisual.id("textures/fx/circle_outer.png");
	private static final Identifier CIRCLE_INNER = OnyxVisual.id("textures/fx/circle_inner.png");
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final Identifier RING = OnyxVisual.id("textures/fx/ring.png");

	private static final int WISPS = 3;
	private static final int TRAIL = 14;
	private static final float SHOCK_LIFE = 0.7f;

	private static final List<Shock> SHOCKS = new ArrayList<>();
	private static float circleFade;
	private static float wispFade;

	private AuraFx() {
	}

	private record ShockView(float x, float y, float z, float progress, int rgb) {
	}

	private static final class Shock {
		Vec3 pos;
		float age;
		int rgb;
	}

	/** Called from the client tick when the player touches down after a real fall or jump. */
	static void addShock(Vec3 pos, float hue) {
		if (SHOCKS.size() < 8) {
			Shock shock = new Shock();
			shock.pos = pos;
			shock.rgb = Draw.hue(hue, 0.35f);
			SHOCKS.add(shock);
		}
	}

	static void render(LevelRenderContext context, float time, float dt) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		OnyxConfig config = OnyxConfig.get();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 feet = player.getPosition(partial);
		float px = (float) (feet.x - camera.x);
		float py = (float) (feet.y - camera.y);
		float pz = (float) (feet.z - camera.z);
		boolean visible = !player.isSpectator() && !player.isInvisible() && !player.isSleeping();
		float baseHue = time * 0.035f;

		// ------------------------------------------------------ rune circle
		boolean grounded = visible && config.magicCircle && player.onGround() && !player.isPassenger();
		circleFade = approach(circleFade, grounded ? 1.0f : 0.0f, dt * 4.0f);
		if (circleFade > 0.01f) {
			float pulse = 0.82f + 0.18f * Mth.sin(time * 2.2f);
			float grow = 0.6f + 0.4f * ease(circleFade);
			float strength = circleFade * pulse;
			float y = py + 0.03f;
			flat(context, CIRCLE_OUTER, px, y, pz, 1.75f * grow, time * 0.35f, Draw.glow(strength, Draw.hue(baseHue, 0.35f)));
			flat(context, CIRCLE_INNER, px, y + 0.01f, pz, 1.35f * grow, -time * 0.6f, Draw.glow(strength * 0.9f, Draw.hue(baseHue + 0.12f, 0.45f)));
			flat(context, GLOW, px, y + 0.02f, pz, 2.2f * grow, 0.0f, Draw.glow(strength * 0.16f, Draw.hue(baseHue + 0.06f, 0.2f)));
		}

		// ------------------------------------------------------------ wisps
		wispFade = approach(wispFade, visible && config.wisps ? 1.0f : 0.0f, dt * 3.0f);
		if (wispFade > 0.01f) {
			Quaternionf orientation = context.levelState().cameraRenderState.orientation;
			Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
			Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
			float fade = wispFade;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), additive(GLOW), (pose, buffer) -> {
				for (int wisp = 0; wisp < WISPS; wisp++) {
					int rgb = Draw.hue(baseHue + wisp / (float) WISPS, 0.3f);
					for (int step = TRAIL - 1; step >= 0; step--) {
						float t = time - step * 0.03f;
						float angle = t * 1.7f + wisp * Mth.TWO_PI / WISPS;
						float radius = 1.05f + 0.18f * Mth.sin(t * 0.9f + wisp * 1.3f);
						float x = px + Mth.cos(angle) * radius;
						float z = pz + Mth.sin(angle) * radius;
						float y = py + 1.0f + 0.55f * Mth.sin(t * 1.25f + wisp * 2.1f);
						float k = 1.0f - step / (float) TRAIL;
						float size = (step == 0 ? 0.34f : 0.05f + 0.16f * k) * fade;
						int color = Draw.glow((step == 0 ? 1.0f : 0.7f * k * k) * fade, step == 0 ? Draw.mix(rgb, 0xFFFFFF, 0.35f) : rgb);
						Draw.sprite(pose, buffer, x, y, z,
								right.x * size, right.y * size, right.z * size,
								up.x * size, up.y * size, up.z * size, color);
					}
				}
			});
		}

		// ------------------------------------------------------- shockwaves
		if (!SHOCKS.isEmpty()) {
			List<ShockView> views = new ArrayList<>(SHOCKS.size());
			for (Iterator<Shock> it = SHOCKS.iterator(); it.hasNext(); ) {
				Shock shock = it.next();
				shock.age += dt;
				if (shock.age >= SHOCK_LIFE) {
					it.remove();
					continue;
				}
				views.add(new ShockView((float) (shock.pos.x - camera.x), (float) (shock.pos.y - camera.y) + 0.05f,
						(float) (shock.pos.z - camera.z), shock.age / SHOCK_LIFE, shock.rgb));
			}
			if (!views.isEmpty()) {
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), additive(RING), (pose, buffer) -> {
					for (ShockView view : views) {
						float size = 0.4f + 3.4f * (1.0f - (1.0f - view.progress) * (1.0f - view.progress));
						float strength = (1.0f - view.progress) * (1.0f - view.progress);
						Draw.sprite(pose, buffer, view.x, view.y, view.z, size, 0, 0, 0, 0, size, Draw.glow(strength, view.rgb));
					}
				});
			}
		}
	}

	/** A textured quad lying on the ground, rotated around the vertical axis. */
	private static void flat(LevelRenderContext context, Identifier texture, float x, float y, float z, float size, float angle, int color) {
		float cos = Mth.cos(angle) * size;
		float sin = Mth.sin(angle) * size;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), additive(texture),
				(pose, buffer) -> Draw.sprite(pose, buffer, x, y, z, cos, 0, sin, -sin, 0, cos, color));
	}

	private static RenderType additive(Identifier texture) {
		return GlowTypes.textured(texture);
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	private static float ease(float t) {
		return t * t * (3.0f - 2.0f * t);
	}
}
