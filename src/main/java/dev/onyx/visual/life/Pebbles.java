package dev.onyx.visual.life;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.fx.AtmosFx;
import dev.onyx.visual.fx.Compat;
import dev.onyx.visual.fx.NetherFx;
import dev.onyx.visual.fx.Splash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The phantom pocket: small things picked up off the ground, carried in extra slots and thrown.
 * Each kind behaves differently in the air and when it lands. Nothing here exists on the server.
 */
public final class Pebbles {
	public enum Kind {
		PEBBLE, STICK, FLOWER, SNOWBALL, FEATHER, SHELL, FIREFLY
	}

	public static final int CAPACITY = 16;
	private static final float PX = 1.0f / 16.0f;
	private static final double REACH = 4.5;
	private static final int[] PETALS = {0xF4F4F4, 0xF4D83A, 0xF08AB8, 0xA87AF0, 0x5A9AF0, 0xE84A3A, 0xF0A03A};

	static final class Stone {
		Kind kind;
		double x, y, z, px, py, pz;
		double vx, vy, vz;
		float spin, pspin, spinSpeed;
		int skips;
		int age;
		int variant;
	}

	private static final List<Stone> STONES = new ArrayList<>();
	private static volatile List<Stone> snapshot = List.of();
	private static boolean useWasDown;
	private static boolean throwWasDown;
	private static boolean attackWasDown;
	private static int chargeTicks;
	private static int pickedTick = -100;
	private static int ticks;
	private static float pop;
	private static String hint;
	private static int lastSkips;

	private Pebbles() {
	}

	// ----------------------------------------------------------- HUD access

	public static int count(Kind kind) {
		return OnyxConfig.get().pocket[kind.ordinal()];
	}

	public static Kind selected() {
		return Kind.values()[Mth.clamp(OnyxConfig.get().pocketSelected, 0, Kind.values().length - 1)];
	}

	/** Moves the selection to the next kind that the pocket actually holds. */
	public static void cycle() {
		OnyxConfig config = OnyxConfig.get();
		int n = Kind.values().length;
		for (int i = 1; i <= n; i++) {
			int next = (config.pocketSelected + i) % n;
			if (config.pocket[next] > 0) {
				config.pocketSelected = next;
				config.save();
				pop = 0.6f;
				return;
			}
		}
	}

	/** 0..1 while the throw key is held. */
	public static float charge() {
		return chargeTicks <= 0 ? 0.0f : Math.min(1.0f, chargeTicks / 18.0f);
	}

	/** Decays from 1 after a pick-up; used for the slot's little bounce. */
	public static float pop() {
		return pop;
	}

	/** Translation key of the hint for what the crosshair is on, or null. */
	public static String hint() {
		return hint;
	}

	static void reset() {
		STONES.clear();
		snapshot = List.of();
		chargeTicks = 0;
		hint = null;
	}

	private static boolean add(Kind kind) {
		OnyxConfig config = OnyxConfig.get();
		if (config.pocket[kind.ordinal()] >= CAPACITY) {
			return false;
		}
		config.pocket[kind.ordinal()]++;
		if (config.pocket[Mth.clamp(config.pocketSelected, 0, 6)] <= 0 || config.pocket[kind.ordinal()] == 1) {
			config.pocketSelected = kind.ordinal();
		}
		config.save();
		pop = 1.0f;
		dev.onyx.visual.hud.Toasts.phantom("onyxvisual.item." + kind.name().toLowerCase(java.util.Locale.ROOT));
		return true;
	}

	private static Kind kindOf(int type) {
		return switch (type) {
			case Ground.STICK -> Kind.STICK;
			case Ground.FLOWER -> Kind.FLOWER;
			case Ground.SHELL -> Kind.SHELL;
			case Ground.FEATHER -> Kind.FEATHER;
			default -> Kind.PEBBLE;
		};
	}

	// --------------------------------------------------------------- pickup

