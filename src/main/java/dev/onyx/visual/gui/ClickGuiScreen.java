package dev.onyx.visual.gui;

import dev.onyx.visual.OnyxConfig;
import dev.onyx.visual.OnyxVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Minecraft screen hosting the Onyx Visual ClickGUI (opens with Right Shift by default). */
public final class ClickGuiScreen extends Screen {
	private final ClickGui gui;
	private long lastFrame = System.nanoTime();
	private boolean down, pressed, rightPressed, releaseQueued;
	/** Settings are written at most twice a second (a slider drag changes them every frame). */
	private boolean dirty;
	private long lastSave;
	/** Test hook: when set, replaces the real cursor position (GUI units). */
	private int fakeX = Integer.MIN_VALUE, fakeY;
	private PerfLog perf;
	private boolean autoLite;
	private long lastRealFrame;

	public ClickGuiScreen() {
		super(Component.translatable("onyxvisual.screen.title"));
		OnyxConfig config = OnyxConfig.get();
		Tr.set(key -> net.minecraft.client.resources.language.I18n.get(key));
		Runnable save = () -> dirty = true;
		gui = new ClickGui(OnyxModules.create(config, save, () -> OnyxVisual.settingsKey.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT)),
				config.gui, Binds.get(), save);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// The ClickGUI paints its own animated backdrop.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(.05f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		if (fakeX != Integer.MIN_VALUE) {
			mouseX = fakeX;
			mouseY = fakeY;
		}
		com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
		float guiScale = (float) window.getGuiScale();
		boolean lite = gui.lite() || autoLite;
		if (perf == null) perf = new PerfLog(guiScale, this.width, this.height, window.getWidth(), window.getHeight(), lite);
		double frameMs = lastRealFrame == 0 ? 0 : (now - lastRealFrame) / 1e6;
		lastRealFrame = now;
		McCanvas.statFills = 0;
		McCanvas.statTexts = 0;
		McCanvas canvas = new McCanvas(g, this.font, guiScale, lite);
		canvas.begin();
		try {
			// A click shorter than one frame still counts as held for that frame (sliders, colour picker).
			gui.frame(canvas, this.width, this.height, guiScale, mouseX, mouseY, down || pressed, pressed, rightPressed, dt);
		} finally {
			canvas.end();
		}
		perf.frame(frameMs, (System.nanoTime() - now) / 1e6, lite);
		if (perf.autoLite()) autoLite = true;
		pressed = false;
		rightPressed = false;
		if (releaseQueued) {
			down = false;
			releaseQueued = false;
		}
		if (dirty && now / 1_000_000L - lastSave > 500) flush();
	}

	private void flush() {
		dirty = false;
		lastSave = System.nanoTime() / 1_000_000L;
		OnyxConfig.get().save();
	}

	/** Test hook: puts the cursor at the first card and clicks it. */
	public void debugClickFirstCard() {
		fakeX = Math.round(gui.firstCardX);
		fakeY = Math.round(gui.firstCardY);
		pressed = true;
		down = true;
		releaseQueued = true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			down = true;
			pressed = true;
		} else if (event.button() == 1) {
			rightPressed = true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) releaseQueued = true;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return gui.mouseScrolled(fakeX != Integer.MIN_VALUE ? fakeX : mouseX, fakeX != Integer.MIN_VALUE ? fakeY : mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event.isAllowedChatCharacter() && gui.charTyped(event.codepointAsString())) return true;
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean capturing = gui.capturingKeys();
		if (gui.keyPressed(event.key())) return true;
		if (!capturing && OnyxVisual.settingsKey.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		flush();
		if (perf != null) perf.close();
		super.onClose();
	}
}
