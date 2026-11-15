package dev.onyx.visual.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onyx.visual.OnyxConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Replaces the thin black block outline with a glowing colour-cycling frame. */
final class OutlineFx {
	private OutlineFx() {
	}

	/** @return false to cancel the vanilla outline */
	static boolean render(LevelRenderContext context, BlockOutlineRenderState outline, float time) {
		if (!OnyxConfig.get().outline || outline.highContrast()) {
			return true;
		}
		VoxelShape shape = outline.shape();
		if (shape.isEmpty()) {
			return true;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		BlockPos pos = outline.pos();
		float ox = (float) (pos.getX() - camera.x);
		float oy = (float) (pos.getY() - camera.y);
		float oz = (float) (pos.getZ() - camera.z);
		AABB bounds = shape.bounds();
		float cx = (float) (bounds.minX + bounds.maxX) * 0.5f;
		float cy = (float) (bounds.minY + bounds.maxY) * 0.5f;
		float cz = (float) (bounds.minZ + bounds.maxZ) * 0.5f;
		float pulse = 0.75f + 0.25f * Mth.sin(time * 3.0f);

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GlowTypes.QUADS, (pose, buffer) ->
				shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
					// Push the frame slightly outwards so it does not z-fight with the block faces.
					float ax = ox + cx + ((float) x1 - cx) * 1.006f;
					float ay = oy + cy + ((float) y1 - cy) * 1.006f;
					float az = oz + cz + ((float) z1 - cz) * 1.006f;
					float bx = ox + cx + ((float) x2 - cx) * 1.006f;
					float by = oy + cy + ((float) y2 - cy) * 1.006f;
					float bz = oz + cz + ((float) z2 - cz) * 1.006f;
					int colorA = Draw.hue(time * 0.25f + (float) (x1 + y1 + z1) * 0.22f, 0.1f);
					int colorB = Draw.hue(time * 0.25f + (float) (x2 + y2 + z2) * 0.22f, 0.1f);
					edge(pose, buffer, ax, ay, az, bx, by, bz, 0.05f, Draw.glow(0.3f * pulse, colorA), Draw.glow(0.3f * pulse, colorB));
					edge(pose, buffer, ax, ay, az, bx, by, bz, 0.012f, Draw.glow(0.95f, Draw.mix(colorA, 0xFFFFFF, 0.15f)), Draw.glow(0.95f, Draw.mix(colorB, 0xFFFFFF, 0.15f)));
				}));
		return false;
	}

	/** An axis-aligned edge drawn as two crossed strips. */
	private static void edge(PoseStack.Pose pose, VertexConsumer buffer, float ax, float ay, float az,
			float bx, float by, float bz, float w, int colorA, int colorB) {
		float dx = Math.abs(bx - ax);
		float dy = Math.abs(by - ay);
		float dz = Math.abs(bz - az);
		if (dx >= dy && dx >= dz) {
			strip(pose, buffer, ax, ay, az, bx, by, bz, 0, w, 0, colorA, colorB);
			strip(pose, buffer, ax, ay, az, bx, by, bz, 0, 0, w, colorA, colorB);
		} else if (dy >= dz) {
			strip(pose, buffer, ax, ay, az, bx, by, bz, w, 0, 0, colorA, colorB);
			strip(pose, buffer, ax, ay, az, bx, by, bz, 0, 0, w, colorA, colorB);
		} else {
			strip(pose, buffer, ax, ay, az, bx, by, bz, w, 0, 0, colorA, colorB);
			strip(pose, buffer, ax, ay, az, bx, by, bz, 0, w, 0, colorA, colorB);
		}
	}

	private static void strip(PoseStack.Pose pose, VertexConsumer buffer, float ax, float ay, float az,
			float bx, float by, float bz, float wx, float wy, float wz, int colorA, int colorB) {
		Draw.colored(pose, buffer, ax - wx, ay - wy, az - wz, colorA);
		Draw.colored(pose, buffer, bx - wx, by - wy, bz - wz, colorB);
		Draw.colored(pose, buffer, bx + wx, by + wy, bz + wz, colorB);
		Draw.colored(pose, buffer, ax + wx, ay + wy, az + wz, colorA);
	}
}
