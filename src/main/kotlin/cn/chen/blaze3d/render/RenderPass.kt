package cn.chen.blaze3d.render
import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines
enum class RenderPass(val order: Int, val pipeline: () -> RenderPipeline) {
    OPAQUE(0, { RenderPipelines.ENTITY_CUTOUT }),
    OPAQUE_CULL(1, { RenderPipelines.ENTITY_CUTOUT_CULL }),
    EMISSIVE(2, { RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE }),
    TRANSLUCENT(3, { RenderPipelines.ENTITY_TRANSLUCENT }),
    AI_OPAQUE(4, { Pipelines.AI_OPAQUE }),
    AI_OPAQUE_CULL(5, { Pipelines.AI_OPAQUE_CULL }),
    AI_TRANSLUCENT(6, { Pipelines.AI_TRANSLUCENT }),
    AI_OUTLINE(7, { Pipelines.AI_OUTLINE });
}
