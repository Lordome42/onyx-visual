package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.life.LifeFx;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Nether atmosphere: embers and spores tinted by what lies below, soul wisps, and lively lava. */
public final class NetherFx {
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int MOTES = 170;
	private static final int WISPS = 6;
	private static final int EMBER = 0, SOUL = 1, WARPED = 2, CRIMSON = 3;
	private static final int[] COLORS = {0xFF8A30, 0x60DCFF, 0x40F0C8, 0xFF4858};

	private static final class Mote {
		double x, y, z, vx, vy, vz;
		float age, life, size, phase;
		int kind;
		boolean lantern;
		boolean alive;
	}

	private static final Mote[] POOL = new Mote[MOTES];
	private static final Mote[] SOULS = new Mote[WISPS];

	static {
		for (int i = 0; i < MOTES; i++) {
			POOL[i] = new Mote();
		}
		for (int i = 0; i < WISPS; i++) {
			SOULS[i] = new Mote();
		}
	}

	private NetherFx() {
	}

	static void reset() {
		for (Mote mote : POOL) {
			mote.alive = false;
		}
		for (Mote mote : SOULS) {
			mote.alive = false;
		}
	}

	/** A snowball bursting at a point. */
	public static void snowPuff(double x, double y, double z) {
		BurstFx.snow(new Vec3(x, y, z));
	}

	/** A fiery splash on a lava surface. */
	public static void lavaSplash(double x, double y, double z, float strength) {
		BurstFx.lava(new Vec3(x, y, z), strength);
	}

	/** What kind of ground lies within a few blocks below a point; -1 if there is none. */
	private static int groundKind(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		for (int down = 0; down < 10; down++) {
			BlockState state = level.getBlockState(pos.set(x, y - down, z));
			if (state.isAir()) {
				continue;
			}
			if (state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) {
				return SOUL;
			}
			if (state.is(Blocks.WARPED_NYLIUM) || state.is(Blocks.WARPED_WART_BLOCK)) {
				return WARPED;
			}
			if (state.is(Blocks.CRIMSON_NYLIUM) || state.is(Blocks.NETHER_WART_BLOCK)) {
				return CRIMSON;
			}
			return EMBER;
		}
		return -1;
	}

	static void tick(ClientLevel level, LocalPlayer player) {
		OnyxConfig config = OnyxConfig.get();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		// ---- drifting motes
		int spawned = 0;
		for (Mote mote : POOL) {
			if (mote.alive) {
				mote.x += mote.vx + Mth.sin(mote.age * 2.0f + mote.phase) * 0.006;
				mote.y += mote.vy;
				mote.z += mote.vz + Mth.cos(mote.age * 1.7f + mote.phase) * 0.006;
				mote.age += 0.05f;
				double dx = mote.x - player.getX();
				double dz = mote.z - player.getZ();
				if (mote.age >= mote.life || dx * dx + dz * dz > 20.0 * 20.0
						|| !level.getBlockState(pos.set(Mth.floor(mote.x), Mth.floor(mote.y), Mth.floor(mote.z))).isAir()) {
					mote.alive = false;
				}
			} else if (config.netherMotes && spawned < 8) {
				int x = Mth.floor(player.getX()) + RANDOM.nextInt(31) - 15;
				int y = Mth.floor(player.getY()) + RANDOM.nextInt(13) - 4;
				int z = Mth.floor(player.getZ()) + RANDOM.nextInt(31) - 15;
				if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
					continue;
				}
				int kind = groundKind(level, pos, x, y, z);
				if (kind < 0) {
					continue;
				}
				spawned++;
				mote.alive = true;
				mote.kind = kind;
				mote.x = x + RANDOM.nextDouble();
				mote.y = y + RANDOM.nextDouble();
				mote.z = z + RANDOM.nextDouble();
				mote.age = 0.0f;
				mote.phase = RANDOM.nextFloat() * Mth.TWO_PI;
				// Embers shoot upwards; spores and soul motes hang and drift.
				boolean ember = kind == EMBER;
				mote.lantern = !ember && RANDOM.nextInt(7) == 0;
				mote.vy = ember ? 0.02 + RANDOM.nextDouble() * 0.05 : 0.004 + RANDOM.nextDouble() * 0.012;
				mote.vx = (RANDOM.nextDouble() - 0.5) * 0.02;
				mote.vz = (RANDOM.nextDouble() - 0.5) * 0.02;
				mote.life = ember ? 2.5f + RANDOM.nextFloat() * 3.5f : 5.0f + RANDOM.nextFloat() * 6.0f;
				mote.size = mote.lantern ? 0.22f + RANDOM.nextFloat() * 0.14f : 0.045f + RANDOM.nextFloat() * 0.05f;
			}
		}

