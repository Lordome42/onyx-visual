package dev.onyx.visual.gui;

import dev.onyx.visual.OnyxVisual;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Frame-time guard for the ClickGUI. Writes config/onyx-visual-perf.log (one line per second plus every very slow
 * frame) and reports when the menu is running so slowly that it should switch itself to lite mode.
 */
final class PerfLog {
	private static final int MAX_LINES = 400;
	private BufferedWriter out;
	private int lines;
	private long start = System.nanoTime(), secondStart = start;
	private int frames, totalFrames, slowStreak;
	private double buildMsSum, buildMsMax, frameMsMax;
	private long fills, texts;
	private boolean autoLite;

	PerfLog(float guiScale, int w, int h, int fbw, int fbh, boolean lite) {
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("onyx-visual-perf.log");
			if (Files.exists(file) && Files.size(file) > 200_000) Files.delete(file);
			out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			String gpu = "?";
			try {
				var info = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo();
				gpu = info.name() + " / " + info.backendName();
			} catch (Throwable ignored) {
			}
			line("Onyx Visual " + FabricLoader.getInstance().getModContainer("onyxvisual").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")
					+ " | gpu=" + gpu + " | gui=" + w + "x" + h + " scale=" + guiScale + " | framebuffer=" + fbw + "x" + fbh + " | lite=" + lite);
		} catch (Throwable t) {
			out = null;
		}
	}

	boolean autoLite() { return autoLite; }

	/** @param frameMs time since the previous frame (real FPS), @param buildMs time spent building the GUI */
	void frame(double frameMs, double buildMs, boolean lite) {
		totalFrames++;
		frames++;
		buildMsSum += buildMs;
		buildMsMax = Math.max(buildMsMax, buildMs);
		frameMsMax = Math.max(frameMsMax, frameMs);
		fills += McCanvas.statFills;
		texts += McCanvas.statTexts;
		if (frameMs > 100 && totalFrames > 2) line(String.format("slow frame #%d: %.0f ms (gui build %.1f ms, fills %d, texts %d)", totalFrames, frameMs, buildMs, McCanvas.statFills, McCanvas.statTexts));
		// The first frames load fonts and textures; after that, a long run of slow frames means the GPU can't keep up.
		if (!lite && totalFrames > 5) {
			slowStreak = frameMs > 45 ? slowStreak + 1 : 0;
			if (slowStreak >= 12 && !autoLite) {
				autoLite = true;
				line("auto lite mode: 12 frames in a row slower than 45 ms");
			}
		}
		long now = System.nanoTime();
		if (now - secondStart >= 1_000_000_000L) {
			double sec = (now - secondStart) / 1e9;
			line(String.format("t=%.0fs fps=%.1f build avg %.2f ms max %.1f ms | longest frame %.0f ms | fills/frame %d texts/frame %d | lite=%s",
					(now - start) / 1e9, frames / sec, buildMsSum / Math.max(1, frames), buildMsMax, frameMsMax, fills / Math.max(1, frames), texts / Math.max(1, frames), lite));
			secondStart = now;
			frames = 0;
			buildMsSum = buildMsMax = frameMsMax = 0;
			fills = texts = 0;
		}
	}

	private void line(String s) {
		if (out == null || lines++ > MAX_LINES) return;
		try {
			out.write(s);
			out.newLine();
			out.flush();
		} catch (Throwable t) {
			out = null;
		}
	}

	void close() {
		if (out == null) return;
		try {
			out.close();
		} catch (Throwable ignored) {
		}
		out = null;
	}
}
