package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Overworld atmosphere: sun shafts under trees, sun and moon halos, dawn mist, drifting fluff, water sparkle. */
public final class AtmosFx {
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int MIST = 46;
	private static final int FLUFF = 30;

	private record Shaft(double x0, double y0, double z0, double x1, double y1, double z1, float phase) {
	}

	private static final class Puff {
		double x, y, z, vx, vz;
		float age, life, size;
		boolean alive;
	}

	private static volatile List<Shaft> shafts = List.of();
	private static final Puff[] MISTS = new Puff[MIST];
	private static final Puff[] FLUFFS = new Puff[FLUFF];
	private static int scanTicks;
	private static float sunX, sunY;
	private static float dawn;
	private static float windAngle;

	static {
		for (int i = 0; i < MIST; i++) {
			MISTS[i] = new Puff();
		}
		for (int i = 0; i < FLUFF; i++) {
			FLUFFS[i] = new Puff();
		}
	}

	private AtmosFx() {
	}

	/** A puff of dandelion seeds from a point (blowing on a flower). */
	public static void fluffBurst(double x, double y, double z, int count) {
		int made = 0;
		for (Puff puff : FLUFFS) {
			if (made >= count) {
				break;
			}
			if (puff.alive && puff.age < puff.life - 2.0f) {
				continue;
			}
			made++;
			puff.alive = true;
			puff.x = x + (RANDOM.nextDouble() - 0.5) * 0.2;
			puff.y = y + RANDOM.nextDouble() * 0.2;
			puff.z = z + (RANDOM.nextDouble() - 0.5) * 0.2;
			float angle = RANDOM.nextFloat() * Mth.TWO_PI;
			puff.vx = Mth.cos(angle) * (0.02 + RANDOM.nextDouble() * 0.04) + Mth.cos(windAngle) * 0.02;
			puff.vz = Mth.sin(angle) * (0.02 + RANDOM.nextDouble() * 0.04) + Mth.sin(windAngle) * 0.02;
			puff.age = 0.0f;
			puff.life = 5.0f + RANDOM.nextFloat() * 5.0f;
			puff.size = 0.03f + RANDOM.nextFloat() * 0.025f;
		}
	}

	static void reset() {
		shafts = List.of();
		scanTicks = 0;
		for (Puff puff : MISTS) {
			puff.alive = false;
		}
		for (Puff puff : FLUFFS) {
			puff.alive = false;
		}
	}

	private static double ground(ClientLevel level, BlockPos.MutableBlockPos pos, double x, double guessY, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		for (int y = Mth.floor(guessY) + 3; y >= Mth.floor(guessY) - 6; y--) {
			BlockState state = level.getBlockState(pos.set(bx, y, bz));
			boolean water = state.is(Blocks.WATER);
			if ((water || !state.getCollisionShape(level, pos).isEmpty()) && level.getBlockState(pos.set(bx, y + 1, bz)).isAir()) {
				return y + (water ? 0.9 : 1.0);
			}
		}
		return Double.NaN;
	}

