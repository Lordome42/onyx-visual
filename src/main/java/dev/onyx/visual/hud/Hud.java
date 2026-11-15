package dev.onyx.visual.hud;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The on-screen widgets: compass, target card, titles, crosshair accents, keys, gear, stats and flight read-outs. */
public final class Hud {
	private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private static long lastNanos = System.nanoTime();
	private static float clock;

	// Target card.
	private static float lookAlpha;
	private static Component lookName = Component.empty();
	private static ItemStack lookIcon = ItemStack.EMPTY;
	private static boolean lookLiving;
	private static int lookId = -1;
	private static float lookHealth, lookLag, lookMax = 1.0f;

	// Titles.
	private static String shownBiome;
	private static String pendingBiome;
	private static float pendingTime;
	private static long shownDay = Long.MIN_VALUE;
	private static Component title;
	private static Component subtitle;
	private static float titleAge = 99.0f;

	// Crosshair.
	private static float hitMarker;
	private static float spread;

	// Keys.
	private static final float[] keyGlow = new float[7];
	private static final boolean[] keyWas = new boolean[7];
	private static final long[][] clicks = new long[2][32];
	private static final int[] clickHead = new int[2];

	private static float flight;
	private static float smoothSpeed;

	private Hud() {
	}

	public static void register() {
		HudElementRegistry.addLast(OnyxVisual.id("overlay"), (graphics, delta) -> render(graphics));
	}

	/** Called when one of our own hits lands, to flash the hit marker. */
	public static void hit() {
		hitMarker = 1.0f;
	}

	/** Test hook: show a title right away. */
	public static void debugTitle(String text, String sub) {
		title = Component.literal(text);
		subtitle = Component.literal(sub);
		titleAge = 0.0f;
	}

