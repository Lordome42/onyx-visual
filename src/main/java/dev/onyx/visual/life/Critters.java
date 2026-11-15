package dev.onyx.visual.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The ambient creatures. They exist only on the client and never touch the real world. */
final class Critters {
	static final RandomSource RANDOM = RandomSource.create();
	private static final float PX = 1.0f / 16.0f;

	private Critters() {
	}

	abstract static class Critter {
		double x, y, z, px, py, pz;
		float yaw, pyaw;
		int age;
		int dying = -1;
		boolean dead;

		final void baseTick(ClientLevel level, LocalPlayer player) {
			px = x;
			py = y;
			pz = z;
			pyaw = yaw;
			age++;
			if (happy > 0) {
				happy--;
			}
			if (dying >= 0 && ++dying > 10) {
				dead = true;
				return;
			}
			double dx = x - player.getX();
			double dz = z - player.getZ();
			if (dx * dx + dz * dz > maxRange() * maxRange()) {
				dead = true;
				return;
			}
			tick(level, player);
		}

		double maxRange() {
			return 44.0;
		}

		/** Grows in when spawned and shrinks away when leaving, so nothing pops. */
		final float grow() {
			float in = Math.min(1.0f, age / 10.0f);
			float out = dying < 0 ? 1.0f : Math.max(0.0f, 1.0f - dying / 10.0f);
			return in * out;
		}

		final void leave() {
			if (dying < 0) {
				dying = 0;
			}
		}

