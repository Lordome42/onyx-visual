package dev.onyx.visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Locale;

/** A small card in the top-left corner: clock with a sun/moon, coordinates, heading and biome. */
public final class InfoCardHud implements HudElement {
	private static final String[] SUN = {
			"....#....",
			".#.....#.",
			"...###...",
			"..#####..",
			"#.#####.#",
			"..#####..",
			"...###...",
			".#.....#.",
			"....#....",
	};
	private static final String[] MOON = {
			"...###...",
			"..###....",
			".###.....",
			".###.....",
			".###.....",
			".####....",
			"..#####..",
			"...####..",
			".........",
	};
	// Top/bottom colours of the accent stripe at sunrise, noon, sunset and midnight.
	private static final int[] SKY_TOP = {0xFFC46B, 0x7FD4FF, 0xFF8E6B, 0x6B7BFF};
	private static final int[] SKY_BOTTOM = {0xFF7AA8, 0x4D8DFF, 0xB05CFF, 0x2A2F7A};

	private float appear;
	private long lastNanos = System.nanoTime();

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || !OnyxConfig.get().hud
				|| mc.gui.hud.isHidden() || mc.debugEntries.isOverlayVisible()) {
			appear = 0.0f;
			return;
		}
		// Timed with the wall clock rather than the game's frame timer: other mods that redraw the HUD on
		// their own schedule can hand over a timer that never advances, and the card would then never fade in.
		long now = System.nanoTime();
		float seconds = Math.min((now - lastNanos) / 1.0e9f, 0.25f);
		lastNanos = now;
		appear = Math.min(1.0f, appear + Math.max(seconds, 0.004f) * 1.6f);
		float eased = 1.0f - (1.0f - appear) * (1.0f - appear);
		int alpha = (int) (eased * 255.0f);
		if (alpha < 8) {
			return;
		}

		Font font = mc.font;
		long clock = level.getDefaultClockTime();
		int dayTime = (int) Math.floorMod(clock, 24000L);
		long day = Math.floorDiv(clock, 24000L) + 1;
		int hours = (dayTime / 1000 + 6) % 24;
		int minutes = dayTime % 1000 * 60 / 1000;
		boolean night = dayTime >= 13000 && dayTime < 23000;

		String time = String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
		Component dayText = Component.translatable("onyxvisual.hud.day", day);
		BlockPos pos = player.blockPosition();
		String coords = pos.getX() + "  " + pos.getY() + "  " + pos.getZ();
		Component heading = Component.translatable("onyxvisual.hud.dir." + player.getDirection().getName());
		Component biome = level.getBiome(pos).unwrapKey()
				.map(key -> (Component) Component.translatable(biomeKey(key.identifier())))
				.orElse(Component.literal("?"));

		int timeWidth = font.width(time);
		int row1 = 13 + timeWidth + 6 + font.width(dayText);
		int row2 = font.width(coords) + 8 + font.width(heading);
		int row3 = font.width(biome);
		int width = Math.max(row1, Math.max(row2, row3)) + 15;
		int height = 41;

		// Slides in from the left while fading.
		int x = 6 - (int) ((1.0f - eased) * 12.0f);
		int y = 6;

		dev.onyx.visual.hud.HudLayout.report("info", x, y, width, height);
		dev.onyx.visual.hud.HudLayout.begin(graphics, "info");
		try {
			drawCard(graphics, font, alpha, eased, x, y, width, height, dayTime, night, time, dayText, coords, heading, biome, timeWidth);
		} finally {
			dev.onyx.visual.hud.HudLayout.end(graphics);
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics, Font font, int alpha, float eased, int x, int y, int width, int height, int dayTime,
			boolean night, String time, Component dayText, String coords, Component heading, Component biome, int timeWidth) {
		int panel = argb(alpha * 150 / 255, 0x0E0F1A);
		graphics.fill(x + 1, y, x + width - 1, y + height, panel);
		graphics.fill(x, y + 1, x + 1, y + height - 1, panel);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, panel);

		// Accent stripe tinted by the time of day.
		float phase = dayTime / 6000.0f;
		int from = (int) phase % 4;
		int to = (from + 1) % 4;
		float t = phase - (int) phase;
		int top = lerpColor(SKY_TOP[from], SKY_TOP[to], t);
		int bottom = lerpColor(SKY_BOTTOM[from], SKY_BOTTOM[to], t);
		graphics.fillGradient(x + 3, y + 4, x + 5, y + height - 4, argb(alpha, top), argb(alpha, bottom));

		int textX = x + 10;
		drawIcon(graphics, night ? MOON : SUN, textX, y + 5, argb(alpha, night ? 0xD8E0FF : 0xFFD95A));
		graphics.text(font, time, textX + 13, y + 5, argb(alpha, 0xFFFFFF));
		graphics.text(font, dayText, textX + 13 + timeWidth + 6, y + 5, argb(alpha, 0x9A9FB8));

		graphics.text(font, coords, textX, y + 17, argb(alpha, 0xDDE3F5));
		graphics.text(font, heading, textX + font.width(coords) + 8, y + 17, argb(alpha, top));

		graphics.text(font, biome, textX, y + 28, argb(alpha, 0xA8E6B0));
	}

	private static void drawIcon(GuiGraphicsExtractor graphics, String[] rows, int x, int y, int color) {
		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			int start = -1;
			// Merge horizontal runs so an icon is a handful of fills instead of dozens.
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == '#';
				if (on && start < 0) {
					start = col;
				} else if (!on && start >= 0) {
					graphics.fill(x + start, y + row, x + col, y + row + 1, color);
					start = -1;
				}
			}
		}
	}

	private static String biomeKey(Identifier id) {
		return "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.');
	}

	private static int argb(int alpha, int rgb) {
		return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
