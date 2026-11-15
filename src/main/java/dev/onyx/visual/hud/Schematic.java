package dev.onyx.visual.hud;

import java.lang.reflect.Method;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional, reflection-only bridge to Litematica: reads which block the loaded schematic wants at a position.
 * Onyx Visual has no compile-time dependency on Litematica; if it is missing everything here returns null.
 */
public final class Schematic {
	/** Test hook: when set, used instead of Litematica. */
	public static Function<BlockPos, BlockState> override;

	private static boolean resolved;
	private static Method getWorld;

	private Schematic() {
	}

	public static boolean available() {
		return override != null || world() != null;
	}

	/** The block the schematic expects at {@code pos}, or null when no schematic is loaded. */
	public static BlockState wanted(BlockPos pos) {
		if (override != null) {
			return override.apply(pos);
		}
		Level world = world();
		if (world == null) {
			return null;
		}
		try {
			return world.getBlockState(pos);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Level world() {
		if (!resolved) {
			resolved = true;
			if (FabricLoader.getInstance().isModLoaded("litematica")) {
				for (String name : new String[] {"fi.dy.masa.litematica.world.SchematicWorldHandler"}) {
					try {
						getWorld = Class.forName(name).getMethod("getSchematicWorld");
						break;
					} catch (Throwable ignored) {
					}
				}
			}
		}
		if (getWorld == null) {
			return null;
		}
		try {
			return getWorld.invoke(null) instanceof Level level ? level : null;
		} catch (Throwable t) {
			getWorld = null;
			return null;
		}
	}
}
