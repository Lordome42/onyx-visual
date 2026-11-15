package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Effects attached to the world itself: light halos, ore glints, soul bursts and the rainbow. */
final class WorldGlowFx {
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final Identifier FLARE = OnyxVisual.id("textures/fx/flare.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static final float GLINT_LIFE = 0.7f;
	private static final int RAINBOW_TICKS = 2400;
	private static final int[] RAINBOW = {0xFF3030, 0xFF9020, 0xFFE830, 0x40E040, 0x30B0FF, 0x5050FF, 0xA040FF};

	private static Map<Block, Integer> lights;
	private static Map<Block, Integer> ores;
	private static volatile List<Light> lightList = List.of();
	private static final List<Glint> GLINTS = new ArrayList<>();
	private static int scanTicks;
	private static boolean wet;
	private static int rainbowAge = -1;

	private WorldGlowFx() {
	}

	private record Light(double x, double y, double z, int rgb, float phase) {
	}

	private static final class Glint {
		Vec3 pos;
		float age;
		int rgb;
	}

	private record GlintView(float x, float y, float z, float pop, int rgb) {
	}

	/** A brief star-shaped twinkle at a point; used for ores and for sunlight on water. */
	static void addGlint(Vec3 pos, int rgb) {
		if (GLINTS.size() < 90) {
			Glint glint = new Glint();
			glint.pos = pos;
			glint.rgb = rgb;
			GLINTS.add(glint);
		}
	}

	private static void initTables() {
		lights = new IdentityHashMap<>();
		for (Block block : new Block[] {Blocks.TORCH, Blocks.WALL_TORCH, Blocks.LANTERN}) {
			lights.put(block, 0xFFB050);
		}
		for (Block block : new Block[] {Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.SOUL_LANTERN}) {
			lights.put(block, 0x50D8FF);
		}
		for (Block block : new Block[] {Blocks.COPPER_TORCH, Blocks.COPPER_WALL_TORCH}) {
			lights.put(block, 0x70FF90);
		}
		lights.put(Blocks.REDSTONE_TORCH, 0xFF4030);
		lights.put(Blocks.REDSTONE_WALL_TORCH, 0xFF4030);
		lights.put(Blocks.END_ROD, 0xF0E8FF);

		ores = new IdentityHashMap<>();
		ores.put(Blocks.DIAMOND_ORE, 0x70FFF0);
		ores.put(Blocks.DEEPSLATE_DIAMOND_ORE, 0x70FFF0);
		ores.put(Blocks.EMERALD_ORE, 0x50FF80);
		ores.put(Blocks.DEEPSLATE_EMERALD_ORE, 0x50FF80);
		ores.put(Blocks.LAPIS_ORE, 0x6080FF);
		ores.put(Blocks.DEEPSLATE_LAPIS_ORE, 0x6080FF);
		ores.put(Blocks.REDSTONE_ORE, 0xFF5040);
		ores.put(Blocks.DEEPSLATE_REDSTONE_ORE, 0xFF5040);
		ores.put(Blocks.AMETHYST_CLUSTER, 0xD090FF);
		ores.put(Blocks.ANCIENT_DEBRIS, 0xFFB080);
	}

	private static int oreColor(BlockState state) {
		Integer color = ores.get(state.getBlock());
		if (color != null) {
			return color;
		}
		if (state.is(BlockTags.GOLD_ORES)) {
			return 0xFFE050;
		}
		if (state.is(BlockTags.IRON_ORES)) {
			return 0xFFD8C0;
		}
		if (state.is(BlockTags.COPPER_ORES)) {
			return 0xFFA070;
		}
		return -1;
	}

	// ------------------------------------------------------------------ tick

	static void tick(Minecraft mc, ClientLevel level, LocalPlayer player) {
		if (lights == null) {
			initTables();
		}
		OnyxConfig config = OnyxConfig.get();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int px = Mth.floor(player.getX());
		int py = Mth.floor(player.getY());
		int pz = Mth.floor(player.getZ());

		// Light sources are re-collected every two seconds.
		if (config.lightHalos) {
			if (--scanTicks <= 0) {
				scanTicks = 40;
				List<Light> found = new ArrayList<>();
				for (int x = px - 14; x <= px + 14 && found.size() < 96; x++) {
					for (int z = pz - 14; z <= pz + 14; z++) {
						for (int y = py - 7; y <= py + 9; y++) {
							Block block = level.getBlockState(pos.set(x, y, z)).getBlock();
							Integer rgb = lights.get(block);
							if (rgb != null) {
								boolean lantern = block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN;
								found.add(new Light(x + 0.5, y + (lantern ? 0.35 : block == Blocks.END_ROD ? 0.5 : 0.7), z + 0.5,
										rgb, (x * 31 + y * 17 + z * 13) % 64));
							}
						}
					}
				}
				lightList = found;
			}
		} else if (!lightList.isEmpty()) {
			lightList = List.of();
			scanTicks = 0;
		}

		// Ores with an exposed face twinkle now and then.
		if (config.oreGlints && GLINTS.size() < 40) {
			for (int attempt = 0; attempt < 60; attempt++) {
				pos.set(px + RANDOM.nextInt(17) - 8, py + RANDOM.nextInt(11) - 5, pz + RANDOM.nextInt(17) - 8);
				int rgb = oreColor(level.getBlockState(pos));
				if (rgb < 0 || RANDOM.nextInt(3) != 0) {
					continue;
				}
				Direction face = Direction.values()[RANDOM.nextInt(6)];
				BlockPos origin = pos.immutable();
				if (!level.getBlockState(origin.relative(face)).isAir()) {
					continue;
				}
				// A random point on the exposed face, lifted just off the surface.
				double u = RANDOM.nextDouble() * 0.8 - 0.4;
				double v = RANDOM.nextDouble() * 0.8 - 0.4;
				double fx = face.getStepX();
				double fy = face.getStepY();
				double fz = face.getStepZ();
				Glint glint = new Glint();
				glint.pos = new Vec3(
						origin.getX() + 0.5 + fx * 0.53 + (fx == 0 ? u : 0),
						origin.getY() + 0.5 + fy * 0.53 + (fy == 0 ? (fx == 0 ? v : u) : 0),
						origin.getZ() + 0.5 + fz * 0.53 + (fz == 0 ? v : 0));
				glint.rgb = rgb;
				GLINTS.add(glint);
			}
		}

		// A mob that just started dying releases a rising burst of pale light.
		if (config.soulBurst) {
			for (Entity entity : level.entitiesForRendering()) {
				if (entity instanceof LivingEntity living && living != player && living.deathTime == 1
						&& entity.distanceToSqr(player) < 32.0 * 32.0) {
					BurstFx.soul(entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0));
				}
			}
		}

		// Rainbow: armed while it rains, shown once the rain stops.
		float rain = level.getRainLevel(1.0f);
		if (rain > 0.5f) {
			wet = true;
			rainbowAge = -1;
		} else if (wet && rain < 0.2f) {
			wet = false;
			rainbowAge = 0;
		}
		if (rainbowAge >= 0 && ++rainbowAge > RAINBOW_TICKS) {
			rainbowAge = -1;
		}
	}