	static void tick(ClientLevel level, LocalPlayer player) {
		OnyxConfig config = OnyxConfig.get();
		int dayTime = (int) Math.floorMod(level.getDefaultClockTime(), 24000L);
		// The game eases the sun's path with a cosine, so it is not a plain linear sweep.
		float f = dayTime / 24000.0f - 0.25f;
		f -= Mth.floor(f);
		float eased = 0.5f - Mth.cos(f * Mth.PI) / 2.0f;
		float angle = (f + (eased - f) / 3.0f) * Mth.TWO_PI;
		sunX = -Mth.sin(angle);
		sunY = Mth.cos(angle);
		boolean rain = level.isRaining();
		float dawnNow = dayTime > 22300 ? (dayTime - 22300) / 1700.0f : dayTime < 1800 ? 1.0f - dayTime / 1800.0f : 0.0f;
		dawn = Mth.clamp(dawnNow * 1.6f, 0.0f, 1.0f);
		windAngle += 0.0015f;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		// ---- sun shafts: re-collected every two seconds from leaves that have open sky above
		if (config.sunShafts && sunY > 0.12f && !rain) {
			if (--scanTicks <= 0) {
				scanTicks = 40;
				List<Shaft> found = new ArrayList<>();
				int px = Mth.floor(player.getX());
				int py = Mth.floor(player.getY());
				int pz = Mth.floor(player.getZ());
				// Light travels from the sun downwards: this is the unit direction of the beam.
				float dx = -sunX;
				float dy = -Math.max(sunY, 0.7f);
				float len = Mth.sqrt(dx * dx + dy * dy);
				dx /= len;
				dy /= len;
				for (int x = px - 13; x <= px + 13 && found.size() < 34; x++) {
					for (int z = pz - 13; z <= pz + 13; z++) {
						int h = (x * 73428767 ^ z * 42317861) & 0x7FFFFFFF;
						if (h % 100 >= 6) {
							continue;
						}
						for (int y = py + 12; y >= py - 2; y--) {
							if (!level.getBlockState(pos.set(x, y, z)).is(BlockTags.LEAVES)) {
								continue;
							}
							// The lowest leaf of the column, with air under it and daylight reaching the canopy.
							while (level.getBlockState(pos.set(x, y - 1, z)).is(BlockTags.LEAVES)) {
								y--;
							}
							if (!level.getBlockState(pos.set(x, y - 1, z)).isAir()
									|| level.getBrightness(LightLayer.SKY, pos.set(x, y - 1, z)) < 9) {
								break;
							}
							double sx = x + 0.5;
							double sy = y;
							double sz = z + 0.5;
							double ex = sx;
							double ey = sy;
							for (int step = 0; step < 16; step++) {
								double nx = ex + dx * 0.5;
								double ny = ey + dy * 0.5;
								if (!level.getBlockState(pos.set(Mth.floor(nx), Mth.floor(ny), z)).getCollisionShape(level, pos).isEmpty()) {
									break;
								}
								ex = nx;
								ey = ny;
							}
							if (sy - ey > 1.5) {
								found.add(new Shaft(sx, sy, sz, ex, ey, sz, (h >> 8 & 255) / 40.0f));
							}
							break;
						}
					}
				}
				shafts = found;
			}
		} else if (!shafts.isEmpty()) {
			shafts = List.of();
			scanTicks = 0;
		}

		// ---- dawn mist
		boolean mist = config.mist && dawn > 0.02f;
		for (Puff puff : MISTS) {
			if (puff.alive) {
				puff.x += puff.vx;
				puff.z += puff.vz;
				puff.age += 0.05f;
				double dx = puff.x - player.getX();
				double dz = puff.z - player.getZ();
				if (puff.age >= puff.life || dx * dx + dz * dz > 30.0 * 30.0) {
					puff.alive = false;
				}
			} else if (mist && RANDOM.nextInt(6) == 0) {
				double x = player.getX() + RANDOM.nextDouble() * 44.0 - 22.0;
				double z = player.getZ() + RANDOM.nextDouble() * 44.0 - 22.0;
				double top = ground(level, pos, x, player.getY(), z);
				if (!Double.isNaN(top) && level.getBrightness(LightLayer.SKY, pos.set(Mth.floor(x), Mth.floor(top) + 1, Mth.floor(z))) > 10) {
					puff.alive = true;
					puff.x = x;
					puff.y = top + 0.35 + RANDOM.nextDouble() * 0.5;
					puff.z = z;
					puff.vx = Mth.cos(windAngle) * 0.012 + (RANDOM.nextDouble() - 0.5) * 0.008;
					puff.vz = Mth.sin(windAngle) * 0.012 + (RANDOM.nextDouble() - 0.5) * 0.008;
					puff.age = 0.0f;
					puff.life = 7.0f + RANDOM.nextFloat() * 7.0f;
					puff.size = 2.2f + RANDOM.nextFloat() * 2.2f;
				}
			}
		}

		// ---- dandelion fluff on the breeze
		boolean fluff = config.fluff && sunY > 0.0f && !rain;
		for (Puff puff : FLUFFS) {
			if (puff.alive) {
				puff.x += puff.vx + Weather.windX() * Weather.gust() * 0.12;
				puff.z += puff.vz + Weather.windZ() * Weather.gust() * 0.12;
				puff.y += Mth.sin(puff.age * 1.7f + puff.size * 90.0f) * 0.006 + 0.002;
				puff.age += 0.05f;
				if (puff.age >= puff.life || !level.getBlockState(pos.set(Mth.floor(puff.x), Mth.floor(puff.y), Mth.floor(puff.z))).isAir()) {
					puff.alive = false;
				}
			} else if (fluff && RANDOM.nextInt(10) == 0) {
				double x = player.getX() + RANDOM.nextDouble() * 28.0 - 14.0;
				double z = player.getZ() + RANDOM.nextDouble() * 28.0 - 14.0;
				double top = ground(level, pos, x, player.getY(), z);
				if (!Double.isNaN(top) && level.getBlockState(pos.set(Mth.floor(x), Mth.floor(top) - 1, Mth.floor(z))).is(Blocks.GRASS_BLOCK)
						&& level.getBrightness(LightLayer.SKY, pos.set(Mth.floor(x), Mth.floor(top) + 1, Mth.floor(z))) > 12) {
					puff.alive = true;
					puff.x = x;
					puff.y = top + 0.4 + RANDOM.nextDouble() * 2.4;
					puff.z = z;
					puff.vx = Mth.cos(windAngle) * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01;
					puff.vz = Mth.sin(windAngle) * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01;
					puff.age = 0.0f;
					puff.life = 9.0f + RANDOM.nextFloat() * 9.0f;
					puff.size = 0.03f + RANDOM.nextFloat() * 0.025f;
				}
			}
		}

		// ---- sunlight glittering on open water
		if (config.waterSparkles && sunY > 0.1f && !rain) {
			for (int i = 0; i < 7; i++) {
				double x = player.getX() + RANDOM.nextDouble() * 36.0 - 18.0;
				double z = player.getZ() + RANDOM.nextDouble() * 36.0 - 18.0;
				for (int dy = 3; dy >= -5; dy -= 2) {
					double top = Splash.surface(level, x, player.getY() + dy, z);
					if (!Double.isNaN(top)) {
						if (level.getBrightness(LightLayer.SKY, pos.set(Mth.floor(x), Mth.floor(top) + 1, Mth.floor(z))) > 12) {
							WorldGlowFx.addGlint(new Vec3(x, top + 0.03, z), 0xFFF6D8);
						}
						break;
					}
				}
			}
		}
	}

