package dev.onyx.visual.life;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.life.Critters.Critter;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Spawns, updates and draws the client-side wildlife and ground decorations. */
public final class LifeFx {
	private static final Identifier TEXTURE = OnyxVisual.id("textures/life/materials.png");
	private static final RandomSource RANDOM = Critters.RANDOM;
	private static final List<Critter> CRITTERS = new ArrayList<>();
	private static volatile List<Critter> snapshot = List.of();
	private static int flockTimer = 200;
	/** How long the player has been standing still, in ticks. */
	static int stillTicks;

	private LifeFx() {
	}

	public static void reset() {
		CRITTERS.clear();
		snapshot = List.of();
		Ground.reset();
		Pebbles.reset();
	}

	private static int count(Class<?> type) {
		int n = 0;
		for (Critter critter : CRITTERS) {
			if (type.isInstance(critter)) {
				n++;
			}
		}
		return n;
	}

	public static void tick(Minecraft mc, ClientLevel level, LocalPlayer player) {
		if (mc.isPaused()) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		stillTicks = motion.x * motion.x + motion.z * motion.z < 1.0e-4 && player.onGround() ? stillTicks + 1 : 0;
		Ground.tick(level, player);
		Pebbles.tick(mc, level, player);
		for (Critter critter : CRITTERS) {
			critter.baseTick(level, player);
		}
		CRITTERS.removeIf(critter -> critter.dead);
		if (level.dimension() == Level.OVERWORLD && level.getGameTime() % 4 == 0 && CRITTERS.size() < 60) {
			trySpawn(level, player);
		}
		snapshot = List.copyOf(CRITTERS);
	}

