package cn.chen.assimp.render
import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines
enum class AIRenderPass(val order: Int, val pipeline: () -> RenderPipeline) {
    OPAQUE(0, { RenderPipelines.ENTITY_CUTOUT }),
    OPAQUE_CULL(1, { RenderPipelines.ENTITY_CUTOUT_CULL }),
    EMISSIVE(2, { RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE }),
    TRANSLUCENT(3, { RenderPipelines.ENTITY_TRANSLUCENT }),
    AI_OPAQUE(4, { AIPipelines.AI_OPAQUE }),
    AI_OPAQUE_CULL(5, { AIPipelines.AI_OPAQUE_CULL }),
    AI_TRANSLUCENT(6, { AIPipelines.AI_TRANSLUCENT });
}
