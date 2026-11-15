package dev.onyx.visual.life;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * A tiny immediate-mode box modeller. Boxes are given in model pixels in a local frame
 * (x = the creature's right, y = up, z = forwards) and are placed into the world by an
 * origin, a scale and an orientation; a movable "part" can be rotated around a pivot.
 */
final class Model {
	static final int PLAIN = 0, FEATHER = 1, FUR = 2, SPECKLE = 3, SHELL = 4, WING = 5, SCALES = 6, SNOW = 7,
			WOOD = 8, LEAF = 9, PETAL = 10, SKIN = 11, BUTTERFLY = 12, SPINES = 13, FLUFF = 14, STONE = 15;
	// For each face: which local axis runs along u and which along v (0 = x, 1 = y, 2 = z).
	private static final int[] FACE_U = {2, 2, 0, 0, 0, 0};
	private static final int[] FACE_V = {1, 1, 2, 2, 1, 1};

	private static final int[][] FACES = {
			{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}
	};

	private final PoseStack.Pose pose;
	private final VertexConsumer buffer;
	private final float[][] corners = new float[8][3];

	private float ox, oy, oz, scale;
	private float rx, ry, rz, ux, uy, uz, fx, fy, fz;
	private int light;
	private float tileU, tileV;
	private final float[] size = new float[3];
	private boolean part;
	private int partAxis;
	private float partX, partY, partZ, partCos, partSin;

	Model(PoseStack.Pose pose, VertexConsumer buffer) {
		this.pose = pose;
		this.buffer = buffer;
	}

	/** Places the model. Yaw follows the game's convention (0 faces south); pitch tips the nose down. */
	void place(float x, float y, float z, float scale, float yaw, float pitch, float roll, int light) {
		this.ox = x;
		this.oy = y;
		this.oz = z;
		this.scale = scale;
		this.light = light;
		this.part = false;
		material(PLAIN);
		float sin = Mth.sin(yaw);
		float cos = Mth.cos(yaw);
		fx = -sin;
		fy = 0.0f;
		fz = cos;
		rx = -cos;
		ry = 0.0f;
		rz = -sin;
		ux = 0.0f;
		uy = 1.0f;
		uz = 0.0f;
		if (pitch != 0.0f) {
			float c = Mth.cos(pitch);
			float s = Mth.sin(pitch);
			float nfx = fx * c - ux * s, nfy = fy * c - uy * s, nfz = fz * c - uz * s;
			float nux = ux * c + fx * s, nuy = uy * c + fy * s, nuz = uz * c + fz * s;
			fx = nfx;
			fy = nfy;
			fz = nfz;
			ux = nux;
			uy = nuy;
			uz = nuz;
		}
		if (roll != 0.0f) {
			float c = Mth.cos(roll);
			float s = Mth.sin(roll);
			float nrx = rx * c + ux * s, nry = ry * c + uy * s, nrz = rz * c + uz * s;
			float nux = ux * c - rx * s, nuy = uy * c - ry * s, nuz = uz * c - rz * s;
			rx = nrx;
			ry = nry;
			rz = nrz;
			ux = nux;
			uy = nuy;
			uz = nuz;
		}
	}

	/** Picks the 16x16 detail tile that following boxes are textured with (one texel per model pixel). */
	void material(int tile) {
		tileU = (tile & 3) * 0.25f;
		tileV = (tile >> 2) * 0.25f;
	}

	/** Following boxes are rotated by {@code angle} around a local axis (0 = x, 1 = y, 2 = z) through the pivot. */
	void beginPart(float pivotX, float pivotY, float pivotZ, int axis, float angle) {
		part = true;
		partAxis = axis;
		partX = pivotX;
		partY = pivotY;
		partZ = pivotZ;
		partCos = Mth.cos(angle);
		partSin = Mth.sin(angle);
	}

	void endPart() {
		part = false;
	}

	void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		float mx = 0.0f, my = 0.0f, mz = 0.0f;
		size[0] = Math.min(16.0f, Math.abs(x1 - x0));
		size[1] = Math.min(16.0f, Math.abs(y1 - y0));
		size[2] = Math.min(16.0f, Math.abs(z1 - z0));
		for (int i = 0; i < 8; i++) {
			float x = (i & 1) != 0 ? x1 : x0;
			float y = (i & 2) != 0 ? y1 : y0;
			float z = (i & 4) != 0 ? z1 : z0;
			if (part) {
				float dx = x - partX, dy = y - partY, dz = z - partZ;
				switch (partAxis) {
					case 0 -> {
						float ny = dy * partCos - dz * partSin;
						dz = dy * partSin + dz * partCos;
						dy = ny;
					}
					case 1 -> {
						float nx = dx * partCos + dz * partSin;
						dz = -dx * partSin + dz * partCos;
						dx = nx;
					}
					default -> {
						float nx = dx * partCos - dy * partSin;
						dy = dx * partSin + dy * partCos;
						dx = nx;
					}
				}
				x = partX + dx;
				y = partY + dy;
				z = partZ + dz;
			}
			x *= scale;
			y *= scale;
			z *= scale;
			float[] c = corners[i];
			c[0] = ox + x * rx + y * ux + z * fx;
			c[1] = oy + x * ry + y * uy + z * fy;
			c[2] = oz + x * rz + y * uz + z * fz;
			mx += c[0];
			my += c[1];
			mz += c[2];
		}
		mx *= 0.125f;
		my *= 0.125f;
		mz *= 0.125f;
		int argb = 0xFF000000 | rgb;
		for (int f = 0; f < 6; f++) {
			int[] face = FACES[f];
			float[] a = corners[face[0]];
			float[] b = corners[face[1]];
			float[] c = corners[face[2]];
			float[] d = corners[face[3]];
			float e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2];
			float e2x = d[0] - a[0], e2y = d[1] - a[1], e2z = d[2] - a[2];
			float nx = e1y * e2z - e1z * e2y;
			float ny = e1z * e2x - e1x * e2z;
			float nz = e1x * e2y - e1y * e2x;
			float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1.0e-9f) {
				continue;
			}
			nx /= len;
			ny /= len;
			nz /= len;
			// Make the normal point away from the box centre, and wind the quad to match.
			float cx = (a[0] + c[0]) * 0.5f - mx, cy = (a[1] + c[1]) * 0.5f - my, cz = (a[2] + c[2]) * 0.5f - mz;
			boolean flip = nx * cx + ny * cy + nz * cz < 0.0f;
			if (flip) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
				for (int k = 3; k >= 0; k--) {
					vertex(face[k], f, argb, nx, ny, nz);
				}
			} else {
				for (int k = 0; k < 4; k++) {
					vertex(face[k], f, argb, nx, ny, nz);
				}
			}
		}
	}

	private void vertex(int corner, int face, int argb, float nx, float ny, float nz) {
		float[] p = corners[corner];
		int ua = FACE_U[face];
		int va = FACE_V[face];
		// Texels are a sixteenth of a tile; a tiny inset keeps neighbouring tiles from bleeding in.
		float u = (corner >> ua & 1) != 0 ? size[ua] : 0.0f;
		float v = (corner >> va & 1) != 0 ? 0.0f : size[va];
		buffer.addVertex(pose, p[0], p[1], p[2]).setColor(argb)
				.setUv(tileU + 0.0005f + u / 64.0f * 0.998f, tileV + 0.0005f + v / 64.0f * 0.998f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
