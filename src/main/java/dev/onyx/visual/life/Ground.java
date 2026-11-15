package dev.onyx.visual.life;

import dev.onyx.visual.OnyxConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Small things lying on the ground: flowers, pebbles, sticks, fallen leaves and pushable snow piles. */
final class Ground {
	private static final float PX = 1.0f / 16.0f;
	private static final int RADIUS = 13;
	private static final int[] PETALS = {0xF4F4F4, 0xF4D83A, 0xF08AB8, 0xA87AF0, 0x5A9AF0, 0xE84A3A, 0xF0A03A};
	private static final int[] LEAVES = {0x5A8A32, 0x7A9A3A, 0xC8A032, 0xC87A2A, 0xA8582A, 0x8A6A3A};

	static final int FLOWER = 0, PEBBLE = 1, STICK = 2, LEAF = 3, SHELL = 4, FEATHER = 5;
	private static final int[] SHELLS = {0xF4D8C0, 0xF0B8A0, 0xE8E0D0, 0xD8A8B8, 0xF4E4B8};

	/** A decoration with a little physics: it can be kicked, bent or blown about. */
	static final class Item {
		int type;
		int variant;
		int light;
		/** Pebbles can be stacked into a little cairn. */
		int stack = 1;
		/** Overrides the colour (used for scattered petals); -1 for the default. */
		int tint = -1;
		double x, y, z, px, py, pz;
		double vx, vy, vz;
		double ground;
		float yaw, pyaw, spin;
		// Flowers: how far the stem leans (radians) and in which direction, with a springy velocity.
		float bendX, bendZ, pbendX, pbendZ, bendVX, bendVZ;
		boolean active;
	}

	static final class Pile {
		double x, y, z, px, pz, py;
		double vx, vz;
		float size;
		int light;
		/** Enough snow pushed together turns the drift into a snowman. */
		boolean snowman;
		int splash;
	}

	private static final Map<Long, Item> ITEMS = new HashMap<>();
	private static volatile List<Item> items = List.of();
	private static final Map<Long, Pile> PILES = new HashMap<>();
	private static volatile List<Pile> pileList = List.of();
	private static int scanTicks;
	// Home blocks whose pebble has been picked up, so the scan does not put it straight back.
	private static final java.util.Set<Long> TAKEN = new java.util.HashSet<>();
	private static long dropKey = Long.MIN_VALUE;

	/** The pickable decoration closest to the line of sight within reach, or null. Leaves cannot be picked up. */
	static Map.Entry<Long, Item> findPickable(Vec3 eye, Vec3 look, double reach) {
		Map.Entry<Long, Item> best = null;
		double bestScore = 0.3;
		for (Map.Entry<Long, Item> entry : ITEMS.entrySet()) {
			Item item = entry.getValue();
			if (item.type == LEAF) {
				continue;
			}
			double height = item.type == FLOWER ? 0.2 : 0.08 + (item.stack - 1) * 0.1;
			double dx = item.x - eye.x, dy = item.y + height - eye.y, dz = item.z - eye.z;
			double along = dx * look.x + dy * look.y + dz * look.z;
			if (along < 0.2 || along > reach) {
				continue;
			}
			// Distance from the item to the view ray.
			double ox = dx - look.x * along, oy = dy - look.y * along, oz = dz - look.z * along;
			double off = Math.sqrt(ox * ox + oy * oy + oz * oz);
			if (off < bestScore) {
				bestScore = off;
				best = entry;
			}
		}
		return best;
	}

	/** The snow drift under the crosshair, or null. */
	static Pile findPile(Vec3 eye, Vec3 look, double reach) {
		Pile best = null;
		double bestScore = 0.45;
		for (Pile pile : PILES.values()) {
			double dx = pile.x - eye.x, dy = pile.y + 0.15 * pile.size - eye.y, dz = pile.z - eye.z;
			double along = dx * look.x + dy * look.y + dz * look.z;
			if (along < 0.2 || along > reach) {
				continue;
			}
			double ox = dx - look.x * along, oy = dy - look.y * along, oz = dz - look.z * along;
			double off = Math.sqrt(ox * ox + oy * oy + oz * oz) / Math.max(0.6f, pile.size);
			if (off < bestScore) {
				bestScore = off;
				best = pile;
			}
		}
		return best;
	}

	/** Scoops a snowball's worth out of a drift. */
	static void scoop(Pile pile) {
		pile.size -= 0.14f;
		if (pile.size < 0.35f && !pile.snowman) {
			PILES.values().remove(pile);
			pileList = List.copyOf(PILES.values());
		}
	}

	/** Removes one item (the top stone of a cairn, or the whole thing) so it can go into the pocket. */
	static void take(long key) {
		Item item = ITEMS.get(key);
		if (item != null && item.stack > 1) {
			item.stack--;
			return;
		}
		ITEMS.remove(key);
		if (TAKEN.size() > 4096) {
			TAKEN.clear();
		}
		TAKEN.add(key);
		items = List.copyOf(ITEMS.values());
	}

