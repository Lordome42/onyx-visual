package dev.onyx.visual.ai;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Gives the model real recipes instead of letting it guess: finds the items a question mentions
 * (by their in-game names, in the player's language) and reads their vanilla recipes from the game jar.
 */
final class RecipeFacts {
	private static final String DIR = "data/minecraft/recipe/";
	private static Map<String, List<JsonObject>> byResult;

	private RecipeFacts() {
	}

	/** Recipe text for the items mentioned in {@code question}, or an empty string. */
	static String forQuestion(String question) {
		try {
			List<Item> items = mentioned(question);
			if (items.isEmpty()) {
				return "";
			}
			StringBuilder out = new StringBuilder();
			for (Item item : items) {
				List<JsonObject> recipes = recipes(BuiltInRegistries.ITEM.getKey(item).getPath());
				int shown = 0;
				for (JsonObject recipe : recipes) {
					String text = describe(recipe);
					if (text != null && shown < 3 && out.length() < 2200) {
						out.append(text).append('\n');
						shown++;
					}
				}
				if (shown == 0) {
					out.append(name(item)).append(": no crafting recipe in vanilla (it is found, mined, traded or dropped).\n");
				}
			}
			return out.toString().strip();
		} catch (Throwable t) {
			return "";
		}
	}

	// ------------------------------------------------------------ matching

	private static List<String> words(String text) {
		List<String> list = new ArrayList<>();
		for (String w : text.toLowerCase(Locale.ROOT).replace('ё', 'е').split("[^\\p{L}\\p{Nd}]+")) {
			if (!w.isEmpty()) {
				list.add(w);
			}
		}
		return list;
	}

	private static String stem(String word) {
		return word.length() <= 4 ? word : word.substring(0, Math.max(4, word.length() - 2));
	}

	private static boolean matches(List<String> nameWords, List<String> question) {
		if (nameWords.isEmpty()) {
			return false;
		}
		for (String w : nameWords) {
			if (w.length() < 2) {
				return false;
			}
			String stem = stem(w);
			boolean found = false;
			for (String q : question) {
				if (q.startsWith(stem) && q.length() <= w.length() + 3 && q.length() >= w.length() - 1) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static List<Item> mentioned(String question) {
		List<String> q = words(question);
		List<Item> found = new ArrayList<>();
		List<Integer> scores = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			List<String> local = words(name(item));
			List<String> english = words(BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' '));
			int score = matches(local, q) ? local.size() * 10 + name(item).length()
					: matches(english, q) ? english.size() * 10 + english.toString().length() : -1;
			if (score >= 0) {
				found.add(item);
				scores.add(score);
			}
		}
		if (found.isEmpty()) {
			// Nothing named in full: allow one missing word, so "bed" finds "White Bed" (colours, materials).
			for (Item item : BuiltInRegistries.ITEM) {
				List<String> local = words(name(item));
				if (item == Items.AIR || local.size() < 2) {
					continue;
				}
				for (int skip = 0; skip < local.size(); skip++) {
					List<String> rest = new ArrayList<>(local);
					rest.remove(skip);
					if (String.join("", rest).length() >= 6 && matches(rest, q)) {
						found.add(item);
						scores.add(-name(item).length());
						return List.of(item);
					}
				}
			}
		}
		// Most specific names first ("Iron Pickaxe" beats "Iron"), at most three.
		List<Item> best = new ArrayList<>();
		while (!found.isEmpty() && best.size() < 3) {
			int top = 0;
			for (int i = 1; i < found.size(); i++) {
				if (scores.get(i) > scores.get(top)) {
					top = i;
				}
			}
			Item item = found.remove(top);
			scores.remove(top);
			List<String> mine = words(name(item));
			boolean covered = false;
			for (Item b : best) {
				if (words(name(b)).containsAll(mine)) {
					covered = true;
				}
			}
			if (!covered) {
				best.add(item);
			}
		}
		return best;
	}

	// ------------------------------------------------------------ recipes

	private static synchronized List<JsonObject> recipes(String resultPath) {
		if (byResult == null) {
			byResult = index();
		}
		List<JsonObject> list = byResult.get("minecraft:" + resultPath);
		if (list != null && !list.isEmpty()) {
			return list;
		}
		// Index unavailable: at least try the file named after the item.
		JsonObject direct = read(DIR + resultPath + ".json");
		return direct == null ? List.of() : List.of(direct);
	}

	private static Map<String, List<JsonObject>> index() {
		Map<String, List<JsonObject>> map = new HashMap<>();
		try {
			URL url = RecipeFacts.class.getClassLoader().getResource(DIR + "beacon.json");
			if (url == null) {
				return map;
			}
			URI uri = url.toURI();
			Path file;
			try {
				file = Paths.get(uri);
			} catch (FileSystemNotFoundException e) {
				file = FileSystems.newFileSystem(uri, Map.of()).provider().getPath(uri);
			}
			try (Stream<Path> files = Files.list(file.getParent())) {
				files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
					try {
						JsonObject json = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
						String id = resultId(json);
						if (id != null) {
							map.computeIfAbsent(id, k -> new ArrayList<>()).add(json);
						}
					} catch (Exception ignored) {
					}
				});
			}
		} catch (Exception ignored) {
		}
		return map;
	}

	private static JsonObject read(String path) {
		try (InputStream in = RecipeFacts.class.getClassLoader().getResourceAsStream(path)) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	private static String resultId(JsonObject json) {
		JsonElement result = json.get("result");
		if (result == null) {
			return null;
		}
		if (result.isJsonPrimitive()) {
			return normalise(result.getAsString());
		}
		JsonObject object = result.getAsJsonObject();
		return object.has("id") ? normalise(object.get("id").getAsString()) : null;
	}

	private static int resultCount(JsonObject json) {
		JsonElement result = json.get("result");
		return result != null && result.isJsonObject() && result.getAsJsonObject().has("count")
				? result.getAsJsonObject().get("count").getAsInt() : 1;
	}

	private static String normalise(String id) {
		return id.contains(":") ? id : "minecraft:" + id;
	}

	private static String describe(JsonObject json) {
		String type = json.has("type") ? json.get("type").getAsString().replace("minecraft:", "") : "";
		String id = resultId(json);
		if (id == null) {
			return null;
		}
		int count = resultCount(json);
		String result = itemName(id) + (count > 1 ? " x" + count : "");
		switch (type) {
			case "crafting_shaped": {
				JsonObject key = json.getAsJsonObject("key");
				JsonArray pattern = json.getAsJsonArray("pattern");
				StringBuilder s = new StringBuilder("RECIPE ").append(result).append(" — crafting grid, shaped (rows top to bottom):");
				Map<String, Integer> totals = new java.util.LinkedHashMap<>();
				int rowNumber = 1;
				for (JsonElement row : pattern) {
					s.append("\n  row ").append(rowNumber++).append(": ");
					String r = row.getAsString();
					for (int i = 0; i < r.length(); i++) {
						if (i > 0) {
							s.append(" | ");
						}
						char c = r.charAt(i);
						String name = c == ' ' ? "empty" : ingredient(key.get(String.valueOf(c)));
						s.append(name);
						if (c != ' ') {
							totals.merge(name, 1, Integer::sum);
						}
					}
				}
				s.append("\n  TOTAL needed: ");
				List<String> parts = new ArrayList<>();
				totals.forEach((name, n) -> parts.add(n + " " + name));
				s.append(String.join(", ", parts));
				return s.toString();
			}
			case "crafting_shapeless": {
				List<String> parts = new ArrayList<>();
				for (JsonElement e : json.getAsJsonArray("ingredients")) {
					parts.add(ingredient(e));
				}
				return "RECIPE " + result + " — crafting grid, any arrangement: " + String.join(" + ", parts);
			}
			case "crafting_transmute":
				return "RECIPE " + result + " — crafting grid: " + ingredient(json.get("input")) + " + " + ingredient(json.get("material"));
			case "smelting":
			case "blasting":
			case "smoking":
			case "campfire_cooking":
				return "RECIPE " + result + " — " + type.replace('_', ' ') + " (" + (type.equals("smelting") ? "furnace" : type.equals("blasting") ? "blast furnace" : type.equals("smoking") ? "smoker" : "campfire") + "): " + ingredient(json.get("ingredient"));
			case "stonecutting":
				return "RECIPE " + result + " — stonecutter: " + ingredient(json.get("ingredient"));
			case "smithing_transform":
				return "RECIPE " + result + " — smithing table: " + ingredient(json.get("template")) + " + " + ingredient(json.get("base")) + " + " + ingredient(json.get("addition"));
			default:
				return null;
		}
	}

	private static String ingredient(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return "?";
		}
		if (e.isJsonArray()) {
			List<String> options = new ArrayList<>();
			for (JsonElement option : e.getAsJsonArray()) {
				options.add(ingredient(option));
			}
			return String.join(" or ", options);
		}
		if (e.isJsonObject()) {
			JsonObject o = e.getAsJsonObject();
			if (o.has("item")) {
				return itemName(o.get("item").getAsString());
			}
			if (o.has("tag")) {
				return tag(o.get("tag").getAsString());
			}
			return "?";
		}
		String id = e.getAsString();
		if (id.startsWith("#")) {
			return tag(id.substring(1));
		}
		return itemName(id);
	}

	/** A tag shown as its actual items ("Cobblestone or Blackstone or …") when the game knows them. */
	private static String tag(String id) {
		try {
			var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(normalise(id)));
			List<String> names = new ArrayList<>();
			int total = 0;
			for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
				if (names.size() < 4) {
					names.add(name(holder.value()));
				}
				total++;
			}
			if (total == 1) {
				return names.get(0);
			}
			if (total > 1) {
				return String.join(" or ", names) + (total > names.size() ? " or other " + id.replaceAll(".*:", "").replace('_', ' ') : "");
			}
		} catch (RuntimeException ignored) {
		}
		return "any " + id.replaceAll(".*:", "").replace('_', ' ');
	}

	private static String itemName(String id) {
		try {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(normalise(id)));
			if (item != null && item != Items.AIR) {
				return name(item);
			}
		} catch (RuntimeException ignored) {
		}
		return id.replaceAll(".*:", "").replace('_', ' ');
	}

	private static String name(Item item) {
		return item.getDefaultInstance().getHoverName().getString();
	}
}