	static void render(LevelRenderContext context, float time, float night) {
		OnyxConfig config = OnyxConfig.get();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
		Vector3f forward = orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
		float rain = Mth.clamp(context.levelState().skyRenderState.rainBrightness, 0.0f, 1.0f);

		// ---- halos around the sun and the moon
		if (config.skyHalos) {
			float fogStart = context.levelState().cameraRenderState.fogData.renderDistanceStart;
			float dome = Mth.clamp(fogStart * 0.8f, 30.0f, 300.0f);
			float sunUp = Mth.clamp(sunY * 4.0f + 0.3f, 0.0f, 1.0f);
			float moonUp = Mth.clamp(-sunY * 4.0f + 0.3f, 0.0f, 1.0f);
			// Warmer and larger near the horizon.
			float low = 1.0f - Mth.clamp(Math.abs(sunY) * 1.6f, 0.0f, 1.0f);
			int sunColor = Draw.mix(0xFFF2C8, 0xFF9A4A, low);
			float sx = sunX * dome, sy = sunY * dome;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
				if (sunUp > 0.01f) {
					float size = dome * (0.42f + 0.25f * low);
					Draw.sprite(pose, buffer, sx, sy, 0, right.x * size, right.y * size, right.z * size,
							up.x * size, up.y * size, up.z * size, Draw.glow(0.2f * sunUp * rain, sunColor));
					float core = dome * 0.1f;
					Draw.sprite(pose, buffer, sx, sy, 0, right.x * core, right.y * core, right.z * core,
							up.x * core, up.y * core, up.z * core, Draw.glow(0.28f * sunUp * rain, 0xFFFFFF));
				}
				if (moonUp > 0.01f) {
					float size = dome * 0.3f;
					Draw.sprite(pose, buffer, -sx, -sy, 0, right.x * size, right.y * size, right.z * size,
							up.x * size, up.y * size, up.z * size, Draw.glow(0.3f * moonUp * rain, 0xB8CCFF));
				}
			});
		}

		// ---- sun shafts
		List<Shaft> beams = shafts;
		if (!beams.isEmpty()) {
			// Strongest when the sun is low; gone at night.
			float strength = Mth.clamp(sunY * 4.0f, 0.0f, 1.0f) * (0.075f + 0.06f * (1.0f - sunY)) * rain;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.QUADS, (pose, buffer) -> {
				for (Shaft shaft : beams) {
					float x0 = (float) (shaft.x0 - camera.x), y0 = (float) (shaft.y0 - camera.y), z0 = (float) (shaft.z0 - camera.z);
					float x1 = (float) (shaft.x1 - camera.x), y1 = (float) (shaft.y1 - camera.y), z1 = (float) (shaft.z1 - camera.z);
					// A strip along the beam, turned to face the camera.
					float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
					float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f, mz = (z0 + z1) * 0.5f;
					float cx = ay * mz - az * my, cy = az * mx - ax * mz, cz = ax * my - ay * mx;
					float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
					if (len < 1.0e-4f) {
						continue;
					}
					float shimmer = 0.75f + 0.25f * Mth.sin(time * 0.9f + shaft.phase);
					float w = 0.3f / len;
					cx *= w;
					cy *= w;
					cz *= w;
					int top = Draw.glow(strength * shimmer, 0xFFF4D0);
					int bottom = Draw.glow(strength * shimmer * 0.25f, 0xFFE8B0);
					for (int pass = 0; pass < 2; pass++) {
						// A wide soft strip plus a narrower brighter core.
						float k = pass == 0 ? 1.0f : 0.4f;
						Draw.colored(pose, buffer, x0 - cx * k, y0 - cy * k, z0 - cz * k, pass == 0 ? 0 : top);
						Draw.colored(pose, buffer, x0 + cx * k, y0 + cy * k, z0 + cz * k, pass == 0 ? 0 : top);
						Draw.colored(pose, buffer, x1 + cx * k * 1.5f, y1 + cy * k * 1.5f, z1 + cz * k * 1.5f, pass == 0 ? 0 : bottom);
						Draw.colored(pose, buffer, x1 - cx * k * 1.5f, y1 - cy * k * 1.5f, z1 - cz * k * 1.5f, pass == 0 ? 0 : bottom);
						if (pass == 0) {
							// Soft edges: centre line bright, both rims transparent.
							Draw.colored(pose, buffer, x0, y0, z0, top);
							Draw.colored(pose, buffer, x0 + cx, y0 + cy, z0 + cz, 0);
							Draw.colored(pose, buffer, x1 + cx * 1.5f, y1 + cy * 1.5f, z1 + cz * 1.5f, 0);
							Draw.colored(pose, buffer, x1, y1, z1, bottom);
							Draw.colored(pose, buffer, x0 - cx, y0 - cy, z0 - cz, 0);
							Draw.colored(pose, buffer, x0, y0, z0, top);
							Draw.colored(pose, buffer, x1, y1, z1, bottom);
							Draw.colored(pose, buffer, x1 - cx * 1.5f, y1 - cy * 1.5f, z1 - cz * 1.5f, 0);
						}
					}
				}
			});
		}

		// ---- mist and fluff share the soft glow sprite
		float mistStrength = dawn * 0.17f * (0.4f + 0.6f * rain);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (Puff puff : MISTS) {
				if (!puff.alive || mistStrength <= 0.002f) {
					continue;
				}
				float x = (float) (puff.x - camera.x), y = (float) (puff.y - camera.y), z = (float) (puff.z - camera.z);
				// Fade in and out over the puff's life, and close to the camera so it never flashes in your face.
				float life = Mth.sin(Mth.clamp(puff.age / puff.life, 0.0f, 1.0f) * Mth.PI);
				float near = Mth.clamp((x * forward.x + y * forward.y + z * forward.z - 2.0f) / 5.0f, 0.0f, 1.0f);
				float s = puff.size;
				Draw.sprite(pose, buffer, x, y, z, right.x * s * 1.6f, right.y * s * 1.6f, right.z * s * 1.6f,
						up.x * s * 0.55f, up.y * s * 0.55f, up.z * s * 0.55f, Draw.glow(mistStrength * life * near, 0xE8F0FF));
			}
			for (Puff puff : FLUFFS) {
				if (!puff.alive) {
					continue;
				}
				float x = (float) (puff.x - camera.x), y = (float) (puff.y - camera.y), z = (float) (puff.z - camera.z);
				float life = Mth.clamp(Math.min(puff.age, puff.life - puff.age) * 1.5f, 0.0f, 1.0f);
				float s = puff.size;
				int color = Draw.glow(0.85f * life, 0xFFFFFF);
				Draw.sprite(pose, buffer, x, y, z, right.x * s, right.y * s, right.z * s, up.x * s, up.y * s, up.z * s, color);
				// A faint tail below reads as the seed hanging under its parachute.
				Draw.sprite(pose, buffer, x, y - s * 1.6f, z, right.x * s * 0.35f, right.y * s * 0.35f, right.z * s * 0.35f,
						up.x * s * 1.2f, up.y * s * 1.2f, up.z * s * 1.2f, Draw.glow(0.5f * life, 0xFFF0D0));
			}
		});
	}
}