	static void reset() {
		lightList = List.of();
		GLINTS.clear();
		wet = false;
		rainbowAge = -1;
		scanTicks = 0;
	}

	// ---------------------------------------------------------------- render

	static void render(LevelRenderContext context, float time, float dt, float night, boolean overworld) {
		OnyxConfig config = OnyxConfig.get();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Quaternionf orientation = context.levelState().cameraRenderState.orientation;
		Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));

		List<Light> lightsNow = lightList;
		if (config.lightHalos && !lightsNow.isEmpty()) {
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
				for (Light light : lightsNow) {
					float flicker = 0.85f + 0.1f * Mth.sin(time * 9.0f + light.phase) + 0.05f * Mth.sin(time * 23.0f + light.phase * 2.0f);
					float size = 1.05f * flicker;
					Draw.sprite(pose, buffer, (float) (light.x - camera.x), (float) (light.y - camera.y), (float) (light.z - camera.z),
							right.x * size, right.y * size, right.z * size, up.x * size, up.y * size, up.z * size,
							Draw.glow(0.8f * flicker, light.rgb));
				}
			});
		}

		if (!GLINTS.isEmpty()) {
			List<GlintView> views = new ArrayList<>(GLINTS.size());
			for (Iterator<Glint> it = GLINTS.iterator(); it.hasNext(); ) {
				Glint glint = it.next();
				glint.age += dt;
				if (glint.age >= GLINT_LIFE) {
					it.remove();
					continue;
				}
				views.add(new GlintView((float) (glint.pos.x - camera.x), (float) (glint.pos.y - camera.y),
						(float) (glint.pos.z - camera.z), Mth.sin(glint.age / GLINT_LIFE * Mth.PI), glint.rgb));
			}
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(FLARE), (pose, buffer) -> {
				for (GlintView view : views) {
					float size = 0.08f + 0.2f * view.pop;
					Draw.sprite(pose, buffer, view.x, view.y, view.z, right.x * size, right.y * size, right.z * size,
							up.x * size, up.y * size, up.z * size, Draw.glow(view.pop, Draw.mix(view.rgb, 0xFFFFFF, 0.35f)));
				}
			});
		}

		if (overworld && config.rainbow && rainbowAge >= 0) {
			float fadeIn = Mth.clamp(rainbowAge / 60.0f, 0.0f, 1.0f);
			float fadeOut = Mth.clamp((RAINBOW_TICKS - rainbowAge) / 300.0f, 0.0f, 1.0f);
			float strength = fadeIn * fadeOut * (1.0f - night) * 0.55f;
			if (strength > 0.01f) {
				rainbow(context, strength);
			}
		}
	}

	private static void rainbow(LevelRenderContext context, float strength) {
		float fogStart = context.levelState().cameraRenderState.fogData.renderDistanceStart;
		float dome = Mth.clamp(fogStart * 0.85f, 30.0f, 300.0f);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.TRIANGLES, (pose, buffer) -> {
			// The arc is centred a little below the northern horizon and opens upwards.
			float dip = 0.14f;
			float cx = 0.0f;
			float cy = -Mth.sin(dip);
			float cz = -Mth.cos(dip);
			float vx = 0.0f;
			float vy = Mth.cos(dip);
			float vz = -Mth.sin(dip);
			int segments = 72;
			int bands = RAINBOW.length;
			float inner = 0.66f;
			float width = 0.016f;
			for (int i = 0; i < segments; i++) {
				float a0 = i / (float) segments * Mth.PI;
				float a1 = (i + 1) / (float) segments * Mth.PI;
				// Fade the feet of the arc into the horizon.
				float e0 = Mth.clamp(Mth.sin(a0) * 3.0f, 0.0f, 1.0f) * strength;
				float e1 = Mth.clamp(Mth.sin(a1) * 3.0f, 0.0f, 1.0f) * strength;
				for (int band = -1; band < bands; band++) {
					// Red sits on the outside, violet inside; one extra strip on each edge fades to nothing.
					float r0 = inner + width * (bands - band);
					float r1 = inner + width * (bands - band - 1);
					int outerRgb = RAINBOW[Math.max(band, 0)];
					int innerRgb = RAINBOW[Math.min(band + 1, bands - 1)];
					float outerA = band < 0 ? 0.0f : 1.0f;
					float innerA = band + 1 >= bands ? 0.0f : 1.0f;
					float[] p00 = point(cx, cy, cz, vx, vy, vz, r0, a0, dome);
					float[] p01 = point(cx, cy, cz, vx, vy, vz, r0, a1, dome);
					float[] p10 = point(cx, cy, cz, vx, vy, vz, r1, a0, dome);
					float[] p11 = point(cx, cy, cz, vx, vy, vz, r1, a1, dome);
					int c00 = Draw.glow(e0 * outerA, outerRgb);
					int c01 = Draw.glow(e1 * outerA, outerRgb);
					int c10 = Draw.glow(e0 * innerA, innerRgb);
					int c11 = Draw.glow(e1 * innerA, innerRgb);
					Draw.colored(pose, buffer, p00[0], p00[1], p00[2], c00);
					Draw.colored(pose, buffer, p01[0], p01[1], p01[2], c01);
					Draw.colored(pose, buffer, p11[0], p11[1], p11[2], c11);
					Draw.colored(pose, buffer, p00[0], p00[1], p00[2], c00);
					Draw.colored(pose, buffer, p11[0], p11[1], p11[2], c11);
					Draw.colored(pose, buffer, p10[0], p10[1], p10[2], c10);
				}
			}
		});
	}

	/** Point on the sky dome at angular radius {@code rho} around the centre direction. */
	private static float[] point(float cx, float cy, float cz, float vx, float vy, float vz, float rho, float theta, float dome) {
		float sin = Mth.sin(rho);
		float cos = Mth.cos(rho);
		// The horizontal axis of the arc is +X (east-west).
		float x = cos * cx + sin * Mth.cos(theta);
		float y = cos * cy + sin * Mth.sin(theta) * vy;
		float z = cos * cz + sin * Mth.sin(theta) * vz;
		return new float[] {x * dome, y * dome, z * dome};
	}
}
