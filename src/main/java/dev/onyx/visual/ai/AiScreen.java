package dev.onyx.visual.ai;

import java.util.ArrayList;
import java.util.List;

import dev.onyx.visual.OnyxConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** A small chat window for asking an AI questions without leaving the game. */
public final class AiScreen extends Screen {
	private static final int ACCENT = 0xFF5B7BF5;
	private static final int PANEL = 0xFC111114;
	private static final int RING = 0x30FFFFFF;
	private static final int TEXT = 0xFFEDEDEF;
	private static final int DIM = 0xFF8A8A96;
	private static final int PAD = 10;
	private static final int HEADER = 22;
	private static final int FOOTER = 30;

	private static final String[][] PRESETS = {
			// name, url, model
			{"Free", "", ""},
			{"Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"},
			{"OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openrouter/free"},
			{"Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-flash-latest"},
			{"Ollama", "http://localhost:11434/v1/chat/completions", "llama3.2"},
	};

	private final String initial;
	private EditBox input;
	private EditBox urlBox;
	private EditBox modelBox;
	private EditBox keyBox;
	private boolean settings;
	private float open;
	private float clock;
	private long lastNanos = System.nanoTime();
	private float scroll;
	private float scrollTarget;
	private int lastCount = -1;
	private int px, py, pw, ph;
	private final List<int[]> bubbleRects = new ArrayList<>();
	private final List<AiChat.Entry> bubbleEntries = new ArrayList<>();

	public AiScreen() {
		this("");
	}