	private static void trySpawn(ClientLevel level, LocalPlayer player) {
		OnyxConfig config = OnyxConfig.get();
		boolean day = !level.isDarkOutside();
		boolean rain = level.isRaining();

		if (config.birds && day && !rain && --flockTimer <= 0) {
			flockTimer = 300 + RANDOM.nextInt(500);
			if (count(Critters.Flock.class) < 2) {
				float from = RANDOM.nextFloat() * Mth.TWO_PI;
				float heading = from + Mth.PI + (RANDOM.nextFloat() - 0.5f) * 0.8f;
				CRITTERS.add(new Critters.Flock(player.getX() - Mth.sin(from) * 110.0, player.getY() + 34.0 + RANDOM.nextInt(20),
						player.getZ() + Mth.cos(from) * 110.0, heading));
			}
		}

		float angle = RANDOM.nextFloat() * Mth.TWO_PI;
		double dist = 9.0 + RANDOM.nextDouble() * 17.0;
		double x = player.getX() + Mth.cos(angle) * dist;
		double z = player.getZ() + Mth.sin(angle) * dist;
		double ground = Terrain.groundY(level, x, player.getY() + 3.0, z, true);
		if (Double.isNaN(ground)) {
			ground = Terrain.groundY(level, x, player.getY() - 3.0, z, true);
		}
		if (Double.isNaN(ground)) {
			return;
		}
		BlockState surface = Terrain.stateAt(level, x, ground - 0.5, z);
		// Only under open sky, so nothing appears in caves or houses.
		if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, net.minecraft.core.BlockPos.containing(x, ground + 1.0, z)) < 12) {
			return;
		}

		if (surface.is(Blocks.WATER)) {
			if (config.fish && RANDOM.nextInt(4) == 0 && count(Critters.Fish.class) < 2
					&& Terrain.isWater(level, x, ground - 1.5, z)) {
				// Only where the water is at least two blocks deep.
				double top = dev.onyx.visual.fx.Splash.surface(level, x, ground - 0.5, z);
				if (!Double.isNaN(top)) {
					CRITTERS.add(new Critters.Fish(x, top, z));
				}
			} else if (config.butterflies && day && !rain && count(Critters.Dragonfly.class) < 5) {
				CRITTERS.add(new Critters.Dragonfly(x, ground, z));
			}
			return;
		}
		boolean green = surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.MOSS_BLOCK) || surface.is(Blocks.PODZOL);
		boolean shore = green || surface.is(BlockTags.SAND);
		if (!shore) {
			return;
		}
		if (config.glowBugs && green && !day && !rain && RANDOM.nextInt(3) == 0 && count(Critters.Firefly.class) < 9) {
			CRITTERS.add(new Critters.Firefly(x, ground + 0.6 + RANDOM.nextDouble(), z));
			return;
		}
		int roll = RANDOM.nextInt(100);
		if (!green) {
			// Sand: crabs live near the water's edge.
			if (config.crabs && roll < 40 && count(Critters.Crab.class) < 5 && waterNearby(level, x, ground, z)) {
				CRITTERS.add(new Critters.Crab(x, ground, z));
				return;
			}
		}
		if (roll >= 78 && roll < 88 && !rain) {
			// Hedgehogs come out mostly around dusk and at night.
			if (config.hedgehogs && green && (!day || RANDOM.nextInt(4) == 0) && count(Critters.Hedgehog.class) < 3) {
				CRITTERS.add(new Critters.Hedgehog(x, ground, z));
			}
			return;
		}
		if (roll < 30) {
			if (config.butterflies && green && day && !rain && count(Critters.Butterfly.class) < 10) {
				CRITTERS.add(new Critters.Butterfly(x, ground, z));
			}
		} else if (roll < 50) {
			if (config.birds && green && day && !rain && count(Critters.Sparrow.class) < 9) {
				int group = 2 + RANDOM.nextInt(3);
				for (int i = 0; i < group; i++) {
					double sx = x + RANDOM.nextDouble() * 3.0 - 1.5;
					double sz = z + RANDOM.nextDouble() * 3.0 - 1.5;
					double sy = Terrain.groundY(level, sx, ground + 0.5, sz, false);
					if (!Double.isNaN(sy)) {
						CRITTERS.add(new Critters.Sparrow(sx, sy, sz));
					}
				}
			}
		} else if (roll < 70) {
			if (config.geese && day && count(Critters.Goose.class) < 6 && waterNearby(level, x, ground, z)) {
				int group = 2 + RANDOM.nextInt(3);
				for (int i = 0; i < group; i++) {
					double gx = x + RANDOM.nextDouble() * 4.0 - 2.0;
					double gz = z + RANDOM.nextDouble() * 4.0 - 2.0;
					double gy = Terrain.groundY(level, gx, ground + 0.5, gz, true);
					if (!Double.isNaN(gy)) {
						Critters.Goose goose = new Critters.Goose(gx, gy, gz, x, z);
						CRITTERS.add(goose);
						if (i == 0 && RANDOM.nextInt(2) == 0) {
							int chicks = 2 + RANDOM.nextInt(3);
							for (int c = 0; c < chicks; c++) {
								CRITTERS.add(new Critters.Gosling(goose, gx + c * 0.3 - 0.4, gy, gz - 0.6));
							}
						}
					}
				}
			}
		} else if (roll < (rain ? 95 : 78)) {
			if (config.snails && green && count(Critters.Snail.class) < (rain ? 8 : 4)) {
				CRITTERS.add(new Critters.Snail(x, ground, z));
			}
		}
	}

	private static boolean waterNearby(ClientLevel level, double x, double y, double z) {
		for (int i = 0; i < 16; i++) {
			if (Terrain.isWater(level, x + RANDOM.nextInt(13) - 6, y - 0.5 - RANDOM.nextInt(2), z + RANDOM.nextInt(13) - 6)) {
				return true;
			}
		}
		return false;
	}

	/** Puts one of everything in front of the player; used by the automated visual test. */
	public static void spawnShowcase(ClientLevel level, LocalPlayer player) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Critters.Goose mother = new Critters.Goose(x - 2.2, y, z + 5.0, x - 2.2, z + 5.0);
		CRITTERS.add(mother);
		for (int i = 0; i < 3; i++) {
			CRITTERS.add(new Critters.Gosling(mother, x - 2.6 + i * 0.4, y, z + 4.2));
		}
		CRITTERS.add(new Critters.Goose(x - 0.8, y, z + 6.0, x - 2.2, z + 5.0));
		CRITTERS.add(new Critters.Hedgehog(x - 1.2, y, z + 2.8));
		CRITTERS.add(new Critters.Crab(x + 1.6, y, z + 3.0));
		CRITTERS.add(new Critters.Snail(x + 0.5, y, z + 2.6));
		CRITTERS.add(new Critters.Butterfly(x + 1.5, y, z + 4.0));
		CRITTERS.add(new Critters.Butterfly(x - 1.0, y, z + 3.5));
		CRITTERS.add(new Critters.Butterfly(x + 0.2, y, z + 4.5));
		CRITTERS.add(new Critters.Dragonfly(x + 3.0, y, z + 5.0));
		CRITTERS.add(new Critters.Flock(x, y + 14.0, z + 30.0, Mth.HALF_PI));
		for (int i = 0; i < 3; i++) {
			CRITTERS.add(new Critters.Sparrow(x + 2.2 + i * 0.7, y, z + 6.5 + i * 0.5));
		}
	}

	/** The creature under the crosshair, or null. */
	private static Critter aimed(Vec3 eye, Vec3 look, double reach, Class<?> only) {
		Critter best = null;
		double bestScore = 0.45;
		for (Critter critter : CRITTERS) {
			if (only != null ? !only.isInstance(critter) : critter instanceof Critters.Flock || critter instanceof Critters.Fish) {
				continue;
			}
			double dx = critter.x - eye.x, dy = critter.y + critter.height() * 0.5 - eye.y, dz = critter.z - eye.z;
			double along = dx * look.x + dy * look.y + dz * look.z;
			if (along < 0.2 || along > reach) {
				continue;
			}
			double ox = dx - look.x * along, oy = dy - look.y * along, oz = dz - look.z * along;
			double off = Math.sqrt(ox * ox + oy * oy + oz * oz) / (0.6 + critter.height());
			if (off < bestScore) {
				bestScore = off;
				best = critter;
			}
		}
		return best;
	}

	/** Catches the firefly under the crosshair. */
	static boolean catchFirefly(Vec3 eye, Vec3 look, double reach) {
		Critter bug = aimed(eye, look, reach, Critters.Firefly.class);
		if (bug == null) {
			return false;
		}
		bug.dead = true;
		CRITTERS.remove(bug);
		snapshot = List.copyOf(CRITTERS);
		return true;
	}

	static boolean aimingAtFirefly(Vec3 eye, Vec3 look, double reach) {
		return aimed(eye, look, reach, Critters.Firefly.class) != null;
	}

	static void releaseFirefly(double x, double y, double z) {
		CRITTERS.add(new Critters.Firefly(x, y, z));
		snapshot = List.copyOf(CRITTERS);
	}

	/** Pets the creature under the crosshair: a happy hop and a few hearts. */
	static boolean pet(ClientLevel level, Vec3 eye, Vec3 look, double reach) {
		Critter critter = aimed(eye, look, reach, null);
		if (critter == null) {
			return false;
		}
		if (critter.pet()) {
			for (int i = 0; i < 3; i++) {
				level.addParticle(net.minecraft.core.particles.ParticleTypes.HEART,
						critter.x + (RANDOM.nextDouble() - 0.5) * 0.4, critter.y + critter.height() + 0.3 + RANDOM.nextDouble() * 0.2,
						critter.z + (RANDOM.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
			}
		}
		return true;
	}

	/** Startles everything on the ground within a radius of a point. */
	static void scare(double x, double z, double radius) {
		for (Critter critter : CRITTERS) {
			double dx = critter.x - x, dz = critter.z - z;
			if (dx * dx + dz * dz < radius * radius) {
				critter.scare(x, z);
			}
		}
	}

	/** Test hooks. */
	public static void debugSpawnFireflies(double x, double y, double z, int count) {
		for (int i = 0; i < count; i++) {
			CRITTERS.add(new Critters.Firefly(x + i * 0.5 - count * 0.25, y + (i % 3) * 0.3, z));
		}
	}

	public static void debugSnowPiles(double x, double y, double z) {
		Ground.debugPiles(x, y, z);
	}

	public static int debugSnowmen() {
		return Ground.snowmen();
	}

	public static void debugWet() {
		Ground.debugWet();
	}

	/** A molten fish leaping from a lava surface (called by the Nether effects). */
	public static void spawnLavaFish(double x, double surface, double z) {
		if (count(Critters.Fish.class) < 3) {
			Critters.Fish fish = new Critters.Fish(x, surface, z);
			fish.lava = true;
			CRITTERS.add(fish);
			snapshot = List.copyOf(CRITTERS);
		}
	}

	/** Test hook: a group of geese (with goslings) at a spot. */
	public static void spawnGeese(double x, double y, double z, int count) {
		for (int i = 0; i < count; i++) {
			Critters.Goose goose = new Critters.Goose(x + i * 0.4, y, z + i * 0.3, x, z);
			CRITTERS.add(goose);
			if (i == 0) {
				CRITTERS.add(new Critters.Gosling(goose, x, y, z - 0.5));
				CRITTERS.add(new Critters.Gosling(goose, x + 0.3, y, z - 0.5));
			}
		}
	}

	/** Test hook: how many ground creatures currently sit inside a solid block, and how many exist. */
	public static int[] embeddedCount(ClientLevel level) {
		int inside = 0;
		int total = 0;
		for (Critter critter : CRITTERS) {
			if (critter instanceof Critters.Goose || critter instanceof Critters.Gosling
					|| critter instanceof Critters.Hedgehog || critter instanceof Critters.Crab) {
				total++;
				net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(critter.x, critter.y + 0.3, critter.z);
				// Standing on a slab or stair is fine; only count points that lie within the block's actual shape.
				net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
				if (!shape.isEmpty() && critter.y + 0.3 - pos.getY() < shape.max(net.minecraft.core.Direction.Axis.Y) - 0.05) {
					inside++;
				}
			}
		}
		return new int[] {inside, total};
	}

	/** Test hook: summed distance of all geese from a point (used to check they keep moving). */
	public static double gooseSpread(double x, double z) {
		double sum = 0.0;
		for (Critter critter : CRITTERS) {
			if (critter instanceof Critters.Goose) {
				sum += Math.abs(critter.x - x) + Math.abs(critter.z - z);
			}
		}
		return sum;
	}

	public static void render(LevelRenderContext context, float time) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		List<Critter> critters = snapshot;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
			Model model = new Model(pose, buffer);
			Ground.render(model, camera, partial, time);
			Pebbles.render(model, level, camera, partial);
			for (Critter critter : critters) {
				float cx = (float) (Mth.lerp(partial, critter.px, critter.x) - camera.x);
				float cy = (float) (Mth.lerp(partial, critter.py, critter.y) - camera.y);
				float cz = (float) (Mth.lerp(partial, critter.pz, critter.z) - camera.z);
				if (critter.happy > 0) {
					// A pleased little hop.
					cy += Math.abs(Mth.sin((critter.happy - partial) * 0.4f)) * 0.16f;
				}
				critter.render(model, level, cx, cy, cz, partial, time);
			}
		});
	}
}