	private static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = now;
		if (player == null || level == null || mc.gui.hud.isHidden()) {
			return;
		}
		clock += dt;
		OnyxConfig config = OnyxConfig.get();
		try {
			ScreenFx.render(g, mc, player, level, dt);
			if (mc.debugEntries.isOverlayVisible()) {
				return;
			}
			if (config.hudCompass) {
				compass(g, mc.font, player);
			}
			look(g, mc, player, level, dt, config.hudLook);
			schematic(g, mc, player, level, dt, config.hudSchematic);
			titles(g, mc.font, player, level, dt, config.hudTitles);
			crosshair(g, player, dt, config.hudCrosshair);
			if (config.hudKeys) {
				keys(g, mc, dt);
			}
			if (config.hudGear) {
				gear(g, mc.font, player);
			}
			if (config.hudStats) {
				stats(g, mc, player, dt);
			}
			flight(g, mc.font, player, dt, config.hudFlight);
			Toasts.render(g, mc, player, dt);
		} catch (RuntimeException e) {
			// A drawing mistake must not take the whole HUD (or the game) down.
			OnyxVisual.LOGGER.debug("HUD widget failed", e);
		}
	}

	// -------------------------------------------------------------- compass

	private static void compass(GuiGraphicsExtractor g, Font font, LocalPlayer player) {
		int w = 170;
		int x = g.guiWidth() / 2 - w / 2;
		int y = 5;
		Ui.panel(g, x, y, w, 14, 1.0f);
		// 0 degrees is south; the strip shows 120 degrees around where you are facing.
		float yaw = Mth.wrapDegrees(player.getYRot());
		float pixelsPerDegree = w / 120.0f;
		g.enableScissor(x + 2, y, x + w - 2, y + 14);
		for (int mark = 0; mark < 360; mark += 15) {
			float diff = Mth.wrapDegrees(mark - yaw);
			if (Math.abs(diff) > 66.0f) {
				continue;
			}
			int px = x + w / 2 + Math.round(diff * pixelsPerDegree);
			float edge = 1.0f - Math.abs(diff) / 66.0f;
			if (mark % 90 == 0) {
				String key = switch (mark) {
					case 0 -> "south";
					case 90 -> "west";
					case 180 -> "north";
					default -> "east";
				};
				Component letter = Component.translatable("onyxvisual.hud.dir." + key);
				g.text(font, letter, px - font.width(letter) / 2, y + 3, Ui.a(mark == 180 ? 0xFFFF6A6A : Ui.TEXT, edge * 1.6f), false);
			} else if (mark % 45 == 0) {
				g.fill(px, y + 4, px + 1, y + 10, Ui.a(0xFFB8B8C0, edge));
			} else {
				g.fill(px, y + 6, px + 1, y + 9, Ui.a(0xFF707078, edge));
			}
		}
		g.disableScissor();
		// A small pointer under the middle.
		int cx = g.guiWidth() / 2;
		g.fill(cx - 2, y + 14, cx + 3, y + 15, Ui.ACCENT);
		g.fill(cx - 1, y + 15, cx + 2, y + 16, Ui.ACCENT);
		g.fill(cx, y + 16, cx + 1, y + 17, Ui.ACCENT);
	}

	// ---------------------------------------------------------- target card

	private static void look(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, ClientLevel level, float dt, boolean enabled) {
		HitResult hit = mc.hitResult;
		boolean found = false;
		if (enabled && hit instanceof EntityHitResult entityHit
				&& !(entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
			// Players get no card at all: no name, no health.
			found = true;
			lookName = entityHit.getEntity().getDisplayName();
			lookIcon = ItemStack.EMPTY;
			if (entityHit.getEntity() instanceof LivingEntity living) {
				if (lookId != living.getId()) {
					lookId = living.getId();
					lookHealth = lookLag = living.getHealth();
				}
				lookLiving = true;
				lookMax = Math.max(1.0f, living.getMaxHealth());
				float real = living.getHealth();
				// The bar itself follows quickly; a pale "ghost" behind it trails, showing the damage just dealt.
				lookHealth += (real - lookHealth) * Math.min(1.0f, dt * 12.0f);
				lookLag = lookLag < lookHealth ? lookHealth : Math.max(lookHealth, lookLag - dt * lookMax * 0.5f);
			} else {
				lookLiving = false;
			}
		} else if (enabled && hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				found = true;
				lookLiving = false;
				lookId = -1;
				lookName = state.getBlock().getName();
				ItemStack stack = new ItemStack(state.getBlock().asItem());
				lookIcon = stack;
			}
		}
		lookAlpha = Ui.approach(lookAlpha, found ? 1.0f : 0.0f, dt * 6.0f);
		if (lookAlpha <= 0.02f) {
			return;
		}
		Font font = mc.font;
		float alpha = lookAlpha;
		boolean icon = !lookIcon.isEmpty();
		int nameWidth = font.width(lookName);
		int w = Math.max(lookLiving ? 96 : 0, nameWidth + (icon ? 30 : 14));
		int h = lookLiving ? 27 : 18;
		int x = g.guiWidth() / 2 - w / 2;
		int y = 27 - Math.round((1.0f - Ui.ease(alpha)) * 5.0f);
		Ui.panel(g, x, y, w, h, alpha);
		int tx = x + 7;
		if (icon) {
			if (alpha > 0.6f) {
				g.item(lookIcon, x + 3, y + 1);
			}
			tx = x + 22;
		}
		g.text(font, lookName, lookLiving ? x + w / 2 - nameWidth / 2 : tx, y + 5, Ui.a(Ui.TEXT, alpha), false);
		if (lookLiving) {
			int bx = x + 7;
			int bw = w - 14;
			int by = y + 17;
			float fraction = Mth.clamp(lookHealth / lookMax, 0.0f, 1.0f);
			float lag = Mth.clamp(lookLag / lookMax, 0.0f, 1.0f);
			Ui.round(g, bx, by, bw, 5, 2, Ui.a(0xFF26262A, alpha));
			Ui.round(g, bx, by, Math.round(bw * lag), 5, 2, Ui.a(0xFFF4F4F4, alpha * 0.8f));
			// Green when healthy, through amber to red.
			int color = fraction > 0.5f ? Ui.mix(0xFFF0C040, 0xFF5CD66A, (fraction - 0.5f) * 2.0f) : Ui.mix(0xFFE84848, 0xFFF0C040, fraction * 2.0f);
			Ui.round(g, bx, by, Math.max(fraction > 0.0f ? 2 : 0, Math.round(bw * fraction)), 5, 2, Ui.a(color, alpha));
			String number = String.format(Locale.ROOT, "%.0f / %.0f", Math.max(0.0f, lookHealth), lookMax);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2.0f, by + 0.5f);
			g.pose().scale(0.5f, 0.5f);
			g.text(font, number, -font.width(number) / 2, 1, Ui.a(0xFF101012, alpha), false);
			g.pose().popMatrix();
		}
	}

	// ------------------------------------------------------------ schematic

	private static float schemAlpha;
	private static int schemBottom;
	private static int schemMode; // 0 place, 1 wrong block, 2 wrong state, 3 matches
	private static BlockState schemState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
	private static Component schemDetail = Component.empty();
	private static int schemDetailColor = Ui.DIM;

	private static void schematic(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, ClientLevel level, float dt, boolean enabled) {
		boolean found = false;
		if (enabled && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && Schematic.available()) {
			BlockPos pos = hit.getBlockPos();
			BlockState here = level.getBlockState(pos);
			BlockState need = Schematic.wanted(pos);
			if (need != null && !need.isAir()) {
				found = true;
				schemState = need;
				if (need.getBlock() != here.getBlock()) {
					schemMode = 1;
				} else if (!need.equals(here)) {
					schemMode = 2;
				} else {
					schemMode = 3;
				}
			}
			// The block is right: look at where a new block would go, against the face we look at.
			if (!found || schemMode == 3) {
				BlockPos next = pos.relative(hit.getDirection());
				BlockState there = level.getBlockState(next);
				BlockState needNext = Schematic.wanted(next);
				if (needNext != null && !needNext.isAir() && (there.isAir() || there.canBeReplaced())) {
					found = true;
					schemState = needNext;
					schemMode = 0;
				}
			}
			if (found) {
				schemDetail(player, here);
			}
		}
		schemAlpha = Ui.approach(schemAlpha, found ? 1.0f : 0.0f, dt * 7.0f);
		if (schemAlpha <= 0.02f) {
			return;
		}
		Font font = mc.font;
		float alpha = schemAlpha;
		int accent = switch (schemMode) {
			case 0 -> 0xFF4FC3F7;
			case 1 -> 0xFFE85A5A;
			case 2 -> 0xFFF0B040;
			default -> 0xFF5CD66A;
		};
		Component label = Component.translatable(switch (schemMode) {
			case 0 -> "onyxvisual.schem.place";
			case 1 -> "onyxvisual.schem.wrong";
			case 2 -> "onyxvisual.schem.state";
			default -> "onyxvisual.schem.ok";
		});
		Component name = schemState.getBlock().getName();
		ItemStack icon = new ItemStack(schemState.getBlock().asItem());
		int top = font.width(label) + 4 + font.width(name);
		int w = Math.max(top, font.width(schemDetail)) + 34;
		int h = 29;
		int lookH = lookLiving ? 27 : 18;
		int y = 27 + Math.round(Ui.ease(lookAlpha) * (lookH + 4)) - Math.round((1.0f - Ui.ease(alpha)) * 4.0f);
		int x = g.guiWidth() / 2 - w / 2;
		Ui.panel(g, x, y, w, h, alpha);
		schemBottom = y + h;
		// Coloured stripe on the left, pulsing gently while something still needs doing.
		float pulse = schemMode == 3 ? 1.0f : 0.65f + 0.35f * Mth.sin(clock * 5.0f);
		Ui.round(g, x + 2, y + 3, 2, h - 6, 1, Ui.a(accent, alpha * pulse));
		if (alpha > 0.6f && !icon.isEmpty()) {
			g.item(icon, x + 8, y + 6);
		}
		int tx = x + 28;
		g.text(font, label, tx, y + 5, Ui.a(accent, alpha), false);
		g.text(font, name, tx + font.width(label) + 4, y + 5, Ui.a(Ui.TEXT, alpha), false);
		g.text(font, schemDetail, tx, y + 17, Ui.a(schemDetailColor, alpha), false);
	}

	private static void schemDetail(LocalPlayer player, BlockState here) {
		if (schemMode == 2) {
			// Same block, wrong orientation or properties: name the first one that differs.
			for (var property : schemState.getProperties()) {
				if (here.hasProperty(property) && !here.getValue(property).equals(schemState.getValue(property))) {
					schemDetail = Component.literal(property.getName() + ": " + here.getValue(property) + " → " + schemState.getValue(property));
					schemDetailColor = 0xFFF0C878;
					return;
				}
			}
		}
		if (schemMode == 3) {
			schemDetail = Component.translatable("onyxvisual.schem.done");
			schemDetailColor = Ui.DIM;
			return;
		}
		net.minecraft.world.item.Item item = schemState.getBlock().asItem();
		if (player.getMainHandItem().is(item)) {
			schemDetail = Component.translatable("onyxvisual.schem.hand");
			schemDetailColor = 0xFF5CD66A;
			return;
		}
		var inventory = player.getInventory();
		int hotbar = -1;
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
				if (i < 9 && hotbar < 0) {
					hotbar = i;
				}
			}
		}
		if (hotbar >= 0) {
			schemDetail = Component.translatable("onyxvisual.schem.hotbar", hotbar + 1, count);
			schemDetailColor = 0xFFB8E0FF;
		} else if (count > 0) {
			schemDetail = Component.translatable("onyxvisual.schem.inv", count);
			schemDetailColor = 0xFFF0C878;
		} else {
			schemDetail = Component.translatable("onyxvisual.schem.none");
			schemDetailColor = 0xFFE88080;
		}
	}

	// --------------------------------------------------------------- titles

	private static void titles(GuiGraphicsExtractor g, Font font, LocalPlayer player, ClientLevel level, float dt, boolean enabled) {
		// A biome has to hold for a couple of seconds before it is announced, so borders do not flicker titles.
		String biome = level.getBiome(player.blockPosition()).unwrapKey().map(key -> key.identifier().toString()).orElse(null);
		long day = Math.floorDiv(level.getDefaultClockTime(), 24000L) + 1;
		if (biome != null && !biome.equals(shownBiome)) {
			if (!biome.equals(pendingBiome)) {
				pendingBiome = biome;
				pendingTime = 0.0f;
			}
			pendingTime += dt;
			if (pendingTime > 2.0f) {
				boolean first = shownBiome == null;
				shownBiome = biome;
				if (enabled && titleAge > 4.5f) {
					Identifier id = Identifier.parse(biome);
					title = Component.translatable("biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
					subtitle = first ? Component.translatable("onyxvisual.hud.day", day) : Component.translatable("onyxvisual.title.entered");
					titleAge = 0.0f;
				}
			}
		} else {
			pendingBiome = null;
		}
		if (shownDay == Long.MIN_VALUE) {
			shownDay = day;
		} else if (day != shownDay) {
			boolean next = day == shownDay + 1;
			shownDay = day;
			if (enabled && next && titleAge > 4.5f) {
				title = Component.translatable("onyxvisual.hud.day", day);
				subtitle = Component.translatable("onyxvisual.title.sunrise");
				titleAge = 0.0f;
			}
		}
		if (title == null || titleAge > 4.5f) {
			return;
		}
		titleAge += dt;
		float in = Ui.ease(titleAge / 0.7f);
		float out = Ui.ease((4.5f - titleAge) / 0.9f);
		float alpha = Math.min(in, out);
		if (alpha <= 0.02f) {
			return;
		}
		int cx = g.guiWidth() / 2;
		// Below the compass and the target card, so the three never overlap.
		int y = Math.max(Math.max(64, (int) (g.guiHeight() * 0.26f)), schemAlpha > 0.02f ? schemBottom + 8 : 0) + Math.round((1.0f - in) * 6.0f);
		int tw = font.width(title);
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(2.0f, 2.0f);
		g.text(font, title, -tw / 2, 0, Ui.a(0xFFFFFFFF, alpha), true);
		g.pose().popMatrix();
		// A line that grows outwards from the middle, with a small diamond at its centre.
		int half = Math.round((tw + 14) * in);
		int ly = y + 22;
		g.fillGradient(cx - half, ly, cx + half, ly + 1, Ui.a(0xFFFFFFFF, alpha * 0.9f), Ui.a(0xFFFFFFFF, alpha * 0.9f));
		g.fill(cx - half, ly, cx - half + half / 3, ly + 1, Ui.a(0xFF101012, alpha * 0.35f));
		g.fill(cx + half - half / 3, ly, cx + half, ly + 1, Ui.a(0xFF101012, alpha * 0.35f));
		g.fill(cx - 2, ly - 1, cx + 3, ly + 2, Ui.a(Ui.ACCENT, alpha));
		g.fill(cx - 1, ly - 2, cx + 2, ly + 3, Ui.a(Ui.ACCENT, alpha));
		if (subtitle != null) {
			g.text(font, subtitle, cx - font.width(subtitle) / 2, ly + 6, Ui.a(0xFFC8C8D2, alpha), true);
		}
	}

	// ------------------------------------------------------------ crosshair

	private static void crosshair(GuiGraphicsExtractor g, LocalPlayer player, float dt, boolean enabled) {
		hitMarker = Math.max(0.0f, hitMarker - dt * 3.2f);
		if (!enabled) {
			return;
		}
		float cx = g.guiWidth() / 2.0f;
		float cy = g.guiHeight() / 2.0f;
		// Four small ticks around the crosshair that drift apart while moving or in the air.
		Vec3 motion = player.getDeltaMovement();
		float speed = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		float target = Mth.clamp(speed * 9.0f + (player.onGround() ? 0.0f : 2.0f), 0.0f, 5.0f);
		spread += (target - spread) * Math.min(1.0f, dt * 10.0f);
		int gap = 8 + Math.round(spread);
		int color = 0x70FFFFFF;
		int ix = (int) cx;
		int iy = (int) cy;
		g.fill(ix - gap - 3, iy, ix - gap, iy + 1, color);
		g.fill(ix + gap + 1, iy, ix + gap + 4, iy + 1, color);
		g.fill(ix, iy - gap - 3, ix + 1, iy - gap, color);
		g.fill(ix, iy + gap + 1, ix + 1, iy + gap + 4, color);
		if (hitMarker > 0.0f) {
			// The classic X that snaps outwards when a hit lands.
			float k = 1.0f - hitMarker;
			float inner = 4.0f + k * 3.0f;
			float outer = 9.0f + k * 4.0f;
			int white = Ui.a(0xFFFFFFFF, hitMarker * 1.5f);
			for (int i = 0; i < 4; i++) {
				float dx = i % 2 == 0 ? 1.0f : -1.0f;
				float dy = i < 2 ? 1.0f : -1.0f;
				Ui.line(g, cx + 0.5f + dx * inner, cy + 0.5f + dy * inner, cx + 0.5f + dx * outer, cy + 0.5f + dy * outer, 1, white);
			}
		}
	}

	// ----------------------------------------------------------------- keys

	private static void keys(GuiGraphicsExtractor g, Minecraft mc, float dt) {
		KeyMapping[] keys = {mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight,
				mc.options.keyJump, mc.options.keyAttack, mc.options.keyUse};
		long now = System.currentTimeMillis();
		for (int i = 0; i < keys.length; i++) {
			boolean down = keys[i].isDown();
			if (down && !keyWas[i] && i >= 5) {
				int mouse = i - 5;
				clicks[mouse][clickHead[mouse]++ & 31] = now;
			}
			keyWas[i] = down;
			keyGlow[i] = Ui.approach(keyGlow[i], down ? 1.0f : 0.0f, dt * (down ? 14.0f : 6.0f));
		}
		Font font = mc.font;
		int s = 15;
		// On the right edge above the pickup notes; the top-right corner is where the game shows its own toasts.
		int x = g.guiWidth() - 4 - s * 3 - 2;
		int y = Math.max(5, g.guiHeight() / 2 - 86);
		key(g, font, x + s + 1, y, s, s, label(keys[0]), keyGlow[0]);
		key(g, font, x, y + s + 1, s, s, label(keys[1]), keyGlow[1]);
		key(g, font, x + s + 1, y + s + 1, s, s, label(keys[2]), keyGlow[2]);
		key(g, font, x + (s + 1) * 2, y + s + 1, s, s, label(keys[3]), keyGlow[3]);
		int half = (s * 3 + 2 - 1) / 2;
		key(g, font, x, y + (s + 1) * 2, half, s, cps(0, now), keyGlow[5]);
		key(g, font, x + half + 1, y + (s + 1) * 2, s * 3 + 2 - half - 1, s, cps(1, now), keyGlow[6]);
		// Space bar: just a line that lights up.
		int by = y + (s + 1) * 3;
		Ui.round(g, x, by, s * 3 + 2, 7, 3, Ui.mix(Ui.PANEL, 0xE05B7BF5, keyGlow[4]));
		g.fill(x + 12, by + 3, x + s * 3 + 2 - 12, by + 4, Ui.mix(0xFF8A8A92, 0xFFFFFFFF, keyGlow[4]));
	}

	private static String label(KeyMapping key) {
		String text = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
		return text.length() > 2 ? text.substring(0, 1) : text;
	}

	private static String cps(int mouse, long now) {
		int count = 0;
		for (long time : clicks[mouse]) {
			if (now - time < 1000L) {
				count++;
			}
		}
		return count > 0 ? Integer.toString(count) : mouse == 0 ? "L" : "R";
	}

	private static void key(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, float glow) {
		Ui.round(g, x, y, w, h, 3, Ui.mix(Ui.PANEL, 0xE05B7BF5, glow));
		g.text(font, text, x + (w - font.width(text) + 1) / 2, y + (h - 8) / 2 + Math.round(glow), Ui.mix(0xFFB4B4BC, 0xFFFFFFFF, glow), false);
	}

	// ----------------------------------------------------------------- gear

	private static void gear(GuiGraphicsExtractor g, Font font, LocalPlayer player) {
		// A compact row under the info card (the bottom-left corner belongs to chat).
		OnyxConfig config = OnyxConfig.get();
		int x = 6;
		int y = (config.hud ? 52 : 6) + (config.hudStats ? 18 : 0);
		for (EquipmentSlot slot : GEAR) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem()) {
				continue;
			}
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			float fraction = max > 0 ? left / (float) max : 1.0f;
			boolean low = fraction < 0.15f;
			Ui.panel(g, x, y, 20, 25, 1.0f);
			if (low) {
				// A worn-out piece pulses red so it is hard to miss.
				Ui.round(g, x, y, 20, 25, 4, Ui.a(0xFFE04040, 0.2f + 0.2f * Mth.sin(clock * 6.0f)));
			}
			g.item(stack, x + 2, y + 2);
			int color = fraction > 0.5f ? Ui.mix(0xFFF0C040, 0xFF5CD66A, (fraction - 0.5f) * 2.0f) : Ui.mix(0xFFE84848, 0xFFF0C040, fraction * 2.0f);
			Ui.round(g, x + 3, y + 20, 14, 2, 1, 0xFF26262A);
			Ui.round(g, x + 3, y + 20, Math.max(1, Math.round(14 * fraction)), 2, 1, color);
			if (low) {
				// The exact number of uses left, in small print.
				String text = Integer.toString(left);
				g.pose().pushMatrix();
				g.pose().translate(x + 10.0f, y + 26.0f);
				g.pose().scale(0.5f, 0.5f);
				g.text(font, text, -font.width(text) / 2, 2, 0xFFFF9090, true);
				g.pose().popMatrix();
			}
			x += 23;
		}
	}

	// ---------------------------------------------------------------- stats

	private static void stats(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, float dt) {
		Font font = mc.font;
		Vec3 motion = player.getDeltaMovement();
		// Blocks per second, smoothed so the number is readable.
		float speed = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0f;
		smoothSpeed += (speed - smoothSpeed) * Math.min(1.0f, dt * 6.0f);
		int ping = -1;
		if (mc.getConnection() != null && !mc.isLocalServer()) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
			if (info != null) {
				ping = info.getLatency();
			}
		}
		int fps = mc.getFps();
		String speedText = String.format(Locale.ROOT, "%.1f", smoothSpeed);
		Component speedUnit = Component.translatable("onyxvisual.stats.speed");
		int x = 6;
		int y = OnyxConfig.get().hud ? 52 : 6;
		int w = 10 + font.width("FPS " + fps) + 9 + (ping >= 0 ? font.width(ping + " ms") + 9 : 0) + font.width(speedText) + 3 + font.width(speedUnit);
		Ui.panel(g, x, y, w, 14, 1.0f);
		int tx = x + 5;
		g.text(font, "FPS", tx, y + 3, Ui.DIM, false);
		tx += font.width("FPS ");
		int fpsColor = fps >= 50 ? 0xFF7CF08A : fps >= 25 ? 0xFFF0C040 : 0xFFE84848;
		g.text(font, Integer.toString(fps), tx, y + 3, fpsColor, false);
		tx += font.width(Integer.toString(fps)) + 4;
		g.fill(tx, y + 4, tx + 1, y + 10, 0x40FFFFFF);
		tx += 5;
		if (ping >= 0) {
			int pingColor = ping < 80 ? 0xFF7CF08A : ping < 180 ? 0xFFF0C040 : 0xFFE84848;
			g.text(font, Integer.toString(ping), tx, y + 3, pingColor, false);
			tx += font.width(Integer.toString(ping)) + 2;
			g.text(font, "ms", tx, y + 3, Ui.DIM, false);
			tx += font.width("ms") + 4;
			g.fill(tx, y + 4, tx + 1, y + 10, 0x40FFFFFF);
			tx += 5;
		}
		g.text(font, speedText, tx, y + 3, Ui.TEXT, false);
		tx += font.width(speedText) + 3;
		g.text(font, speedUnit, tx, y + 3, Ui.DIM, false);
	}

	// --------------------------------------------------------------- flight

	private static void flight(GuiGraphicsExtractor g, Font font, LocalPlayer player, float dt, boolean enabled) {
		flight = Ui.approach(flight, enabled && player.isFallFlying() ? 1.0f : 0.0f, dt * 4.0f);
		if (flight <= 0.02f) {
			return;
		}
		float alpha = flight;
		Vec3 motion = player.getDeltaMovement();
		float speed = (float) Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z) * 20.0f;
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int offset = 46 + Math.round((1.0f - Ui.ease(alpha)) * 10.0f);

		// Left: air speed. Right: altitude with a climb/sink arrow.
		String speedText = String.format(Locale.ROOT, "%.0f", speed);
		Component unit = Component.translatable("onyxvisual.stats.speed");
		int lw = font.width(speedText) + font.width(unit) + 13;
		Ui.panel(g, cx - offset - lw, cy - 8, lw, 16, alpha);
		g.text(font, speedText, cx - offset - lw + 5, cy - 4, Ui.a(Ui.TEXT, alpha), false);
		g.text(font, unit, cx - offset - lw + 8 + font.width(speedText), cy - 4, Ui.a(Ui.DIM, alpha), false);

		String height = "Y " + Mth.floor(player.getY());
		int rw = font.width(height) + 20;
		Ui.panel(g, cx + offset, cy - 8, rw, 16, alpha);
		g.text(font, height, cx + offset + 5, cy - 4, Ui.a(Ui.TEXT, alpha), false);
		float climb = (float) motion.y * 20.0f;
		int ax = cx + offset + rw - 9;
		int arrow = Ui.a(climb > 0.5f ? 0xFF7CF08A : climb < -6.0f ? 0xFFE84848 : 0xFFF0C040, alpha);
		if (Math.abs(climb) < 0.5f) {
			g.fill(ax - 2, cy - 1, ax + 3, cy, arrow);
		} else {
			int dir = climb > 0.0f ? -1 : 1;
			g.fill(ax, cy - 4, ax + 1, cy + 4, arrow);
			g.fill(ax - 1, cy + dir * 3 - (dir > 0 ? 1 : 0), ax + 2, cy + dir * 3 + (dir > 0 ? 0 : 1), arrow);
			g.fill(ax - 2, cy + dir * 2 - (dir > 0 ? 1 : 0), ax + 3, cy + dir * 2 + (dir > 0 ? 0 : 1), arrow);
		}

		// An artificial horizon: a line that rises and falls with the pitch of the glide.
		float pitch = player.getXRot();
		int hy = cy + Math.round(Mth.clamp(-pitch, -60.0f, 60.0f) * 0.9f);
		g.fill(cx - 34, hy, cx - 14, hy + 1, Ui.a(0x90FFFFFF, alpha));
		g.fill(cx + 15, hy, cx + 35, hy + 1, Ui.a(0x90FFFFFF, alpha));
		g.fill(cx - 36, hy - 2, cx - 34, hy + 3, Ui.a(0x90FFFFFF, alpha));
		g.fill(cx + 35, hy - 2, cx + 37, hy + 3, Ui.a(0x90FFFFFF, alpha));
	}
}
