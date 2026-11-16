package dev.onyx.visual.hud;

import dev.onyx.visual.OnyxConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/**
 * Movable HUD widgets. Every widget is drawn inside begin/end, which shifts it by its saved offset. While the chat
 * is open the widgets get outlines and can be dragged with the mouse (double click puts one back). Offsets live in
 * the config (and therefore in saved profiles).
 */
public final class HudLayout {
	public static final String[] IDS = {"info", "stats", "gear", "compass", "look", "schematic", "titles", "keys", "flight"};
	private static final Map<String, int[]> REPORTED = new HashMap<>();
	private static String dragging;
	private static float grabX, grabY;
	private static boolean wasDown;
	private static String lastClickId;
	private static long lastClickTime;

	private HudLayout() {}

	public static int[] offset(String id) {
		int[] o = OnyxConfig.get().hudPos.get(id);
		return o != null && o.length == 2 ? o : new int[2];
	}

	public static void begin(GuiGraphicsExtractor g, String id) {
		int[] o = offset(id);
		g.pose().pushMatrix();
		if (o[0] != 0 || o[1] != 0) g.pose().translate(o[0], o[1]);
	}

	public static void end(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	/** Widgets with a variable size report it so the edit outline fits. */
	public static void report(String id, int x, int y, int w, int h) {
		REPORTED.put(id, new int[]{x, y, w, h});
	}

	private static boolean enabled(String id) {
		OnyxConfig c = OnyxConfig.get();
		return switch (id) {
			case "info" -> c.hud;
			case "stats" -> c.hudStats;
			case "gear" -> c.hudGear;
			case "compass" -> c.hudCompass;
			case "look" -> c.hudLook;
			case "schematic" -> c.hudSchematic;
			case "titles" -> c.hudTitles;
			case "keys" -> c.hudKeys;
			case "flight" -> c.hudFlight;
			default -> false;
		};
	}

	private static int[] base(String id, int w, int h) {
		OnyxConfig c = OnyxConfig.get();
		int[] r = REPORTED.get(id);
		if (r != null) return r;
		int cx = w / 2;
		int row = c.hud ? 52 : 6;
		return switch (id) {
			case "info" -> new int[]{6, 6, 110, 41};
			case "stats" -> new int[]{6, row, 130, 14};
			case "gear" -> new int[]{6, row + (c.hudStats ? 18 : 0), 140, 25};
			case "compass" -> new int[]{cx - 85, 5, 170, 14};
			case "look" -> new int[]{cx - 48, 27, 96, 27};
			case "schematic" -> new int[]{cx - 70, 62, 140, 29};
			case "titles" -> new int[]{cx - 70, Math.max(64, (int) (h * 0.26f)) + (c.hudSchematic ? 40 : 0), 140, 30};
			case "keys" -> new int[]{w - 53, Math.max(5, h / 2 - 86), 49, 47};
			default -> new int[]{cx - 70, h / 2 - 10, 140, 20};
		};
	}

	/** Called every frame; draws the edit outlines and handles dragging while the chat is open. */
	public static void edit(GuiGraphicsExtractor g, Minecraft mc) {
		if (!(mc.gui.screen() instanceof ChatScreen)) {
			if (dragging != null) {
				dragging = null;
				OnyxConfig.get().save();
			}
			wasDown = false;
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		var window = mc.getWindow();
		double mx = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
		double my = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
		boolean down = mc.mouseHandler.isLeftPressed();
		OnyxConfig config = OnyxConfig.get();
		Map<String, int[]> rects = new LinkedHashMap<>();
		for (String id : IDS) {
			if (!enabled(id)) continue;
			int[] b = base(id, w, h);
			int[] o = offset(id);
			rects.put(id, new int[]{b[0] + o[0], b[1] + o[1], b[2], b[3]});
		}
		if (down && !wasDown && my < h - 16) {
			String hitId = null;
			for (Map.Entry<String, int[]> e : rects.entrySet()) {
				int[] r = e.getValue();
				if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) hitId = e.getKey();
			}
			long now = System.currentTimeMillis();
			if (hitId != null) {
				if (hitId.equals(lastClickId) && now - lastClickTime < 350) {
					config.hudPos.remove(hitId);
					config.save();
					lastClickId = null;
				} else {
					dragging = hitId;
					int[] o = offset(hitId);
					grabX = (float) mx - o[0];
					grabY = (float) my - o[1];
					lastClickId = hitId;
					lastClickTime = now;
				}
			}
		}
		if (dragging != null) {
			if (down && rects.containsKey(dragging)) {
				int[] b = base(dragging, w, h);
				int nx = Math.max(-b[0], Math.min(w - b[2] - b[0], Math.round((float) mx - grabX)));
				int ny = Math.max(-b[1], Math.min(h - b[3] - b[1], Math.round((float) my - grabY)));
				config.hudPos.put(dragging, new int[]{nx, ny});
				int[] r = rects.get(dragging);
				r[0] = b[0] + nx;
				r[1] = b[1] + ny;
			} else {
				dragging = null;
				config.save();
			}
		}
		wasDown = down;
		Font font = mc.font;
		int accent = config.gui != null ? config.gui.accent | 0xFF000000 : 0xFFFF8FB9;
		for (Map.Entry<String, int[]> e : rects.entrySet()) {
			int[] r = e.getValue();
			boolean active = e.getKey().equals(dragging);
			int col = (active ? 0xFF000000 : 0xB0000000) | (accent & 0xFFFFFF);
			g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1], col);
			g.fill(r[0] - 1, r[1] + r[3], r[0] + r[2] + 1, r[1] + r[3] + 1, col);
			g.fill(r[0] - 1, r[1], r[0], r[1] + r[3], col);
			g.fill(r[0] + r[2], r[1], r[0] + r[2] + 1, r[1] + r[3], col);
			g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], (active ? 0x40 : 0x1C) << 24 | (accent & 0xFFFFFF));
			Component name = Component.translatable("onyxvisual.hud.w." + e.getKey());
			g.text(font, name, r[0] + 2, Math.max(1, r[1] - 10), 0xFFFFFFFF, true);
		}
		Component hint = Component.translatable("onyxvisual.hud.edit_hint");
		g.text(font, hint, w / 2 - font.width(hint) / 2, h - 32, 0xFFE8E8E8, true);
	}
}