	/** Puts a loose decoration into the world; it can be kicked and picked up like any other. */
	static Item drop(int type, double x, double y, double z, float yaw, int variant, double vx, double vz) {
		Item item = new Item();
		item.type = type;
		item.variant = variant;
		item.x = item.px = x;
		item.y = item.py = item.ground = y;
		item.z = item.pz = z;
		item.yaw = item.pyaw = yaw;
		item.vx = vx * 0.5;
		item.vz = vz * 0.5;
		item.active = true;
		item.light = 0xF000F0;
		if (ITEMS.size() < 520) {
			ITEMS.put(dropKey++, item);
			items = List.copyOf(ITEMS.values());
		}
		return item;
	}

	/** Knocks over any cairn within reach of a point, scattering its stones. Returns true if one fell. */
	static boolean topple(double x, double z, double radius, double pushX, double pushZ) {
		List<Item> fallen = new ArrayList<>();
		for (Item item : ITEMS.values()) {
			if (item.type == PEBBLE && item.stack > 1) {
				double dx = item.x - x, dz = item.z - z;
				if (dx * dx + dz * dz < radius * radius) {
					fallen.add(item);
				}
			}
		}
		for (Item item : fallen) {
			for (int i = 1; i < item.stack; i++) {
				float angle = Critters.RANDOM.nextFloat() * Mth.TWO_PI;
				Item stone = drop(PEBBLE, item.x, item.ground, item.z, angle, Critters.RANDOM.nextInt(1024),
						pushX + Mth.cos(angle) * 0.25, pushZ + Mth.sin(angle) * 0.25);
				stone.y = item.ground + i * 0.1;
				stone.vy = 0.08 + i * 0.02;
				stone.spin = 0.4f;
			}
			item.stack = 1;
		}
		return !fallen.isEmpty();
	}

	private Ground() {
	}

	static void reset() {
		ITEMS.clear();
		TAKEN.clear();
		items = List.of();
		PILES.clear();
		pileList = List.of();
		scanTicks = 0;
		PRINTS.clear();
		printList = List.of();
		puddles = List.of();
		wetness = 0.0f;
	}

	private static int hash(int x, int y, int z) {
		int h = x * 73428767 ^ y * 912931 ^ z * 42317861;
		h ^= h >>> 13;
		h *= 0x5BD1E995;
		h ^= h >>> 15;
		return h & 0x7FFFFFFF;
	}

	static void tick(ClientLevel level, LocalPlayer player) {
		OnyxConfig config = OnyxConfig.get();
		if (--scanTicks <= 0) {
			scanTicks = 30;
			scan(level, player, config);
		}
		if (!config.groundClutter) {
			if (!ITEMS.isEmpty()) {
				ITEMS.clear();
				items = List.of();
			}
		} else {
			tickItems(level, player);
		}
		if (!config.snowPiles) {
			if (!PILES.isEmpty()) {
				PILES.clear();
				pileList = List.of();
			}
			return;
		}

		// Snow piles get shoved along by the player and slide to a stop.
		Vec3 motion = player.getDeltaMovement();
		for (Iterator<Pile> it = PILES.values().iterator(); it.hasNext(); ) {
			Pile pile = it.next();
			pile.px = pile.x;
			pile.py = pile.y;
			pile.pz = pile.z;
			double dx = pile.x - player.getX();
			double dz = pile.z - player.getZ();
			double distSq = dx * dx + dz * dz;
			if (distSq > 34.0 * 34.0) {
				it.remove();
				continue;
			}
			// A drift left on bare ground (pushed off the snow, or the snow was removed) melts away.
			if (!pile.snowman && pile.vx == 0.0 && pile.vz == 0.0 && level.getGameTime() % 20 == 0) {
				BlockState under = Terrain.stateAt(level, pile.x, pile.y - 0.05, pile.z);
				if (!under.is(Blocks.SNOW) && !under.is(Blocks.SNOW_BLOCK)) {
					pile.size -= 0.25f;
					if (pile.size < 0.3f) {
						it.remove();
						continue;
					}
				}
			}
			if (pile.splash > 0) {
				pile.splash--;
			}
			double reach = 0.3 + pile.size * 0.45;
			if (!pile.snowman && distSq < reach * reach && Math.abs(player.getY() - pile.y) < 1.2) {
				double dist = Math.max(0.05, Math.sqrt(distSq));
				double push = (reach - dist) * 0.35;
				pile.vx += dx / dist * push + motion.x * 0.6;
				pile.vz += dz / dist * push + motion.z * 0.6;
				if (level.getGameTime() % 2 == 0) {
					level.addParticle(ParticleTypes.SNOWFLAKE, pile.x, pile.y + 0.25, pile.z,
							(Critters.RANDOM.nextDouble() - 0.5) * 0.08, 0.06, (Critters.RANDOM.nextDouble() - 0.5) * 0.08);
				}
			}
			double speedSq = pile.vx * pile.vx + pile.vz * pile.vz;
			if (speedSq > 1.0e-5) {
				double nx = pile.x + pile.vx;
				double nz = pile.z + pile.vz;
				double ground = Terrain.groundY(level, nx, pile.y + 0.6, nz, false);
				if (Double.isNaN(ground) || ground - pile.y > 0.6) {
					// Blocked by a wall or a step up.
					pile.vx = 0.0;
					pile.vz = 0.0;
				} else {
					pile.x = nx;
					pile.z = nz;
					pile.y += Mth.clamp(ground - pile.y, -0.5, 0.5);
				}
				pile.vx *= 0.78;
				pile.vz *= 0.78;
				if (pile.vx * pile.vx + pile.vz * pile.vz < 1.0e-5) {
					pile.vx = 0.0;
					pile.vz = 0.0;
				}
				pile.light = Terrain.light(level, pile.x, pile.y + 0.4, pile.z);
			}
		}
		// Drifts pushed into each other pack together; enough of them make a snowman.
		Pile absorbed = null;
		outer:
		for (Pile a : PILES.values()) {
			if (a.snowman || a.vx == 0.0 && a.vz == 0.0) {
				continue;
			}
			for (Pile b : PILES.values()) {
				if (a == b || b.snowman) {
					continue;
				}
				double dx = a.x - b.x, dz = a.z - b.z;
				double touch = (a.size + b.size) * 0.22;
				if (dx * dx + dz * dz < touch * touch && Math.abs(a.y - b.y) < 0.6) {
					b.size += a.size * 0.62f;
					b.vx += a.vx * 0.4;
					b.vz += a.vz * 0.4;
					b.splash = 8;
					if (b.size >= 2.25f) {
						b.snowman = true;
						b.vx = 0.0;
						b.vz = 0.0;
						for (int i = 0; i < 24; i++) {
							level.addParticle(ParticleTypes.SNOWFLAKE, b.x, b.y + 0.3 + Critters.RANDOM.nextDouble() * 1.4, b.z,
									(Critters.RANDOM.nextDouble() - 0.5) * 0.2, 0.08, (Critters.RANDOM.nextDouble() - 0.5) * 0.2);
						}
					}
					absorbed = a;
					break outer;
				}
			}
		}
		if (absorbed != null) {
			PILES.values().remove(absorbed);
		}
		pileList = List.copyOf(PILES.values());
		tickPrints(level, player, config);
	}

