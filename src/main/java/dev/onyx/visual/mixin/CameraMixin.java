package dev.onyx.visual.mixin;

import dev.onyx.visual.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	// require = 0: if a future version moves this method the zoom just stops working instead of crashing.
	@Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true, require = 1)
	private void onyx$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float fov = cir.getReturnValueF();
		float zoomed = Zoom.apply(fov);
		if (zoomed != fov) {
			cir.setReturnValue(zoomed);
		}
	}
}
