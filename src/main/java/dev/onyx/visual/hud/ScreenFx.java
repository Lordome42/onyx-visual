package dev.onyx.visual.hud;

import dev.onyx.visual.OnyxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Effects that live on the "lens": raindrops, frost creeping in from the edges, a low-health pulse, speed lines. */
final class ScreenFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int CRYSTALS = 420;
	// Frost crystals: side (0 top, 1 right, 2 bottom, 3 left), position along the edge, depth and size, fixed once.
	private static final float[] FROST = new float[CRYSTALS * 4];

	private static final class Drop {
		float x, y, vy, size, age, life, wobble;
	}

	private static final List<Drop> DROPS = new ArrayList<>();
	private static float frost;
	private static float danger;
	private static float rush;
	private static float wet;
	private static boolean wasUnderWater;
	private static float clock;

	static {
		RandomSource seeded = RandomSource.create(42L);
		for (int i = 0; i < CRYSTALS; i++) {
			FROST[i * 4] = seeded.nextInt(4);
			FROST[i * 4 + 1] = seeded.nextFloat();
			// Squared so that most crystals hug the edge and only a few reach far in.
			float depth = seeded.nextFloat();
			FROST[i * 4 + 2] = depth * depth * depth;
			FROST[i * 4 + 3] = 1 + seeded.nextInt(3);
		}
	}

	private ScreenFx() {
	}

	static void render(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, ClientLevel level, float dt) {
		OnyxConfig config = OnyxConfig.get();
		int w = g.guiWidth();
		int h = g.guiHeight();
		clock += dt;

		// ---- frost: builds up slowly while standing on snow or ice
		BlockPos feet = player.blockPosition();
		BlockState under = level.getBlockState(feet.below());
		boolean cold = config.fxFrost && (under.is(Blocks.SNOW_BLOCK) || under.is(Blocks.ICE) || under.is(Blocks.PACKED_ICE)
				|| under.is(Blocks.POWDER_SNOW) || under.is(Blocks.SNOW) || level.getBlockState(feet).is(Blocks.SNOW)
				|| level.getBlockState(feet).is(Blocks.POWDER_SNOW));
		frost = Ui.approach(frost, cold ? 1.0f : 0.0f, dt * (cold ? 0.12f : 0.3f));
		if (frost > 0.01f) {
			edges(g, w, h, Ui.a(0x50DCEEFF, frost), 0.16f);
			int reach = (int) (Math.min(w, h) * 0.24f);
			for (int i = 0; i < CRYSTALS; i++) {
				float depth = FROST[i * 4 + 2];
				if (depth > frost) {
					continue;
				}
				int side = (int) FROST[i * 4];
				float along = FROST[i * 4 + 1];
				int size = (int) FROST[i * 4 + 3];
				int d = (int) (depth * reach);
				int x = side == 1 ? w - d - size : side == 3 ? d : (int) (along * w);
				int y = side == 0 ? d : side == 2 ? h - d - size : (int) (along * h);
				float sparkle = 0.7f + 0.3f * Mth.sin(clock * 2.0f + i);
				int color = Ui.a(0xC0F0F8FF, (frost - depth) * 2.5f * (1.0f - depth) * sparkle);
				// A tiny six-armed star: a cross plus two diagonal pixels.
				g.fill(x - size, y, x + size + 1, y + 1, color);
				g.fill(x, y - size, x + 1, y + size + 1, color);
				if (size > 1) {
					g.fill(x - 1, y - 1, x, y, color);
					g.fill(x + 1, y + 1, x + 2, y + 2, color);
					g.fill(x + 1, y - 1, x + 2, y, color);
					g.fill(x - 1, y + 1, x, y + 2, color);
				}
			}
		}

		// ---- raindrops on the lens, plus a wet lens for a moment after surfacing
		boolean underWater = player.isUnderWater();
		if (wasUnderWater && !underWater) {
			wet = 1.0f;
		}
		wasUnderWater = underWater;
		wet = Math.max(0.0f, wet - dt * 0.5f);
		boolean rained = config.fxRain && !underWater && level.isRaining() && level.isRainingAt(BlockPos.containing(player.getEyePosition()).above());
		// Looking up into the rain catches far more drops.
		float pitchFactor = Mth.clamp(0.35f - player.getXRot() / 90.0f, 0.1f, 1.3f);
		float rate = (rained ? 7.0f * pitchFactor : 0.0f) + (config.fxRain ? wet * 30.0f : 0.0f);
		if (rate > 0.0f && DROPS.size() < 40 && RANDOM.nextFloat() < rate * dt) {
			Drop drop = new Drop();
			drop.x = RANDOM.nextFloat() * w;
			drop.y = RANDOM.nextFloat() * h * 0.85f;
			drop.size = 1.5f + RANDOM.nextFloat() * 2.5f;
			drop.vy = 4.0f + RANDOM.nextFloat() * 10.0f;
			drop.life = 1.6f + RANDOM.nextFloat() * 2.2f;
			drop.wobble = RANDOM.nextFloat() * Mth.TWO_PI;
			DROPS.add(drop);
		}
		for (Iterator<Drop> it = DROPS.iterator(); it.hasNext(); ) {
			Drop drop = it.next();
			drop.age += dt;
			if (drop.age >= drop.life || drop.y > h + 4) {
				it.remove();
				continue;
			}
			// Drops hang for a moment, then run down faster and faster.
			float run = Math.max(0.0f, drop.age - 0.35f);
			drop.y += drop.vy * run * dt * 6.0f;
			drop.x += Mth.sin(drop.age * 5.0f + drop.wobble) * dt * 3.0f;
			float fade = Mth.clamp((drop.life - drop.age) * 2.0f, 0.0f, 1.0f) * Mth.clamp(drop.age * 8.0f, 0.0f, 1.0f);
			int s = Math.round(drop.size);
			int x = Math.round(drop.x);
			int y = Math.round(drop.y);
			int trail = (int) Math.min(18.0f, drop.vy * run * 2.2f);
			if (trail > 1) {
				g.fillGradient(x - 1 + s / 2, y - trail, x + s / 2, y, 0x00C8E4FF, Ui.a(0x38C8E4FF, fade));
			}
			Ui.round(g, x - s, y - s, s * 2, s * 2 + 1, s, Ui.a(0x40BCDCFF, fade));
			g.fill(x - s + 1, y - s + 1, x - s + 2, y - s + 2, Ui.a(0xB0FFFFFF, fade));
			g.fill(x - 1, y + s - 1, x + s - 1, y + s, Ui.a(0x30FFFFFF, fade));
		}

		// ---- low health: red edges that throb like a heartbeat; a sharper flash when hit
		float health = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
		float target = config.fxVignette && !player.isCreative() && health < 0.4f ? Mth.clamp((0.4f - health) / 0.28f, 0.0f, 1.0f) : 0.0f;
		danger = Ui.approach(danger, target, dt * 1.5f);
		float beat = 0.6f + 0.4f * (float) Math.pow(Math.abs(Mth.sin(clock * (3.0f + danger * 2.5f))), 6.0);
		float hurt = config.fxVignette ? player.hurtTime / 10.0f : 0.0f;
		float red = Math.min(0.85f, Math.max(danger * beat * 1.1f, hurt * 0.6f));
		if (red > 0.01f) {
			edges(g, w, h, Ui.a(0xFFC00014, red), 0.26f + danger * 0.12f);
		}

		// ---- speed lines streaking in from the edges
		Vec3 motion = player.getDeltaMovement();
		float speed = (float) Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
		rush = Ui.approach(rush, config.fxSpeed ? Mth.clamp((speed - 0.42f) / 0.9f, 0.0f, 1.0f) : 0.0f, dt * 3.0f);
		if (rush > 0.02f) {
			float cx = w * 0.5f;
			float cy = h * 0.5f;
			float radius = Mth.sqrt(cx * cx + cy * cy);
			// The set of lines changes many times a second, which reads as streaming motion.
			long frame = (long) (clock * 24.0f);
			RandomSource lines = RandomSource.create(frame * 7919L);
			int count = 10 + (int) (rush * 26.0f);
			for (int i = 0; i < count; i++) {
				float angle = lines.nextFloat() * Mth.TWO_PI;
				float inner = radius * (0.62f - rush * 0.18f + lines.nextFloat() * 0.25f);
				float outer = inner + radius * (0.1f + lines.nextFloat() * 0.3f);
				float cos = Mth.cos(angle);
				float sin = Mth.sin(angle);
				Ui.line(g, cx + cos * inner, cy + sin * inner, cx + cos * outer, cy + sin * outer, 1,
						Ui.a(0x70FFFFFF, rush * (0.4f + lines.nextFloat() * 0.6f)));
			}
		}
	}

	/** A soft glow hugging all four screen edges, strongest at the very rim. */
	private static void edges(GuiGraphicsExtractor g, int w, int h, int argb, float size) {
		int depth = (int) (Math.min(w, h) * size);
		int clear = argb & 0xFFFFFF;
		g.fillGradient(0, 0, w, depth, argb, clear);
		g.fillGradient(0, h - depth, w, h, clear, argb);
		// fillGradient only runs top to bottom, so the sides are built from thin strips of falling alpha.
		int strips = 14;
		int sideDepth = (int) (depth * 1.4f);
		for (int i = 0; i < strips; i++) {
			float k = 1.0f - (i + 0.5f) / strips;
			int color = Ui.a(argb, k * k);
			int x0 = sideDepth * i / strips;
			int x1 = sideDepth * (i + 1) / strips;
			g.fill(x0, 0, x1, h, color);
			g.fill(w - x1, 0, w - x0, h, color);
		}
	}
}
