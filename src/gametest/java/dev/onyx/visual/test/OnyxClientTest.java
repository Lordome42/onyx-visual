package dev.onyx.visual.test;

import dev.onyx.visual.OnyxVisual;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

public class OnyxClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(100);
			// A fresh install starts with every function off.
			int onAtStart = context.computeOnClient(mc -> setAll(false, true));
			if (onAtStart != 0) throw new AssertionError(onAtStart + " functions are on in a fresh config");
			context.runOnClient(mc -> setAll(true, false));
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.waitTicks(40);
			context.takeScreenshot("onyx-hud-day");

			// ClickGUI opens from its key; toggling a module through the model persists into the config.
			context.getInput().pressKey(OnyxVisual.settingsKey);
			context.waitForScreen(dev.onyx.visual.gui.ClickGuiScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("onyx-clickgui");
			// A real click on the first card must flip its option, and the menu must stay readable at every GUI scale.
			boolean auroraBefore = context.computeOnClient(mc -> OnyxConfig.get().aurora);
			context.runOnClient(mc -> ((dev.onyx.visual.gui.ClickGuiScreen) mc.gui.screen()).debugClickFirstCard());
			context.waitTicks(6);
			boolean auroraAfter = context.computeOnClient(mc -> OnyxConfig.get().aurora);
			if (auroraBefore == auroraAfter) {
				throw new AssertionError("Clicking the first card did not toggle its option");
			}
			context.runOnClient(mc -> ((dev.onyx.visual.gui.ClickGuiScreen) mc.gui.screen()).debugClickFirstCard());
			context.waitTicks(6);
			context.runOnClient(mc -> ((dev.onyx.visual.gui.ClickGuiScreen) mc.gui.screen()).debugClickStar());
			context.waitTicks(6);
			if (context.computeOnClient(mc -> ((dev.onyx.visual.gui.ClickGuiScreen) mc.gui.screen()).debugFavorites()) != 1) {
				throw new AssertionError("Clicking the star did not mark the module as favourite");
			}
			context.takeScreenshot("onyx-clickgui-star");
			for (int scale = 1; scale <= 4; scale++) {
				final int gs = scale;
				context.runOnClient(mc -> {
					mc.options.guiScale().set(gs);
					mc.getWindow().setGuiScale(mc.getWindow().calculateScale(gs, mc.isEnforceUnicode()));
					mc.resizeGui();
				});
				context.waitTicks(8);
				context.takeScreenshot("onyx-clickgui-scale" + gs);
			}
			// Lite mode must stay readable and cheap.
			context.runOnClient(mc -> OnyxConfig.get().gui.lite = true);
			context.waitTicks(8);
			context.takeScreenshot("onyx-clickgui-lite");
			context.runOnClient(mc -> OnyxConfig.get().gui.lite = false);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.getWindow().setGuiScale(mc.getWindow().calculateScale(0, mc.isEnforceUnicode()));
				mc.resizeGui();
			});
			// Configs page: save a profile, change a setting, load it back.
			context.runOnClient(mc -> {
				OnyxConfig c = OnyxConfig.get();
				c.hudPos.put("stats", new int[]{40, 30});
				dev.onyx.visual.gui.Profiles.save("Test config", 0xFF6FA8FF, c);
				dev.onyx.visual.gui.Profiles.save("Night PvP", 0xFFFF6B6B, c);
				((dev.onyx.visual.gui.ClickGuiScreen) mc.gui.screen()).debugOpenConfigs();
			});
			context.waitTicks(8);
			context.takeScreenshot("onyx-clickgui-configs");
			context.runOnClient(mc -> {
				OnyxConfig c = OnyxConfig.get();
				c.aurora = false;
				c.hudPos.clear();
				var info = dev.onyx.visual.gui.Profiles.list().stream().filter(i -> i.name().equals("Test config")).findFirst().orElseThrow();
				dev.onyx.visual.gui.Profiles.load(info, c);
				if (!c.aurora || c.hudPos.get("stats") == null || c.hudPos.get("stats")[0] != 40) throw new AssertionError("Profile did not restore the settings");
			});
			context.runOnClient(mc -> OnyxConfig.get().birds = true);
			context.setScreen(() -> null);
			// HUD edit mode in the chat.
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			context.waitTicks(10);
			context.takeScreenshot("onyx-hud-edit");
			// Dragging the FPS widget with the (simulated) mouse moves it and stores the offset.
			context.runOnClient(mc -> OnyxConfig.get().hudPos.clear());
			dev.onyx.visual.hud.HudLayout.testInput = new double[]{20, 58, 0};
			context.waitTicks(3);
			dev.onyx.visual.hud.HudLayout.testInput = new double[]{20, 58, 1};
			context.waitTicks(3);
			dev.onyx.visual.hud.HudLayout.testInput = new double[]{120, 128, 1};
			context.waitTicks(3);
			dev.onyx.visual.hud.HudLayout.testInput = new double[]{120, 128, 0};
			context.waitTicks(3);
			int[] dragged = context.computeOnClient(mc -> OnyxConfig.get().hudPos.get("stats"));
			dev.onyx.visual.hud.HudLayout.testInput = null;
			if (dragged == null || dragged[0] < 50 || dragged[1] < 50) throw new AssertionError("HUD drag did not move the widget");
			context.takeScreenshot("onyx-hud-moved");
			context.setScreen(() -> null);

			// ---- On-screen widgets.
			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				OnyxConfig c = OnyxConfig.get();
				c.wisps = false;
				c.magicCircle = false;
			});
			world.getServer().runCommand("item replace entity @p armor.head with minecraft:diamond_helmet[damage=40]");
			world.getServer().runCommand("item replace entity @p armor.chest with minecraft:iron_chestplate[damage=120]");
			world.getServer().runCommand("item replace entity @p armor.feet with minecraft:leather_boots[damage=62]");
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:iron_sword[damage=30]");
			world.getServer().runCommand("summon cow ~ ~ ~2.5 {NoAI:1b}");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 20");
			context.waitTicks(20);
			world.getServer().runCommand("give @p minecraft:oak_log 12");
			world.getServer().runCommand("give @p minecraft:diamond 3");
			world.getServer().runCommand("give @p minecraft:apple 5");
			context.runOnClient(mc -> dev.onyx.visual.hud.Hud.debugTitle("Cherry Grove", "You have entered"));
			context.waitTicks(18);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(3);
			context.takeScreenshot("onyx-hud-widgets");
			world.getServer().runCommand("kill @e[type=cow]");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 40");
			context.waitTicks(12);
			context.takeScreenshot("onyx-hud-block");
			// Schematic helper with a fake schematic: first a wrong block, then a spot to place on.
			context.runOnClient(mc -> {
				net.minecraft.core.BlockPos target = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
				dev.onyx.visual.hud.Schematic.override = pos -> pos.equals(target)
						? net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			});
			context.waitTicks(10);
			context.takeScreenshot("onyx-hud-schematic-wrong");
			world.getServer().runCommand("item replace entity @p hotbar.2 with minecraft:gold_block 24");
			context.runOnClient(mc -> {
				net.minecraft.world.phys.BlockHitResult hit = (net.minecraft.world.phys.BlockHitResult) mc.hitResult;
				net.minecraft.core.BlockPos target = hit.getBlockPos();
				net.minecraft.world.level.block.state.BlockState here = mc.level.getBlockState(target);
				net.minecraft.core.BlockPos above = target.relative(hit.getDirection());
				dev.onyx.visual.hud.Schematic.override = pos -> pos.equals(target) ? here
						: pos.equals(above) ? net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			});
			context.waitTicks(10);
			context.takeScreenshot("onyx-hud-schematic-place");
			context.runOnClient(mc -> dev.onyx.visual.hud.Schematic.override = null);
			world.getServer().runCommand("clear @p minecraft:gold_block");
			// AI window: empty state, a real question to the free service, then the settings page.
			context.getInput().pressKey(OnyxVisual.aiKey);
			context.waitForScreen(dev.onyx.visual.ai.AiScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("onyx-ai-empty");
			context.runOnClient(mc -> {
				for (String q : new String[] {"Как скрафтить маяк?", "how to make a torch", "how do I craft a beacon", "iron pickaxe recipe", "furnace", "how to craft a bed", "golden apple"}) {
					System.out.println("ONYX-AI-FACTS [" + q + "]: " + dev.onyx.visual.ai.AiClient.factsFor(q).replace("\n", " / "));
				}
				dev.onyx.visual.ai.AiChat.ask("How do I craft a furnace?");
			});
			context.waitTicks(20);
			context.takeScreenshot("onyx-ai-thinking");
			context.waitFor(mc -> !dev.onyx.visual.ai.AiChat.pending, 2400);
			context.waitTicks(100);
			context.runOnClient(mc -> System.out.println("ONYX-AI-ANSWER: " + dev.onyx.visual.ai.AiChat.ENTRIES.get(dev.onyx.visual.ai.AiChat.ENTRIES.size() - 1).text.replace("\n", " | ")));
			context.takeScreenshot("onyx-ai-answer");
			context.runOnClient(mc -> ((dev.onyx.visual.ai.AiScreen) mc.gui.screen()).toggleSettings());
			context.waitTicks(5);
			context.takeScreenshot("onyx-ai-settings");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitTicks(5);
			// Rain on the lens (looking up), then frost on snow, then the low-health pulse.
			world.getServer().runCommand("weather rain");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 -50");
			context.waitTicks(110);
			context.takeScreenshot("onyx-hud-rain");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:snow_block");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 5");
			context.waitTicks(170);
			context.takeScreenshot("onyx-hud-frost");
			world.getServer().runCommand("fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:grass_block");
			world.getServer().runCommand("damage @p 16");
			context.waitTicks(30);
			context.takeScreenshot("onyx-hud-lowhealth");
			world.getServer().runCommand("effect give @p minecraft:instant_health 1 5");
			// Elytra: flight read-out and speed lines.
			world.getServer().runCommand("item replace entity @p armor.chest with minecraft:elytra");
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("tp @p ~ ~120 ~ 0 25");
			context.waitTicks(12);
			context.getInput().pressKey(options -> options.keyJump);
			context.waitTicks(4);
			context.getInput().pressKey(options -> options.keyJump);
			context.waitTicks(30);
			boolean gliding = context.computeOnClient(mc -> mc.player.isFallFlying());
			System.out.println("FLIGHT-TEST gliding=" + gliding);
			context.takeScreenshot("onyx-hud-flight");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 10");
			context.waitTicks(20);
			world.getServer().runCommand("gamemode survival @p");
			world.getServer().runCommand("clear @p");
			context.waitTicks(20);
			context.runOnClient(mc -> {
				OnyxConfig c = OnyxConfig.get();
				c.wisps = true;
				c.magicCircle = true;
			});

			// Trail: sprint forward in third person.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			for (OnyxConfig.TrailStyle style : OnyxConfig.TrailStyle.values()) {
				context.runOnClient(mc -> OnyxConfig.get().trailStyle = style);
				context.getInput().holdKey(options -> options.keySprint);
				context.getInput().holdKey(options -> options.keyUp);
				context.waitTicks(30);
				context.takeScreenshot("onyx-trail-" + style.name().toLowerCase());
				context.getInput().releaseKey(options -> options.keyUp);
				context.getInput().releaseKey(options -> options.keySprint);
				context.waitTicks(10);
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Zoom must actually narrow the field of view.
			float before = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
			context.getInput().holdKey(OnyxVisual.zoomKey);
			context.waitTicks(20);
			float zoomed = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
			context.takeScreenshot("onyx-zoom");
			context.getInput().releaseKey(OnyxVisual.zoomKey);
			if (!(zoomed < before * 0.5f)) {
				throw new AssertionError("Zoom did not change FOV: " + before + " -> " + zoomed);
			}

			// Living world: meadow clutter, creatures, a pond, a tree canopy and a snow field.
			context.runOnClient(mc -> {
				dev.onyx.visual.OnyxConfig c = dev.onyx.visual.OnyxConfig.get();
				c.wisps = false;
				c.magicCircle = false;
				c.footprints = false;
			});
			world.getServer().runCommand("fill ~2 ~-3 ~4 ~5 ~-1 ~7 minecraft:water");
			world.getServer().runCommand("fill ~0 ~-1 ~2 ~1 ~-1 ~4 minecraft:sand");
			world.getServer().runCommand("fill ~-8 ~5 ~2 ~-3 ~5 ~8 minecraft:oak_leaves[persistent=true]");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 -22");
			context.waitTicks(45);
			context.runOnClient(mc -> dev.onyx.visual.life.LifeFx.spawnShowcase(mc.level, mc.player));
			context.waitTicks(12);
			context.takeScreenshot("onyx-life-flock");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 18");
			context.waitTicks(15);
			context.takeScreenshot("onyx-life-meadow");
			// Close-up of the creatures' textures.
			world.getServer().runCommand("tp @p ~-0.5 ~ ~1.2 0 38");
			context.runOnClient(mc -> mc.options.keyShift.setDown(true));
			context.waitTicks(12);
			context.takeScreenshot("onyx-life-closeup");
			context.runOnClient(mc -> mc.options.keyShift.setDown(false));
			// Jump into the pond for a splash.
			world.getServer().runCommand("tp @p ~3.5 ~3 ~5.5 0 50");
			context.waitTicks(11);
			context.takeScreenshot("onyx-splash");
			context.waitTicks(4);
			context.takeScreenshot("onyx-splash2");
			world.getServer().runCommand("tp @p ~-3 ~ ~1 60 28");
			context.waitTicks(15);
			context.takeScreenshot("onyx-life-leaves");
			// Run through the litter under the canopy and look at what got kicked about.
			world.getServer().runCommand("tp @p ~-2 ~ ~3 90 35");
			context.waitTicks(40);
			context.takeScreenshot("onyx-life-kick-before");
			context.getInput().holdKey(options -> options.keySprint);
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(9);
			context.takeScreenshot("onyx-life-kick-during");
			context.waitTicks(6);
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keySprint);
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ -90 40");
			context.waitTicks(3);
			context.takeScreenshot("onyx-life-kick-after");
			world.getServer().runCommand("fill ~-8 ~ ~-12 ~8 ~ ~-2 minecraft:snow");
			world.getServer().runCommand("tp @p ~ ~ ~-3 180 25");
			context.waitTicks(45);
			context.takeScreenshot("onyx-life-snow");
			context.getInput().holdKeyFor(options -> options.keyUp, 30);
			context.waitTicks(40);
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 45");
			context.waitTicks(10);
			context.takeScreenshot("onyx-life-snow-pushed");
			world.getServer().runCommand("fill ~-9 ~ ~-14 ~9 ~ ~8 minecraft:air");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 10");
			world.getServer().runCommand("fill ~2 ~-3 ~4 ~5 ~-1 ~7 minecraft:grass_block");
			world.getServer().runCommand("fill ~0 ~-1 ~2 ~1 ~-1 ~4 minecraft:grass_block");
			world.getServer().runCommand("fill ~-8 ~5 ~2 ~-3 ~5 ~8 minecraft:air");
			context.runOnClient(mc -> {
				dev.onyx.visual.OnyxConfig c = dev.onyx.visual.OnyxConfig.get();
				c.wisps = true;
				c.magicCircle = true;
				c.footprints = true;
				dev.onyx.visual.life.LifeFx.reset();
			});
			context.waitTicks(10);

			// Pebble pocket: pick one up with right-click, then skip stones across a long pool.
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				java.util.Arrays.fill(OnyxConfig.get().pocket, 0);
				OnyxConfig.get().bestSkips = 0;
			});
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 46");
			context.waitTicks(10);
			context.runOnClient(mc -> dev.onyx.visual.life.Pebbles.debugDropAhead(1, 1.5));
			context.waitTicks(8);
			context.takeScreenshot("onyx-pebble-aim");
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(4);
			int pocket = context.computeOnClient(mc -> OnyxConfig.get().pocket[0]);
			if (pocket != 1) {
				throw new AssertionError("Right-click did not pick the pebble up: pocket=" + pocket);
			}
			context.runOnClient(mc -> OnyxConfig.get().pocket[0] = 5);
			world.getServer().runCommand("fill ~-3 ~-2 ~3 ~3 ~-1 ~40 minecraft:water");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 6");
			context.waitTicks(30);
			context.takeScreenshot("onyx-pebble-slot");
			context.getInput().holdKey(OnyxVisual.throwKey);
			context.waitTicks(22);
			context.takeScreenshot("onyx-pebble-charge");
			context.getInput().releaseKey(OnyxVisual.throwKey);
			context.waitTicks(9);
			context.takeScreenshot("onyx-pebble-skip");
			context.waitTicks(70);
			int skips = context.computeOnClient(mc -> dev.onyx.visual.life.Pebbles.debugLastSkips());
			int left = context.computeOnClient(mc -> OnyxConfig.get().pocket[0]);
			System.out.println("PEBBLE-TEST skips=" + skips + " left=" + left);
			if (skips < 2 || left != 4) {
				throw new AssertionError("Stone did not skip as expected: skips=" + skips + " left=" + left);
			}
			context.takeScreenshot("onyx-pebble-result");
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(10);
			context.takeScreenshot("onyx-pebble-inventory");
			context.setScreen(() -> null);
			world.getServer().runCommand("fill ~-3 ~-2 ~3 ~3 ~-1 ~40 minecraft:grass_block");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 10");
			context.waitTicks(10);

			// ---- Pocket: every kind of thing can be picked up with right-click.
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				java.util.Arrays.fill(OnyxConfig.get().pocket, 0);
				OnyxConfig c = OnyxConfig.get();
				c.wisps = false;
				c.magicCircle = false;
				c.footprints = false;
			});
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 46");
			context.waitTicks(10);
			int[][] kinds = {{2, 1}, {0, 2}, {4, 5}, {5, 4}};
			for (int[] pair : kinds) {
				int groundType = pair[0];
				int pocketIndex = pair[1];
				context.runOnClient(mc -> dev.onyx.visual.life.Pebbles.debugDropAhead(groundType, 1.5));
				context.waitTicks(4);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(4);
				int got = context.computeOnClient(mc -> OnyxConfig.get().pocket[pocketIndex]);
				if (got != 1) {
					throw new AssertionError("Could not pick up ground type " + groundType + ": pocket[" + pocketIndex + "]=" + got);
				}
			}
			// Cairn: sneak + right-click four times builds a stack of four from the pocket.
			context.runOnClient(mc -> {
				OnyxConfig.get().pocket[0] = 6;
				OnyxConfig.get().pocketSelected = 0;
				mc.options.keyShift.setDown(true);
			});
			context.waitTicks(6);
			for (int i = 0; i < 4; i++) {
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(5);
			}
			context.runOnClient(mc -> mc.options.keyShift.setDown(false));
			context.waitTicks(6);
			int stonesLeft = context.computeOnClient(mc -> OnyxConfig.get().pocket[0]);
			context.takeScreenshot("onyx-cairn");
			if (stonesLeft != 2) {
				throw new AssertionError("Cairn building used the wrong number of stones: left=" + stonesLeft);
			}
			// All slots filled, then throw one of each kind.
			context.runOnClient(mc -> java.util.Arrays.fill(OnyxConfig.get().pocket, 3));
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 -5");
			context.waitTicks(10);
			context.takeScreenshot("onyx-pocket-slots");
			for (int i = 0; i < 6; i++) {
				context.getInput().holdKey(OnyxVisual.throwKey);
				context.waitTicks(10);
				context.getInput().releaseKey(OnyxVisual.throwKey);
				context.waitTicks(i == 2 ? 6 : 3);
				if (i == 2) {
					context.takeScreenshot("onyx-pocket-throws");
				}
				context.getInput().pressKey(OnyxVisual.cycleKey);
				context.waitTicks(3);
			}
			context.waitTicks(60);
			int inFlight = context.computeOnClient(mc -> dev.onyx.visual.life.Pebbles.debugFlying());
			int total = context.computeOnClient(mc -> java.util.Arrays.stream(OnyxConfig.get().pocket).sum());
			System.out.println("POCKET-TEST inFlight=" + inFlight + " total=" + total);
			if (total != 15) {
				throw new AssertionError("Throwing one of each of six kinds should leave 15 items, left " + total);
			}
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(8);
			context.takeScreenshot("onyx-pocket-inventory");
			context.setScreen(() -> null);

			// Petting a goose.
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				dev.onyx.visual.life.LifeFx.spawnGeese(0.5, -60, 2.3, 1);
			});
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 35");
			context.waitTicks(3);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(6);
			context.takeScreenshot("onyx-petting");

			// Wind gust over the meadow, then rain puddles.
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 12");
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				dev.onyx.visual.fx.Weather.debugGust();
			});
			context.waitTicks(45);
			context.takeScreenshot("onyx-wind");
			world.getServer().runCommand("weather rain");
			context.waitTicks(60);
			context.runOnClient(mc -> dev.onyx.visual.life.LifeFx.debugWet());
			context.waitTicks(50);
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 30");
			context.waitTicks(10);
			context.takeScreenshot("onyx-puddles");
			world.getServer().runCommand("weather clear");

			// Snow: footprints, breath, and drifts pushed together into a snowman.
			world.getServer().runCommand("fill ~-6 ~ ~-2 ~6 ~ ~14 minecraft:snow");
			context.waitTicks(40);
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				dev.onyx.visual.life.LifeFx.debugSnowPiles(0.5, -59.875, 2.0);
			});
			world.getServer().runCommand("tp @p 0.5 -59.875 0.5 0 20");
			context.waitTicks(10);
			context.getInput().holdKeyFor(options -> options.keyUp, 60);
			context.waitTicks(70);
			int snowmen = context.computeOnClient(mc -> dev.onyx.visual.life.LifeFx.debugSnowmen());
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~-3 0 10");
			context.waitTicks(15);
			context.takeScreenshot("onyx-snowman");
			System.out.println("SNOW-TEST snowmen=" + snowmen);
			if (snowmen < 1) {
				throw new AssertionError("Pushing drifts together did not make a snowman");
			}
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 35");
			context.waitTicks(10);
			context.takeScreenshot("onyx-snow-prints");
			world.getServer().runCommand("fill ~-6 ~ ~-2 ~6 ~ ~14 minecraft:air");

			// Glow bugs at night.
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				dev.onyx.visual.life.LifeFx.debugSpawnFireflies(0.5, -58.6, 3.0, 7);
			});
			context.waitTicks(30);
			context.takeScreenshot("onyx-glowbugs");
			world.getServer().runCommand("time set noon");
			context.runOnClient(mc -> {
				OnyxConfig c = OnyxConfig.get();
				c.wisps = true;
				c.magicCircle = true;
				c.footprints = true;
				java.util.Arrays.fill(c.pocket, 0);
			});
			context.waitTicks(20);

			// Overworld atmosphere: morning sun through a canopy, mist, halos.
			world.getServer().runCommand("fill ~-7 ~6 ~3 ~7 ~6 ~14 minecraft:oak_leaves[persistent=true]");
			world.getServer().runCommand("fill ~-7 ~7 ~3 ~7 ~7 ~14 minecraft:oak_leaves[persistent=true]");
			world.getServer().runCommand("fill ~-1 ~ ~8 ~-1 ~5 ~8 minecraft:oak_log");
			world.getServer().runCommand("fill ~4 ~ ~11 ~4 ~5 ~11 minecraft:oak_log");
			world.getServer().runCommand("time set 1300");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 -2");
			context.waitTicks(120);
			context.takeScreenshot("onyx-atmos-shafts");
			world.getServer().runCommand("tp @p ~ ~ ~-4 -90 -12");
			context.waitTicks(40);
			context.takeScreenshot("onyx-atmos-sunrise");
			world.getServer().runCommand("time set 23400");
			world.getServer().runCommand("tp @p ~ ~ ~-6 180 4");
			context.waitTicks(200);
			context.takeScreenshot("onyx-atmos-mist");
			world.getServer().runCommand("fill ~-7 ~ ~3 ~7 ~7 ~14 minecraft:air");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 10");
			context.waitTicks(20);

			// Geese in an obstacle course: walls, single blocks, a slab, stairs and a pool. They must keep
			// moving and never end up inside a block.
			world.getServer().runCommand("fill ~-6 ~ ~10 ~6 ~1 ~22 minecraft:stone_bricks hollow");
			world.getServer().runCommand("fill ~-5 ~ ~11 ~5 ~1 ~21 minecraft:air");
			world.getServer().runCommand("fill ~-6 ~2 ~10 ~6 ~2 ~22 minecraft:air");
			world.getServer().runCommand("setblock ~-2 ~ ~14 minecraft:stone");
			world.getServer().runCommand("setblock ~2 ~ ~18 minecraft:stone");
			world.getServer().runCommand("setblock ~0 ~ ~16 minecraft:oak_slab");
			world.getServer().runCommand("setblock ~3 ~ ~13 minecraft:oak_stairs");
			world.getServer().runCommand("fill ~-4 ~ ~19 ~-3 ~1 ~20 minecraft:stone");
			world.getServer().runCommand("fill ~-1 ~-1 ~19 ~1 ~-1 ~20 minecraft:water");
			world.getServer().runCommand("fill ~4 ~ ~15 ~4 ~ ~17 minecraft:oak_fence");
			world.getServer().runCommand("tp @p ~ ~6 ~8 0 40");
			world.getServer().runCommand("setblock ~ ~5 ~8 minecraft:glass");
			context.waitTicks(40);
			context.runOnClient(mc -> {
				dev.onyx.visual.life.LifeFx.reset();
				dev.onyx.visual.life.LifeFx.spawnGeese(0.5, -60, 12.5, 3);
				dev.onyx.visual.life.LifeFx.spawnGeese(-3.5, -60, 16.5, 3);
				dev.onyx.visual.life.LifeFx.spawnGeese(3.5, -60, 20.5, 2);
			});
			int worstInside = 0;
			double moved = 0.0;
			double lastSpread = context.computeOnClient(mc -> dev.onyx.visual.life.LifeFx.gooseSpread(0.5, 16.5));
			for (int round = 0; round < 24; round++) {
				context.waitTicks(25);
				int[] counts = context.computeOnClient(mc -> dev.onyx.visual.life.LifeFx.embeddedCount(mc.level));
				worstInside = Math.max(worstInside, counts[0]);
				double spread = context.computeOnClient(mc -> dev.onyx.visual.life.LifeFx.gooseSpread(0.5, 16.5));
				moved += Math.abs(spread - lastSpread);
				lastSpread = spread;
				if (round == 12) {
					context.takeScreenshot("onyx-geese-course");
				}
			}
			int[] finalCounts = context.computeOnClient(mc -> dev.onyx.visual.life.LifeFx.embeddedCount(mc.level));
			System.out.println("GEESE-TEST inside=" + worstInside + " total=" + finalCounts[1] + " moved=" + moved);
			if (worstInside > 0) {
				throw new AssertionError("A creature ended up inside a block: " + worstInside);
			}
			if (finalCounts[1] < 10 || moved < 3.0) {
				throw new AssertionError("Geese stopped moving or vanished: total=" + finalCounts[1] + " moved=" + moved);
			}
			world.getServer().runCommand("fill ~-6 ~ ~10 ~6 ~1 ~22 minecraft:air");
			world.getServer().runCommand("fill ~-1 ~-1 ~19 ~1 ~-1 ~20 minecraft:grass_block");
			world.getServer().runCommand("setblock ~ ~5 ~8 minecraft:air");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 10");
			context.runOnClient(mc -> dev.onyx.visual.life.LifeFx.reset());
			context.waitTicks(30);

			// Loot beams, one per rarity.
			world.getServer().runCommand("summon item ~-2 ~ ~4 {Item:{id:\"minecraft:stick\"}}");
			world.getServer().runCommand("summon item ~-0.7 ~ ~4 {Item:{id:\"minecraft:experience_bottle\"}}");
			world.getServer().runCommand("summon item ~0.7 ~ ~4 {Item:{id:\"minecraft:nether_star\"}}");
			world.getServer().runCommand("summon item ~2 ~ ~4 {Item:{id:\"minecraft:dragon_egg\"}}");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 10");
			context.waitTicks(30);
			context.takeScreenshot("onyx-loot-beams");
			world.getServer().runCommand("kill @e[type=item]");

			// Hit flash on a mob and sparks from a broken block.
			world.getServer().runCommand("summon pig ~ ~ ~2 {NoAI:1b}");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 30");
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(3);
			context.takeScreenshot("onyx-hit");
			context.waitTicks(2);
			context.takeScreenshot("onyx-hit2");
			world.getServer().runCommand("kill @e[type=pig]");
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("setblock ~ ~ ~2 minecraft:diamond_block");
			world.getServer().runCommand("setblock ~ ~1 ~2 minecraft:diamond_block");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 0");
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(4);
			context.takeScreenshot("onyx-break");
			world.getServer().runCommand("gamemode survival @p");
			world.getServer().runCommand("fill ~ ~ ~2 ~ ~1 ~2 minecraft:air");
			context.waitTicks(10);

			// Rainbow after rain.
			world.getServer().runCommand("weather rain");
			context.waitTicks(140);
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("tp @p ~ ~ ~ 180 -22");
			context.waitTicks(200);
			context.takeScreenshot("onyx-rainbow");

			// Slash arc.
			world.getServer().runCommand("tp @p ~ ~ ~ 0 10");
			context.runOnClient(mc -> dev.onyx.visual.OnyxConfig.get().wisps = false);
			context.waitTicks(30);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(2);
			context.takeScreenshot("onyx-slash");
			context.runOnClient(mc -> dev.onyx.visual.OnyxConfig.get().wisps = true);

			// Glowing outline on a block right in front of the player.
			world.getServer().runCommand("setblock ~ ~ ~2 minecraft:amethyst_block");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 25");
			context.waitTicks(20);
			context.takeScreenshot("onyx-outline");
			world.getServer().runCommand("setblock ~ ~ ~2 minecraft:air");

			// Torches, lanterns and ores for the halo and glint effects.
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("setblock ~-4 ~ ~4 minecraft:torch");
			world.getServer().runCommand("setblock ~4 ~ ~4 minecraft:soul_lantern");
			world.getServer().runCommand("setblock ~-5 ~ ~6 minecraft:redstone_torch");
			world.getServer().runCommand("setblock ~5 ~ ~6 minecraft:lantern");
			world.getServer().runCommand("fill ~-2 ~ ~3 ~-1 ~1 ~3 minecraft:diamond_ore");
			world.getServer().runCommand("fill ~1 ~ ~3 ~2 ~1 ~3 minecraft:gold_ore");
			world.getServer().runCommand("summon zombie ~ ~ ~4 {NoAI:1b}");
			world.getServer().runCommand("tp @p ~ ~ ~ 0 8");
			context.waitTicks(70);
			context.takeScreenshot("onyx-halos-glints");
			world.getServer().runCommand("kill @e[type=zombie]");
			context.waitTicks(8);
			context.takeScreenshot("onyx-soul-burst");
			world.getServer().runCommand("fill ~-5 ~ ~3 ~5 ~1 ~6 minecraft:air");

			// Night sky and the aura around the player.
			world.getServer().runCommand("tp @p ~ ~ ~ 180 -28");
			context.waitTicks(260);
			context.takeScreenshot("onyx-aurora");
			world.getServer().runCommand("tp @p ~ ~ ~ 180 20");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.getInput().holdKeyFor(options -> options.keyUp, 12);
			context.waitTicks(40);
			context.takeScreenshot("onyx-aura");
			world.getServer().runCommand("xp add @p 3 levels");
			context.waitTicks(9);
			context.takeScreenshot("onyx-level-up");
			context.waitTicks(40);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("onyx-aura-front");
			context.getInput().holdKeyFor(options -> options.keyJump, 3);
			context.waitTicks(14);
			context.takeScreenshot("onyx-shockwave");
			context.waitTicks(3);
			context.takeScreenshot("onyx-shockwave2");
			context.waitTicks(4);
			context.takeScreenshot("onyx-shockwave3");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			world.getServer().runCommand("tp @p ~ ~ ~ 180 60");
			context.waitTicks(20);
			context.takeScreenshot("onyx-circle-first-person");

			// Nether: build a small scene with lava, soul sand and both nylium kinds.
			world.getServer().runCommand("execute in minecraft:the_nether run forceload add -1 -1 1 1");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -12 99 -12 12 99 12 minecraft:netherrack");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -12 100 -12 12 112 12 minecraft:air");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -12 113 -12 12 113 12 minecraft:netherrack");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -3 97 5 4 99 11 minecraft:lava");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -12 99 3 -5 99 12 minecraft:soul_sand");
			world.getServer().runCommand("execute in minecraft:the_nether run fill 6 99 3 12 99 12 minecraft:warped_nylium");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -12 99 -12 12 99 -4 minecraft:crimson_nylium");
			world.getServer().runCommand("execute in minecraft:the_nether run tp @p 0.5 100 0.5 0 8");
			context.waitTicks(260);
			context.takeScreenshot("onyx-nether-lava");
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 60 4");
			context.waitTicks(60);
			context.takeScreenshot("onyx-nether-souls");
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ -60 4");
			context.waitTicks(60);
			context.takeScreenshot("onyx-nether-warped");
		}
	}

	private static int setAll(boolean value, boolean count) {
		int on = 0;
		for (java.lang.reflect.Field f : OnyxConfig.class.getFields()) {
			if (f.getType() != boolean.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
			try {
				if (count) {
					if (f.getBoolean(OnyxConfig.get())) on++;
				} else {
					f.setBoolean(OnyxConfig.get(), value);
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		return on;
	}
}
