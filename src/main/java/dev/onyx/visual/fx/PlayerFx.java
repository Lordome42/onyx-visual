package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Wings, glowing footprints, the level-up spiral and beams over dropped items. */
final class PlayerFx {
	private static final Identifier WING = OnyxVisual.id("textures/fx/wing.png");
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final Identifier HALO = OnyxVisual.id("textures/fx/halo.png");
	private static final Identifier SLASH = OnyxVisual.id("textures/fx/slash.png");
	private static final float SLASH_LIFE = 0.34f;
	private static final float PRINT_LIFE = 2.6f;
	private static final float LEVEL_UP_LIFE = 1.9f;

	private static final List<Print> PRINTS = new ArrayList<>();
	private static float wingFade;
	private static float levelUpAge = -1.0f;
	private static int lastLevel = -1;
	private static int stepTicks;
	private static boolean leftFoot;
	private static boolean wasSwinging;
	private static float slashAge = -1.0f;
	private static float slashRoll;
	private static Vec3 slashPos = Vec3.ZERO;
	private static Vec3 slashLook = Vec3.ZERO;

	private PlayerFx() {
	}

	private static final class Print {
		Vec3 pos;
		float yaw;
		float age;
		int rgb;
	}

	private record PrintView(float x, float y, float z, float yaw, float fade, int rgb) {
	}

	private record Beam(float x, float y, float z, float height, int rgb, float strength) {
	}

	// ------------------------------------------------------------------ tick

	static void tick(Minecraft mc, LocalPlayer player, float time) {
		OnyxConfig config = OnyxConfig.get();

		int level = player.experienceLevel;
		if (lastLevel >= 0 && level > lastLevel && config.levelUp) {
			levelUpAge = 0.0f;
			AuraFx.addShock(player.position(), 0.13f);
		}
		lastLevel = level;

		boolean swinging = Compat.isSwinging(player);
		if (swinging && !wasSwinging && config.slash) {
			slashAge = 0.0f;
			slashLook = player.getLookAngle();
			// Slightly below eye level; the far edge is tilted up in render so the arc faces the viewer.
			slashPos = player.getEyePosition().add(slashLook.scale(1.5)).add(0.0, -0.28, 0.0);
			slashRoll = (leftFoot ? 1.0f : -1.0f) * (0.25f + (stepTicks % 3) * 0.12f);
			leftFoot = !leftFoot;
		}
		wasSwinging = swinging;

		Vec3 motion = player.getDeltaMovement();
		double speedSq = motion.x * motion.x + motion.z * motion.z;
		if (config.footprints && player.onGround() && speedSq > 0.003 && !player.isSpectator() && !player.isInvisible()) {
			stepTicks++;
			if (stepTicks >= (player.isSprinting() ? 4 : 6) && PRINTS.size() < 48) {
				stepTicks = 0;
				leftFoot = !leftFoot;
				float yaw = (float) Mth.atan2(motion.z, motion.x);
				float side = leftFoot ? 0.16f : -0.16f;
				Print print = new Print();
				print.pos = player.position().add(-Mth.sin(yaw) * side, 0.02, Mth.cos(yaw) * side);
				print.yaw = yaw;
				print.rgb = Draw.hue(time * 0.035f + 0.5f, 0.3f);
				PRINTS.add(print);
			}
		}
	}

	static void reset() {
		lastLevel = -1;
		levelUpAge = -1.0f;
		PRINTS.clear();
		slashAge = -1.0f;
	}

	// ---------------------------------------------------------------- render

