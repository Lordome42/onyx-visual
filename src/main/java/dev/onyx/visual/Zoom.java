package dev.onyx.visual;

/** Smooth, frame-rate independent zoom. Queried once per frame from the camera mixin. */
public final class Zoom {
	private static float progress;
	private static long lastNanos = System.nanoTime();

	private Zoom() {
	}

	public static float apply(float fov) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
		lastNanos = now;

		boolean held = OnyxConfig.get().zoom && OnyxVisual.zoomKey != null && OnyxVisual.zoomKey.isDown();
		float target = held ? 1.0f : 0.0f;
		// Exponential approach: snappy at the start, soft at the end.
		progress += (target - progress) * (1.0f - (float) Math.exp(-dt * 14.0f));
		if (Math.abs(target - progress) < 0.001f) {
			progress = target;
		}
		if (progress <= 0.0f) {
			return fov;
		}
		float eased = progress * progress * (3.0f - 2.0f * progress);
		return fov * (1.0f + (1.0f / Math.max(1.5f, OnyxConfig.get().zoomStrength) - 1.0f) * eased);
	}
}
