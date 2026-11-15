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
	private boolean down, pressed, rightPressed;

	public ClickGuiScreen() {
		super(Component.translatable("onyxvisual.screen.title"));
		OnyxConfig config = OnyxConfig.get();
		Runnable save = config::save;
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
		McCanvas canvas = new McCanvas(g, this.font, (float) Minecraft.getInstance().getWindow().getGuiScale());
		canvas.begin();
		try {
			gui.frame(canvas, this.width, this.height, mouseX, mouseY, down, pressed, rightPressed, dt);
		} finally {
			canvas.end();
		}
		pressed = false;
		rightPressed = false;
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
		if (event.button() == 0) down = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return gui.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
		OnyxConfig.get().save();
		super.onClose();
	}
}
