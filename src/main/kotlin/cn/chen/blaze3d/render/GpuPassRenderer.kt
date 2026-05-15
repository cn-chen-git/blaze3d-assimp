package cn.chen.blaze3d.render
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.Optional
import java.util.OptionalDouble
object GpuPassRenderer {
    private val tmpCenter = Vector3f()
    private val passNames by lazy { PASS_ORDER.associateWith { "model_${it.name}" } }
    fun render(
        batches: List<GpuBatch>,
        passRanges: Map<RenderPass, IntRange>,
        mergedVbo: GpuBuffer,
        modelMat: Matrix4f,
        objectMat: Matrix4f,
        camPos: Vector3f,
        camLook: Vector3f,
        scale: Float,
        tint: Vector4f,
        boneSlice: GpuBufferSlice?
    ) {
        val mc = Minecraft.getInstance()
        val mainTarget = mc.gameRenderer.mainRenderTarget()
        val colorView = mainTarget.colorTextureView ?: return
        val depthView = mainTarget.depthTextureView ?: return
        val transformSlice = RenderSystem.getDynamicUniforms().writeTransform(modelMat, tint)
        val overlayView = mc.gameRenderer.overlayTexture().textureView
        val lightView = mc.gameRenderer.lightmap()
        val lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val tm = mc.textureManager
        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()
        val vboSlice = mergedVbo.slice()
        var maxQuads = 0
        for (b in batches) if (b.quadCount > maxQuads) maxQuads = b.quadCount
        val quadBuf = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val idxBuf = quadBuf.getBuffer(maxQuads)
        val idxType = quadBuf.type()
        for (pass in PASS_ORDER) {
            val range = passRanges[pass] ?: continue
            if (range.isEmpty()) continue
            val passName = passNames[pass] ?: pass.name
            val rp = encoder.createRenderPass({ passName }, colorView, Optional.empty(), depthView, OptionalDouble.empty())
            rp.setPipeline(pass.pipeline())
            RenderSystem.bindDefaultUniforms(rp)
            rp.setUniform("DynamicTransforms", transformSlice)
            if (pass.isAi) {
                val bones = boneSlice
                if (bones == null) { rp.close(); continue }
                rp.setUniform("BoneMatrices", bones)
            }
            rp.setVertexBuffer(0, vboSlice)
            rp.setIndexBuffer(idxBuf, idxType)
            var lastTex: Identifier? = null
            var lastNorm: Identifier? = null
            for (i in range) {
                val batch = batches[i]
                if (!isBatchVisible(batch, objectMat, camPos, camLook, scale)) continue
                if (batch.texId != lastTex) {
                    val tex = tm.getTexture(batch.texId) ?: continue
                    rp.bindTexture("Sampler0", tex.textureView, tex.sampler)
                    rp.bindTexture("Sampler1", overlayView, tex.sampler)
                    rp.bindTexture("Sampler2", lightView, lightSampler)
                    lastTex = batch.texId
                }
                if (pass.isAi) {
                    if (batch.normalMapId != lastNorm) {
                        batch.normalMapId?.let { id -> tm.getTexture(id)?.let { t -> rp.bindTexture("NormalMap", t.textureView, t.sampler) } }
                        lastNorm = batch.normalMapId
                    }
                }
                rp.drawIndexed(batch.quadCount * 6, 1, 0, batch.baseVertex, 0)
            }
            rp.close()
        }
        encoder.submit()
    }
    private fun isBatchVisible(batch: GpuBatch, objectMat: Matrix4f, camPos: Vector3f, camLook: Vector3f, scale: Float): Boolean {
        tmpCenter.set(batch.aabbCenter)
        objectMat.transformPosition(tmpCenter)
        val dx = tmpCenter.x - camPos.x; val dy = tmpCenter.y - camPos.y; val dz = tmpCenter.z - camPos.z
        val dist2 = dx * dx + dy * dy + dz * dz
        val sr = batch.aabbRadius * scale
        val r = sr + BATCH_CULL_PADDING
        if (dist2 > r * r) return false
        val forward = dx * camLook.x + dy * camLook.y + dz * camLook.z
        if (forward < -(sr + BEHIND_CAM_PADDING)) return false
        return true
    }
    private val PASS_ORDER = arrayOf(
        RenderPass.OPAQUE_CULL, RenderPass.OPAQUE, RenderPass.EMISSIVE, RenderPass.TRANSLUCENT,
        RenderPass.AI_OUTLINE, RenderPass.AI_OPAQUE_CULL, RenderPass.AI_OPAQUE, RenderPass.AI_TRANSLUCENT
    )
    private val RenderPass.isAi get() = this == RenderPass.AI_OPAQUE || this == RenderPass.AI_OPAQUE_CULL || this == RenderPass.AI_TRANSLUCENT || this == RenderPass.AI_OUTLINE
    private const val BATCH_CULL_PADDING = 64.0f
    private const val BEHIND_CAM_PADDING = 4.0f
}
