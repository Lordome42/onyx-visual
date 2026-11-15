package dev.onyx.visual;

import dev.onyx.visual.life.Pebbles;
import dev.onyx.visual.life.Pebbles.Kind;
import dev.onyx.visual.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Draws the phantom pocket: slots next to the hotbar in game, and a side panel on the inventory. */
final class PocketHud {
	// 16x16 pixel icons; each letter is a colour from the matching palette, '.' is empty.
	private static final String[][] ICONS = {
			{ // pebble
					"................", "................", "................", ".....######.....", "...##aaaaaa##...", "..#aaaaabbbbb#..",
					".#aaaabbbbbbbc#.", ".#aabbbbbbbccc#.", ".#bbbbbbbbcccc#.", ".#bbbbbbccccdd#.", "..#bbbccccddd#..", "...##ccdddd##...",
					".....######.....", "................", "................", "................"},
			{ // stick
					"................", "............##..", "...........#ab#.", "..........#ab#..", ".....##..#ab#...", "....#ab##ab#....",
					".....#abab#.....", "......#ab#......", ".....#ab#.......", "....#ab#........", "...#ab#.........", "..#ab#..........",
					".#ab#...........", ".#b#............", "..#.............", "................"},
			{ // flower
					"................", "......###.......", ".....#aaa#......", "..###aaaaa###...", ".#aaa#aaa#aaa#..", ".#aaaa#c#aaaa#..",
					".#aaa#ccc#aaa#..", "..###aaaaa###...", ".....#aaa#......", "......###d......", ".......d........", "...ee..d........",
					"....eedd........", ".......d........", ".......d........", "................"},
			{ // snowball
					"................", "................", ".....######.....", "...##aaaaaa##...", "..#aaaaaaaaab#..", "..#aaaaaaaabb#..",
					".#aaaaaaaaabbb#.", ".#aaaaaaaabbbb#.", ".#aaaaaaabbbbb#.", ".#aaaaabbbbbbc#.", "..#aabbbbbbcc#..", "..#bbbbbbccc#...",
					"...##bbccc##....", ".....#####......", "................", "................"},
			{ // feather
					"................", "...........###..", ".........##aab#.", "........#aaaab#.", ".......#aaaab#..", "......#aaabab#..",
					".....#aaabab#...", "....#aaabab#....", "....#aabab#.....", "...#aabab#......", "...#abab#.......", "..#cbb##........",
					"..#c##..........", ".#c#............", ".##.............", "................"},
			{ // shell
					"................", "................", ".....######.....", "...##abacab##...", "..#abacabacab#..", ".#abacabacabac#.",
					".#bacabacabaca#.", ".#abacabacabac#.", "..#bacabacaba#..", "..#abacabacab#..", "...#bacabaca#...", "....#bacaba#....",
					".....#dddd#.....", "......####......", "................", "................"},
			{ // firefly
					"................", "................", "....#......#....", ".....#....#.....", "......####......", ".....#bbbb#.....",
					"..cc#bbbbbb#cc..", ".ccc#bbbbbb#ccc.", "..cc#aaaaaa#cc..", ".....#aaaa#.....", ".....#aaaa#.....", "......#aa#......",
					".......##.......", "................", "................", "................"},
	};
	private static final int[][] PALETTES = {
			{0xC8C8CE, 0x9C9CA4, 0x7C7C86, 0x62626C, 0}, {0x9A6E42, 0x6A4A2A, 0, 0, 0}, {0xF4D83A, 0, 0xE88A1E, 0x4A8A32, 0x5A9A3A},
			{0xFFFFFF, 0xDCE6F0, 0xB4C4D8, 0, 0}, {0xFAFAF6, 0xD0D0C8, 0xB8A888, 0, 0}, {0xF8E0CC, 0xE8B8A0, 0xFFF0E0, 0xC89880, 0},
			{0xF0FF80, 0x3A3020, 0xC0E0F0, 0, 0},
	};
	private static final int[] OUTLINES = {0x3C3C42, 0x3A2A18, 0x8A6A10, 0x8090A8, 0x808078, 0x8A6850, 0x1A1810};

	private PocketHud() {
	}

