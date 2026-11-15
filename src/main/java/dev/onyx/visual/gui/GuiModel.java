package dev.onyx.visual.gui;

import dev.onyx.visual.gui.Anim.AnimatedFloat;
import dev.onyx.visual.gui.Anim.SliderModel;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Data model behind the ClickGUI: categories, modules and their settings. Backed by lambdas. */
public final class GuiModel {
	private GuiModel() {}

	public record Category(String id, String name, String description, int icon, List<Module> modules) {}

	public static final class Module {
		public final String id, name, description;
		public final List<Setting> settings;
		public final AnimatedFloat animation;
		public boolean enabled;
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;

		public Module(String id, String name, String description, BooleanSupplier getter, Consumer<Boolean> setter, List<Setting> settings) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.getter = getter;
			this.setter = setter;
			this.settings = List.copyOf(settings);
			this.enabled = getter.getAsBoolean();
			this.animation = new AnimatedFloat(enabled ? 1 : 0);
		}

		public String id() { return id; }

		public void toggle() {
			enabled = !enabled;
			setter.accept(enabled);
			animation.setTarget(enabled ? 1 : 0);
		}

		/** Re-reads the backing value (a keybind may have toggled it while the GUI was closed). */
		public void refresh() {
			boolean now = getter.getAsBoolean();
			if (now != enabled) {
				enabled = now;
				animation.setTarget(enabled ? 1 : 0);
			}
		}
	}

	public sealed interface Setting permits Toggle, Slider, Choice, Group, ColorValue, Key {
		String name();
		String hint();
		default boolean visible() { return true; }
	}

	public static final class Toggle implements Setting {
		private final String name, hint;
		private final Consumer<Boolean> setter;
		public final AnimatedFloat animation;
		public boolean value;

		public Toggle(String name, String hint, BooleanSupplier getter, Consumer<Boolean> setter) {
			this.name = name;
			this.hint = hint;
			this.setter = setter;
			this.value = getter.getAsBoolean();
			this.animation = new AnimatedFloat(value ? 1 : 0);
		}
		public String name() { return name; }
		public String hint() { return hint; }
		public void toggle() {
			value = !value;
			setter.accept(value);
			animation.setTarget(value ? 1 : 0);
		}
	}

	public static final class Slider implements Setting {
		private final String name, hint, suffix;
		private final DoubleConsumer setter;
		public final SliderModel model;

		public Slider(String name, String hint, float min, float max, float step, String suffix, DoubleSupplier getter, DoubleConsumer setter) {
			this.name = name;
			this.hint = hint;
			this.suffix = suffix;
			this.setter = setter;
			this.model = new SliderModel(min, max, step, (float) getter.getAsDouble());
		}
		public String name() { return name; }
		public String hint() { return hint; }
		public String value() {
			float value = model.value();
			String formatted = Math.abs(value - Math.round(value)) < .01f
					? Integer.toString(Math.round(value))
					: String.format(java.util.Locale.ROOT, "%.1f", value);
			return formatted + suffix;
		}
		public void sync() { setter.accept(model.value()); }
	}

	public static final class Choice implements Setting {
		private final String name, hint;
		private final IntConsumer setter;
		public final List<String> values;
		public int selected;

		public Choice(String name, String hint, List<String> values, IntSupplier getter, IntConsumer setter) {
			this.name = name;
			this.hint = hint;
			this.values = List.copyOf(values);
			this.setter = setter;
			this.selected = Math.max(0, Math.min(values.size() - 1, getter.getAsInt()));
		}
		public String name() { return name; }
		public String hint() { return hint; }
		public String value() { return values.get(selected); }
		public void next() { select((selected + 1) % values.size()); }
		public void select(int index) {
			selected = Math.max(0, Math.min(values.size() - 1, index));
			setter.accept(selected);
		}
	}

	public static final class Group implements Setting {
		private final String name, hint;
		private final Supplier<String> enabledWord;
		public final List<Toggle> values;

		public Group(String name, String hint, List<Toggle> values, Supplier<String> enabledWord) {
			this.name = name;
			this.hint = hint;
			this.values = List.copyOf(values);
			this.enabledWord = enabledWord;
		}
		public String name() { return name; }
		public String hint() { return hint; }
		public String summary() {
			long enabled = values.stream().filter(value -> value.value).count();
			return enabled + "/" + values.size() + " " + enabledWord.get();
		}
	}

	public static final class ColorValue implements Setting {
		private final String name, hint;
		private final IntConsumer setter;
		private float hue, saturation, brightness, alpha;

		public ColorValue(String name, String hint, IntSupplier getter, IntConsumer setter) {
			this.name = name;
			this.hint = hint;
			this.setter = setter;
			int argb = getter.getAsInt();
			float[] hsb = Colors.rgbToHsb(argb);
			hue = hsb[0];
			saturation = hsb[1];
			brightness = hsb[2];
			alpha = ((argb >>> 24) & 255) / 255f;
		}
		public String name() { return name; }
		public String hint() { return hint; }
		public int argb() { return (Math.round(alpha * 255) << 24) | (Colors.hsbToRgb(hue, saturation, brightness) & 0xFFFFFF); }
		public String value() { return String.format("#%06X", argb() & 0xFFFFFF); }
		public float hue() { return hue; }
		public float saturation() { return saturation; }
		public float brightness() { return brightness; }
		public float alpha() { return alpha; }
		public void setHsb(float h, float s, float b) { hue = h; saturation = s; brightness = b; setter.accept(argb()); }
		public void setAlpha(float a) { alpha = a; setter.accept(argb()); }
		public void setArgb(int argb) {
			float[] hsb = Colors.rgbToHsb(argb);
			hue = hsb[0]; saturation = hsb[1]; brightness = hsb[2];
			alpha = ((argb >>> 24) & 255) / 255f;
			setter.accept(argb());
		}
	}

	public record Key(String name, String hint, Supplier<String> keyLabel) implements Setting {
		public String key() { return keyLabel.get(); }
	}
}
