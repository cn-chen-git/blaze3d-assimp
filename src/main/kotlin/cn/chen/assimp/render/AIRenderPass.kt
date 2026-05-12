package cn.chen.assimp.render
import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines
enum class AIRenderPass(val order: Int, val pipeline: () -> RenderPipeline, val isPbr: Boolean) {
    OPAQUE(0, { RenderPipelines.ENTITY_CUTOUT }, false),
    OPAQUE_CULL(1, { RenderPipelines.ENTITY_CUTOUT_CULL }, false),
    EMISSIVE(2, { RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE }, false),
    TRANSLUCENT(3, { RenderPipelines.ENTITY_TRANSLUCENT }, false),
    PBR_OPAQUE(4, { AIPipelines.PBR_OPAQUE }, true),
    PBR_OPAQUE_CULL(5, { AIPipelines.PBR_OPAQUE_CULL }, true),
    PBR_TRANSLUCENT(6, { AIPipelines.PBR_TRANSLUCENT }, true);
}
