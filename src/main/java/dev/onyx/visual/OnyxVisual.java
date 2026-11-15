package dev.onyx.visual;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import dev.onyx.visual.life.Pebbles;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OnyxVisual implements ClientModInitializer {
	public static final String MOD_ID = "onyxvisual";
	public static final Logger LOGGER = LoggerFactory.getLogger("Onyx Visual");

	public static KeyMapping zoomKey;
	public static KeyMapping settingsKey;
	public static KeyMapping throwKey;
	public static KeyMapping cycleKey;
	public static KeyMapping aiKey;
	private static boolean openAi;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		OnyxConfig.get();
		dev.onyx.visual.gui.Binds.init(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("onyx-visual-binds.properties"));
		dev.onyx.visual.gui.Tr.set(key -> net.minecraft.client.resources.language.I18n.get(key));

		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		zoomKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.onyxvisual.zoom", InputConstants.KEY_C, category));
		settingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.onyxvisual.settings", InputConstants.KEY_RSHIFT, category));

		throwKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.onyxvisual.throw", InputConstants.KEY_V, category));

		cycleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.onyxvisual.cycle", InputConstants.KEY_B, category));

		// A right-click that picks up a pebble must not also place a block or use an item.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				level.isClientSide() && hand == InteractionHand.MAIN_HAND && Pebbles.tryPickup() ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				level.isClientSide() && hand == InteractionHand.MAIN_HAND && Pebbles.tryPickup() ? InteractionResult.FAIL : InteractionResult.PASS);
		aiKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.onyxvisual.ai", InputConstants.KEY_H, category));
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
				dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("ai")
						.executes(ctx -> {
							openAi = true;
							return 1;
						})
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("question",
										com.mojang.brigadier.arguments.StringArgumentType.greedyString())
								.executes(ctx -> {
									String q = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "question");
									ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("[AI] \u2026 " + q)
											.withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
									dev.onyx.visual.ai.AiChat.ask(q, true);
									return 1;
								}))));
		dev.onyx.visual.hud.Hud.register();
		PocketHud.register();

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		HudElementRegistry.addLast(id("info_card"), new InfoCardHud());
		dev.onyx.visual.fx.WorldFx.register();
	}

	private void onTick(Minecraft mc) {
		while (settingsKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.setScreenAndShow(new dev.onyx.visual.gui.ClickGuiScreen());
			}
		}
		while (aiKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				openAi = true;
			}
		}
		if (openAi && mc.gui.screen() == null) {
			openAi = false;
			mc.setScreenAndShow(new dev.onyx.visual.ai.AiScreen());
		}
		tickBinds(mc);
		try {
			dev.onyx.visual.fx.WorldFx.tick(mc);
			AmbientEffects.tick(mc);
		} catch (RuntimeException e) {
			// Eye candy must never take the game down with it.
			LOGGER.debug("Ambient effect tick failed", e);
		}
	}

	/** Module key binds from the ClickGUI: work only in game, not while a screen is open. */
	private static void tickBinds(Minecraft mc) {
		dev.onyx.visual.gui.Binds binds = dev.onyx.visual.gui.Binds.get();
		if (binds == null || mc.getWindow() == null) return;
		long window = mc.getWindow().handle();
		java.util.function.IntPredicate isDown = key -> org.lwjgl.glfw.GLFW.glfwGetKey(window, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		if (mc.gui.screen() != null || mc.player == null) {
			binds.resetPressed(isDown);
			return;
		}
		binds.tick(isDown, id -> {
			OnyxConfig config = OnyxConfig.get();
			boolean on = dev.onyx.visual.gui.OnyxModules.toggle(config, id);
			config.save();
			mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Onyx Visual: ")
					.append(net.minecraft.network.chat.Component.translatable(id.startsWith("gui.") ? "onyxvisual.gui.m." + id.substring(4) : "onyxvisual.option." + id.replaceAll("([A-Z])", "_$1").toLowerCase(java.util.Locale.ROOT)))
					.append(net.minecraft.network.chat.Component.literal(on ? " \u2714" : " \u2716")));
		});
	}
}
