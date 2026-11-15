package dev.onyx.visual.ai;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.onyx.visual.OnyxConfig;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Talks to a chat model over HTTP. Out of the box it uses free services that need no account
 * (LLM7, then Pollinations as a fallback); with a URL and key in the config it talks to any
 * OpenAI-compatible API instead (Groq, OpenRouter, Gemini, a local Ollama…).
 */
public final class AiClient {
	public static final String FREE_URL = "https://api.llm7.io/v1/chat/completions";
	public static final String FREE_MODEL = "default";
	private static final String FALLBACK_URL = "https://text.pollinations.ai/";

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.proxy(java.net.ProxySelector.getDefault())
			.build();

	public record Message(String role, String text) {
	}

	private AiClient() {
	}

	public static boolean usingFree() {
		return OnyxConfig.get().aiUrl == null || OnyxConfig.get().aiUrl.isBlank();
	}

	public static String providerName() {
		if (usingFree()) {
			return "LLM7 / Pollinations";
		}
		String url = OnyxConfig.get().aiUrl;
		try {
			return URI.create(url.trim()).getHost();
		} catch (RuntimeException e) {
			return url;
		}
	}

	/** Sends the conversation and completes with the answer text (already cleaned for the game font). */
	public static CompletableFuture<String> ask(List<Message> history) {
		String base = systemPrompt();
		List<Message> recent = history.size() > 12 ? history.subList(history.size() - 12, history.size()) : history;
		List<Message> copy = new ArrayList<>(recent);
		// Look up real recipes for what the last two questions mention, off the render thread.
		StringBuilder asked = new StringBuilder();
		int users = 0;
		for (int i = copy.size() - 1; i >= 0 && users < 2; i--) {
			if (copy.get(i).role().equals("user")) {
				asked.insert(0, copy.get(i).text() + " ");
				users++;
			}
		}
		OnyxConfig config = OnyxConfig.get();
		return CompletableFuture.supplyAsync(() -> RecipeFacts.forQuestion(asked.toString()))
				.thenCompose(facts -> {
					String system = facts.isEmpty() ? base : base
							+ "\n\nVERIFIED GAME DATA (read from this exact game version; always trust it over your memory, "
							+ "and when asked how to craft something, describe exactly this, row by row, using these item names):\n" + facts;
					if (!usingFree()) {
						String model = config.aiModel == null || config.aiModel.isBlank() ? "gpt-4o-mini" : config.aiModel.trim();
						return openAi(config.aiUrl.trim(), config.aiKey, model, system, copy);
					}
					return openAi(FREE_URL, "unused", FREE_MODEL, system, copy)
							.exceptionallyCompose(first -> pollinations(system, copy));
				})
				.thenApply(AiClient::clean);
	}

	/** For tests: the recipe facts that would be attached to {@code question}. */
	public static String factsFor(String question) {
		return RecipeFacts.forQuestion(question);
	}

