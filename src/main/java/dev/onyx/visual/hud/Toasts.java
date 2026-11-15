package dev.onyx.visual.hud;

import dev.onyx.visual.OnyxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Little "+3 Oak Log" notes that slide in on the right when something lands in your inventory or pocket. */
public final class Toasts {
	private static final float LIFE = 3.2f;

	private static final class Toast {
		ItemStack icon = ItemStack.EMPTY;
		Component name;
		Object key;
		int amount;
		float age;
		float slot;
		float bump;
	}

	private static final List<Toast> LIST = new ArrayList<>();
	private static Map<Item, Integer> known = new HashMap<>();
	private static LocalPlayer knownPlayer;
	private static float sinceScan;

	private Toasts() {
	}

	/** A phantom pocket item was picked up. */
	public static void phantom(String nameKey) {
		push(nameKey, ItemStack.EMPTY, Component.translatable(nameKey), 1);
	}

	private static void push(Object key, ItemStack icon, Component name, int amount) {
		if (!OnyxConfig.get().hudToasts) {
			return;
		}
		for (Toast toast : LIST) {
			// Picking up more of the same thing bumps the existing note instead of stacking a new one.
			if (toast.key.equals(key) && toast.age < LIFE - 0.4f) {
				toast.amount += amount;
				toast.age = Math.min(toast.age, 0.5f);
				toast.bump = 1.0f;
				return;
			}
		}
		Toast toast = new Toast();
		toast.key = key;
		toast.icon = icon;
		toast.name = name;
		toast.amount = amount;
		toast.slot = LIST.size();
		LIST.add(toast);
		if (LIST.size() > 4) {
			LIST.remove(0);
		}
	}

	static void render(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, float dt) {
		// Compare the inventory with what it held a moment ago. While a screen is open (chests, crafting)
		// things are being moved about by hand, so only the baseline is refreshed.
		sinceScan += dt;
		if (sinceScan > 0.1f) {
			sinceScan = 0.0f;
			Map<Item, Integer> now = new HashMap<>();
			Map<Item, ItemStack> sample = new HashMap<>();
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty()) {
					now.merge(stack.getItem(), stack.getCount(), Integer::sum);
					sample.putIfAbsent(stack.getItem(), stack);
				}
			}
			if (knownPlayer == player && mc.gui.screen() == null && OnyxConfig.get().hudToasts) {
				for (Map.Entry<Item, Integer> entry : now.entrySet()) {
					int gained = entry.getValue() - known.getOrDefault(entry.getKey(), 0);
					if (gained > 0) {
						ItemStack stack = sample.get(entry.getKey());
						push(entry.getKey(), stack.copyWithCount(1), stack.getHoverName(), gained);
					}
				}
			}
			known = now;
			knownPlayer = player;
		}

		if (LIST.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		int baseY = g.guiHeight() / 2 - 12;
		int index = 0;
		for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) {
			Toast toast = it.next();
			toast.age += dt;
			if (toast.age >= LIFE) {
				it.remove();
				continue;
			}
			// Notes below slide up smoothly when one above them disappears.
			toast.slot += (index - toast.slot) * Math.min(1.0f, dt * 10.0f);
			toast.bump = Math.max(0.0f, toast.bump - dt * 4.0f);
			float in = Ui.ease(toast.age / 0.25f);
			float out = Ui.ease((LIFE - toast.age) / 0.4f);
			float alpha = Math.min(in, out);
			String amount = "+" + toast.amount;
			boolean hasIcon = !toast.icon.isEmpty();
			int width = (hasIcon ? 20 : 6) + font.width(amount) + 4 + Math.min(110, font.width(toast.name)) + 6;
			int x = w - 4 - width + Math.round((1.0f - in) * (width + 8));
			int y = baseY + Math.round(toast.slot * 21);
			Ui.panel(g, x, y, width, 18, alpha);
			g.fill(x, y + 4, x + 1, y + 14, Ui.a(Ui.ACCENT, alpha));
			int tx = x + 5;
			if (hasIcon) {
				if (alpha > 0.5f) {
					g.item(toast.icon, x + 3, y + 1);
				}
				tx = x + 21;
			}
			int grow = Math.round(toast.bump * 2.0f);
			g.text(font, amount, tx, y + 5 - grow, Ui.a(0xFF7CF08A, alpha), false);
			g.enableScissor(tx, y, x + width - 4, y + 18);
			g.text(font, toast.name, tx + font.width(amount) + 4, y + 5, Ui.a(Ui.TEXT, alpha), false);
			g.disableScissor();
			index++;
		}
	}
}
