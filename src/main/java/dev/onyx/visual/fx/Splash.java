package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Water splashes: crowns of droplets and ripples spreading over the surface. */
public final class Splash {
	private static final Identifier GLOW = OnyxVisual.id("textures/fx/glow.png");
	private static final Identifier RING = OnyxVisual.id("textures/fx/halo.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Drop> DROPS = new ArrayList<>();
	private static final List<Ripple> RIPPLES = new ArrayList<>();
	private static boolean wasInWater;
	private static int wade;

	private Splash() {
	}

	private static final class Drop {
		double x, y, z, vx, vy, vz;
		float age, life, size;
	}

	private static final class Ripple {
		double x, y, z;
		float age, life, size;
	}

	private record DropView(float x, float y, float z, float size, float fade) {
	}

	private record RippleView(float x, float y, float z, float size, float fade) {
	}

	public static void reset() {
		DROPS.clear();
		RIPPLES.clear();
		wasInWater = false;
	}

	/** A ring spreading over the water at the given surface point. */
	public static void ripple(double x, double y, double z, float size) {
		if (!OnyxConfig.get().splashes || RIPPLES.size() >= 90) {
			return;
		}
		Ripple ripple = new Ripple();
		ripple.x = x;
		ripple.y = y;
		ripple.z = z;
		ripple.size = size;
		ripple.life = 0.7f + size * 0.5f;
		RIPPLES.add(ripple);
	}

	/** A crown of droplets plus ripples; {@code strength} is roughly 0.3 (small) to 1.5 (cannonball). */
	public static void burst(double x, double y, double z, float strength) {
		if (!OnyxConfig.get().splashes) {
			return;
		}
		ripple(x, y, z, 0.6f + strength);
		ripple(x, y, z, 0.3f + strength * 0.5f);
		int count = Math.min(40, (int) (8 + strength * 22));
		for (int i = 0; i < count && DROPS.size() < 260; i++) {
			float angle = RANDOM.nextFloat() * Mth.TWO_PI;
			float out = (0.03f + RANDOM.nextFloat() * 0.09f) * (0.6f + strength);
			Drop drop = new Drop();
			drop.x = x + Mth.cos(angle) * 0.15;
			drop.y = y + 0.05;
			drop.z = z + Mth.sin(angle) * 0.15;
			drop.vx = Mth.cos(angle) * out;
			drop.vz = Mth.sin(angle) * out;
			drop.vy = (0.12f + RANDOM.nextFloat() * 0.2f) * (0.6f + strength * 0.7f);
			drop.life = 0.5f + RANDOM.nextFloat() * 0.6f;
			drop.size = 0.035f + RANDOM.nextFloat() * 0.04f;
			DROPS.add(drop);
		}
	}

	/** Height of the water surface in the column at this position, or NaN if it is not open water. */
	public static double surface(ClientLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		if (!level.getBlockState(pos).is(Blocks.WATER)) {
			pos.move(0, -1, 0);
			if (!level.getBlockState(pos).is(Blocks.WATER)) {
				return Double.NaN;
			}
		}
		for (int i = 0; i < 4; i++) {
			pos.move(0, 1, 0);
			if (!level.getBlockState(pos).is(Blocks.WATER)) {
				return level.getBlockState(pos).isAir() ? pos.getY() - 0.11 : Double.NaN;
			}
		}
		return Double.NaN;
	}

	static void tick(ClientLevel level, LocalPlayer player) {
		for (Iterator<Drop> it = DROPS.iterator(); it.hasNext(); ) {
			Drop drop = it.next();
			drop.x += drop.vx;
			drop.y += drop.vy;
			drop.z += drop.vz;
			drop.vy -= 0.035;
			drop.age += 0.05f;
			if (drop.age >= drop.life) {
				it.remove();
			}
		}
		if (!OnyxConfig.get().splashes) {
			wasInWater = player.isInWater();
			return;
		}
		boolean inWater = player.isInWater();
		Vec3 motion = player.getDeltaMovement();
		if (inWater != wasInWater) {
			double top = surface(level, player.getX(), player.getY() + (inWater ? 0.2 : -0.3), player.getZ());
			if (!Double.isNaN(top)) {
				// Diving in makes a big splash, climbing out a small one.
				float strength = inWater ? Mth.clamp((float) (-motion.y) * 1.6f + 0.35f, 0.35f, 1.6f) : 0.3f;
				burst(player.getX(), top, player.getZ(), strength);
			}
		} else if (inWater && !player.isUnderWater()) {
			double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
			if (++wade >= (speed > 0.05 ? 3 : 14)) {
				wade = 0;
				double top = surface(level, player.getX(), player.getY() + 0.5, player.getZ());
				if (!Double.isNaN(top)) {
					ripple(player.getX(), top, player.getZ(), 0.5f + (float) speed * 3.0f);
					if (speed > 0.12) {
						burst(player.getX() + motion.x * 3.0, top, player.getZ() + motion.z * 3.0, 0.15f);
					}
				}
			}
		}
		wasInWater = inWater;

		// A boat (or anything else you ride across water) leaves a wake.
		if (player.getVehicle() != null) {
			net.minecraft.world.entity.Entity vehicle = player.getVehicle();
			Vec3 drive = vehicle.getDeltaMovement();
			double speed = Math.sqrt(drive.x * drive.x + drive.z * drive.z);
			if (speed > 0.04 && ++wade >= 2) {
				wade = 0;
				double top = surface(level, vehicle.getX(), vehicle.getY() + 0.3, vehicle.getZ());
				if (!Double.isNaN(top)) {
					double bx = vehicle.getX() - drive.x / speed * 0.9;
					double bz = vehicle.getZ() - drive.z / speed * 0.9;
					ripple(bx, top, bz, 0.6f + (float) speed * 2.0f);
					if (speed > 0.2) {
						burst(bx - drive.z / speed * 0.5, top, bz + drive.x / speed * 0.5, 0.12f);
						burst(bx + drive.z / speed * 0.5, top, bz - drive.x / speed * 0.5, 0.12f);
					}
				}
			}
		}

		// Raindrops dimple nearby water.
		if (level.isRaining()) {
			for (int i = 0; i < 5; i++) {
				double x = player.getX() + RANDOM.nextDouble() * 24.0 - 12.0;
				double z = player.getZ() + RANDOM.nextDouble() * 24.0 - 12.0;
				boolean onWater = false;
				for (int dy = 2; dy >= -4 && !onWater; dy--) {
					double top = surface(level, x, player.getY() + dy, z);
					if (!Double.isNaN(top)) {
						onWater = true;
						if (level.isRainingAt(BlockPos.containing(x, top + 1.0, z))) {
							ripple(x, top, z, 0.25f + RANDOM.nextFloat() * 0.2f);
						}
					}
				}
				if (!onWater) {
					// Drops also make small rings where they hit the ground.
					BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
					for (int y = Mth.floor(player.getY()) + 4; y >= Mth.floor(player.getY()) - 5; y--) {
						pos.set(Mth.floor(x), y, Mth.floor(z));
						if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
							if (level.isRainingAt(pos.above())) {
								ripple(x, y + level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) + 0.01, z,
										0.12f + RANDOM.nextFloat() * 0.1f);
							}
							break;
						}
					}
				}
			}
		}
	}

	static void render(LevelRenderContext context, float dt) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		if (!RIPPLES.isEmpty()) {
			List<RippleView> views = new ArrayList<>(RIPPLES.size());
			for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
				Ripple ripple = it.next();
				ripple.age += dt;
				if (ripple.age >= ripple.life) {
					it.remove();
					continue;
				}
				float k = ripple.age / ripple.life;
				views.add(new RippleView((float) (ripple.x - camera.x), (float) (ripple.y - camera.y) + 0.01f,
						(float) (ripple.z - camera.z), ripple.size * (0.15f + 0.85f * (1.0f - (1.0f - k) * (1.0f - k))), (1.0f - k) * (1.0f - k)));
			}
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(RING), (pose, buffer) -> {
				for (RippleView view : views) {
					Draw.sprite(pose, buffer, view.x, view.y, view.z, view.size, 0, 0, 0, 0, view.size, Draw.glow(view.fade * 0.55f, 0xDDF2FF));
				}
			});
		}
		if (!DROPS.isEmpty()) {
			List<DropView> views = new ArrayList<>(DROPS.size());
			for (Drop drop : DROPS) {
				float k = drop.age / drop.life;
				views.add(new DropView((float) (drop.x - camera.x), (float) (drop.y - camera.y), (float) (drop.z - camera.z),
						drop.size, 1.0f - k * k));
			}
			Quaternionf orientation = context.levelState().cameraRenderState.orientation;
			Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
			Vector3f up = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.textured(GLOW), (pose, buffer) -> {
				for (DropView view : views) {
					float s = view.size;
					Draw.sprite(pose, buffer, view.x, view.y, view.z, right.x * s, right.y * s, right.z * s,
							up.x * s, up.y * s, up.z * s, Draw.glow(view.fade * 0.9f, 0xCFEAFF));
				}
			});
		}
	}
}
