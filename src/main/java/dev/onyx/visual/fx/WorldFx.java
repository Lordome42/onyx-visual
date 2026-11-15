package dev.onyx.visual.fx;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.life.LifeFx;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Entry point for everything drawn directly into the world. */
public final class WorldFx {
	private static long lastNanos = System.nanoTime();
	private static float time;
	private static float dt;
	private static int airTicks;
	private static boolean failed;
	private static float lastNight;
	private static int lastTargetId = -1;
	private static int lastTargetHurt;

	private WorldFx() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WorldFx::collect);
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
			if (OnyxConfig.get().breakBurst) {
				int rgb = state.getMapColor(level, pos).col;
				BurstFx.shatter(Vec3.atCenterOf(pos), Draw.mix(rgb == 0 ? 0xFFFFFF : rgb, 0xFFFFFF, 0.35f));
			}
		});
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
			if (failed) {
				return true;
			}
			try {
				return OutlineFx.render(context, outline, time);
			} catch (RuntimeException e) {
				disable(e);
				return true;
			}
		});
	}

	private static void collect(LevelRenderContext context) {
		if (failed) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = now;
		if (mc.isPaused()) {
			dt = 0.0f;
		}
		time += dt;
		try {
			ClientLevel level = mc.level;
			if (level != null && level.dimension() == Level.OVERWORLD) {
				float stars = context.levelState().skyRenderState.starBrightness;
				float rain = context.levelState().skyRenderState.rainBrightness;
				float night = Mth.clamp((stars - 0.12f) * 3.2f, 0.0f, 1.0f) * Mth.clamp(rain, 0.0f, 1.0f);
				lastNight = night;
				SkyFx.render(context, time, dt, night);
				AtmosFx.render(context, time, night);
			}
			boolean overworld = level != null && level.dimension() == Level.OVERWORLD;
			WorldGlowFx.render(context, time, dt, lastNight, overworld);
			if (level != null && level.dimension() == Level.NETHER) {
				NetherFx.render(context, time);
			}
			Weather.render(context);
			AuraFx.render(context, time, dt);
			PlayerFx.render(context, time, dt);
			BurstFx.render(context, dt);
			LifeFx.render(context, time);
			Splash.render(context, dt);
		} catch (RuntimeException e) {
			disable(e);
		}
	}

	/** Landing detection; runs once per client tick. */
	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			airTicks = 0;
			PlayerFx.reset();
			WorldGlowFx.reset();
			LifeFx.reset();
			Splash.reset();
			AtmosFx.reset();
			Weather.reset();
			NetherFx.reset();
			return;
		}
		PlayerFx.tick(mc, player, time);
		detectHit(mc, player);
		WorldGlowFx.tick(mc, level, player);
		LifeFx.tick(mc, level, player);
		if (!mc.isPaused()) {
			Splash.tick(level, player);
			Weather.tick(level, player, level.dimension() == Level.OVERWORLD);
			if (level.dimension() == Level.OVERWORLD) {
				AtmosFx.tick(level, player);
			} else if (level.dimension() == Level.NETHER) {
				NetherFx.tick(level, player);
			}
		}
		if (player.onGround()) {
			if (airTicks >= 7 && OnyxConfig.get().shockwave && !player.isSpectator()) {
				Vec3 pos = player.position();
				AuraFx.addShock(pos, time * 0.035f);
				for (int i = 0; i < 16; i++) {
					float angle = i / 16.0f * Mth.TWO_PI;
					level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 0.1, pos.z,
							Mth.cos(angle) * 0.12, 0.03, Mth.sin(angle) * 0.12);
				}
			}
			airTicks = 0;
		} else if (!player.isFallFlying() && !player.isInWater() && !player.isPassenger()) {
			airTicks++;
		}
	}

	/**
	 * Flashes when the entity under the crosshair starts its hurt animation while we are swinging.
	 * Reading the hurt animation works the same in singleplayer and on servers.
	 */
	private static void detectHit(Minecraft mc, LocalPlayer player) {
		int id = -1;
		int hurt = 0;
		if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target) {
			id = target.getId();
			hurt = target.hurtTime;
			if (id == lastTargetId && hurt > lastTargetHurt && Compat.isSwinging(player) && OnyxConfig.get().hitFlash) {
				// Aim at the upper body, nudged towards us so the flash is not buried in the model.
				Vec3 centre = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
				Vec3 toPlayer = player.getEyePosition().subtract(centre).normalize().scale(target.getBbWidth() * 0.6 + 0.1);
				BurstFx.impact(centre.add(toPlayer), Draw.hue(time * 0.035f + 0.3f, 0.25f));
				dev.onyx.visual.hud.Hud.hit();
			}
		}
		lastTargetId = id;
		lastTargetHurt = hurt;
	}

	private static void disable(RuntimeException e) {
		// Eye candy must never crash the renderer: log once and switch the world effects off.
		failed = true;
		OnyxVisual.LOGGER.error("Onyx Visual world effects failed and were disabled for this session", e);
	}
}