	static void render(LevelRenderContext context, float time, float dt) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return;
		}
		OnyxConfig config = OnyxConfig.get();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 feet = player.getPosition(partial);
		float px = (float) (feet.x - camera.x);
		float py = (float) (feet.y - camera.y);
		float pz = (float) (feet.z - camera.z);
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));

		wings(context, mc, player, config, time, dt, partial, px, py, pz);
		halo(context, player, config, time, px, py, pz);
		slash(context, camera, time, dt);
		footprints(context, camera, dt);
		levelUp(context, time, dt, px, py, pz, right, up);
		if (config.lootBeams) {
			lootBeams(context, level, player, camera, partial, time, right, up);
		}
	}

	private static void wings(LevelRenderContext context, Minecraft mc, LocalPlayer player, OnyxConfig config,
			float time, float dt, float partial, float px, float py, float pz) {
		boolean show = config.wings && !mc.options.getCameraType().isFirstPerson() && !player.isSpectator()
				&& !player.isInvisible() && !player.isSleeping() && !player.isFallFlying() && !player.isPassenger();
		wingFade = Mth.clamp(wingFade + (show ? dt : -dt) * 3.0f, 0.0f, 1.0f);
		if (wingFade <= 0.01f) {
			return;
		}
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		// Unit vectors of the body: forward and to the player's right.
		float fx = -Mth.sin(bodyYaw);
		float fz = Mth.cos(bodyYaw);
		float rx = -fz;
		float rz = fx;
		// Wings beat faster in the air.
		float beat = player.onGround() ? 2.2f : 7.0f;
		float fold = 0.42f + (player.onGround() ? 0.2f : 0.42f) * Mth.sin(time * beat);
		float cos = Mth.cos(fold);
		float sin = Mth.sin(fold);
		float hingeY = py + (player.isCrouching() ? 1.05f : 1.3f);
		float hx = px - fx * 0.17f;
		float hz = pz - fz * 0.17f;
		float half = 0.95f * (0.7f + 0.3f * wingFade);
		int color = Draw.glow(wingFade * 0.9f, Draw.hue(time * 0.035f + 0.08f, 0.55f));

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(WING), (pose, buffer) -> {
			for (int side = -1; side <= 1; side += 2) {
				// The span swings between "straight out" and "swept back".
				float sx = (rx * side * cos - fx * sin) * half;
				float sz = (rz * side * cos - fz * sin) * half;
				// The texture's hinge sits 60% of the way down its left edge.
				Draw.sprite(pose, buffer, hx + sx + rx * side * 0.08f, hingeY + half * 0.2f, hz + sz + rz * side * 0.08f,
						sx, 0.0f, sz, 0.0f, half, 0.0f, color);
			}
		});
	}

	private static void halo(LevelRenderContext context, LocalPlayer player, OnyxConfig config, float time, float px, float py, float pz) {
		if (!config.halo || wingFade <= 0.01f && !config.wings && Minecraft.getInstance().options.getCameraType().isFirstPerson()
				|| Minecraft.getInstance().options.getCameraType().isFirstPerson() || player.isSpectator() || player.isInvisible() || player.isSleeping()) {
			return;
		}
		float y = py + (player.isCrouching() ? 1.78f : 2.12f) + 0.03f * Mth.sin(time * 2.0f);
		float size = 0.36f;
		// Tilted a touch so it reads as a ring from most angles.
		float tilt = 0.12f * Mth.sin(time * 0.8f);
		int color = Draw.glow(0.95f, Draw.mix(0xFFE070, 0xFFFFFF, 0.25f + 0.15f * Mth.sin(time * 3.0f)));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(HALO),
				(pose, buffer) -> Draw.sprite(pose, buffer, px, y, pz, size, tilt * size, 0, 0, 0, size, color));
	}

	private static void slash(LevelRenderContext context, Vec3 camera, float time, float dt) {
		if (slashAge < 0.0f) {
			return;
		}
		slashAge += dt;
		if (slashAge >= SLASH_LIFE) {
			slashAge = -1.0f;
			return;
		}
		float progress = slashAge / SLASH_LIFE;
		float x = (float) (slashPos.x - camera.x);
		float y = (float) (slashPos.y - camera.y);
		float z = (float) (slashPos.z - camera.z);
		float lx = (float) slashLook.x;
		float ly = (float) slashLook.y;
		float lz = (float) slashLook.z;
		// Sideways axis: horizontal and perpendicular to the look direction, then rolled around it.
		float hl = Mth.sqrt(lx * lx + lz * lz);
		float sx = hl > 0.001f ? -lz / hl : 1.0f;
		float sz = hl > 0.001f ? lx / hl : 0.0f;
		// Up axis perpendicular to both (look x side).
		float ux = ly * sz;
		float uy = lz * sx - lx * sz;
		float uz = -ly * sx;
		if (uy < 0.0f) {
			ux = -ux;
			uy = -uy;
			uz = -uz;
		}
		float cos = Mth.cos(slashRoll);
		float sin = Mth.sin(slashRoll);
		float ax = sx * cos + ux * sin;
		float ay = uy * sin;
		// Depth axis: forwards, with the leading edge lifted towards the viewer's line of sight.
		float bx = lx * 0.75f + ux * 0.66f;
		float by = ly * 0.75f + uy * 0.66f;
		float bz = lz * 0.75f + uz * 0.66f;
		float az = sz * cos + uz * sin;
		float grow = 0.75f + 0.5f * progress;
		float width = 1.25f * grow;
		float depth = 0.62f * grow;
		int color = Draw.glow(1.0f - progress * progress, Draw.mix(Draw.hue(time * 0.035f + 0.55f, 0.35f), 0xFFFFFF, 0.4f));
		// The texture's bright edge is at v = 0, which Draw.sprite maps to +b: point that forwards.
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(SLASH),
				(pose, buffer) -> Draw.sprite(pose, buffer, x, y, z, ax * width, ay * width, az * width, bx * depth, by * depth, bz * depth, color));
	}

	private static void footprints(LevelRenderContext context, Vec3 camera, float dt) {
		if (PRINTS.isEmpty()) {
			return;
		}
		List<PrintView> views = new ArrayList<>(PRINTS.size());
		for (Iterator<Print> it = PRINTS.iterator(); it.hasNext(); ) {
			Print print = it.next();
			print.age += dt;
			if (print.age >= PRINT_LIFE) {
				it.remove();
				continue;
			}
			float k = print.age / PRINT_LIFE;
			views.add(new PrintView((float) (print.pos.x - camera.x), (float) (print.pos.y - camera.y),
					(float) (print.pos.z - camera.z), print.yaw, Math.min(1.0f, print.age * 8.0f) * (1.0f - k) * (1.0f - k), print.rgb));
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (PrintView view : views) {
				float cos = Mth.cos(view.yaw);
				float sin = Mth.sin(view.yaw);
				// A stretched glow: long along the walking direction, narrow across it.
				Draw.sprite(pose, buffer, view.x, view.y, view.z, cos * 0.3f, 0, sin * 0.3f, -sin * 0.14f, 0, cos * 0.14f,
						Draw.glow(view.fade * 0.9f, view.rgb));
			}
		});
	}

	private static void levelUp(LevelRenderContext context, float time, float dt, float px, float py, float pz, Vector3f right, Vector3f up) {
		if (levelUpAge < 0.0f) {
			return;
		}
		levelUpAge += dt;
		if (levelUpAge >= LEVEL_UP_LIFE) {
			levelUpAge = -1.0f;
			return;
		}
		float progress = levelUpAge / LEVEL_UP_LIFE;
		float envelope = Mth.sin(progress * Mth.PI);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (int strand = 0; strand < 3; strand++) {
				for (int i = 0; i < 26; i++) {
					// Each strand is a rising spiral; later sprites trail behind the head.
					float head = progress * 3.4f - i * 0.045f;
					if (head < 0.0f || head > 2.6f) {
						continue;
					}
					float angle = head * 5.0f + strand * Mth.TWO_PI / 3.0f + time * 2.0f;
					float radius = 0.85f - head * 0.12f;
					float k = 1.0f - i / 26.0f;
					float size = (i == 0 ? 0.3f : 0.07f + 0.15f * k);
					int color = Draw.glow(envelope * (i == 0 ? 1.0f : 0.75f * k), i == 0 ? 0xFFFFFF : Draw.hue(0.13f + strand * 0.05f + head * 0.05f, 0.35f));
					Draw.sprite(pose, buffer, px + Mth.cos(angle) * radius, py + head, pz + Mth.sin(angle) * radius,
							right.x * size, right.y * size, right.z * size, up.x * size, up.y * size, up.z * size, color);
				}
			}
		});
	}

	private static void lootBeams(LevelRenderContext context, ClientLevel level, LocalPlayer player, Vec3 camera,
			float partial, float time, Vector3f right, Vector3f up) {
		List<Beam> beams = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity item) || item.getItem().isEmpty()) {
				continue;
			}
			double distance = entity.distanceToSqr(player);
			if (distance > 32.0 * 32.0) {
				continue;
			}
			Rarity rarity = item.getItem().getRarity();
			int rgb;
			float height;
			float strength;
			if (rarity == Rarity.EPIC) {
				rgb = 0xE070FF;
				height = 2.6f;
				strength = 1.0f;
			} else if (rarity == Rarity.RARE) {
				rgb = 0x60E8FF;
				height = 2.2f;
				strength = 0.9f;
			} else if (rarity == Rarity.UNCOMMON) {
				rgb = 0xFFE860;
				height = 1.8f;
				strength = 0.8f;
			} else {
				rgb = 0xFFFFFF;
				height = 1.1f;
				strength = 0.4f;
			}
			Vec3 pos = entity.getPosition(partial);
			float shimmer = 0.85f + 0.15f * Mth.sin(time * 4.0f + entity.getId());
			beams.add(new Beam((float) (pos.x - camera.x), (float) (pos.y - camera.y) + 0.1f, (float) (pos.z - camera.z),
					height, rgb, strength * shimmer));
			if (beams.size() >= 64) {
				break;
			}
		}
		if (beams.isEmpty()) {
			return;
		}
		// The beam is a camera-facing vertical strip: bright core plus a wide soft halo.
		float len = Mth.sqrt(right.x * right.x + right.z * right.z);
		float sx = len > 0.001f ? right.x / len : 1.0f;
		float sz = len > 0.001f ? right.z / len : 0.0f;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.QUADS, (pose, buffer) -> {
			for (Beam beam : beams) {
				strip(pose, buffer, beam, sx, sz, 0.16f, Draw.glow(beam.strength * 0.28f, beam.rgb));
				strip(pose, buffer, beam, sx, sz, 0.035f, Draw.glow(beam.strength, Draw.mix(beam.rgb, 0xFFFFFF, 0.5f)));
			}
		});
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (Beam beam : beams) {
				float size = 0.45f;
				Draw.sprite(pose, buffer, beam.x, beam.y + 0.15f, beam.z, right.x * size, right.y * size, right.z * size,
						up.x * size, up.y * size, up.z * size, Draw.glow(beam.strength * 0.7f, beam.rgb));
			}
		});
	}

	private static void strip(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
			Beam beam, float sx, float sz, float width, int color) {
		float wx = sx * width;
		float wz = sz * width;
		int clear = color & 0xFFFFFF;
		Draw.colored(pose, buffer, beam.x - wx, beam.y, beam.z - wz, color);
		Draw.colored(pose, buffer, beam.x + wx, beam.y, beam.z + wz, color);
		Draw.colored(pose, buffer, beam.x + wx, beam.y + beam.height, beam.z + wz, clear);
		Draw.colored(pose, buffer, beam.x - wx, beam.y + beam.height, beam.z - wz, clear);
	}
}