	// ------------------------------------------------- footprints and puddles

	private record Print(double x, double y, double z, float yaw, long born) {
	}

	record Puddle(double x, double y, double z, int variant, int light) {
	}

	private static final List<Print> PRINTS = new ArrayList<>();
	private static volatile List<Print> printList = List.of();
	private static volatile List<Puddle> puddles = List.of();
	private static float wetness;
	private static int stepTicks;
	private static boolean leftFoot;
	private static int puddleCooldown;

	static int snowmen() {
		int n = 0;
		for (Pile pile : PILES.values()) {
			if (pile.snowman) {
				n++;
			}
		}
		return n;
	}

	private static void tickPrints(ClientLevel level, LocalPlayer player, OnyxConfig config) {
		Vec3 motion = player.getDeltaMovement();
		double speedSq = motion.x * motion.x + motion.z * motion.z;
		long now = level.getGameTime();

		// Footprints pressed into snow.
		if (config.snowPrints && player.onGround() && speedSq > 0.003) {
			BlockState under = Terrain.stateAt(level, player.getX(), player.getY() - 0.05, player.getZ());
			BlockState at = Terrain.stateAt(level, player.getX(), player.getY() + 0.02, player.getZ());
			if ((under.is(Blocks.SNOW_BLOCK) || under.is(Blocks.SNOW) || at.is(Blocks.SNOW)) && ++stepTicks >= (player.isSprinting() ? 4 : 6)) {
				stepTicks = 0;
				leftFoot = !leftFoot;
				float yaw = (float) Mth.atan2(-motion.x, motion.z);
				double side = leftFoot ? 0.16 : -0.16;
				PRINTS.add(new Print(player.getX() + Mth.cos(yaw) * side, player.getY() + 0.004, player.getZ() + Mth.sin(yaw) * side, yaw, now));
				if (PRINTS.size() > 140) {
					PRINTS.remove(0);
				}
			}
		}
		if (!PRINTS.isEmpty() && now % 20 == 0) {
			PRINTS.removeIf(print -> now - print.born > 1800);
		}
		printList = List.copyOf(PRINTS);

		// The ground gets wet in the rain and dries out again afterwards.
		boolean raining = level.isRaining() && level.isRainingAt(player.blockPosition().above(2));
		wetness = Mth.clamp(wetness + (raining ? 0.004f : -0.0006f), 0.0f, 1.0f);
		if (puddleCooldown > 0) {
			puddleCooldown--;
		}
		if (config.puddles && wetness > 0.15f && player.onGround() && speedSq > 0.002 && puddleCooldown == 0) {
			for (Puddle puddle : puddles) {
				double dx = puddle.x - player.getX(), dz = puddle.z - player.getZ();
				if (dx * dx + dz * dz < 0.3 && Math.abs(puddle.y - player.getY()) < 0.3) {
					dev.onyx.visual.fx.Splash.burst(player.getX(), puddle.y + 0.02, player.getZ(), 0.22f + (float) Math.sqrt(speedSq) * 1.5f);
					puddleCooldown = 7;
					break;
				}
			}
		}
	}

