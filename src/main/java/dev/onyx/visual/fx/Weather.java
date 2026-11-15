package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Wind gusts that sweep across the land, and your breath steaming in the cold. */
public final class Weather {
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int STREAKS = 22;
	private static final int BREATHS = 8;

	private static final class Wisp {
		double x, y, z, vx, vy, vz;
		float age, life, size;
		boolean alive;
	}

	private static final Wisp[] WIND = new Wisp[STREAKS];
	private static final Wisp[] BREATH = new Wisp[BREATHS];
	private static int nextGust = 600;
	private static int gustTicks;
	private static float gust;
	private static float angle;
	private static int breathTimer;

	static {
		for (int i = 0; i < STREAKS; i++) {
			WIND[i] = new Wisp();
		}
		for (int i = 0; i < BREATHS; i++) {
			BREATH[i] = new Wisp();
		}
	}

	private Weather() {
	}

	/** Current gust strength, 0 (calm) to 1. Ground decorations and fluff read this. */
	public static float gust() {
		return gust;
	}

	public static float windX() {
		return Mth.cos(angle);
	}

	public static float windZ() {
		return Mth.sin(angle);
	}

	/** Test hook: start a gust right now. */
	public static void debugGust() {
		gustTicks = 80;
	}

	static void reset() {
		gust = 0.0f;
		gustTicks = 0;
		for (Wisp wisp : WIND) {
			wisp.alive = false;
		}
		for (Wisp wisp : BREATH) {
			wisp.alive = false;
		}
	}

	static void tick(ClientLevel level, LocalPlayer player, boolean overworld) {
		OnyxConfig config = OnyxConfig.get();

		// ---- gusts
		if (overworld && config.windGusts) {
			if (gustTicks > 0) {
				gustTicks--;
			} else if (--nextGust <= 0) {
				nextGust = 700 + RANDOM.nextInt(1100);
				gustTicks = 70 + RANDOM.nextInt(50);
				angle = RANDOM.nextFloat() * Mth.TWO_PI;
			}
		} else {
			gustTicks = 0;
		}
		gust = Mth.clamp(gust + (gustTicks > 0 ? 0.06f : -0.03f), 0.0f, 1.0f);
		for (Wisp wisp : WIND) {
			if (wisp.alive) {
				wisp.x += wisp.vx;
				wisp.y += Mth.sin(wisp.age * 3.0f) * 0.01;
				wisp.z += wisp.vz;
				wisp.age += 0.05f;
				if (wisp.age >= wisp.life) {
					wisp.alive = false;
				}
			} else if (gust > 0.4f && RANDOM.nextInt(3) == 0) {
				wisp.alive = true;
				wisp.x = player.getX() + RANDOM.nextDouble() * 30.0 - 15.0 - windX() * 8.0;
				wisp.y = player.getY() + 0.5 + RANDOM.nextDouble() * 5.0;
				wisp.z = player.getZ() + RANDOM.nextDouble() * 30.0 - 15.0 - windZ() * 8.0;
				double speed = 0.45 + RANDOM.nextDouble() * 0.25;
				wisp.vx = windX() * speed;
				wisp.vz = windZ() * speed;
				wisp.age = 0.0f;
				wisp.life = 1.0f + RANDOM.nextFloat() * 0.9f;
				wisp.size = 1.2f + RANDOM.nextFloat() * 1.6f;
			}
		}

		// ---- breath: only when standing on something frozen
		for (Wisp wisp : BREATH) {
			if (wisp.alive) {
				wisp.x += wisp.vx;
				wisp.y += wisp.vy;
				wisp.z += wisp.vz;
				wisp.vx *= 0.93;
				wisp.vz *= 0.93;
				wisp.age += 0.05f;
				wisp.size += 0.006f;
				if (wisp.age >= wisp.life) {
					wisp.alive = false;
				}
			}
		}
		if (config.coldBreath && !player.isUnderWater() && !player.isSpectator() && ++breathTimer >= 70) {
			BlockState under = level.getBlockState(player.blockPosition().below());
			BlockState at = level.getBlockState(player.blockPosition());
			boolean cold = under.is(Blocks.SNOW_BLOCK) || under.is(Blocks.ICE) || under.is(Blocks.PACKED_ICE)
					|| under.is(Blocks.POWDER_SNOW) || at.is(Blocks.SNOW) || under.is(Blocks.SNOW);
			if (!cold) {
				breathTimer = 40;
			} else {
				breathTimer = RANDOM.nextInt(15);
				Vec3 look = player.getLookAngle();
				Vec3 eye = player.getEyePosition();
				int made = 0;
				for (Wisp wisp : BREATH) {
					if (wisp.alive || made >= 4) {
						continue;
					}
					made++;
					wisp.alive = true;
					wisp.x = eye.x + look.x * 0.35;
					wisp.y = eye.y - 0.18 + look.y * 0.35;
					wisp.z = eye.z + look.z * 0.35;
					double speed = 0.03 + made * 0.012;
					wisp.vx = look.x * speed + (RANDOM.nextDouble() - 0.5) * 0.01;
					wisp.vy = 0.006 + look.y * speed * 0.5;
					wisp.vz = look.z * speed + (RANDOM.nextDouble() - 0.5) * 0.01;
					wisp.age = 0.0f;
					wisp.life = 1.2f + RANDOM.nextFloat() * 0.6f;
					wisp.size = 0.09f + made * 0.02f;
				}
			}
		}
	}

	static void render(LevelRenderContext context) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
		float wx = windX(), wz = windZ();
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (Wisp wisp : WIND) {
				if (!wisp.alive) {
					continue;
				}
				float x = (float) (wisp.x - camera.x), y = (float) (wisp.y - camera.y), z = (float) (wisp.z - camera.z);
				float fade = Mth.sin(Mth.clamp(wisp.age / wisp.life, 0.0f, 1.0f) * Mth.PI);
				// A long thin streak lying along the wind direction.
				float s = wisp.size;
				Draw.sprite(pose, buffer, x, y, z, wx * s, 0.0f, wz * s, up.x * 0.05f, up.y * 0.05f, up.z * 0.05f,
						Draw.glow(0.85f * fade * gust, 0xFFFFFF));
			}
			for (Wisp wisp : BREATH) {
				if (!wisp.alive) {
					continue;
				}
				float x = (float) (wisp.x - camera.x), y = (float) (wisp.y - camera.y), z = (float) (wisp.z - camera.z);
				float fade = 1.0f - wisp.age / wisp.life;
				float s = wisp.size;
				Draw.sprite(pose, buffer, x, y, z, right.x * s, right.y * s, right.z * s, up.x * s, up.y * s, up.z * s,
						Draw.glow(0.22f * fade, 0xF0F6FF));
			}
		});
	}
}
