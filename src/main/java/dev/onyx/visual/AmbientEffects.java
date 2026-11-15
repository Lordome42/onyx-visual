package dev.onyx.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Purely client-side particles: a trail behind the player, night fireflies and flower pollen. */
public final class AmbientEffects {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int[] POLLEN_COLORS = {0xFFF2A8, 0xFFD1E8, 0xD8F5FF, 0xFFFFFF, 0xE8D5FF};
	private static int ticks;

	private AmbientEffects() {
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null || mc.isPaused()) {
			return;
		}
		ticks++;
		OnyxConfig config = OnyxConfig.get();
		if (config.trail) {
			trail(level, player, config.trailStyle);
		}
		if (config.fireflies) {
			fireflies(level, player);
		}
		if (config.pollen) {
			pollen(level, player);
		}
	}

	// ---------------------------------------------------------------- trail

	private static void trail(ClientLevel level, LocalPlayer player, OnyxConfig.TrailStyle style) {
		if (player.isSpectator() || player.isInvisible() || player.isPassenger()) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		double speedSq = motion.x * motion.x + motion.y * motion.y + motion.z * motion.z;
		boolean gliding = player.isFallFlying();
		boolean running = player.isSprinting() && speedSq > 0.01;
		if (!gliding && !running) {
			return;
		}

		int count = gliding ? 3 : 2;
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.5;
			double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.5;
			double y;
			if (gliding) {
				// Spread over the wingspan, perpendicular to the flight direction.
				float yaw = player.getYRot() * Mth.DEG_TO_RAD;
				double side = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble() * 0.5);
				x = player.getX() + Mth.cos(yaw) * side;
				z = player.getZ() + Mth.sin(yaw) * side;
				y = player.getY() + 0.4 + RANDOM.nextDouble() * 0.2;
			} else {
				y = player.getY() + 0.1 + RANDOM.nextDouble() * 0.25;
			}
			spawnTrailParticle(level, style, x, y, z);
		}
	}

	private static void spawnTrailParticle(ClientLevel level, OnyxConfig.TrailStyle style, double x, double y, double z) {
		double drift = 0.01;
		double vx = (RANDOM.nextDouble() - 0.5) * drift;
		double vz = (RANDOM.nextDouble() - 0.5) * drift;
		switch (style) {
			case RAINBOW -> {
				float hue = (ticks % 120) / 120.0f + RANDOM.nextFloat() * 0.04f;
				ParticleOptions dust = new DustColorTransitionOptions(hue(hue), hue(hue + 0.18f), 1.0f);
				level.addParticle(dust, x, y, z, vx, 0.02, vz);
				if (RANDOM.nextInt(6) == 0) {
					level.addParticle(ParticleTypes.END_ROD, x, y + 0.1, z, vx, 0.01, vz);
				}
			}
			case STARDUST -> {
				level.addParticle(RANDOM.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.WAX_OFF,
						x, y, z, vx * 20, 0.2, vz * 20);
				if (RANDOM.nextInt(4) == 0) {
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, vx, 0.02, vz);
				}
			}
			case PETALS -> {
				level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y + 0.5, z, 0, 0, 0);
				if (RANDOM.nextInt(5) == 0) {
					level.addParticle(new DustParticleOptions(0xFFC4DE, 0.8f), x, y, z, vx, 0.02, vz);
				}
			}
			case SOUL -> {
				level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, vx, 0.015, vz);
				if (RANDOM.nextInt(8) == 0) {
					level.addParticle(ParticleTypes.SOUL, x, y + 0.2, z, vx, 0.03, vz);
				}
			}
		}
	}

	/** Fully saturated colour for a hue in turns (0..1 wraps). */
	static int hue(float h) {
		h = (h % 1.0f + 1.0f) % 1.0f;
		float r = Mth.clamp(Math.abs(h * 6.0f - 3.0f) - 1.0f, 0.0f, 1.0f);
		float g = Mth.clamp(2.0f - Math.abs(h * 6.0f - 2.0f), 0.0f, 1.0f);
		float b = Mth.clamp(2.0f - Math.abs(h * 6.0f - 4.0f), 0.0f, 1.0f);
		// Mix a little white in so the dust looks pastel rather than neon.
		int ri = (int) ((0.25f + 0.75f * r) * 255.0f);
		int gi = (int) ((0.25f + 0.75f * g) * 255.0f);
		int bi = (int) ((0.25f + 0.75f * b) * 255.0f);
		return ri << 16 | gi << 8 | bi;
	}

	// ------------------------------------------------------------ fireflies

	private static void fireflies(ClientLevel level, LocalPlayer player) {
		if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || level.isRaining()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = Mth.floor(player.getX()) + RANDOM.nextInt(33) - 16;
			int y = Mth.floor(player.getY()) + RANDOM.nextInt(9) - 3;
			int z = Mth.floor(player.getZ()) + RANDOM.nextInt(33) - 16;
			pos.set(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			// Look for greenery within two blocks below the chosen air block.
			boolean nearPlants = false;
			for (int down = 1; down <= 2 && !nearPlants; down++) {
				pos.set(x, y - down, z);
				nearPlants = isGreenery(level.getBlockState(pos));
			}
			if (!nearPlants || RANDOM.nextInt(4) != 0) {
				continue;
			}
			level.addParticle(ParticleTypes.FIREFLY,
					x + RANDOM.nextDouble(), y + RANDOM.nextDouble() * 0.8, z + RANDOM.nextDouble(),
					0, 0, 0);
		}
	}

	private static boolean isGreenery(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
				|| state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SHORT_GRASS)
				|| state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN);
	}

	// --------------------------------------------------------------- pollen

	private static void pollen(ClientLevel level, LocalPlayer player) {
		if (level.isDarkOutside() || level.isRaining()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int attempt = 0; attempt < 24; attempt++) {
			pos.set(Mth.floor(player.getX()) + RANDOM.nextInt(21) - 10,
					Mth.floor(player.getY()) + RANDOM.nextInt(7) - 3,
					Mth.floor(player.getZ()) + RANDOM.nextInt(21) - 10);
			if (!level.getBlockState(pos).is(BlockTags.FLOWERS) || RANDOM.nextInt(3) != 0) {
				continue;
			}
			int color = POLLEN_COLORS[RANDOM.nextInt(POLLEN_COLORS.length)];
			level.addParticle(new DustParticleOptions(color, 0.45f + RANDOM.nextFloat() * 0.25f),
					pos.getX() + 0.2 + RANDOM.nextDouble() * 0.6,
					pos.getY() + 0.5 + RANDOM.nextDouble() * 0.5,
					pos.getZ() + 0.2 + RANDOM.nextDouble() * 0.6,
					0, 0.01, 0);
		}
	}
}
