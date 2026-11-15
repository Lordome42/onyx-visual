package dev.onyx.visual.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** World queries shared by the creatures and the ground decorations. */
final class Terrain {
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

	private Terrain() {
	}

	/**
	 * Height of the walkable surface near {@code guessY}, or NaN if there is none.
	 * With {@code water} set, an open water surface also counts (creatures float on it).
	 */
	static double groundY(ClientLevel level, double x, double guessY, double z, boolean water) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int top = Mth.floor(guessY) + 2;
		for (int y = top; y >= top - 6; y--) {
			BlockState state = level.getBlockState(POS.set(bx, y, bz));
			if (state.is(Blocks.WATER)) {
				if (water && level.getBlockState(POS.set(bx, y + 1, bz)).isAir()) {
					return y + 0.72;
				}
				return Double.NaN;
			}
			VoxelShape shape = state.getCollisionShape(level, POS.set(bx, y, bz));
			if (!shape.isEmpty()) {
				BlockState above = level.getBlockState(POS.set(bx, y + 1, bz));
				if (above.getCollisionShape(level, POS).isEmpty() && above.getFluidState().isEmpty()) {
					return y + shape.max(Direction.Axis.Y);
				}
				return Double.NaN;
			}
		}
		return Double.NaN;
	}

	static BlockState stateAt(ClientLevel level, double x, double y, double z) {
		return level.getBlockState(POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
	}

	static boolean isWater(ClientLevel level, double x, double y, double z) {
		return stateAt(level, x, y, z).is(Blocks.WATER);
	}

	/** Packed light for the entity shader at a position. */
	static int light(ClientLevel level, double x, double y, double z) {
		POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		int block = level.getBrightness(LightLayer.BLOCK, POS);
		int sky = level.getBrightness(LightLayer.SKY, POS);
		return block << 4 | sky << 20;
	}
}