	/**
	 * Handles a right-click: place a stone on a cairn, take what is under the crosshair, or pet a creature.
	 * Returns true if the click was used, so the caller can stop the game from also placing a block.
	 */
	public static boolean tryPickup() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		OnyxConfig config = OnyxConfig.get();
		if (player == null || level == null) {
			return false;
		}
		if (pickedTick == ticks) {
			return true;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		boolean used = false;

		if (config.pebblePocket && config.groundClutter) {
			Map.Entry<Long, Ground.Item> entry = Ground.findPickable(eye, look, REACH);
			if (player.isShiftKeyDown() && selected() == Kind.PEBBLE && count(Kind.PEBBLE) > 0) {
				// Sneak + right-click builds: onto a cairn if aiming at one, otherwise onto the ground.
				if (entry != null && entry.getValue().type == Ground.PEBBLE && entry.getValue().stack < 6) {
					entry.getValue().stack++;
					used = true;
				} else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
						&& hit.getDirection() == Direction.UP && hit.getLocation().distanceTo(eye) < REACH) {
					Vec3 at = hit.getLocation();
					Ground.drop(Ground.PEBBLE, at.x, at.y, at.z, player.getYRot() * Mth.DEG_TO_RAD, Critters.RANDOM.nextInt(1024), 0.0, 0.0);
					used = true;
				}
				if (used) {
					config.pocket[0]--;
					config.save();
					sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_PLACE, 0.4f, 1.5f);
				}
			} else if (entry != null && add(kindOf(entry.getValue().type))) {
				Ground.take(entry.getKey());
				used = true;
				sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, 0.25f, 1.6f);
			}
		}
		if (!used && config.pebblePocket && config.snowPiles) {
			Ground.Pile pile = Ground.findPile(eye, look, REACH);
			if (pile != null && add(Kind.SNOWBALL)) {
				Ground.scoop(pile);
				used = true;
				sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOW_BREAK, 0.4f, 1.4f);
			}
		}
		if (!used && config.pebblePocket && config.glowBugs && count(Kind.FIREFLY) < CAPACITY && LifeFx.catchFirefly(eye, look, REACH)) {
			add(Kind.FIREFLY);
			used = true;
			sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, 0.25f, 2.0f);
		}
		if (!used && config.petting && LifeFx.pet(level, eye, look, 3.5)) {
			used = true;
		}
		if (used) {
			pickedTick = ticks;
			Compat.swing(player);
		}
		return used;
	}

	// ----------------------------------------------------------------- tick

	public static void tick(Minecraft mc, ClientLevel level, LocalPlayer player) {
		ticks++;
		pop = Math.max(0.0f, pop - 0.12f);
		OnyxConfig config = OnyxConfig.get();
		boolean enabled = config.pebblePocket;
		boolean free = mc.gui.screen() == null;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		// Work out what a right-click would do right now, for the hint under the crosshair.
		hint = null;
		if (free && enabled) {
			Map.Entry<Long, Ground.Item> entry = config.groundClutter ? Ground.findPickable(eye, look, REACH) : null;
			if (player.isShiftKeyDown() && selected() == Kind.PEBBLE && count(Kind.PEBBLE) > 0) {
				hint = "onyxvisual.hint.stack";
			} else if (entry != null) {
				hint = "onyxvisual.hint.take";
			} else if (config.snowPiles && Ground.findPile(eye, look, REACH) != null) {
				hint = "onyxvisual.hint.snowball";
			} else if (config.glowBugs && LifeFx.aimingAtFirefly(eye, look, REACH)) {
				hint = "onyxvisual.hint.catch";
			}
		}

		// Right-click that hit nothing interactive still reaches us here.
		boolean useDown = free && mc.options.keyUse.isDown();
		if (useDown && !useWasDown) {
			tryPickup();
		}
		useWasDown = useDown;

		// A punch at a ground flower blows it apart into drifting seeds.
		boolean attackDown = free && mc.options.keyAttack.isDown();
		if (attackDown && !attackWasDown && config.groundClutter) {
			Map.Entry<Long, Ground.Item> entry = Ground.findPickable(eye, look, 3.5);
			if (entry != null && entry.getValue().type == Ground.FLOWER) {
				Ground.Item flower = entry.getValue();
				AtmosFx.fluffBurst(flower.x, flower.y + 0.3, flower.z, 14);
				scatterPetals(flower.x, flower.y + 0.25, flower.z, flower.variant, 0.0, 0.0);
				Ground.take(entry.getKey());
				sound(level, flower.x, flower.y, flower.z, SoundEvents.BEE_POLLINATE, 0.3f, 1.6f);
			}
		}
		attackWasDown = attackDown;

		// Keep the selection on something the pocket actually holds.
		if (count(selected()) <= 0) {
			for (Kind kind : Kind.values()) {
				if (count(kind) > 0) {
					config.pocketSelected = kind.ordinal();
					break;
				}
			}
		}
		while (OnyxVisual.cycleKey != null && OnyxVisual.cycleKey.consumeClick()) {
			if (free && enabled) {
				cycle();
			}
		}

		// Hold the throw key to wind up, release to throw.
		boolean throwDown = free && enabled && OnyxVisual.throwKey != null && OnyxVisual.throwKey.isDown();
		if (throwDown && count(selected()) > 0) {
			chargeTicks++;
		} else if (!throwDown && throwWasDown && chargeTicks > 0) {
			launch(level, player, selected(), charge());
			chargeTicks = 0;
		} else if (!throwDown) {
			chargeTicks = 0;
		}
		throwWasDown = throwDown;

		for (Iterator<Stone> it = STONES.iterator(); it.hasNext(); ) {
			if (!step(level, it.next())) {
				it.remove();
			}
		}
		snapshot = List.copyOf(STONES);
	}

	private static void launch(ClientLevel level, LocalPlayer player, Kind kind, float power) {
		OnyxConfig config = OnyxConfig.get();
		if (config.pocket[kind.ordinal()] <= 0) {
			return;
		}
		config.pocket[kind.ordinal()]--;
		config.save();
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		Vec3 motion = player.getDeltaMovement();
		// Released from the right hand, a little below eye level.
		double side = 0.3;
		double hx = eye.x - look.z * side + look.x * 0.4;
		double hy = eye.y - 0.25 + look.y * 0.4;
		double hz = eye.z + look.x * side + look.z * 0.4;
		Compat.swing(player);
		if (kind == Kind.FIREFLY) {
			LifeFx.releaseFirefly(hx + look.x * 0.4, hy + 0.1, hz + look.z * 0.4);
			sound(level, hx, hy, hz, SoundEvents.BEE_POLLINATE, 0.25f, 1.9f);
			return;
		}
		// Light things cannot be thrown far.
		double reachFactor = kind == Kind.FEATHER ? 0.35 : kind == Kind.FLOWER ? 0.6 : kind == Kind.STICK ? 0.85 : 1.0;
		double speed = (0.45 + power * 0.95) * reachFactor;
		Stone stone = new Stone();
		stone.kind = kind;
		stone.x = stone.px = hx;
		stone.y = stone.py = hy;
		stone.z = stone.pz = hz;
		stone.vx = look.x * speed + motion.x;
		stone.vy = look.y * speed + 0.06 + Math.max(0.0, motion.y) * 0.5;
		stone.vz = look.z * speed + motion.z;
		stone.spinSpeed = 0.5f + power * 0.7f;
		stone.variant = Critters.RANDOM.nextInt(1024);
		STONES.add(stone);
		sound(level, hx, hy, hz, SoundEvents.SNOWBALL_THROW, 0.5f, 0.55f + power * 0.3f);
	}

	private static void scatterPetals(double x, double y, double z, int variant, double vx, double vz) {
		int color = PETALS[variant % PETALS.length];
		for (int i = 0; i < 5; i++) {
			float angle = Critters.RANDOM.nextFloat() * Mth.TWO_PI;
			Ground.Item petal = Ground.drop(Ground.LEAF, x, y - 0.25, z, angle, i, vx + Mth.cos(angle) * 0.14, vz + Mth.sin(angle) * 0.14);
			petal.y = y;
			petal.vy = 0.06 + Critters.RANDOM.nextDouble() * 0.06;
			petal.spin = 0.5f;
			petal.tint = color;
		}
	}

	/** Advances one thrown thing by a tick. Returns false once it has come to rest, sunk or burst. */
	private static boolean step(ClientLevel level, Stone stone) {
		stone.px = stone.x;
		stone.py = stone.y;
		stone.pz = stone.z;
		stone.pspin = stone.spin;
		stone.spin += stone.spinSpeed;
		if (++stone.age > 500) {
			return false;
		}
		Kind kind = stone.kind;
		boolean hard = kind == Kind.PEBBLE || kind == Kind.SHELL;
		if (kind == Kind.FEATHER) {
			// Feathers lose their speed at once and then rock gently down.
			stone.vx = stone.vx * 0.88 + Mth.sin(stone.age * 0.35f) * 0.004;
			stone.vz = stone.vz * 0.88 + Mth.cos(stone.age * 0.29f) * 0.004;
			stone.vy = Math.max(stone.vy * 0.9 - 0.004, -0.045);
		} else if (kind == Kind.FLOWER) {
			stone.vx *= 0.955;
			stone.vz *= 0.955;
			stone.vy -= 0.022;
		} else {
			stone.vy -= kind == Kind.SNOWBALL ? 0.03 : 0.035;
			stone.vx *= 0.992;
			stone.vz *= 0.992;
		}

		// Move in small sub-steps so a fast throw cannot tunnel through a surface.
		double speed = Math.sqrt(stone.vx * stone.vx + stone.vy * stone.vy + stone.vz * stone.vz);
		int steps = Math.max(1, (int) Math.ceil(speed / 0.2));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < steps; i++) {
			double nx = stone.x + stone.vx / steps;
			double ny = stone.y + stone.vy / steps;
			double nz = stone.z + stone.vz / steps;
			pos.set(Mth.floor(nx), Mth.floor(ny), Mth.floor(nz));
			BlockState state = level.getBlockState(pos);

			if (state.is(Blocks.LAVA)) {
				// Anything thrown into lava is gone with a hiss.
				NetherFx.lavaSplash(nx, ny, nz, hard ? 0.9f : 0.5f);
				level.addParticle(ParticleTypes.LARGE_SMOKE, nx, ny + 0.2, nz, 0.0, 0.05, 0.0);
				sound(level, nx, ny, nz, SoundEvents.LAVA_EXTINGUISH, 0.4f, 1.3f);
				return false;
			}
			if (state.is(Blocks.WATER)) {
				double surface = Splash.surface(level, nx, ny, nz);
				if (!Double.isNaN(surface) && ny > surface) {
					// Inside the water block's space but still above the actual surface.
					stone.x = nx;
					stone.y = ny;
					stone.z = nz;
					continue;
				}
				double horizontal = Math.sqrt(stone.vx * stone.vx + stone.vz * stone.vz);
				// A skip needs a hard flat stone, speed and a shallow angle; anything else just plops in.
				if (hard && !Double.isNaN(surface) && stone.vy < 0.0 && horizontal > (kind == Kind.SHELL ? 0.36 : 0.28) && -stone.vy < horizontal * 0.6) {
					stone.skips++;
					stone.y = surface + 0.02;
					stone.x = nx;
					stone.z = nz;
					stone.vy = Math.min(0.26, -stone.vy * 0.55 + 0.07);
					stone.vx *= 0.8;
					stone.vz *= 0.8;
					Splash.ripple(nx, surface, nz, 0.5f + (float) horizontal * 0.5f);
					Splash.burst(nx, surface, nz, 0.12f + (float) horizontal * 0.15f);
					sound(level, nx, surface, nz, SoundEvents.FISHING_BOBBER_SPLASH, 0.18f, 1.5f + stone.skips * 0.08f);
					return true;
				}
				double top = Double.isNaN(surface) ? ny : surface;
				boolean lightweight = kind == Kind.FEATHER || kind == Kind.FLOWER;
				Splash.burst(nx, top, nz, lightweight ? 0.08f : 0.45f);
				sound(level, nx, top, nz, SoundEvents.GENERIC_SPLASH, lightweight ? 0.08f : 0.25f, 1.3f);
				LifeFx.scare(nx, nz, lightweight ? 0.0 : 5.0);
				announce(stone);
				return false;
			}

			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty() && ny - pos.getY() < shape.max(Direction.Axis.Y)) {
				boolean fromAbove = stone.y - pos.getY() >= shape.max(Direction.Axis.Y) - 0.02;
				double top = pos.getY() + shape.max(Direction.Axis.Y);
				if (kind == Kind.SNOWBALL) {
					// Bursts on whatever it hits.
					NetherFx.snowPuff(stone.x, stone.y, stone.z);
					for (int p = 0; p < 10; p++) {
						level.addParticle(ParticleTypes.SNOWFLAKE, stone.x, stone.y + 0.05, stone.z,
								(Critters.RANDOM.nextDouble() - 0.5) * 0.25, Critters.RANDOM.nextDouble() * 0.12, (Critters.RANDOM.nextDouble() - 0.5) * 0.25);
					}
					sound(level, nx, ny, nz, SoundEvents.SNOW_BREAK, 0.5f, 1.2f);
					LifeFx.scare(nx, nz, 5.0);
					return false;
				}
				if (kind != Kind.FEATHER && kind != Kind.FLOWER) {
					knock(level, state, nx, ny, nz);
				}
				if (fromAbove) {
					if (hard && stone.vy < -0.18) {
						// Bounce and tumble on.
						stone.y = top + 0.01;
						stone.vy = -stone.vy * 0.32;
						stone.vx *= 0.6;
						stone.vz *= 0.6;
						stone.spinSpeed *= 0.6f;
						return true;
					}
					land(level, stone, top);
					return false;
				}
				// Hit a wall: lose most of the speed and fall.
				stone.vx *= -0.25;
				stone.vz *= -0.25;
				stone.vy = Math.min(stone.vy, 0.0);
				return true;
			}
			stone.x = nx;
			stone.y = ny;
			stone.z = nz;
		}
		return true;
	}

	/** The sound of a throw hitting a block: bells ring, note blocks chime, everything else gives its own knock. */
	private static void knock(ClientLevel level, BlockState state, double x, double y, double z) {
		if (state.is(Blocks.BELL)) {
			sound(level, x, y, z, SoundEvents.BELL_BLOCK, 1.0f, 1.0f);
		} else if (state.is(Blocks.NOTE_BLOCK)) {
			sound(level, x, y, z, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 0.8f + Critters.RANDOM.nextFloat() * 0.8f);
		} else {
			sound(level, x, y, z, state.getSoundType().getHitSound(), 0.45f, 1.3f);
		}
	}

	/** A throw that has come to rest turns back into something lying on the ground. */
	private static void land(ClientLevel level, Stone stone, double top) {
		switch (stone.kind) {
			case PEBBLE -> {
				// Bowling: a stone that comes to rest against a cairn knocks it over.
				if (Ground.topple(stone.x, stone.z, 0.55, stone.vx, stone.vz)) {
					sound(level, stone.x, top, stone.z, SoundEvents.STONE_HIT, 0.6f, 0.9f);
				}
				Ground.drop(Ground.PEBBLE, stone.x, top, stone.z, stone.spin, stone.variant, stone.vx, stone.vz);
			}
			case SHELL -> Ground.drop(Ground.SHELL, stone.x, top, stone.z, stone.spin, stone.variant, stone.vx, stone.vz);
			case STICK -> Ground.drop(Ground.STICK, stone.x, top, stone.z, stone.spin, stone.variant, stone.vx, stone.vz);
			case FEATHER -> Ground.drop(Ground.FEATHER, stone.x, top, stone.z, stone.spin, stone.variant, 0.0, 0.0);
			case FLOWER -> {
				// The flower takes root where it falls and sheds a few petals.
				Ground.drop(Ground.FLOWER, stone.x, top, stone.z, stone.spin, stone.variant, 0.0, 0.0).active = false;
				scatterPetals(stone.x, top + 0.3, stone.z, stone.variant, stone.vx * 0.3, stone.vz * 0.3);
			}
			default -> {
			}
		}
		if (stone.kind != Kind.FEATHER && stone.kind != Kind.FLOWER) {
			LifeFx.scare(stone.x, stone.z, 4.5);
		}
		announce(stone);
	}

	private static void announce(Stone stone) {
		if (stone.skips <= 0) {
			return;
		}
		OnyxConfig config = OnyxConfig.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		lastSkips = stone.skips;
		if (stone.skips > config.bestSkips) {
			config.bestSkips = stone.skips;
			config.save();
			player.sendOverlayMessage(Component.translatable("onyxvisual.pebble.record", stone.skips));
		} else {
			player.sendOverlayMessage(Component.translatable("onyxvisual.pebble.skips", stone.skips, config.bestSkips));
		}
	}

	private static void sound(ClientLevel level, double x, double y, double z, SoundEvent event, float volume, float pitch) {
		level.playLocalSound(x, y, z, event, SoundSource.PLAYERS, volume, pitch, false);
	}

	// ----------------------------------------------------------- test hooks

	public static int debugLastSkips() {
		return lastSkips;
	}

	public static int debugFlying() {
		return STONES.size();
	}

	/** Lays one decoration of the given ground type a little way in front of the player. */
	public static void debugDropAhead(int type, double distance) {
		LocalPlayer player = Minecraft.getInstance().player;
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Ground.drop(type, player.getX() - Mth.sin(yaw) * distance, player.getY(), player.getZ() + Mth.cos(yaw) * distance, 0.4f, 7, 0.0, 0.0).active = false;
	}

	// --------------------------------------------------------------- render

	static void render(Model m, ClientLevel level, Vec3 camera, float partial) {
		for (Stone stone : snapshot) {
			float x = (float) (Mth.lerp(partial, stone.px, stone.x) - camera.x);
			float y = (float) (Mth.lerp(partial, stone.py, stone.y) - camera.y);
			float z = (float) (Mth.lerp(partial, stone.pz, stone.z) - camera.z);
			float spin = Mth.lerp(partial, stone.pspin, stone.spin);
			float heading = (float) Mth.atan2(-stone.vx, stone.vz);
			int light = Terrain.light(level, stone.x, stone.y + 0.2, stone.z);
			switch (stone.kind) {
				case STICK -> {
					// End over end.
					m.place(x, y, z, PX, heading, spin, 0.0f, light);
					m.material(Model.WOOD);
					m.box(-0.5f, -0.5f, -4.5f, 0.5f, 0.5f, 4.5f, 0x6A4A2A);
					m.box(-0.4f, 0.5f, 1.0f, 0.4f, 2.6f, 1.8f, 0x60422A);
				}
				case FLOWER -> {
					m.place(x, y, z, PX, heading, spin * 0.6f, spin * 0.3f, light);
					int petal = PETALS[stone.variant % PETALS.length];
					m.material(Model.LEAF);
					m.box(-0.4f, -3, -0.4f, 0.4f, 0, 0.4f, 0x4A8A32);
					m.material(Model.PETAL);
					m.box(-1.5f, 0, -0.5f, 1.5f, 0.8f, 0.5f, petal);
					m.box(-0.5f, 0, -1.5f, 0.5f, 0.8f, 1.5f, petal);
				}
				case SNOWBALL -> {
					m.place(x, y, z, PX, heading + spin, spin * 0.5f, 0.0f, light);
					m.material(Model.SNOW);
					m.box(-1.6f, -1.6f, -1.6f, 1.6f, 1.6f, 1.6f, 0xFFFFFF);
					m.box(-2.0f, -1.0f, -1.0f, 2.0f, 1.0f, 1.0f, 0xF2F6FA);
					m.box(-1.0f, -1.0f, -2.0f, 1.0f, 1.0f, 2.0f, 0xF2F6FA);
					m.box(-1.0f, -2.0f, -1.0f, 1.0f, 2.0f, 1.0f, 0xF2F6FA);
				}
				case FEATHER -> {
					float rock = 0.6f * Mth.sin(stone.age * 0.35f + partial * 0.35f);
					m.place(x, y, z, PX, heading + spin * 0.1f, rock, rock * 0.7f, light);
					Ground.featherModel(m, (stone.variant & 1) == 0 ? 0xF4F4F0 : 0x9A8A76);
				}
				case SHELL -> {
					m.place(x, y, z, PX, heading + spin, stone.skips > 0 ? 0.0f : spin * 0.3f, 0.0f, light);
					Ground.shellModel(m, 0xF4D8C0);
				}
				default -> {
					// A skipping stone spins flat like a frisbee; otherwise it tumbles a little too.
					m.place(x, y, z, PX, heading + spin, stone.skips > 0 ? 0.0f : spin * 0.3f, 0.0f, light);
					int grey = 0x80 + (stone.variant & 31);
					int rgb = grey << 16 | grey << 8 | grey + 4;
					m.material(Model.STONE);
					m.box(-1.6f, -0.8f, -1.3f, 1.6f, 0.8f, 1.3f, rgb);
					m.box(-0.9f, 0.8f, -0.8f, 1.1f, 1.4f, 0.8f, rgb + 0x0A0A0A);
				}
			}
		}
	}
}
