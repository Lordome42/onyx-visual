package dev.onyx.visual.fx;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** The few calls that differ between supported game versions live here. */
public final class Compat {
	private Compat() {
	}

	public static boolean isSwinging(LivingEntity entity) {
		return entity.swinging;
	}

	public static void swing(LivingEntity entity) {
		entity.swing(InteractionHand.MAIN_HAND);
	}
}
