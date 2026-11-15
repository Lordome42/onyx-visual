package dev.onyx.visual.gui;

/** Small animation helpers (replacements for the original client's AnimatedFloat / SliderModel). */
public final class Anim {
	private Anim() {}

	public static final class AnimatedFloat {
		private float value, target;
		public AnimatedFloat(float value) { this.value = this.target = value; }
		public void setTarget(float target) { this.target = target; }
		public void snap(float v) { value = target = v; }
		public float value() { return value; }
		/** Exponential approach to the target; speed is roughly "1/seconds". */
		public float update(float dt, float speed) {
			value += (target - value) * (1 - (float) Math.exp(-speed * dt));
			if (Math.abs(target - value) < .001f) value = target;
			return value;
		}
	}

	public static final class SliderModel {
		private final float min, max, step;
		private float value;
		private float shown = Float.NaN;
		public SliderModel(float min, float max, float step, float value) {
			this.min = min; this.max = max; this.step = step; this.value = clamp(value);
		}
		public float value() { return value; }
		public void set(float v) { value = clamp(v); }
		public void setFromMouse(float mx, float trackX, float trackW) {
			float p = Math.max(0, Math.min(1, (mx - trackX) / trackW));
			float v = min + (max - min) * p;
			if (step > 0) v = Math.round(v / step) * step;
			value = clamp(v);
		}
		/** Animated progress 0..1 used for drawing. */
		public float update(float dt) {
			float p = (value - min) / (max - min);
			if (Float.isNaN(shown)) shown = p;
			shown += (p - shown) * (1 - (float) Math.exp(-22 * dt));
			return shown;
		}
		private float clamp(float v) { return Math.max(min, Math.min(max, v)); }
	}
}
