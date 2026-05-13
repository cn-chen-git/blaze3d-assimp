package cn.chen.assimp.render
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.Optional
import java.util.OptionalDouble
object AIGpuPassRenderer {
    private val tmpCenter = Vector3f()
    private val WHITE_ID = Identifier.fromNamespaceAndPath("lwjgl-assimp", "textures/white.png")
    fun render(
        batches: List<AIGpuBatch>,
        passRanges: Map<AIRenderPass, IntRange>,
        mergedVbo: GpuBuffer,
        modelMat: Matrix4f,
        objectMat: Matrix4f,
        camPos: Vector3f,
        camLook: Vector3f,
        scale: Float,
        boneSlice: GpuBufferSlice?,
        objectSlice: GpuBufferSlice?,
        shadowSlice: GpuBufferSlice?,
        shadowView: GpuTextureView?,
        environmentMap: AIEnvironmentMap?,
        materialBuffer: AIMaterialBuffer?,
        lightsSlice: GpuBufferSlice?,
        worldProbe: AIWorldProbe?
    ) {
        val mc = Minecraft.getInstance()
        val mainTarget = mc.gameRenderer.mainRenderTarget()
        val colorView = mainTarget.colorTextureView ?: return
        val depthView = mainTarget.depthTextureView ?: return
        val transformSlice = RenderSystem.getDynamicUniforms().writeTransform(modelMat, Vector4f(1f, 1f, 1f, 1f))
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
            val rp = encoder.createRenderPass({ "ai_${pass.name}" }, colorView, Optional.empty(), depthView, OptionalDouble.empty())
            rp.setPipeline(pass.pipeline())
            RenderSystem.bindDefaultUniforms(rp)
            rp.setUniform("DynamicTransforms", transformSlice)
            if (pass.isPbr && boneSlice != null) rp.setUniform("BoneMatrices", boneSlice)
            if (pass.isPbr && objectSlice != null) rp.setUniform("ObjectMatrices", objectSlice)
            if (pass.isPbr && shadowSlice != null) rp.setUniform("ShadowData", shadowSlice)
            if (pass.isPbr && lightsSlice != null) rp.setUniform("DynamicLights", lightsSlice)
            if (pass.isPbr) worldProbe?.let { wp -> wp.uboSlice()?.let { rp.setUniform("WorldProbe", it) }; wp.bind(rp) }
            rp.setVertexBuffer(0, vboSlice)
            rp.setIndexBuffer(idxBuf, idxType)
            if (pass.isPbr) {
                if (shadowView != null) rp.bindTexture("ShadowMap", shadowView, lightSampler)
                else tm.getTexture(WHITE_ID)?.let { t -> rp.bindTexture("ShadowMap", t.textureView, lightSampler) }
                environmentMap?.bind(rp)
            }
            var lastTex: Identifier? = null
            var lastNorm: Identifier? = null
            var lastMR: Identifier? = null
            var lastEmissive: Identifier? = null
            var lastMat = -1
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
                if (pass.isPbr) {
                    if (batch.matIdx != lastMat) {
                        materialBuffer?.let { rp.setUniform("MaterialFactors", it.slice(batch.matIdx)) }
                        lastMat = batch.matIdx
                    }
                    if (batch.normalMapId != lastNorm) {
                        batch.normalMapId?.let { id -> tm.getTexture(id)?.let { t -> rp.bindTexture("NormalMap", t.textureView, t.sampler) } }
                        lastNorm = batch.normalMapId
                    }
                    if (batch.mrMapId != lastMR) {
                        batch.mrMapId?.let { id -> tm.getTexture(id)?.let { t -> rp.bindTexture("MetallicRoughnessMap", t.textureView, t.sampler) } }
                        lastMR = batch.mrMapId
                    }
                    if (batch.emissiveMapId != lastEmissive) {
                        batch.emissiveMapId?.let { id -> tm.getTexture(id)?.let { t -> rp.bindTexture("EmissiveMap", t.textureView, t.sampler) } }
                        lastEmissive = batch.emissiveMapId
                    }
                }
                rp.drawIndexed(batch.quadCount * 6, 1, 0, batch.baseVertex, 0)
            }
            rp.close()
        }
        encoder.submit()
    }
    private fun isBatchVisible(batch: AIGpuBatch, objectMat: Matrix4f, camPos: Vector3f, camLook: Vector3f, scale: Float): Boolean {
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
        AIRenderPass.OPAQUE_CULL, AIRenderPass.OPAQUE, AIRenderPass.EMISSIVE, AIRenderPass.TRANSLUCENT,
        AIRenderPass.PBR_OPAQUE_CULL, AIRenderPass.PBR_OPAQUE, AIRenderPass.PBR_TRANSLUCENT
    )
    private const val BATCH_CULL_PADDING = 64.0f
    private const val BEHIND_CAM_PADDING = 4.0f
}
