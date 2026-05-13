package cn.chen.assimp.render
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.BindGroupLayouts
object AIPipelines {
    const val MAX_BONES = 128
    val BONE_MATRICES_LAYOUT: BindGroupLayout = BindGroupLayout.builder()
        .withUniform("BoneMatrices", UniformType.UNIFORM_BUFFER)
        .build()
    val NORMAL_MAP_LAYOUT: BindGroupLayout = BindGroupLayout.builder()
        .withSampler("NormalMap")
        .build()
    val AI_VERTEX_FORMAT: VertexFormat = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("UV1", GpuFormat.RG16_SINT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
        .addAttribute("Tangent", GpuFormat.RGBA8_SNORM)
        .addAttribute("BoneIds", GpuFormat.RGBA8_UINT)
        .addAttribute("BoneWeights", GpuFormat.RGBA8_UNORM)
        .build()
    val AI_SNIPPET: RenderPipeline.Snippet = run {
        val matsSnippet = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .buildSnippet()
        RenderPipeline.builder(matsSnippet)
            .withVertexShader("core/ai_model")
            .withFragmentShader("core/ai_model")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withBindGroupLayout(BONE_MATRICES_LAYOUT)
            .withBindGroupLayout(NORMAL_MAP_LAYOUT)
            .withVertexBinding(0, AI_VERTEX_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .buildSnippet()
    }
    val AI_OPAQUE: RenderPipeline = RenderPipeline.builder(AI_SNIPPET)
        .withLocation("pipeline/ai_model_opaque")
        .withShaderDefine("ALPHA_CUTOUT", 0.01f)
        .withCull(false)
        .build()
    val AI_OPAQUE_CULL: RenderPipeline = RenderPipeline.builder(AI_SNIPPET)
        .withLocation("pipeline/ai_model_opaque_cull")
        .withShaderDefine("ALPHA_CUTOUT", 0.01f)
        .withCull(true)
        .build()
    val AI_TRANSLUCENT: RenderPipeline = RenderPipeline.builder(AI_SNIPPET)
        .withLocation("pipeline/ai_model_translucent")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .build()
    const val AI_VERT_STRIDE = 48
}
