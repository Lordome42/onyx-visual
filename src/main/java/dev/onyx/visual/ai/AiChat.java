package dev.onyx.visual.ai;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

/** The conversation, kept for the whole game session so closing the window loses nothing. */
public final class AiChat {
	public static final class Entry {
		public final boolean user;
		public final String text;
		public final boolean error;
		public float shown;
		public float flash;

		Entry(boolean user, String text, boolean error) {
			this.user = user;
			this.text = text;
			this.error = error;
			this.shown = user || error ? text.length() : 0.0f;
		}
	}

	public static final List<Entry> ENTRIES = new ArrayList<>();
	public static boolean pending;
	public static String lastQuestion = "";
	private static int generation;

	private AiChat() {
	}

	public static void ask(String question) {
		ask(question, false);
	}

	public static void ask(String question, boolean toChat) {
		String q = question.strip();
		if (q.isEmpty() || pending) {
			return;
		}
		lastQuestion = q;
		ENTRIES.add(new Entry(true, q, false));
		List<AiClient.Message> history = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (!e.error) {
				history.add(new AiClient.Message(e.user ? "user" : "assistant", e.text));
			}
		}
		pending = true;
		int ticket = ++generation;
		AiClient.ask(history).whenComplete((answer, error) -> Minecraft.getInstance().execute(() -> {
			if (ticket != generation) {
				return;
			}
			pending = false;
			String text = error != null ? AiClient.describe(error) : answer;
			ENTRIES.add(new Entry(false, text, error != null));
			var player = Minecraft.getInstance().player;
			if (toChat && player != null) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[AI] ")
						.withStyle(net.minecraft.ChatFormatting.BLUE)
						.append(net.minecraft.network.chat.Component.literal(text)
								.withStyle(error != null ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.WHITE)));
			}
		}));
	}

	public static void clear() {
		ENTRIES.clear();
		pending = false;
		generation++;
	}
}