	/** Test hook: a line of drifts one after another, so walking through them packs a snowman. */
	static void debugPiles(double x, double y, double z) {
		for (int i = 0; i < 5; i++) {
			Pile pile = new Pile();
			pile.x = pile.px = x;
			pile.z = pile.pz = z + i * 0.9;
			pile.y = pile.py = y;
			pile.size = 1.1f;
			pile.light = 0xF000F0;
			PILES.put(Long.MAX_VALUE - i, pile);
		}
	}

	static void debugWet() {
		wetness = 1.0f;
		scanTicks = 0;
	}

	private static void scan(ClientLevel level, LocalPlayer player, OnyxConfig config) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int px = Mth.floor(player.getX());
		int py = Mth.floor(player.getY());
		int pz = Mth.floor(player.getZ());
		for (int x = px - RADIUS; x <= px + RADIUS; x++) {
			for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
				int dx = x - px;
				int dz = z - pz;
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				for (int y = py + 4; y >= py - 6; y--) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					boolean snow = state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
					boolean soil = state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK);
					boolean sand = state.is(BlockTags.SAND);
					if (sand && config.groundClutter && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
						// Seashells: on sand with water close by.
						int h = hash(x, y, z);
						long key = BlockPos.asLong(x, y, z);
						if (h % 100 < 9 && ITEMS.size() < 420 && !ITEMS.containsKey(key) && !TAKEN.contains(key)
								&& (level.getBlockState(pos.set(x + 2, y, z)).is(Blocks.WATER) || level.getBlockState(pos.set(x - 2, y, z)).is(Blocks.WATER)
								|| level.getBlockState(pos.set(x, y, z + 2)).is(Blocks.WATER) || level.getBlockState(pos.set(x, y, z - 2)).is(Blocks.WATER)
								|| level.getBlockState(pos.set(x + 3, y - 1, z + 3)).is(Blocks.WATER) || level.getBlockState(pos.set(x - 3, y - 1, z - 3)).is(Blocks.WATER))) {
							Item shell = new Item();
							shell.type = SHELL;
							shell.variant = h >> 5 & 1023;
							shell.x = shell.px = x + 0.2 + (h >> 8 & 63) / 105.0;
							shell.z = shell.pz = z + 0.2 + (h >> 14 & 63) / 105.0;
							shell.y = shell.py = shell.ground = y + 1;
							shell.yaw = shell.pyaw = (h >> 20 & 255) / 255.0f * Mth.TWO_PI;
							shell.light = Terrain.light(level, x, y + 1, z);
							ITEMS.put(key, shell);
						}
					}
					if ((snow || soil) && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
						int h = hash(x, y, z);
						if (snow) {
							if (config.snowPiles && h % 100 < 16) {
								long key = BlockPos.asLong(x, y, z);
								if (!PILES.containsKey(key) && PILES.size() < 160) {
									double top = Terrain.groundY(level, x + 0.5, y, z + 0.5, false);
									if (!Double.isNaN(top)) {
										Pile pile = new Pile();
										pile.x = pile.px = x + 0.2 + (h >> 8 & 63) / 105.0;
										pile.z = pile.pz = z + 0.2 + (h >> 14 & 63) / 105.0;
										pile.y = pile.py = top;
										pile.size = 0.7f + (h >> 20 & 15) / 30.0f;
										pile.light = Terrain.light(level, pile.x, top + 0.4, pile.z);
										PILES.put(key, pile);
									}
								}
							}
						} else if (config.groundClutter && ITEMS.size() < 420 && !ITEMS.containsKey(BlockPos.asLong(x, y, z))
								&& !TAKEN.contains(BlockPos.asLong(x, y, z))) {
							addClutter(level, pos, x, y, z, h);
						}
					}
					break;
				}
			}
		}
		// Puddles sit in fixed spots on open ground while it is wet.
		if (config.puddles && wetness > 0.15f) {
			List<Puddle> found = new ArrayList<>();
			for (int x = px - 10; x <= px + 10; x++) {
				for (int z = pz - 10; z <= pz + 10; z++) {
					int h = hash(x, 77, z);
					if (h % 100 >= 5) {
						continue;
					}
					double top = Terrain.groundY(level, x + 0.5, py + 1.0, z + 0.5, false);
					if (Double.isNaN(top) || top != Math.floor(top)) {
						continue;
					}
					BlockState below = level.getBlockState(pos.set(x, (int) top - 1, z));
					if (below.isSolidRender() && !below.is(Blocks.SNOW_BLOCK)
							&& level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos.set(x, (int) top, z)) >= 15) {
						found.add(new Puddle(x + 0.5, top, z + 0.5, h >> 7 & 255, Terrain.light(level, x, top, z)));
					}
				}
			}
			puddles = found;
		} else if (!puddles.isEmpty()) {
			puddles = List.of();
		}
		// Drop decorations that are far away or whose ground has gone.
		for (Iterator<Item> it = ITEMS.values().iterator(); it.hasNext(); ) {
			Item item = it.next();
			double dx = item.x - player.getX();
			double dz = item.z - player.getZ();
			if (dx * dx + dz * dz > (RADIUS + 4) * (RADIUS + 4)) {
				it.remove();
				continue;
			}
			double top = Terrain.groundY(level, item.x, item.ground + 0.4, item.z, false);
			if (Double.isNaN(top) || Math.abs(top - item.ground) > 1.1) {
				it.remove();
				continue;
			}
			if (!item.active) {
				item.ground = top;
				item.y = item.py = top;
			}
			item.light = Terrain.light(level, item.x, item.ground + 0.5, item.z);
		}
		items = List.copyOf(ITEMS.values());
	}

	private static void tickItems(ClientLevel level, LocalPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		Item toppled = null;
		float gust = dev.onyx.visual.fx.Weather.gust();
		for (Item item : ITEMS.values()) {
			item.px = item.x;
			item.py = item.y;
			item.pz = item.z;
			item.pyaw = item.yaw;
			item.pbendX = item.bendX;
			item.pbendZ = item.bendZ;
			double dx = item.x - player.getX();
			double dz = item.z - player.getZ();
			double distSq = dx * dx + dz * dz;
			boolean level0 = Math.abs(player.getY() - item.ground) < 1.0;

			if (item.type == FLOWER) {
				// Stems lean away from feet and spring back with a wobble.
				float targetX = 0.0f;
				float targetZ = 0.0f;
				if (level0 && distSq < 0.75 * 0.75) {
					double dist = Math.max(0.05, Math.sqrt(distSq));
					float lean = (float) (1.15 * (1.0 - dist / 0.75));
					targetX = (float) (dx / dist) * lean;
					targetZ = (float) (dz / dist) * lean;
				}
				item.bendVX = (item.bendVX + (targetX - item.bendX) * 0.35f) * 0.72f;
				item.bendVZ = (item.bendVZ + (targetZ - item.bendZ) * 0.35f) * 0.72f;
				item.bendX += item.bendVX;
				item.bendZ += item.bendVZ;
				continue;
			}

			boolean light = item.type == LEAF || item.type == FEATHER;
			double reach = light ? 0.6 + speed * 3.0 : 0.5;
			if (level0 && distSq < reach * reach && speed > 0.02) {
				if (item.stack > 1) {
					toppled = item;
				}
				double dist = Math.max(0.05, Math.sqrt(distSq));
				double kick = item.type == PEBBLE || item.type == SHELL ? 0.9 : item.type == STICK ? 0.75 : 0.5;
				item.vx += dx / dist * 0.05 + motion.x * kick;
				item.vz += dz / dist * 0.05 + motion.z * kick;
				if (item.y - item.ground < 0.02) {
					// A little hop: leaves flutter up, pebbles and sticks skip.
					item.vy = light ? 0.07 + speed * 0.35 : 0.06 + speed * 0.3;
					item.spin = (float) ((Critters.RANDOM.nextDouble() - 0.5) * (light ? 0.9 : 0.6));
				}
				item.active = true;
			} else if (light && !item.active && Critters.RANDOM.nextInt(gust > 0.3f ? 60 : 2500) == 0) {
				// An occasional breath of wind stirs a leaf.
				item.vx = (Critters.RANDOM.nextDouble() - 0.5) * 0.08;
				item.vz = (Critters.RANDOM.nextDouble() - 0.5) * 0.08;
				item.vy = 0.05;
				item.spin = 0.3f;
				item.active = true;
			}
			if (!item.active) {
				continue;
			}

			double nx = item.x + item.vx;
			double nz = item.z + item.vz;
			double top = Terrain.groundY(level, nx, item.ground + 0.6, nz, false);
			if (Double.isNaN(top) || top - item.ground > 0.6) {
				// Bounce off walls and ledges.
				item.vx *= -0.3;
				item.vz *= -0.3;
			} else {
				item.x = nx;
				item.z = nz;
				item.ground = top;
			}
			item.y += item.vy;
			boolean airborne = item.y > item.ground + 1.0e-3;
			if (airborne) {
				// Leaves drift down slowly, everything else falls like a stone.
				item.vy -= light ? 0.012 : 0.04;
				if (light) {
					item.vy = Math.max(item.vy, -0.05);
					item.vx += Mth.sin((float) (item.y * 9.0)) * 0.002;
				}
				item.vx *= 0.96;
				item.vz *= 0.96;
			} else {
				item.y = item.ground;
				if (item.vy < -0.08 && !light) {
					item.vy = -item.vy * 0.35;
					item.y = item.ground + 0.002;
				} else {
					item.vy = 0.0;
				}
				double friction = item.type == PEBBLE || item.type == SHELL ? 0.82 : 0.68;
				item.vx *= friction;
				item.vz *= friction;
				item.spin *= 0.7f;
			}
			item.yaw += item.spin;
			if (!airborne && item.vx * item.vx + item.vz * item.vz < 1.0e-6 && item.vy == 0.0) {
				item.vx = 0.0;
				item.vz = 0.0;
				item.spin = 0.0f;
				item.active = false;
			}
		}
		if (toppled != null) {
			topple(toppled.x, toppled.z, 0.1, motion.x, motion.z);
		}
	}

	private static void addClutter(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int h) {
		int roll = h % 100;
		int type;
		if (roll < 7) {
			type = FLOWER;
		} else if (roll < 10) {
			type = PEBBLE;
		} else {
			// Leaves and sticks collect under trees.
			boolean canopy = false;
			for (int up = 2; up <= 9 && !canopy; up++) {
				BlockState above = level.getBlockState(pos.set(x, y + up, z));
				canopy = above.is(BlockTags.LEAVES);
				if (!canopy && !above.isAir()) {
					break;
				}
			}
			if (canopy && roll < 38) {
				type = LEAF;
			} else if (canopy && roll < 48 || !canopy && roll < 12) {
				type = STICK;
			} else {
				return;
			}
		}
		Item item = new Item();
		item.type = type;
		item.variant = h >> 5 & 1023;
		item.x = item.px = x + 0.2 + (h >> 8 & 63) / 105.0;
		item.z = item.pz = z + 0.2 + (h >> 14 & 63) / 105.0;
		item.y = item.py = item.ground = y + 1;
		item.yaw = item.pyaw = (h >> 20 & 255) / 255.0f * Mth.TWO_PI;
		item.light = Terrain.light(level, x, y + 1, z);
		ITEMS.put(BlockPos.asLong(x, y, z), item);
	}

	static void render(Model m, Vec3 camera, float partial, float time) {
		for (Item item : items) {
			float cx = (float) (Mth.lerp(partial, item.px, item.x) - camera.x);
			float cy = (float) (Mth.lerp(partial, item.py, item.y) - camera.y);
			float cz = (float) (Mth.lerp(partial, item.pz, item.z) - camera.z);
			float yaw = Mth.lerp(partial, item.pyaw, item.yaw);
			int v = item.variant;
			switch (item.type) {
				case FLOWER -> {
					// One to three little blossoms that sway slightly.
					int count = 1 + v % 3;
					int petal = PETALS[v % PETALS.length];
					for (int i = 0; i < count; i++) {
						float ax = i == 0 ? 0.0f : (i == 1 ? 0.19f : -0.16f);
						float az = i == 0 ? 0.0f : (i == 1 ? 0.12f : 0.17f);
						float height = 3.0f + (v >> (i * 2) & 3);
						// Wind rolls across the meadow as a wave; a trampled stem leans away from the feet.
						float wave = (float) (item.x * 0.55 + item.z * 0.35);
						float wind = 1.0f + 2.6f * dev.onyx.visual.fx.Weather.gust();
						float sway = wind * (0.17f * Mth.sin(time * 1.5f * wind + wave + i) + 0.06f * Mth.sin(time * 3.7f + wave * 1.7f + i * 2.0f));
						float bx = Mth.lerp(partial, item.pbendX, item.bendX);
						float bz = Mth.lerp(partial, item.pbendZ, item.bendZ);
						float lean = Mth.sqrt(bx * bx + bz * bz);
						if (lean > 0.04f) {
							m.place(cx + ax, cy, cz + az, PX, (float) Mth.atan2(-bx, bz), Math.min(lean, 1.3f) + sway * 0.3f, 0.0f, item.light);
						} else {
							m.place(cx + ax, cy, cz + az, PX, yaw + i, sway, sway * 0.5f, item.light);
						}
						m.material(Model.LEAF);
						m.box(-0.4f, 0, -0.4f, 0.4f, height, 0.4f, 0x4A8A32);
						m.box(-1.4f, height * 0.4f, -0.2f, -0.4f, height * 0.4f + 0.3f, 0.6f, 0x5A9A3A);
						m.material(Model.PETAL);
						m.box(-1.5f, height, -0.5f, 1.5f, height + 0.8f, 0.5f, petal);
						m.box(-0.5f, height, -1.5f, 0.5f, height + 0.8f, 1.5f, petal);
						m.material(Model.SPECKLE);
						m.box(-0.5f, height + 0.3f, -0.5f, 0.5f, height + 1.2f, 0.5f, 0xF4C82A);
					}
				}
				case PEBBLE -> {
					int grey = 0x80 + (v & 31);
					int rgb = grey << 16 | grey << 8 | grey + 4;
					m.place(cx, cy, cz, PX, yaw, 0.0f, 0.0f, item.light);
					m.material(Model.STONE);
					m.box(-1.6f, 0, -1.3f, 1.6f, 1.6f, 1.3f, rgb);
					if (item.stack > 1) {
						// A cairn: flat stones balanced on each other, each a little smaller and turned.
						for (int i = 1; i < item.stack; i++) {
							float s = 1.0f - i * 0.13f;
							float base = 1.6f + (i - 1) * 1.5f;
							int shade = rgb + ((i % 2 == 0) ? 0x0A0A0A : -0x0A0A0A);
							m.place(cx, cy, cz, PX, yaw + i * 1.1f, 0.0f, 0.0f, item.light);
							m.material(Model.STONE);
							m.box(-1.5f * s, base, -1.2f * s, 1.5f * s, base + 1.5f, 1.2f * s, shade);
						}
					} else {
						m.box(-0.9f, 1.6f, -0.8f, 1.1f, 2.3f, 0.8f, rgb + 0x0A0A0A);
						if ((v & 1) == 0) {
							m.box(2.2f, 0, 0.6f, 3.6f, 0.9f, 1.9f, rgb - 0x0C0C0C);
						}
					}
				}
				case SHELL -> {
					int rgb = SHELLS[v % SHELLS.length];
					m.place(cx, cy, cz, PX, yaw, 0.0f, 0.0f, item.light);
					shellModel(m, rgb);
				}
				case FEATHER -> {
					float air = (float) Math.min(1.0, (item.y - item.ground) * 5.0);
					float rock = air * 0.6f * Mth.sin(time * 7.0f + v);
					m.place(cx, cy + 0.01f, cz, PX, yaw, rock, rock, item.light);
					featherModel(m, (v & 1) == 0 ? 0xF4F4F0 : 0x9A8A76);
				}
				case STICK -> {
					int brown = (v & 1) == 0 ? 0x6A4A2A : 0x7A5A36;
					float tumble = (float) Math.min(1.2, (item.y - item.ground) * 6.0);
					m.place(cx, cy, cz, PX, yaw, tumble, 0.0f, item.light);
					m.material(Model.WOOD);
					m.box(-0.5f, 0, -4.5f, 0.5f, 1.0f, 4.5f, brown);
					m.beginPart(0, 0.5f, 1.0f, 1, 0.7f);
					m.box(-0.4f, 0.05f, 1.0f, 0.4f, 0.9f, 4.0f, brown - 0x0A0806);
					m.endPart();
				}
				default -> {
					int count = item.tint >= 0 ? 1 : 1 + v % 3;
					for (int i = 0; i < count; i++) {
						int rgb = item.tint >= 0 ? item.tint : LEAVES[(v >> (i * 3)) % LEAVES.length];
						float ax = i == 0 ? 0.0f : (i == 1 ? 0.24f : -0.2f);
						float az = i == 0 ? 0.0f : (i == 1 ? -0.15f : 0.22f);
						// Airborne leaves rock from side to side as they float down.
						float air = (float) Math.min(1.0, (item.y - item.ground) * 5.0);
						float rock = air * 0.7f * Mth.sin(time * 9.0f + v + i * 2.0f);
						m.place(cx + ax * (1.0f + air), cy + 0.005f + i * 0.004f + air * i * 0.12f, cz + az * (1.0f + air), PX, yaw + i * 2.1f, rock, rock * 0.8f, item.light);
						m.material(Model.LEAF);
						m.box(-1.2f, 0, -1.8f, 1.2f, 0.25f, 1.8f, rgb);
						m.box(-0.7f, 0, 1.8f, 0.7f, 0.25f, 2.6f, rgb);
						m.box(-0.2f, 0.25f, -2.6f, 0.2f, 0.32f, 2.2f, rgb - 0x141408);
					}
				}
			}
		}
		for (Print print : printList) {
			float cx = (float) (print.x - camera.x), cy = (float) (print.y - camera.y), cz = (float) (print.z - camera.z);
			m.place(cx, cy, cz, PX, print.yaw, 0.0f, 0.0f, 0xF000A0);
			m.material(Model.SNOW);
			m.box(-1.2f, 0, -2.2f, 1.2f, 0.08f, 1.2f, 0xB4C2D4);
			m.box(-1.0f, 0, 1.6f, 1.0f, 0.08f, 2.6f, 0xB4C2D4);
		}
		for (Puddle puddle : puddles) {
			float cx = (float) (puddle.x - camera.x), cy = (float) (puddle.y - camera.y), cz = (float) (puddle.z - camera.z);
			float s = Math.min(1.0f, wetness * 1.6f) * (0.8f + (puddle.variant & 15) / 30.0f);
			m.place(cx, cy + 0.003f, cz, PX * s, puddle.variant * 0.07f, 0.0f, 0.0f, puddle.light);
			m.material(Model.PLAIN);
			m.box(-5, 0, -3.2f, 5, 0.06f, 3.2f, 0x3E5E82);
			m.box(-3.4f, 0, -4.6f, 3.0f, 0.06f, 4.4f, 0x3E5E82);
			m.box(-3.6f, 0.06f, -2.2f, 2.6f, 0.09f, 2.0f, 0x56789C);
			m.box(0.8f, 0.09f, -1.2f, 2.0f, 0.11f, -0.6f, 0xC8DCF0);
		}
		for (Pile pile : pileList) {
			float cx = (float) (Mth.lerp(partial, pile.px, pile.x) - camera.x);
			float cy = (float) (Mth.lerp(partial, pile.py, pile.y) - camera.y);
			float cz = (float) (Mth.lerp(partial, pile.pz, pile.z) - camera.z);
			if (pile.snowman) {
				snowman(m, cx, cy, cz, pile, time);
				continue;
			}
			// Overlapping slabs in a plus shape read as a soft rounded drift rather than a pyramid.
			float squash = 1.0f + pile.splash * 0.02f;
			m.place(cx, cy, cz, PX * pile.size * squash, pile.size * 9.0f, 0.0f, 0.0f, pile.light);
			m.material(Model.SNOW);
			m.box(-6, 0, -3.5f, 6, 1.4f, 3.5f, 0xEEF3F8);
			m.box(-3.5f, 0, -6, 3.5f, 1.4f, 6, 0xEEF3F8);
			m.box(-4.6f, 0, -4.6f, 4.6f, 1.9f, 4.6f, 0xF2F6FA);
			m.box(-4.2f, 1.4f, -2.2f, 3.6f, 3.0f, 2.6f, 0xF8FAFD);
			m.box(-2.4f, 1.4f, -4.0f, 2.8f, 3.0f, 3.6f, 0xF8FAFD);
			m.box(-2.2f, 3.0f, -1.8f, 1.6f, 4.0f, 1.9f, 0xFFFFFF);
			m.box(-1.2f, 3.0f, -2.6f, 2.2f, 3.7f, 0.8f, 0xFFFFFF);
		}
	}

	private static void snowman(Model m, float cx, float cy, float cz, Pile pile, float time) {
		// Faces the way it was last pushed; three snowballs, coal, a carrot and stick arms.
		float yaw = (float) (pile.x * 3.0 + pile.z * 5.0);
		m.place(cx, cy, cz, PX, yaw, 0.0f, 0.0f, pile.light);
		m.material(Model.SNOW);
		m.box(-6, 0, -6, 6, 10, 6, 0xF2F6FA);
		m.box(-7, 2, -5, 7, 8, 5, 0xEEF3F8);
		m.box(-5, 2, -7, 5, 8, 7, 0xEEF3F8);
		m.box(-4.5f, 10, -4.5f, 4.5f, 18, 4.5f, 0xF8FAFD);
		m.box(-5.3f, 11.5f, -3.5f, 5.3f, 16.5f, 3.5f, 0xF2F6FA);
		m.box(-3.5f, 11.5f, -5.3f, 3.5f, 16.5f, 5.3f, 0xF2F6FA);
		m.box(-3.5f, 18, -3.5f, 3.5f, 25, 3.5f, 0xFFFFFF);
		m.material(Model.PLAIN);
		m.box(-2.0f, 22, 3.5f, -1.0f, 23, 3.9f, 0x181818);
		m.box(1.0f, 22, 3.5f, 2.0f, 23, 3.9f, 0x181818);
		m.box(-0.6f, 20.2f, 3.5f, 0.6f, 21.4f, 6.4f, 0xF08A20);
		for (int i = -2; i <= 2; i++) {
			m.box(i * 0.9f - 0.3f, 19.0f + Math.abs(i) * 0.35f, 3.5f, i * 0.9f + 0.3f, 19.6f + Math.abs(i) * 0.35f, 3.8f, 0x181818);
		}
		m.box(-0.5f, 15.5f, 4.5f, 0.5f, 16.5f, 4.9f, 0x181818);
		m.box(-0.5f, 13.0f, 4.5f, 0.5f, 14.0f, 4.9f, 0x181818);
		m.material(Model.WOOD);
		float wave = 0.12f * Mth.sin(time * 1.5f + yaw);
		m.beginPart(-4.5f, 15, 0, 2, 0.5f + wave);
		m.box(-11.5f, 14.6f, -0.4f, -4.5f, 15.4f, 0.4f, 0x6A4A2A);
		m.box(-11.0f, 15.4f, -0.4f, -10.2f, 17.4f, 0.4f, 0x6A4A2A);
		m.endPart();
		m.beginPart(4.5f, 15, 0, 2, -0.5f - wave);
		m.box(4.5f, 14.6f, -0.4f, 11.5f, 15.4f, 0.4f, 0x6A4A2A);
		m.box(10.2f, 15.4f, -0.4f, 11.0f, 17.4f, 0.4f, 0x6A4A2A);
		m.endPart();
		// A hat made of a darker block with a brim.
		m.material(Model.PLAIN);
		m.box(-4.2f, 25, -4.2f, 4.2f, 25.7f, 4.2f, 0x2A2A32);
		m.box(-2.8f, 25.7f, -2.8f, 2.8f, 29.5f, 2.8f, 0x2A2A32);
		m.box(-2.85f, 26.3f, -2.85f, 2.85f, 27.1f, 2.85f, 0xB83A3A);
	}

	static void shellModel(Model m, int rgb) {
		m.material(Model.WING);
		m.box(-2.2f, 0, -1.4f, 2.2f, 0.9f, 1.6f, rgb);
		m.box(-1.5f, 0.9f, -1.0f, 1.5f, 1.5f, 1.2f, rgb);
		m.box(-0.7f, 0, -2.2f, 0.7f, 0.8f, -1.4f, rgb - 0x181410);
		m.material(Model.PLAIN);
		m.box(-0.2f, 1.5f, -0.8f, 0.2f, 1.7f, 1.2f, rgb - 0x282018);
	}

	static void featherModel(Model m, int rgb) {
		m.material(Model.FEATHER);
		m.box(-0.9f, 0, -2.6f, 0.9f, 0.2f, 2.0f, rgb);
		m.box(-0.5f, 0, 2.0f, 0.5f, 0.2f, 3.0f, rgb);
		m.material(Model.PLAIN);
		m.box(-0.15f, 0.2f, -4.0f, 0.15f, 0.3f, 2.6f, 0xD8D0C0);
	}
}