		final float lerpYaw(float partial) {
			return pyaw + Mth.wrapDegrees((yaw - pyaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * partial;
		}

		/** Turns towards a heading by at most {@code step} radians. */
		final void turnTowards(float target, float step) {
			float diff = Mth.wrapDegrees((target - yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			yaw += Mth.clamp(diff, -step, step);
		}

		static float headingTo(double dx, double dz) {
			return (float) Mth.atan2(-dx, dz);
		}

		private boolean climbing;
		private int climbTicks;

		/**
		 * Walks {@code speed} blocks along {@code dir}, keeping {@code clearance} blocks of free ground ahead.
		 * Steps up by hopping first and only then moving in, so the body never sinks into a block.
		 * Returns false when the way is blocked; the caller should pick a new direction.
		 */
		final boolean walk(ClientLevel level, float dir, double speed, double clearance, boolean water) {
			double here = Terrain.groundY(level, x, y + 0.6, z, water);
			if (Double.isNaN(here)) {
				// Something was built on top of us, or the ground vanished.
				here = Terrain.groundY(level, x, y + 2.4, z, water);
				if (Double.isNaN(here)) {
					leave();
					return false;
				}
			}
			double fx = -Mth.sin(dir);
			double fz = Mth.cos(dir);
			double ahead = Terrain.groundY(level, x + fx * (speed + clearance), y + 0.6, z + fz * (speed + clearance), water);
			if (Double.isNaN(ahead) || ahead - here > 1.05 || here - ahead > 1.6) {
				climbing = false;
				y += Mth.clamp(here - y, -0.4, 0.4);
				return false;
			}
			// A step right in front: hop up in place until level with it, then move in.
			double near = Terrain.groundY(level, x + fx * (speed + clearance * 0.6), y + 0.6, z + fz * (speed + clearance * 0.6), water);
			if (!Double.isNaN(near) && near > y + 0.05 && near - here <= 1.05) {
				climbing = true;
				climbTicks = 0;
				y = Math.min(near, y + 0.34);
				if (y < near - 0.02) {
					return true;
				}
			}
			if (climbing && (here >= y - 0.02 || ++climbTicks > 14)) {
				climbing = false;
			}
			if (!climbing) {
				y += Mth.clamp(here - y, -0.4, 0.4);
			}
			x += fx * speed;
			z += fz * speed;
			return true;
		}

		/** Drops onto the ground under the creature without moving; removes it if there is none. */
		final void settle(ClientLevel level, boolean water) {
			double here = Terrain.groundY(level, x, y + 0.6, z, water);
			if (Double.isNaN(here)) {
				here = Terrain.groundY(level, x, y + 2.4, z, water);
				if (Double.isNaN(here)) {
					leave();
					return;
				}
			}
			y += Mth.clamp(here - y, -0.4, 0.4);
		}

		/** Ticks of "happy hop" left after being petted. */
		int happy;

		/** How high the picking ray may pass above the creature's feet and still count as aiming at it. */
		float height() {
			return 0.4f;
		}

		/** Something landed or moved suddenly nearby. */
		void scare(double fromX, double fromZ) {
		}

		/** The player reached out and petted it. Returns false if this creature cannot be petted. */
		boolean pet() {
			happy = 16;
			return true;
		}

		abstract void tick(ClientLevel level, LocalPlayer player);

		abstract void render(Model model, ClientLevel level, float cx, float cy, float cz, float partial, float time);
	}

	// --------------------------------------------------------------- sparrow

	static final class Sparrow extends Critter {
		private static final int[][] COLORS = {
				{0x8A6A4A, 0x5E4632, 0xD8C8B0}, {0x4A78C8, 0x2E4E8E, 0xE8ECF4}, {0xC83A32, 0x8A2420, 0x3A2A2A},
				{0xE8C83A, 0x9A8428, 0x4A4A3A}, {0x707884, 0x4A525C, 0xE0E0E0}
		};
		private final int[] color = COLORS[RANDOM.nextInt(COLORS.length)];
		private int wait = 10 + RANDOM.nextInt(40);
		private int hop;
		private int peck;
		private boolean flying;
		private double vx, vy, vz;
		private double baseY;
		private boolean startled;

		@Override
		void scare(double fromX, double fromZ) {
			startled = true;
		}

		@Override
		boolean pet() {
			startled = true;
			return false;
		}

		Sparrow(double x, double y, double z) {
			this.x = px = x;
			this.y = py = baseY = y;
			this.z = pz = z;
			this.yaw = pyaw = RANDOM.nextFloat() * Mth.TWO_PI;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			double dx = x - player.getX();
			double dz = z - player.getZ();
			double distSq = dx * dx + dz * dz;
			if (flying) {
				x += vx;
				y += vy;
				z += vz;
				vy = Math.min(vy + 0.004, 0.2);
				if (age % 20 == 0) {
					yaw += (RANDOM.nextFloat() - 0.5f) * 0.6f;
					vx = -Mth.sin(yaw) * 0.32;
					vz = Mth.cos(yaw) * 0.32;
				}
				if (y - baseY > 26.0) {
					leave();
				}
				return;
			}
			double scare = player.isCrouching() ? 2.0 : 5.0;
			if (startled || distSq < scare * scare || level.isDarkOutside() && RANDOM.nextInt(200) == 0) {
				flying = true;
				if (RANDOM.nextInt(3) == 0) {
					Ground.drop(Ground.FEATHER, x, y, z, yaw, color[0] == 0x8A6A4A ? 1 : 0, 0.0, 0.0).vy = 0.12;
				}
				yaw = headingTo(dx, dz) + (RANDOM.nextFloat() - 0.5f) * 1.2f;
				vx = -Mth.sin(yaw) * 0.3;
				vz = Mth.cos(yaw) * 0.3;
				vy = 0.14;
				return;
			}
			if (hop > 0) {
				hop--;
				double nx = x - Mth.sin(yaw) * 0.07;
				double nz = z + Mth.cos(yaw) * 0.07;
				double ground = Terrain.groundY(level, nx, baseY + 0.6, nz, false);
				if (Double.isNaN(ground) || Math.abs(ground - baseY) > 1.05) {
					yaw += Mth.PI;
					hop = 0;
				} else {
					x = nx;
					z = nz;
					baseY = ground;
				}
				y = baseY + Mth.sin(hop / 6.0f * Mth.PI) * 0.18;
				return;
			}
			if (age % 10 == 0) {
				double ground = Terrain.groundY(level, x, baseY + 0.6, z, false);
				if (Double.isNaN(ground)) {
					leave();
					return;
				}
				baseY = ground;
			}
			y = baseY;
			if (peck > 0) {
				peck--;
				return;
			}
			if (--wait <= 0) {
				wait = 12 + RANDOM.nextInt(50);
				if (RANDOM.nextInt(3) == 0) {
					peck = 14;
				} else {
					yaw += (RANDOM.nextFloat() - 0.5f) * 2.4f;
					hop = 6;
				}
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			float pitch = flying ? -0.25f : peck > 0 ? 0.75f * Math.abs(Mth.sin((peck - partial) * 0.9f)) : 0.0f;
			m.place(cx, cy, cz, PX * 0.9f * grow(), lerpYaw(partial), pitch, 0.0f, Terrain.light(level, x, y + 0.3, z));
			int body = color[0], dark = color[1], belly = color[2];
			m.material(Model.FEATHER);
			m.box(-1.5f, 1, -2, 1.5f, 4, 2, body);
			m.box(-1.2f, 3.2f, 1.2f, 1.2f, 5.6f, 3.4f, body);
			m.box(-1.0f, 5.6f, 1.5f, 1.0f, 5.9f, 3.0f, dark);
			m.material(Model.FLUFF);
			m.box(-1.25f, 0.95f, -1.5f, 1.25f, 2.4f, 2.08f, belly);
			m.box(-0.9f, 3.3f, 3.2f, 0.9f, 4.3f, 3.45f, belly);
			m.material(Model.PLAIN);
			m.box(-0.4f, 3.9f, 3.4f, 0.4f, 4.5f, 4.5f, 0xE8A030);
			m.box(-1.26f, 4.4f, 2.5f, -1.15f, 5.0f, 3.1f, 0x101010);
			m.box(1.15f, 4.4f, 2.5f, 1.26f, 5.0f, 3.1f, 0x101010);
			m.box(-1.28f, 4.8f, 2.9f, -1.2f, 5.0f, 3.1f, 0xFFFFFF);
			m.box(1.2f, 4.8f, 2.9f, 1.28f, 5.0f, 3.1f, 0xFFFFFF);
			m.material(Model.WING);
			m.box(-1, 2.6f, -4.6f, 1, 3.3f, -2, dark);
			if (flying) {
				float flap = Mth.sin(time * 34.0f + age) * 0.95f;
				m.beginPart(-1.5f, 3.4f, 0, 2, -flap);
				m.box(-5.5f, 3.2f, -1.6f, -1.5f, 3.6f, 1.8f, dark);
				m.endPart();
				m.beginPart(1.5f, 3.4f, 0, 2, flap);
				m.box(1.5f, 3.2f, -1.6f, 5.5f, 3.6f, 1.8f, dark);
				m.endPart();
			} else {
				m.box(-1.9f, 1.6f, -1.8f, -1.5f, 3.7f, 1.5f, dark);
				m.box(1.5f, 1.6f, -1.8f, 1.9f, 3.7f, 1.5f, dark);
				m.box(-0.9f, 0, 0, -0.5f, 1, 0.4f, 0xC08040);
				m.box(0.5f, 0, 0, 0.9f, 1, 0.4f, 0xC08040);
			}
		}
	}

	// ----------------------------------------------------------------- goose

	static final class Goose extends Critter {
		final boolean grey = RANDOM.nextInt(3) == 0;
		private final double homeX, homeZ;
		private double walk;
		private float speed;
		private float heading;
		private int wait = RANDOM.nextInt(60);
		private int turning;
		private boolean swimming;
		private int look;

		Goose(double x, double y, double z, double homeX, double homeZ) {
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.homeX = homeX;
			this.homeZ = homeZ;
			this.yaw = pyaw = heading = RANDOM.nextFloat() * Mth.TWO_PI;
		}

		boolean moving() {
			return speed > 0.0f && turning == 0;
		}

		@Override
		float height() {
			return 0.9f;
		}

		@Override
		void scare(double fromX, double fromZ) {
			heading = headingTo(x - fromX, z - fromZ);
			speed = 0.1f;
			wait = 40;
			turning = 0;
		}

		@Override
		boolean pet() {
			happy = 16;
			look = 30;
			speed = 0.0f;
			wait = 40;
			return true;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			double dx = x - player.getX();
			double dz = z - player.getZ();
			double distSq = dx * dx + dz * dz;
			if (turning > 0) {
				// Blocked a moment ago: finish turning round before trying to walk again.
				turning--;
			} else if (distSq < 9.0) {
				heading = headingTo(dx, dz);
				speed = 0.085f;
				wait = 20;
			} else if (--wait <= 0) {
				wait = 40 + RANDOM.nextInt(100);
				double hx = homeX - x;
				double hz = homeZ - z;
				if (hx * hx + hz * hz > 49.0) {
					heading = headingTo(hx, hz) + (RANDOM.nextFloat() - 0.5f) * 0.8f;
					speed = 0.045f;
				} else if (RANDOM.nextInt(3) == 0) {
					speed = 0.0f;
					look = 30;
				} else {
					heading = RANDOM.nextFloat() * Mth.TWO_PI;
					speed = 0.04f;
				}
			}
			if (look > 0) {
				look--;
			}
			turnTowards(heading, 0.16f);
			float off = Math.abs(Mth.wrapDegrees((heading - yaw) * Mth.RAD_TO_DEG));
			if (speed > 0.0f && turning == 0 && off < 50.0f) {
				if (walk(level, yaw, speed, 0.45, true)) {
					walk += speed * 9.0;
				} else {
					// Turn well away from the obstacle, once, and wait until facing the new way.
					heading = yaw + Mth.PI * (0.55f + RANDOM.nextFloat() * 0.9f);
					turning = 14;
					wait = 30;
				}
			} else {
				settle(level, true);
			}
			swimming = Terrain.isWater(level, x, y - 0.4, z);
			if (swimming && moving() && age % 9 == 0) {
				dev.onyx.visual.fx.Splash.ripple(x, y + 0.17, z, 0.45f);
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			float swing = moving() ? Mth.sin((float) walk) : 0.0f;
			float roll = swimming ? 0.03f * Mth.sin(time * 2.0f + age) : swing * 0.1f;
			float bob = swimming ? 0.03f * Mth.sin(time * 2.4f + age) - 0.2f : 0.0f;
			m.place(cx, cy + bob, cz, PX * 0.95f * grow(), lerpYaw(partial), 0.0f, roll, Terrain.light(level, x, y + 0.6, z));
			int body = grey ? 0xA89680 : 0xF6F6F2;
			int wing = grey ? 0x7A6A58 : 0xDEDED8;
			int neck = grey ? 0x1E1E1E : 0xF6F6F2;
			int breast = grey ? 0xD8CCB8 : 0xFFFFFF;
			m.material(Model.FEATHER);
			m.box(-2.5f, 4, -4, 2.5f, 9, 4, body);
			m.box(-2.0f, 9, -3, 2.0f, 9.6f, 2.5f, wing);
			m.material(Model.FLUFF);
			m.box(-2.2f, 3.6f, -2.5f, 2.2f, 4.4f, 3.5f, breast);
			m.box(-2.0f, 4.5f, 3.6f, 2.0f, 8.2f, 4.4f, breast);
			m.material(Model.WING);
			m.box(-2.95f, 5, -3.8f, -2.5f, 8.7f, 3, wing);
			m.box(2.5f, 5, -3.8f, 2.95f, 8.7f, 3, wing);
			m.box(-1.6f, 7, -6.0f, 1.6f, 9, -4, grey ? 0xF0F0EC : wing);
			m.box(-1.0f, 7.6f, -6.8f, 1.0f, 8.6f, -6.0f, grey ? 0x2A2A2A : 0xD0D0CA);
			float nod = look > 0 ? 0.25f * Mth.sin(look * 0.3f) : swing * 0.08f;
			m.beginPart(0, 8, 3.5f, 0, -nod);
			m.material(Model.FEATHER);
			m.box(-1, 8, 2.6f, 1, 14, 4.6f, neck);
			m.box(-1.5f, 13, 2.6f, 1.5f, 16, 6.4f, neck);
			m.material(Model.PLAIN);
			if (grey) {
				m.box(-1.56f, 13.2f, 3.2f, 1.56f, 14.6f, 5.0f, 0xF0F0EC);
			}
			int beak = grey ? 0x1E1E1E : 0xF09020;
			m.box(-1, 13.4f, 6.4f, 1, 14.7f, 8.4f, beak);
			m.box(-0.7f, 13.4f, 8.4f, 0.7f, 14.2f, 8.9f, grey ? 0x141414 : 0xD87810);
			m.box(-1.58f, 14.7f, 5.0f, -1.5f, 15.5f, 5.8f, 0x101010);
			m.box(1.5f, 14.7f, 5.0f, 1.58f, 15.5f, 5.8f, 0x101010);
			m.box(-1.6f, 15.2f, 5.5f, -1.52f, 15.5f, 5.8f, 0xFFFFFF);
			m.box(1.52f, 15.2f, 5.5f, 1.6f, 15.5f, 5.8f, 0xFFFFFF);
			m.endPart();
			if (!swimming) {
				int leg = grey ? 0x2A2A2A : 0xF09020;
				m.material(Model.SKIN);
				for (int side = -1; side <= 1; side += 2) {
					m.beginPart(side * 1.3f, 4, 0, 0, swing * 0.6f * side);
					m.box(side * 1.3f - 0.5f, 0, -0.5f, side * 1.3f + 0.5f, 4, 0.5f, leg);
					m.box(side * 1.3f - 1.1f, 0, -0.5f, side * 1.3f + 1.1f, 0.4f, 2.2f, leg);
					m.endPart();
				}
			}
		}
	}

	/** A fluffy chick that trails after its parent. */
	static final class Gosling extends Critter {
		private final Goose parent;
		private final float offset = RANDOM.nextFloat() * Mth.TWO_PI;
		private double walk;
		private boolean moving;
		private boolean swimming;

		Gosling(Goose parent, double x, double y, double z) {
			this.parent = parent;
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.yaw = pyaw = parent.yaw;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			if (parent.dead || parent.dying >= 0) {
				leave();
			}
			// Aim for a spot just behind the parent, each chick slightly to one side.
			double tx = parent.x + Mth.sin(parent.yaw) * 0.9 + Mth.cos(offset) * 0.45;
			double tz = parent.z - Mth.cos(parent.yaw) * 0.9 + Mth.sin(offset) * 0.45;
			double dx = tx - x;
			double dz = tz - z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			moving = dist > 0.35;
			if (moving) {
				turnTowards(headingTo(dx, dz), 0.4f);
				double speed = Math.min(0.11, 0.03 + dist * 0.05);
				if (walk(level, yaw, speed, 0.2, true)) {
					walk += speed * 22.0;
				} else if (dist > 6.0) {
					// Hopelessly cut off: catch up with a little teleport rather than getting stuck.
					double ground = Terrain.groundY(level, tx, parent.y + 0.6, tz, true);
					if (!Double.isNaN(ground)) {
						x = px = tx;
						z = pz = tz;
						y = py = ground;
					}
				}
			} else {
				settle(level, true);
			}
			swimming = Terrain.isWater(level, x, y - 0.4, z);
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			float swing = moving ? Mth.sin((float) walk) : 0.0f;
			float bob = swimming ? -0.1f : Math.abs(swing) * 0.02f;
			m.place(cx, cy + bob, cz, PX * 0.6f * grow(), lerpYaw(partial), 0.0f, swing * 0.14f, Terrain.light(level, x, y + 0.4, z));
			m.material(Model.FLUFF);
			m.box(-2, 2, -2.5f, 2, 6, 2.5f, 0xF2D860);
			m.box(-1.5f, 5, 1.5f, 1.5f, 9, 4.5f, 0xF6E070);
			m.box(-1.0f, 3.5f, -3.2f, 1.0f, 5.5f, -2.5f, 0xD8BC48);
			m.material(Model.PLAIN);
			m.box(-0.7f, 6.2f, 4.5f, 0.7f, 7.2f, 5.8f, 0x3A3A3A);
			m.box(-1.56f, 7.2f, 3.2f, -1.5f, 8.0f, 4.0f, 0x101010);
			m.box(1.5f, 7.2f, 3.2f, 1.56f, 8.0f, 4.0f, 0x101010);
			if (!swimming) {
				for (int side = -1; side <= 1; side += 2) {
					m.beginPart(side, 2, 0, 0, swing * 0.7f * side);
					m.box(side - 0.4f, 0, -0.4f, side + 0.4f, 2, 0.4f, 0x4A4A4A);
					m.box(side - 0.8f, 0, -0.4f, side + 0.8f, 0.3f, 1.4f, 0x4A4A4A);
					m.endPart();
				}
			}
		}
	}

	// -------------------------------------------------------------- hedgehog

	static final class Hedgehog extends Critter {
		private float heading;
		private int wait = RANDOM.nextInt(40);
		private int turning;
		private float curl;
		private float speed;
		private double walk;
		private final boolean apple = RANDOM.nextInt(3) == 0;
		private int startled;

		@Override
		void scare(double fromX, double fromZ) {
			startled = 60;
		}

		@Override
		boolean pet() {
			happy = 16;
			curl = 0.0f;
			startled = 0;
			return true;
		}

		Hedgehog(double x, double y, double z) {
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.yaw = pyaw = heading = RANDOM.nextFloat() * Mth.TWO_PI;
		}

		@Override
		double maxRange() {
			return 34.0;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			double dx = x - player.getX();
			double dz = z - player.getZ();
			if (startled > 0) {
				startled--;
			}
			boolean scared = startled > 0 || happy == 0 && dx * dx + dz * dz < 6.5 && !player.isCrouching();
			curl = Mth.clamp(curl + (scared ? 0.2f : -0.05f), 0.0f, 1.0f);
			if (curl > 0.0f) {
				settle(level, false);
				return;
			}
			if (turning > 0) {
				turning--;
			} else if (--wait <= 0) {
				wait = 30 + RANDOM.nextInt(80);
				if (RANDOM.nextInt(4) == 0) {
					speed = 0.0f;
				} else {
					heading = yaw + (RANDOM.nextFloat() - 0.5f) * 2.6f;
					speed = 0.028f + RANDOM.nextFloat() * 0.02f;
				}
			}
			turnTowards(heading, 0.2f);
			if (speed > 0.0f && turning == 0) {
				if (walk(level, yaw, speed, 0.3, false)) {
					walk += speed * 30.0;
				} else {
					heading = yaw + Mth.PI * (0.6f + RANDOM.nextFloat() * 0.8f);
					turning = 12;
				}
			} else {
				settle(level, false);
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			float sniff = 0.05f * Mth.sin(time * 6.0f + age);
			m.place(cx, cy, cz, PX * 0.85f * grow(), lerpYaw(partial), 0.0f, 0.0f, Terrain.light(level, x, y + 0.3, z));
			m.material(Model.SPINES);
			if (curl > 0.5f) {
				// Rolled into a ball of spines.
				m.box(-3, 0, -3, 3, 5.5f, 3, 0x6A5440);
				m.box(-2.4f, 5.5f, -2.4f, 2.4f, 6.3f, 2.4f, 0x5A4634);
				m.box(-3.5f, 1, -2.2f, 3.5f, 4.6f, 2.2f, 0x5A4634);
				return;
			}
			m.box(-3, 1, -4, 3, 5.5f, 2.5f, 0x6A5440);
			m.box(-2.4f, 5.5f, -3.2f, 2.4f, 6.3f, 1.8f, 0x5A4634);
			m.box(-3.4f, 1.6f, -3.2f, 3.4f, 4.6f, 1.6f, 0x5A4634);
			if (apple) {
				// Carrying a little apple on its spines.
				m.material(Model.PLAIN);
				m.box(-1.2f, 6.3f, -1.8f, 1.2f, 8.5f, 0.6f, 0xD83A2E);
				m.box(-0.2f, 8.5f, -0.8f, 0.2f, 9.3f, -0.4f, 0x5A3A1A);
				m.box(0.2f, 8.9f, -1.0f, 1.2f, 9.2f, -0.2f, 0x4A9A32);
			}
			m.material(Model.FUR);
			m.box(-2.2f, 0.9f, 2.5f, 2.2f, 4.4f, 4.4f, 0xD8BC98);
			m.box(-1.2f, 1.2f + sniff, 4.4f, 1.2f, 3.2f + sniff, 6.2f, 0xE0C8A8);
			m.box(-2.4f, 4.0f, 2.6f, -1.4f, 5.0f, 3.4f, 0xB89878);
			m.box(1.4f, 4.0f, 2.6f, 2.4f, 5.0f, 3.4f, 0xB89878);
			m.material(Model.PLAIN);
			m.box(-0.5f, 2.0f + sniff, 6.2f, 0.5f, 3.0f + sniff, 6.8f, 0x181818);
			m.box(-1.8f, 3.0f, 4.42f, -1.1f, 3.7f, 4.5f, 0x101010);
			m.box(1.1f, 3.0f, 4.42f, 1.8f, 3.7f, 4.5f, 0x101010);
			float step = speed > 0.0f ? Mth.sin((float) walk) * 0.6f : 0.0f;
			for (int i = 0; i < 4; i++) {
				float lx = (i % 2 == 0 ? -1.8f : 1.8f);
				float lz = i < 2 ? 1.5f : -2.5f;
				m.beginPart(lx, 1, lz, 0, (i == 0 || i == 3 ? step : -step));
				m.box(lx - 0.5f, 0, lz - 0.5f, lx + 0.5f, 1.2f, lz + 0.5f, 0xC8A888);
				m.endPart();
			}
		}
	}

	// ------------------------------------------------------------------ crab

	static final class Crab extends Critter {
		private static final int[] SHELL = {0xD8482E, 0xE87A2E, 0xB83A4A, 0x4A7AA8};
		private final int color = SHELL[RANDOM.nextInt(SHELL.length)];
		private int wait = RANDOM.nextInt(30);
		private int burst;
		private int side = 1;
		private float alarm;
		private double walk;

		@Override
		void scare(double fromX, double fromZ) {
			burst = 20;
			alarm = 1.0f;
		}

		@Override
		boolean pet() {
			happy = 16;
			alarm = 1.0f;
			return true;
		}

		Crab(double x, double y, double z) {
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.yaw = pyaw = RANDOM.nextFloat() * Mth.TWO_PI;
		}

		@Override
		double maxRange() {
			return 34.0;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			double dx = x - player.getX();
			double dz = z - player.getZ();
			double distSq = dx * dx + dz * dz;
			boolean threatened = distSq < 12.0;
			alarm = Mth.clamp(alarm + (threatened ? 0.2f : -0.08f), 0.0f, 1.0f);
			if (threatened) {
				// Face the player, claws up, and scuttle off sideways.
				turnTowards(headingTo(-dx, -dz), 0.3f);
				if (burst <= 0 && distSq < 6.0) {
					burst = 14;
					side = RANDOM.nextBoolean() ? 1 : -1;
				}
			} else if (--wait <= 0) {
				wait = 20 + RANDOM.nextInt(60);
				burst = 6 + RANDOM.nextInt(12);
				side = RANDOM.nextBoolean() ? 1 : -1;
				yaw += (RANDOM.nextFloat() - 0.5f) * 1.2f;
			}
			if (burst > 0) {
				burst--;
				// Crabs walk sideways: move along the body's right-hand axis.
				double speed = threatened ? 0.11 : 0.06;
				if (walk(level, yaw + Mth.HALF_PI * side, speed, 0.3, false)) {
					walk += speed * 40.0;
				} else {
					side = -side;
					burst = Math.min(burst, 3);
				}
			} else {
				settle(level, false);
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			m.place(cx, cy, cz, PX * 0.8f * grow(), lerpYaw(partial), 0.0f, 0.0f, Terrain.light(level, x, y + 0.3, z));
			int dark = color - 0x201410;
			m.material(Model.SPECKLE);
			m.box(-3.5f, 1.5f, -2.2f, 3.5f, 3.6f, 2.2f, color);
			m.box(-2.6f, 3.6f, -1.6f, 2.6f, 4.1f, 1.4f, color);
			m.material(Model.PLAIN);
			m.box(-1.6f, 3.6f, 2.0f, -1.1f, 5.2f, 2.5f, dark);
			m.box(1.1f, 3.6f, 2.0f, 1.6f, 5.2f, 2.5f, dark);
			m.box(-1.75f, 5.0f, 1.9f, -0.95f, 5.8f, 2.7f, 0x101010);
			m.box(0.95f, 5.0f, 1.9f, 1.75f, 5.8f, 2.7f, 0x101010);
			float legs = burst > 0 ? Mth.sin((float) walk) : 0.0f;
			for (int s = -1; s <= 1; s += 2) {
				for (int i = 0; i < 3; i++) {
					float lz = -1.6f + i * 1.3f;
					float lift = (i % 2 == 0 ? legs : -legs) * 0.35f * s;
					m.beginPart(s * 3.5f, 2.2f, lz, 2, s * -0.5f + lift);
					m.box(Math.min(s * 3.5f, s * 6.2f), 1.9f, lz - 0.35f, Math.max(s * 3.5f, s * 6.2f), 2.5f, lz + 0.35f, dark);
					m.endPart();
				}
				// Claws lift and snap when alarmed.
				float raise = 0.25f + alarm * (0.75f + 0.15f * Mth.sin(time * 14.0f + age));
				m.beginPart(s * 3.0f, 2.6f, 2.0f, 0, -raise);
				m.box(s * 3.0f - 0.6f, 2.0f, 2.0f, s * 3.0f + 0.6f, 3.2f, 4.2f, dark);
				m.material(Model.SPECKLE);
				m.box(s * 3.0f - 1.1f, 1.7f, 4.2f, s * 3.0f + 1.1f, 3.5f, 6.4f, color);
				m.material(Model.PLAIN);
				m.box(s * 3.0f - 0.8f, 3.5f, 5.0f, s * 3.0f + 0.8f, 4.0f + alarm * 0.5f, 6.6f, color + 0x101008);
				m.endPart();
			}
		}
	}

	// ------------------------------------------------------------------ fish

	/** A fish leaping clear of the water in an arc. */
	static final class Fish extends Critter {
		private static final int[][] COLORS = {{0x8AA8C0, 0xE8F0F4}, {0xD88A3A, 0xF4E0C0}, {0x5A8A6A, 0xD8E8C8}, {0xC8C8D0, 0xFFFFFF}};
		private final int[] color = COLORS[RANDOM.nextInt(COLORS.length)];
		private final double surface;
		private double vx, vy, vz;
		private boolean splashedOut;
		boolean lava;

		Fish(double x, double surface, double z) {
			this.x = px = x;
			this.surface = surface;
			this.y = py = surface - 0.35;
			this.z = pz = z;
			this.yaw = pyaw = RANDOM.nextFloat() * Mth.TWO_PI;
			double speed = 0.07 + RANDOM.nextDouble() * 0.07;
			vx = -Mth.sin(yaw) * speed;
			vz = Mth.cos(yaw) * speed;
			vy = 0.3 + RANDOM.nextDouble() * 0.12;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			x += vx;
			y += vy;
			z += vz;
			vy -= 0.04;
			if (!splashedOut && y > surface) {
				splashedOut = true;
				splash(0.35f);
			}
			if (vy < 0.0 && y < surface - 0.1) {
				splash(0.5f);
				dead = true;
			}
		}

		private void splash(float strength) {
			if (lava) {
				dev.onyx.visual.fx.NetherFx.lavaSplash(x, surface, z, strength * 2.0f);
			} else {
				dev.onyx.visual.fx.Splash.burst(x, surface, z, strength);
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			double hs = Math.sqrt(vx * vx + vz * vz);
			float pitch = (float) -Mth.atan2(vy, hs);
			float wag = 0.5f * Mth.sin(time * 30.0f + age);
			// Lava fish glow: drawn fully lit, in molten colours, and a bit larger.
			int[] color = lava ? new int[] {0xFF8A20, 0xFFE070} : this.color;
			m.place(cx, cy, cz, PX * (lava ? 1.3f : 0.9f), lerpYaw(partial), pitch, 0.0f,
					lava ? 0xF000F0 : Terrain.light(level, x, Math.max(y, surface) + 0.5, z));
			m.material(Model.SCALES);
			m.box(-1, -1.5f, -3, 1, 1.5f, 3.5f, color[0]);
			m.box(-0.8f, -1.0f, 3.5f, 0.8f, 1.0f, 4.8f, color[0]);
			m.box(-1.05f, -1.6f, -2.5f, 1.05f, -0.4f, 3.0f, color[1]);
			m.material(Model.WING);
			m.box(-0.2f, 1.5f, -1.5f, 0.2f, 2.6f, 1.5f, color[0] - 0x181818);
			m.beginPart(0, 0, -3, 1, wag);
			m.box(-0.25f, -2.0f, -5.5f, 0.25f, 2.0f, -3, color[0] - 0x181818);
			m.endPart();
			m.material(Model.PLAIN);
			m.box(-1.06f, 0.2f, 3.2f, -1.0f, 0.9f, 3.9f, 0x101010);
			m.box(1.0f, 0.2f, 3.2f, 1.06f, 0.9f, 3.9f, 0x101010);
		}
	}

	// ------------------------------------------------------------- butterfly

	static final class Butterfly extends Critter {
		private static final int[][] COLORS = {
				{0xF08A1E, 0x2A1A10}, {0xF4E04A, 0x6A5A10}, {0x58A8F0, 0x1A2A5A}, {0xF4F4F4, 0x808890},
				{0xE85AA0, 0x5A1A40}, {0x9A6AF0, 0x2A1A5A}, {0x6AD86A, 0x1A4A2A}
		};
		private final int[] color = COLORS[RANDOM.nextInt(COLORS.length)];
		private final double homeX, homeY, homeZ;
		private double tx, ty, tz;
		private double vx, vy, vz;
		private int retarget;
		private int rest;
		/** The butterfly currently sitting on the player, if any. */
		static Butterfly perched;

		@Override
		boolean pet() {
			return false;
		}

		Butterfly(double x, double y, double z) {
			this.x = px = homeX = tx = x;
			this.y = py = y + 0.6;
			this.homeY = ty = y;
			this.z = pz = homeZ = tz = z;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			if (level.isDarkOutside() || level.isRaining()) {
				leave();
			}
			// Stand still long enough and one will come and sit on your head.
			double pdx = player.getX() - x, pdz = player.getZ() - z;
			if (LifeFx.stillTicks > 120 && (perched == null || perched == this || perched.dead) && pdx * pdx + pdz * pdz < 64.0) {
				perched = this;
				tx = player.getX();
				ty = player.getY() + (player.isCrouching() ? 1.55 : 1.85);
				tz = player.getZ();
				double ddx = tx - x, ddy = ty - y, ddz = tz - z;
				double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
				if (d < 0.15) {
					x = tx;
					y = ty;
					z = tz;
					rest = 2;
					return;
				}
				rest = 0;
				double step = Math.min(d, 0.09);
				x += ddx / d * step;
				y += ddy / d * step + Mth.sin(age * 0.9f) * 0.01;
				z += ddz / d * step;
				turnTowards(headingTo(ddx, ddz), 0.5f);
				return;
			} else if (perched == this) {
				perched = null;
				rest = 0;
				retarget = 0;
			}
			if (rest > 0) {
				rest--;
				return;
			}
			if (--retarget <= 0) {
				retarget = 15 + RANDOM.nextInt(30);
				tx = homeX + (RANDOM.nextDouble() - 0.5) * 9.0;
				tz = homeZ + (RANDOM.nextDouble() - 0.5) * 9.0;
				double ground = Terrain.groundY(level, tx, homeY + 1.0, tz, true);
				if (Double.isNaN(ground)) {
					tx = homeX;
					tz = homeZ;
					ground = homeY;
				}
				// Now and then settle down for a moment.
				boolean land = RANDOM.nextInt(6) == 0;
				ty = ground + (land ? 0.05 : 0.5 + RANDOM.nextDouble() * 1.6);
				if (land) {
					retarget = 50;
				}
			}
			double dx = tx - x, dy = ty - y, dz = tz - z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist < 0.12 && ty - Terrain.groundY(level, x, y, z, true) < 0.1) {
				rest = 30 + RANDOM.nextInt(50);
				vx = vy = vz = 0.0;
				return;
			}
			if (dist > 0.001) {
				vx += dx / dist * 0.012;
				vy += dy / dist * 0.012;
				vz += dz / dist * 0.012;
			}
			// The characteristic bobbing flutter.
			vy += Mth.sin(age * 0.9f) * 0.008;
			vx *= 0.9;
			vy *= 0.9;
			vz *= 0.9;
			x += vx;
			y += vy;
			z += vz;
			if (vx * vx + vz * vz > 1.0e-5) {
				turnTowards(headingTo(vx, vz), 0.5f);
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			m.place(cx, cy, cz, PX * 0.55f * grow(), lerpYaw(partial), rest > 0 ? 0.0f : -0.2f, 0.0f, Terrain.light(level, x, y + 0.2, z));
			m.material(Model.FUR);
			m.box(-0.4f, -0.4f, -2.2f, 0.4f, 0.4f, 2.2f, 0x2A2018);
			m.box(-0.5f, -0.3f, 2.2f, 0.5f, 0.5f, 3.0f, 0x201810);
			m.material(Model.PLAIN);
			m.box(-0.45f, 0.5f, 2.8f, -0.3f, 0.6f, 4.6f, 0x201810);
			m.box(0.3f, 0.5f, 2.8f, 0.45f, 0.6f, 4.6f, 0x201810);
			float flap = rest > 0 ? 0.9f + 0.25f * Mth.sin(time * 3.0f + age) : 0.25f + 0.95f * Mth.sin(time * 26.0f + age);
			for (int side = -1; side <= 1; side += 2) {
				m.beginPart(side * 0.4f, 0, 0, 2, flap * side);
				float a = side * 0.4f, b = side * 3.4f, c = side * 5.2f;
				// Upper and lower wing, each with a patterned face and a dark rim.
				m.material(Model.BUTTERFLY);
				m.box(Math.min(a, c), 0, 0.1f, Math.max(a, c), 0.25f, 3.4f, color[0]);
				m.box(Math.min(a, b), 0, -2.9f, Math.max(a, b), 0.25f, 0.1f, color[0]);
				m.material(Model.PLAIN);
				m.box(Math.min(c, side * 5.6f), 0, 0.4f, Math.max(c, side * 5.6f), 0.25f, 3.0f, color[1]);
				m.box(Math.min(b, side * 3.8f), 0, -2.5f, Math.max(b, side * 3.8f), 0.25f, -0.2f, color[1]);
				m.endPart();
			}
		}
	}

	// ------------------------------------------------------------- dragonfly

	static final class Dragonfly extends Critter {
		private static final int[] COLORS = {0x28B8C8, 0xD84A32, 0x4AC83A, 0x4A6AE8};
		private final int color = COLORS[RANDOM.nextInt(COLORS.length)];
		private final double homeX, homeY, homeZ;
		private double tx, ty, tz;
		private int hover = 10;

		Dragonfly(double x, double y, double z) {
			this.x = px = homeX = tx = x;
			this.homeY = y;
			this.y = py = ty = y + 0.6;
			this.z = pz = homeZ = tz = z;
			this.yaw = pyaw = RANDOM.nextFloat() * Mth.TWO_PI;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			if (level.isDarkOutside() || level.isRaining()) {
				leave();
			}
			double dx = tx - x, dy = ty - y, dz = tz - z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist < 0.1) {
				// Hover in place, then dart somewhere new.
				if (--hover <= 0) {
					hover = 8 + RANDOM.nextInt(30);
					tx = homeX + (RANDOM.nextDouble() - 0.5) * 8.0;
					tz = homeZ + (RANDOM.nextDouble() - 0.5) * 8.0;
					ty = homeY + 0.35 + RANDOM.nextDouble() * 1.1;
					yaw = headingTo(tx - x, tz - z);
				}
				return;
			}
			double step = Math.min(dist, 0.28);
			x += dx / dist * step;
			y += dy / dist * step;
			z += dz / dist * step;
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			float bob = 0.02f * Mth.sin(time * 9.0f + age);
			m.place(cx, cy + bob, cz, PX * 0.6f * grow(), lerpYaw(partial), 0.0f, 0.0f, Terrain.light(level, x, y + 0.2, z));
			m.material(Model.SCALES);
			m.box(-0.4f, -0.4f, -6, 0.4f, 0.4f, 1, color);
			m.box(-0.6f, -0.6f, 1, 0.6f, 0.6f, 2.6f, color);
			m.material(Model.PLAIN);
			m.box(-0.9f, -0.5f, 2.6f, -0.1f, 0.7f, 3.8f, 0x2A3A28);
			m.box(0.1f, -0.5f, 2.6f, 0.9f, 0.7f, 3.8f, 0x2A3A28);
			m.material(Model.LEAF);
			float flap = 0.35f * Mth.sin(time * 70.0f + age);
			for (int side = -1; side <= 1; side += 2) {
				m.beginPart(side * 0.5f, 0.5f, 0, 2, flap * side);
				m.box(Math.min(side * 0.5f, side * 6), 0.5f, 0.9f, Math.max(side * 0.5f, side * 6), 0.62f, 2.3f, 0xD8ECF4);
				m.endPart();
				m.beginPart(side * 0.5f, 0.5f, 0, 2, -flap * side);
				m.box(Math.min(side * 0.5f, side * 5.4f), 0.5f, -0.9f, Math.max(side * 0.5f, side * 5.4f), 0.62f, 0.5f, 0xC8E0EC);
				m.endPart();
			}
		}
	}

	// ----------------------------------------------------------------- snail

	static final class Snail extends Critter {
		private static final int[][] SHELLS = {{0x9A6A3A, 0xC89A5A}, {0x7A5A4A, 0xB08A6A}, {0xB88A3A, 0xE8C87A}, {0x6A4A5A, 0xA07A8A}};
		private final int[] shell = SHELLS[RANDOM.nextInt(SHELLS.length)];
		private float hide;
		private boolean scared;

		@Override
		boolean pet() {
			happy = 16;
			return true;
		}

		Snail(double x, double y, double z) {
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.yaw = pyaw = RANDOM.nextFloat() * Mth.TWO_PI;
		}

		@Override
		double maxRange() {
			return 30.0;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			double dx = x - player.getX();
			double dz = z - player.getZ();
			scared = dx * dx + dz * dz < 4.0 && !player.isCrouching();
			hide = Mth.clamp(hide + (scared ? 0.15f : -0.04f), 0.0f, 1.0f);
			if (hide > 0.0f) {
				settle(level, false);
				return;
			}
			if (age % 60 == 0) {
				yaw += (RANDOM.nextFloat() - 0.5f) * 0.9f;
			}
			if (!walk(level, yaw, 0.006, 0.25, false)) {
				yaw += 0.6f;
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			m.place(cx, cy, cz, PX * 0.8f * grow(), lerpYaw(partial), 0.0f, 0.0f, Terrain.light(level, x, y + 0.3, z));
			float out = 1.0f - hide;
			float stretch = 0.25f * Mth.sin(time * 1.5f + age) * out;
			int skin = 0xD8C8A0;
			m.material(Model.SKIN);
			m.box(-1, 0, -3 * out - 0.5f, 1, 1.2f, (3.4f + stretch) * out + 0.5f, skin);
			if (out > 0.6f) {
				float tip = 3.4f + stretch;
				m.box(-0.8f, 1.2f, tip - 0.5f, -0.4f, 3.0f, tip - 0.1f, skin);
				m.box(0.4f, 1.2f, tip - 0.5f, 0.8f, 3.0f, tip - 0.1f, skin);
				m.box(-0.9f, 2.8f, tip - 0.6f, -0.3f, 3.4f, tip, 0x202020);
				m.box(0.3f, 2.8f, tip - 0.6f, 0.9f, 3.4f, tip, 0x202020);
			}
			m.material(Model.SHELL);
			m.box(-1.3f, 0.9f, -2.6f, 1.3f, 5.0f, 1.6f, shell[1]);
			m.box(-1.7f, 1.5f, -2.0f, 1.7f, 4.4f, 1.0f, shell[0]);
			m.material(Model.PLAIN);
			m.box(-0.9f, 5.0f, -1.9f, 0.9f, 5.4f, 0.9f, shell[0]);
		}
	}

	// --------------------------------------------------------------- firefly

	/** A glow bug that can be caught and released. */
	static final class Firefly extends Critter {
		private final double homeX, homeY, homeZ;
		private double tx, ty, tz;
		private int retarget;
		private final float phase = RANDOM.nextFloat() * Mth.TWO_PI;

		Firefly(double x, double y, double z) {
			this.x = px = homeX = tx = x;
			this.y = py = ty = y;
			this.homeY = y;
			this.z = pz = homeZ = tz = z;
		}

		@Override
		double maxRange() {
			return 30.0;
		}

		@Override
		boolean pet() {
			return false;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			if (!level.isDarkOutside() && age > 100) {
				leave();
			}
			if (--retarget <= 0) {
				retarget = 20 + RANDOM.nextInt(40);
				tx = homeX + (RANDOM.nextDouble() - 0.5) * 6.0;
				ty = homeY + (RANDOM.nextDouble() - 0.3) * 1.6;
				tz = homeZ + (RANDOM.nextDouble() - 0.5) * 6.0;
			}
			double dx = tx - x, dy = ty - y, dz = tz - z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d > 0.05) {
				double step = Math.min(d, 0.035);
				double nx = x + dx / d * step, ny = y + dy / d * step, nz = z + dz / d * step;
				if (Terrain.stateAt(level, nx, ny, nz).isAir()) {
					x = nx;
					y = ny;
					z = nz;
				} else {
					retarget = 0;
				}
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			// A dark body with a lantern that pulses; drawn fully lit so it shines in the dark.
			float pulse = 0.5f + 0.5f * Mth.sin(time * 3.2f + phase);
			m.place(cx, cy, cz, PX * 0.7f * grow(), lerpYaw(partial), 0.0f, 0.0f, 0xF000F0);
			m.material(Model.PLAIN);
			m.box(-0.5f, 0, 0, 0.5f, 0.8f, 1.6f, 0x2A2418);
			if (pulse > 0.25f) {
				float s = 0.5f + pulse * 0.5f;
				m.box(-0.7f * s, -0.1f, -1.4f * s, 0.7f * s, 0.9f * s, 0, pulse > 0.7f ? 0xF4FF9A : 0xC8E860);
			}
		}
	}

	// ----------------------------------------------------------------- flock

	/** A V of distant birds crossing the sky. */
	static final class Flock extends Critter {
		private final int size = 5 + RANDOM.nextInt(5);
		private final float[] phase = new float[size];

		Flock(double x, double y, double z, float yaw) {
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.yaw = pyaw = yaw;
			for (int i = 0; i < size; i++) {
				phase[i] = RANDOM.nextFloat() * Mth.TWO_PI;
			}
		}

		@Override
		double maxRange() {
			return 150.0;
		}

		@Override
		void tick(ClientLevel level, LocalPlayer player) {
			x -= Mth.sin(yaw) * 0.42;
			z += Mth.cos(yaw) * 0.42;
			y += Mth.sin(age * 0.02f) * 0.01;
			if (level.isDarkOutside()) {
				leave();
			}
		}

		@Override
		void render(Model m, ClientLevel level, float cx, float cy, float cz, float partial, float time) {
			float heading = lerpYaw(partial);
			float sin = Mth.sin(heading);
			float cos = Mth.cos(heading);
			int light = 15 << 20;
			for (int i = 0; i < size; i++) {
				// Alternate sides behind the leader.
				int rank = (i + 1) / 2;
				float side = (i % 2 == 0 ? 1.0f : -1.0f) * rank * 1.9f;
				float back = rank * 1.7f;
				float ox = -cos * side + sin * back;
				float oz = -sin * side - cos * back;
				float bob = 0.25f * Mth.sin(time * 1.3f + phase[i]);
				m.place(cx + ox, cy + bob, cz + oz, PX * 1.7f * grow(), heading, 0.0f, 0.0f, light);
				m.material(Model.FEATHER);
				m.box(-1, -1, -3, 1, 1, 3, 0x3A3A40);
				m.box(-0.7f, -0.6f, 3, 0.7f, 0.8f, 4.6f, 0x2A2A30);
				float flap = 0.7f * Mth.sin(time * 7.0f + phase[i]);
				m.beginPart(-1, 0.5f, 0, 2, -flap);
				m.box(-8, 0.2f, -1.6f, -1, 0.7f, 1.8f, 0x4A4A52);
				m.endPart();
				m.beginPart(1, 0.5f, 0, 2, flap);
				m.box(1, 0.2f, -1.6f, 8, 0.7f, 1.8f, 0x4A4A52);
				m.endPart();
			}
		}
	}
}