	private static CompletableFuture<String> openAi(String url, String key, String model, String system, List<Message> history) {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		JsonArray messages = new JsonArray();
		messages.add(message("system", system));
		for (Message m : history) {
			messages.add(message(m.role(), m.text()));
		}
		body.add("messages", messages);
		body.addProperty("temperature", 0.5);
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(90))
				.header("Content-Type", "application/json")
				.header("User-Agent", "OnyxVisual-Minecraft-Mod")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
		if (key != null && !key.isBlank()) {
			request.header("Authorization", "Bearer " + key.trim());
		}
		return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					JsonObject json;
					try {
						json = JsonParser.parseString(response.body()).getAsJsonObject();
					} catch (RuntimeException e) {
						throw new IllegalStateException("HTTP " + response.statusCode());
					}
					if (json.has("error")) {
						JsonElement error = json.get("error");
						String text = error.isJsonObject() && error.getAsJsonObject().has("message")
								? error.getAsJsonObject().get("message").getAsString() : error.toString();
						throw new IllegalStateException(text);
					}
					if (response.statusCode() / 100 != 2 || !json.has("choices")) {
						throw new IllegalStateException("HTTP " + response.statusCode());
					}
					JsonElement content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
							.getAsJsonObject("message").get("content");
					if (content == null || content.isJsonNull() || content.getAsString().isBlank()) {
						throw new IllegalStateException("empty answer");
					}
					return content.getAsString();
				});
	}

	/** The classic keyless Pollinations endpoint: one GET with the prompt in the path. */
	private static CompletableFuture<String> pollinations(String system, List<Message> history) {
		StringBuilder prompt = new StringBuilder();
		for (int i = Math.max(0, history.size() - 6); i < history.size(); i++) {
			Message m = history.get(i);
			if (i < history.size() - 1) {
				prompt.append(m.role().equals("user") ? "User: " : "Assistant: ");
			}
			prompt.append(m.text()).append('\n');
		}
		String text = prompt.toString().trim();
		if (text.length() > 1500) {
			text = text.substring(text.length() - 1500);
		}
		String url = FALLBACK_URL + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
				+ "?system=" + URLEncoder.encode(system, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(90))
				.header("User-Agent", "OnyxVisual-Minecraft-Mod")
				.GET().build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> {
					if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
						throw new IllegalStateException("HTTP " + response.statusCode());
					}
					return response.body();
				});
	}

	private static JsonObject message(String role, String text) {
		JsonObject object = new JsonObject();
		object.addProperty("role", role);
		object.addProperty("content", text);
		return object;
	}

	private static String systemPrompt() {
		StringBuilder s = new StringBuilder();
		s.append("You are a friendly helper inside Minecraft Java Edition ")
				.append(SharedConstants.getCurrentVersion().name())
				.append(". Answer in the same language as the question. Be short and practical: at most 6 short sentences or a short list. ")
				.append("Plain text only: no markdown, no tables, no ** or #. If you are not sure about a recipe or number, say so instead of guessing.");
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && mc.level != null) {
			s.append(" Context about the player (use only if relevant): dimension ")
					.append(mc.level.dimension().identifier())
					.append(", biome ")
					.append(mc.level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"))
					.append(", Y=").append(player.blockPosition().getY());
			ItemStack held = player.getMainHandItem();
			if (!held.isEmpty()) {
				s.append(", holding ").append(held.getHoverName().getString());
			}
			s.append(", health ").append(Math.round(player.getHealth())).append("/20")
					.append(", time ").append(String.format(Locale.ROOT, "%02d:00", (Math.floorMod(mc.level.getDefaultClockTime(), 24000L) / 1000 + 6) % 24)).append('.');
		}
		return s.toString();
	}

	/** Strips markdown the game font cannot show, and turns tables into simple lines. */
	static String clean(String text) {
		StringBuilder out = new StringBuilder();
		for (String line : text.replace("\r", "").split("\n")) {
			String t = line.strip();
			if (t.matches("^\\|?\\s*:?-{2,}.*")) {
				continue;
			}
			if (t.startsWith("|") && t.endsWith("|") && t.length() > 1) {
				String[] cells = t.substring(1, t.length() - 1).split("\\|");
				List<String> parts = new ArrayList<>();
				for (String cell : cells) {
					if (!cell.isBlank()) {
						parts.add(cell.strip());
					}
				}
				t = String.join(" — ", parts);
			}
			t = t.replaceAll("^#{1,6}\\s*", "").replaceAll("^[*-]\\s+", "• ");
			out.append(t).append('\n');
		}
		String s = out.toString()
				.replace("**", "").replace("__", "").replace("`", "")
				.replaceAll("\\n{3,}", "\n\n")
				.strip();
		return s.isEmpty() ? "…" : s;
	}

	static String describe(Throwable error) {
		Throwable t = error;
		while ((t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
			t = t.getCause();
		}
		String message = t.getMessage();
		if (t instanceof java.net.http.HttpTimeoutException) {
			return "timeout";
		}
		if (t instanceof java.net.ConnectException || t instanceof java.nio.channels.UnresolvedAddressException) {
			return "no connection";
		}
		return message == null ? t.getClass().getSimpleName() : message.toLowerCase(Locale.ROOT).length() > 160 ? message.substring(0, 160) : message;
	}
}
