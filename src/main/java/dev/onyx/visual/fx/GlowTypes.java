package dev.onyx.visual.fx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.onyx.visual.OnyxVisual;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Additive "light" render types. They test against depth but never write it, so glowing
 * geometry cannot punch holes into clouds, water or other glow drawn behind it.
 */
final class GlowTypes {
	// Same depth comparison the game uses, minus the depth write.
	private static final DepthStencilState TEST_ONLY = new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false);

	private static final RenderPipeline QUADS_PIPELINE = colored("pipeline/glow", PrimitiveTopology.QUADS);
	private static final RenderPipeline TRIANGLES_PIPELINE = colored("pipeline/glow_triangles", PrimitiveTopology.TRIANGLES);

	private static final RenderPipeline TEXTURED_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(OnyxVisual.id("pipeline/glow_textured"))
			.withVertexShader("core/entity")
			.withFragmentShader("core/entity")
			.withShaderDefine("EMISSIVE")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withVertexBinding(0, DefaultVertexFormat.ENTITY)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(TEST_ONLY)
			.withCull(false)
			.build();

	private static RenderPipeline colored(String location, PrimitiveTopology topology) {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(OnyxVisual.id(location))
				.withVertexShader("core/rendertype_lightning")
				.withFragmentShader("core/rendertype_lightning")
				.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
				.withPrimitiveTopology(topology)
				.withDepthStencilState(TEST_ONLY)
				.withCull(false)
				.build();
	}

	static final RenderType QUADS = RenderType.create("onyxvisual_glow", RenderSetup.builder(QUADS_PIPELINE).createRenderSetup());
	static final RenderType TRIANGLES = RenderType.create("onyxvisual_glow_triangles", RenderSetup.builder(TRIANGLES_PIPELINE).createRenderSetup());

	private static final Map<Identifier, RenderType> TEXTURED = new ConcurrentHashMap<>();

	private GlowTypes() {
	}

	static RenderType textured(Identifier texture) {
		return TEXTURED.computeIfAbsent(texture, id -> RenderType.create("onyxvisual_glow_textured",
				RenderSetup.builder(TEXTURED_PIPELINE).withTexture("Sampler0", id).createRenderSetup()));
	}
}