	public AiScreen(String initial) {
		super(Component.translatable("onyxvisual.ai.title"));
		this.initial = initial;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	private void layout() {
		pw = Math.min(this.width - 16, 380);
		ph = Math.min(this.height - 12, 270);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	@Override
	protected void init() {
		layout();
		String typed = input != null ? input.getValue() : initial;
		input = new EditBox(this.font, px + PAD + 6, py + ph - FOOTER + 10, pw - PAD * 2 - 34, 12, Component.translatable("onyxvisual.ai.hint"));
		input.setBordered(false);
		input.setMaxLength(600);
		input.setTextColor(TEXT);
		input.setHint(Component.translatable("onyxvisual.ai.hint"));
		input.setValue(typed);
		addRenderableWidget(input);

		OnyxConfig config = OnyxConfig.get();
		int fx = px + PAD + 80;
		int fw = pw - PAD * 2 - 86;
		urlBox = field(fx, py + PAD + HEADER + 40, fw, config.aiUrl, 400);
		modelBox = field(fx, py + PAD + HEADER + 62, fw, config.aiModel, 120);
		keyBox = field(fx, py + PAD + HEADER + 84, fw, config.aiKey, 300);
		urlBox.setHint(Component.translatable("onyxvisual.ai.url_hint"));
		modelBox.setHint(Component.translatable("onyxvisual.ai.model_hint"));
		keyBox.setHint(Component.translatable("onyxvisual.ai.key_hint"));
		showSettings(settings);
	}

	private EditBox field(int x, int y, int w, String value, int max) {
		EditBox box = new EditBox(this.font, x + 4, y + 4, w - 8, 12, Component.empty());
		box.setBordered(false);
		box.setMaxLength(max);
		box.setTextColor(TEXT);
		box.setValue(value == null ? "" : value);
		addRenderableWidget(box);
		return box;
	}

	public void toggleSettings() {
		showSettings(!settings);
	}

	private void showSettings(boolean on) {
		settings = on;
		input.visible = !on;
		urlBox.visible = modelBox.visible = keyBox.visible = on;
		setFocused(on ? urlBox : input);
		if (!on) {
			saveSettings();
		}
	}

	private void saveSettings() {
		OnyxConfig config = OnyxConfig.get();
		config.aiUrl = urlBox.getValue().strip();
		config.aiModel = modelBox.getValue().strip();
		config.aiKey = keyBox.getValue().strip();
		config.save();
	}

	@Override
	public void onClose() {
		if (settings) {
			saveSettings();
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = now;
		open = Math.min(1.0f, open + dt * 5.0f);
		clock += dt;
		layout();
		float alpha = Mth.clamp(open * 1.4f, 0.0f, 1.0f);
		g.fill(0, 0, this.width, this.height, (int) (0x61 * alpha) << 24);

		round(g, px, py + 3, pw, ph, 6, 0x50000000);
		round(g, px - 1, py - 1, pw + 2, ph + 2, 7, RING);
		round(g, px, py, pw, ph, 6, PANEL);

		// Header: a little glowing orb, the title, the provider, and two buttons on the right.
		float pulse = AiChat.pending ? 0.5f + 0.5f * Mth.sin(clock * 6.0f) : 1.0f;
		round(g, px + PAD, py + PAD + 1, 8, 8, 3, mix(0xFF2A2F55, ACCENT, pulse));
		g.text(this.font, Component.translatable("onyxvisual.ai.title"), px + PAD + 13, py + PAD + 1, TEXT, false);
		int titleW = this.font.width(Component.translatable("onyxvisual.ai.title"));
		String provider = AiClient.providerName();
		g.text(this.font, this.font.plainSubstrByWidth(provider, pw - titleW - 120), px + PAD + 19 + titleW, py + PAD + 1, DIM, false);
		button(g, mouseX, mouseY, gearX(), py + PAD - 2, 14, settings ? "✕" : "⚙", settings);
		if (!settings) {
			button(g, mouseX, mouseY, clearX(), py + PAD - 2, this.font.width(Component.translatable("onyxvisual.ai.clear").getString()) + 10,
					Component.translatable("onyxvisual.ai.clear").getString(), false);
		}
		g.fill(px + PAD, py + PAD + HEADER - 6, px + pw - PAD, py + PAD + HEADER - 5, 0x18FFFFFF);

		if (settings) {
			settingsView(g, mouseX, mouseY);
		} else {
			chatView(g, mouseX, mouseY, dt);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private int gearX() {
		return px + pw - PAD - 14;
	}

	private int clearX() {
		return gearX() - 6 - (this.font.width(Component.translatable("onyxvisual.ai.clear").getString()) + 10);
	}

	private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String label, boolean active) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
		round(g, x, y, w, 13, 3, active ? ACCENT : hover ? 0xFF2A2A31 : 0xFF1C1C21);
		g.text(this.font, label, x + w / 2 - this.font.width(label) / 2, y + 3, hover || active ? TEXT : DIM, false);
	}

	// ------------------------------------------------------------------ chat

	private void chatView(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
		int top = py + PAD + HEADER;
		int bottom = py + ph - FOOTER - 4;
		int left = px + PAD;
		int right = px + pw - PAD;
		int maxBubble = (int) ((right - left) * 0.82f) - 14;

		// Measure everything first so the view can stick to the bottom.
		List<AiChat.Entry> entries = AiChat.ENTRIES;
		List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
		int total = 0;
		for (AiChat.Entry e : entries) {
			if (e.shown < e.text.length()) {
				e.shown = Math.min(e.text.length(), e.shown + dt * Math.max(70.0f, e.text.length() * 0.9f));
			}
			e.flash = Math.max(0.0f, e.flash - dt * 2.0f);
			String visible = e.text.substring(0, (int) e.shown);
			List<FormattedCharSequence> lines = this.font.split(Component.literal(visible.isEmpty() ? " " : visible), maxBubble);
			wrapped.add(lines);
			total += lines.size() * 10 + 10 + 6;
		}
		if (AiChat.pending) {
			total += 22;
		}
		int viewH = bottom - top;
		float max = Math.max(0, total - viewH);
		if (entries.size() != lastCount || AiChat.pending) {
			// New message, or still typing out: follow the bottom unless the reader has scrolled up a lot.
			if (lastCount < 0 || scrollTarget >= max - 60 || entries.size() != lastCount) {
				scrollTarget = max;
			}
			lastCount = entries.size();
		}
		boolean typing = false;
		for (AiChat.Entry e : entries) {
			typing |= e.shown < e.text.length();
		}
		if (typing && scrollTarget >= max - 40) {
			scrollTarget = max;
		}
		scrollTarget = Mth.clamp(scrollTarget, 0, max);
		scroll += (scrollTarget - scroll) * Math.min(1.0f, dt * 14.0f);

		bubbleRects.clear();
		bubbleEntries.clear();
		g.enableScissor(left, top, right, bottom);
		if (entries.isEmpty() && !AiChat.pending) {
			emptyState(g, left, top, right, bottom);
		}
		int y = top - Math.round(scroll);
		for (int i = 0; i < entries.size(); i++) {
			AiChat.Entry e = entries.get(i);
			List<FormattedCharSequence> lines = wrapped.get(i);
			int w = 0;
			for (FormattedCharSequence line : lines) {
				w = Math.max(w, this.font.width(line));
			}
			int bw = w + 14;
			int bh = lines.size() * 10 + 10;
			int bx = e.user ? right - bw : left;
			if (y + bh >= top && y <= bottom) {
				int fill = e.error ? 0xFF3A1A1E : e.user ? 0xFF2B3466 : 0xFF1D1D23;
				if (e.flash > 0.0f) {
					fill = mix(fill, 0xFF3E8F5A, e.flash);
				}
				round(g, bx, y, bw, bh, 5, fill);
				if (!e.user) {
					g.fill(bx, y + 4, bx + 1, y + bh - 4, e.error ? 0xFFE85A5A : ACCENT);
				}
				int ly = y + 5;
				for (FormattedCharSequence line : lines) {
					g.text(this.font, line, bx + 7, ly, e.error ? 0xFFFF9A9A : TEXT, false);
					ly += 10;
				}
				bubbleRects.add(new int[] {bx, y, bw, bh});
				bubbleEntries.add(e);
			}
			y += bh + 6;
		}
		if (AiChat.pending) {
			round(g, left, y, 40, 16, 5, 0xFF1D1D23);
			for (int d = 0; d < 3; d++) {
				float bounce = Math.max(0.0f, Mth.sin(clock * 7.0f - d * 0.8f));
				int dy = Math.round(bounce * 2.5f);
				round(g, left + 9 + d * 8, y + 6 - dy, 4, 4, 2, mix(0xFF55556A, ACCENT, bounce));
			}
		}
		g.disableScissor();
		if (max > 0) {
			// Thin scrollbar on the right edge.
			int barH = Math.max(14, viewH * viewH / (viewH + (int) max));
			int barY = top + Math.round((viewH - barH) * (scroll / max));
			g.fill(right + 3, barY, right + 5, barY + barH, 0x40FFFFFF);
		}
		// Input field.
		int fy = py + ph - FOOTER + 4;
		round(g, px + PAD, fy, pw - PAD * 2, 22, 5, input.isFocused() ? 0xFF22222A : 0xFF1A1A20);
		if (input.isFocused()) {
			round(g, px + PAD, fy + 21, pw - PAD * 2, 1, 0, ACCENT);
		}
		int sx = px + pw - PAD - 22;
		boolean canSend = !AiChat.pending && !input.getValue().isBlank();
		round(g, sx, fy + 3, 18, 16, 4, canSend ? ACCENT : 0xFF2A2A31);
		g.text(this.font, "➤", sx + 5, fy + 7, canSend ? 0xFFFFFFFF : DIM, false);
	}

	private void emptyState(GuiGraphicsExtractor g, int left, int top, int right, int bottom) {
		int cx = (left + right) / 2;
		int cy = (top + bottom) / 2 - 16;
		float bob = Mth.sin(clock * 2.0f) * 2.0f;
		round(g, cx - 9, cy - 9 + Math.round(bob), 18, 18, 7, ACCENT);
		round(g, cx - 5, cy - 5 + Math.round(bob), 10, 10, 4, 0xFFB8C6FF);
		Component a = Component.translatable("onyxvisual.ai.empty1");
		Component b = Component.translatable("onyxvisual.ai.empty2");
		g.text(this.font, a, cx - this.font.width(a) / 2, cy + 18, TEXT, false);
		g.text(this.font, b, cx - this.font.width(b) / 2, cy + 30, DIM, false);
	}

	// -------------------------------------------------------------- settings

	private void settingsView(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = px + PAD;
		int y = py + PAD + HEADER;
		g.text(this.font, Component.translatable("onyxvisual.ai.provider"), x, y + 2, DIM, false);
		int cx = x + 80;
		for (int i = 0; i < PRESETS.length; i++) {
			int w = this.font.width(PRESETS[i][0]) + 12;
			boolean active = PRESETS[i][1].equals(urlBox.getValue().strip());
			button(g, mouseX, mouseY, cx, y, w, PRESETS[i][0], active);
			cx += w + 4;
		}
		String[] labels = {"onyxvisual.ai.url", "onyxvisual.ai.model", "onyxvisual.ai.key"};
		EditBox[] boxes = {urlBox, modelBox, keyBox};
		for (int i = 0; i < 3; i++) {
			int fy = py + PAD + HEADER + 40 + i * 22;
			g.text(this.font, Component.translatable(labels[i]), x, fy + 5, DIM, false);
			round(g, x + 80, fy, pw - PAD * 2 - 86, 19, 4, boxes[i].isFocused() ? 0xFF22222A : 0xFF1A1A20);
		}
		int ty = py + PAD + HEADER + 112;
		List<FormattedCharSequence> help = this.font.split(Component.translatable(
				AiClient.usingFree() ? "onyxvisual.ai.help_free" : "onyxvisual.ai.help_key"), pw - PAD * 2);
		for (FormattedCharSequence line : help) {
			g.text(this.font, line, x, ty, DIM, false);
			ty += 10;
		}
	}

	// ----------------------------------------------------------------- input

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (!settings && (key == 257 || key == 335)) {
			send();
			return true;
		}
		if (!settings && key == 265 && input.getValue().isEmpty() && !AiChat.lastQuestion.isEmpty()) {
			input.setValue(AiChat.lastQuestion);
			return true;
		}
		if (key == 256 && settings) {
			showSettings(false);
			return true;
		}
		return super.keyPressed(event);
	}

	private void send() {
		String text = input.getValue();
		if (text.isBlank() || AiChat.pending) {
			return;
		}
		input.setValue("");
		AiChat.ask(text);
		scrollTarget = Float.MAX_VALUE;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == 0) {
			int by = py + PAD - 2;
			if (my >= by && my < by + 13) {
				if (mx >= gearX() && mx < gearX() + 14) {
					showSettings(!settings);
					return true;
				}
				int cw = this.font.width(Component.translatable("onyxvisual.ai.clear").getString()) + 10;
				if (!settings && mx >= clearX() && mx < clearX() + cw) {
					AiChat.clear();
					scroll = scrollTarget = 0;
					return true;
				}
			}
			if (settings) {
				int cx = px + PAD + 80;
				int y = py + PAD + HEADER;
				for (String[] preset : PRESETS) {
					int w = this.font.width(preset[0]) + 12;
					if (mx >= cx && mx < cx + w && my >= y && my < y + 13) {
						urlBox.setValue(preset[1]);
						modelBox.setValue(preset[2]);
						return true;
					}
					cx += w + 4;
				}
			} else {
				int fy = py + ph - FOOTER + 4;
				int sx = px + pw - PAD - 22;
				if (mx >= sx && mx < sx + 18 && my >= fy + 3 && my < fy + 19) {
					send();
					return true;
				}
				// Clicking an answer copies it.
				for (int i = 0; i < bubbleRects.size(); i++) {
					int[] r = bubbleRects.get(i);
					AiChat.Entry e = bubbleEntries.get(i);
					if (!e.user && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]
							&& my >= py + PAD + HEADER && my < py + ph - FOOTER - 4) {
						this.minecraft.keyboardHandler.setClipboard(e.text);
						e.flash = 1.0f;
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget -= (float) scrollY * 24.0f;
		if (scrollTarget < 0) {
			scrollTarget = 0;
		}
		return true;
	}

	// --------------------------------------------------------------- drawing

	private static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x, y, x + w, y + h, argb);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, argb);
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5f;
			int inset = r - Math.round((float) Math.sqrt(Math.max(0.0f, r * r - dy * dy)));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, argb);
		}
	}

	private static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
				| Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}
}