	static void register() {
		HudElementRegistry.addLast(OnyxVisual.id("pebble_pocket"), (graphics, delta) -> hud(graphics));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> inventory(s, graphics));
			}
		});
	}

	private static void hud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		OnyxConfig config = OnyxConfig.get();
		if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) {
			return;
		}
		Font font = mc.font;
		int width = g.guiWidth();
		int height = g.guiHeight();

		String hint = Pebbles.hint();
		if (hint != null && mc.gui.screen() == null) {
			Component text = Component.translatable(hint);
			g.text(font, text, width / 2 - font.width(text) / 2, height / 2 + 10, 0xE0FFFFFF);
		}
		if (!config.pebblePocket) {
			return;
		}
		// Slots line up to the right of the hotbar, mirroring where the off-hand slot sits on the left.
		Kind selected = Pebbles.selected();
		int startX = width / 2 + 91 + 7;
		// On a narrow window the slots wrap into further rows above the first.
		int perRow = Math.max(1, (width - startX - 2) / 23);
		int shown = 0;
		for (Kind kind : Kind.values()) {
			int count = Pebbles.count(kind);
			if (count <= 0) {
				continue;
			}
			boolean active = kind == selected;
			int bounce = active ? Math.round(Mth.sin(Pebbles.pop() * Mth.PI) * 4.0f) : 0;
			int x = startX + (shown % perRow) * 23;
			int y = height - 23 - (shown / perRow) * 24 - bounce;
			slot(g, x, y, kind, count, font, active);
			shown++;
		}
		if (shown > 0) {
			// Key hints sit left of the hotbar's right-hand neighbour so they never overlap the slots.
			String keys = OnyxVisual.throwKey.getTranslatedKeyMessage().getString()
					+ (shown > 1 ? "  " + OnyxVisual.cycleKey.getTranslatedKeyMessage().getString() + " ›" : "");
			int rows = (shown + perRow - 1) / perRow;
			g.text(font, keys, startX + 1, height - 23 - (rows - 1) * 24 - 10, 0xD0FFFFFF);
		}

		float charge = Pebbles.charge();
		if (charge > 0.0f) {
			// Wind-up bar under the crosshair.
			int bw = 40;
			int bx = width / 2 - bw / 2;
			int by = height / 2 + 22;
			g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xA0000000);
			g.fill(bx, by, bx + Math.round(bw * charge), by + 3, charge >= 1.0f ? 0xFF7CF08A : 0xFFEDEDEF);
		}
	}

	private static void inventory(net.minecraft.client.gui.screens.Screen screen, GuiGraphicsExtractor g) {
		OnyxConfig config = OnyxConfig.get();
		if (!config.pebblePocket || !(screen instanceof ContainerScreenAccessor accessor)) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int x = accessor.onyx$leftPos() + accessor.onyx$imageWidth() + 3;
		int y = accessor.onyx$topPos() + 4;
		Component title = Component.translatable("onyxvisual.pocket.title");
		int w = Math.max(48, font.width(title) + 10);
		int rows = (Kind.values().length + 1) / 2;
		int h = 20 + rows * 18 + 6;
		// A small panel in the style of the vanilla container background.
		g.fill(x, y, x + w, y + h, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
		g.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
		g.fill(x + 1, y + 1, x + 2, y + h - 2, 0xFFFFFFFF);
		g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
		g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
		g.text(font, title, x + w / 2 - font.width(title) / 2, y + 5, 0xFF404040, false);
		for (Kind kind : Kind.values()) {
			int i = kind.ordinal();
			int sx = x + w / 2 - 18 + (i % 2) * 18;
			int sy = y + 18 + (i / 2) * 18;
			g.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
			g.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
			g.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
			g.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
			g.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
			int count = Pebbles.count(kind);
			icon(g, kind, sx + 1, sy + 1, count > 0 ? 255 : 55);
			if (count > 0) {
				String text = Integer.toString(count);
				g.text(font, text, sx + 18 - font.width(text), sy + 10, 0xFFFFFFFF);
			}
		}
	}

	private static void slot(GuiGraphicsExtractor g, int x, int y, Kind kind, int count, Font font, boolean active) {
		// Same proportions as a hotbar cell: dark glassy body with a light rim; the selected one is brighter.
		int rim = active ? 0xFFFFFFFF : 0xA0B8B8C0;
		int shade = active ? 0xFFB0B0B8 : 0xA0505058;
		g.fill(x + 1, y, x + 21, y + 22, 0x90000000);
		g.fill(x, y + 1, x + 22, y + 21, 0x90000000);
		g.fill(x + 1, y, x + 21, y + 1, rim);
		g.fill(x + 1, y + 21, x + 21, y + 22, shade);
		g.fill(x, y + 1, x + 1, y + 21, rim);
		g.fill(x + 21, y + 1, x + 22, y + 21, shade);
		icon(g, kind, x + 3, y + 3, 255);
		String text = Integer.toString(count);
		g.text(font, text, x + 20 - font.width(text), y + 12, 0xFFFFFFFF);
	}

	private static void icon(GuiGraphicsExtractor g, Kind kind, int x, int y, int alpha) {
		String[] rows = ICONS[kind.ordinal()];
		int[] palette = PALETTES[kind.ordinal()];
		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			for (int col = 0; col < line.length(); col++) {
				char c = line.charAt(col);
				if (c == '.') {
					continue;
				}
				int rgb = c == '#' ? OUTLINES[kind.ordinal()] : palette[c - 'a'];
				g.fill(x + col, y + row, x + col + 1, y + row + 1, alpha << 24 | rgb);
			}
		}
	}
}
