package cn.chen.assimp.render
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderPassDescriptor
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import java.util.OptionalDouble
object AICascadeShadowRenderer {
    fun render(batches: List<AIGpuBatch>, passRanges: Map<AIRenderPass, IntRange>, mergedVbo: GpuBuffer, boneSlice: GpuBufferSlice?, objectSlice: GpuBufferSlice?, shadowSlice: GpuBufferSlice?, shadowMap: AIShadowMap, materialBuffer: AIMaterialBuffer?) {
        val tex = shadowMap.texture() ?: return
        val view = shadowMap.view() ?: return
        val shadow = shadowSlice ?: return
        val obj = objectSlice ?: return
        val tm = Minecraft.getInstance().textureManager
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.clearDepthTexture(tex, 1.0)
        val desc = RenderPassDescriptor.create { "ai_shadow" }.withUnusedColorAttachment().withDepthAttachment(view, OptionalDouble.empty())
        val rp = encoder.createRenderPass(desc)
        var maxQuads = 0
        for (b in batches) if (b.quadCount > maxQuads) maxQuads = b.quadCount
        val quadBuf = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        rp.setVertexBuffer(0, mergedVbo.slice())
        rp.setIndexBuffer(quadBuf.getBuffer(maxQuads), quadBuf.type())
        for (c in 0 until AIShadowBuffer.CASCADES) {
            rp.setPipeline(AIPipelines.SHADOW_CASCADE[c])
            boneSlice?.let { rp.setUniform("BoneMatrices", it) }
            rp.setUniform("ObjectMatrices", obj)
            rp.setUniform("ShadowData", shadow)
            drawRange(rp, batches, passRanges[AIRenderPass.PBR_OPAQUE_CULL], materialBuffer, tm)
            drawRange(rp, batches, passRanges[AIRenderPass.PBR_OPAQUE], materialBuffer, tm)
        }
        rp.close()
        encoder.submit()
    }
    private fun drawRange(rp: RenderPass, batches: List<AIGpuBatch>, range: IntRange?, materialBuffer: AIMaterialBuffer?, tm: TextureManager) {
        val r = range ?: return
        if (r.isEmpty()) return
        var lastTex: Identifier? = null
        var lastMat = -1
        for (i in r) {
            val batch = batches[i]
            if (batch.texId != lastTex) {
                val t = tm.getTexture(batch.texId) ?: continue
                rp.bindTexture("Sampler0", t.textureView, t.sampler)
                lastTex = batch.texId
            }
            if (batch.matIdx != lastMat) {
                materialBuffer?.let { rp.setUniform("MaterialFactors", it.slice(batch.matIdx)) }
                lastMat = batch.matIdx
            }
            rp.drawIndexed(batch.baseVertex, 0, batch.quadCount * 6, 1, 0)
        }
    }
}