		// ---- soul wisps spiralling up out of soul sand
		for (Mote wisp : SOULS) {
			if (wisp.alive) {
				wisp.y += 0.035;
				wisp.age += 0.05f;
				if (wisp.age >= wisp.life) {
					wisp.alive = false;
				}
			} else if (config.soulWisps && RANDOM.nextInt(25) == 0) {
				int x = Mth.floor(player.getX()) + RANDOM.nextInt(25) - 12;
				int z = Mth.floor(player.getZ()) + RANDOM.nextInt(25) - 12;
				for (int y = Mth.floor(player.getY()) + 4; y >= Mth.floor(player.getY()) - 6; y--) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					if ((state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
						wisp.alive = true;
						wisp.x = x + 0.5;
						wisp.y = y + 1.0;
						wisp.z = z + 0.5;
						wisp.age = 0.0f;
						wisp.life = 4.5f;
						wisp.phase = RANDOM.nextFloat() * Mth.TWO_PI;
					}
					break;
				}
			}
		}

		// ---- lava: popping bubbles, spitting sparks and the occasional leaping fish
		if (config.lavaLife) {
			for (int i = 0; i < 5; i++) {
				int x = Mth.floor(player.getX()) + RANDOM.nextInt(33) - 16;
				int z = Mth.floor(player.getZ()) + RANDOM.nextInt(33) - 16;
				for (int y = Mth.floor(player.getY()) + 5; y >= Mth.floor(player.getY()) - 12; y--) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					if (state.is(Blocks.LAVA) && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
						double sx = x + RANDOM.nextDouble();
						double sz = z + RANDOM.nextDouble();
						int roll = RANDOM.nextInt(60);
						if (roll == 0 && level.getBlockState(pos.set(x, y - 1, z)).is(Blocks.LAVA)) {
							LifeFx.spawnLavaFish(sx, y + 0.9, sz);
						} else if (roll < 22) {
							BurstFx.lava(new Vec3(sx, y + 0.92, sz), 0.25f + RANDOM.nextFloat() * 0.35f);
						}
					}
					break;
				}
			}
		}
	}

	static void render(LevelRenderContext context, float time) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
			for (Mote mote : POOL) {
				if (!mote.alive) {
					continue;
				}
				float x = (float) (mote.x - camera.x), y = (float) (mote.y - camera.y), z = (float) (mote.z - camera.z);
				float fade = Mth.clamp(Math.min(mote.age * 2.0f, (mote.life - mote.age) * 1.2f), 0.0f, 1.0f);
				float flicker = mote.kind == EMBER ? 0.65f + 0.35f * Mth.sin(time * 19.0f + mote.phase * 7.0f)
						: 0.8f + 0.2f * Mth.sin(time * 2.4f + mote.phase);
				float s = mote.size;
				int color = COLORS[mote.kind];
				if (mote.lantern) {
					Draw.sprite(pose, buffer, x, y, z, right.x * s, right.y * s, right.z * s, up.x * s, up.y * s, up.z * s,
							Draw.glow(0.3f * fade * flicker, color));
					s *= 0.3f;
				}
				Draw.sprite(pose, buffer, x, y, z, right.x * s, right.y * s, right.z * s, up.x * s, up.y * s, up.z * s,
						Draw.glow(fade * flicker, Draw.mix(color, 0xFFFFFF, mote.kind == EMBER ? 0.25f : 0.45f)));
			}
			for (Mote wisp : SOULS) {
				if (!wisp.alive) {
					continue;
				}
				float fade = Mth.sin(Mth.clamp(wisp.age / wisp.life, 0.0f, 1.0f) * Mth.PI);
				// A rising spiral with a tail of shrinking lights behind the head.
				for (int i = 9; i >= 0; i--) {
					float t = wisp.age - i * 0.07f;
					if (t < 0.0f) {
						continue;
					}
					float angle = t * 3.2f + wisp.phase;
					float radius = 0.35f + 0.12f * Mth.sin(t * 2.0f);
					float x = (float) (wisp.x - camera.x) + Mth.cos(angle) * radius;
					float y = (float) (wisp.y - camera.y) - i * 0.07f * 0.7f;
					float z = (float) (wisp.z - camera.z) + Mth.sin(angle) * radius;
					float k = 1.0f - i / 10.0f;
					float s = i == 0 ? 0.26f : 0.04f + 0.12f * k;
					Draw.sprite(pose, buffer, x, y, z, right.x * s, right.y * s, right.z * s, up.x * s, up.y * s, up.z * s,
							Draw.glow(fade * (i == 0 ? 1.0f : 0.6f * k), i == 0 ? 0xD8F8FF : 0x50D0FF));
				}
			}
		});
	}
}
