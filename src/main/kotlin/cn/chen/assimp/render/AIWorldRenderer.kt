package cn.chen.assimp.render
import cn.chen.assimp.anim.AIAnimator
import cn.chen.assimp.core.AINodeGraph
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.loader.AIModelLoader
import cn.chen.assimp.math.AIMat4
import cn.chen.assimp.material.AITexType
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.util.*
class AIWorldRenderer {
    private class GpuBatch(
        val vb: GpuBuffer,
        val quadCount: Int,
        val texId: Identifier
    )
    var scene: AISceneData? = null; private set
    var animator: AIAnimator? = null; private set
    var loaded = false; private set
    var pos = floatArrayOf(0f, 0f, 0f)
    var scale = 1f
    var rot = floatArrayOf(0f, 0f, 0f)
    private var lastTime = System.nanoTime()
    private val texIds = mutableMapOf<Int, Identifier>()
    private var gpuBatches = emptyList<GpuBatch>()
    private var totalQuads = 0
    fun load(path: String) {
        if (loaded) unload()
        scene = AIModelLoader.loadFromFile(path)
        loaded = true
        val s = scene!!
        registerTextures(s, File(path).parent ?: "")
        buildGpuBatches(s)
        if (s.hasAnimations) {
            animator = AIAnimator(s)
            animator!!.play(0)
            animator!!.loop = true
        }
    }
    fun unload() {
        for (b in gpuBatches) b.vb.close()
        gpuBatches = emptyList(); totalQuads = 0
        val tm = Minecraft.getInstance().textureManager
        for ((_, id) in texIds) tm.release(id)
        texIds.clear()
        scene = null; animator = null; loaded = false
    }
    fun render(ctx: LevelRenderContext) {
        if (!loaded || gpuBatches.isEmpty()) return
        val now = System.nanoTime()
        val dt = (now - lastTime) / 1e9; lastTime = now
        animator?.update(dt)
        val mc = Minecraft.getInstance()
        val cam = mc.gameRenderer.mainCamera().position()
        val mainTarget = mc.gameRenderer.mainRenderTarget()
        val colorView = mainTarget.colorTextureView ?: return
        val depthView = mainTarget.depthTextureView ?: return
        val modelMat = Matrix4f(RenderSystem.getModelViewStack())
            .translate((pos[0] - cam.x).toFloat(), (pos[1] - cam.y).toFloat(), (pos[2] - cam.z).toFloat())
            .scale(scale)
        if (rot[0] != 0f) modelMat.rotateX(Math.toRadians(rot[0].toDouble()).toFloat())
        if (rot[1] != 0f) modelMat.rotateY(Math.toRadians(rot[1].toDouble()).toFloat())
        if (rot[2] != 0f) modelMat.rotateZ(Math.toRadians(rot[2].toDouble()).toFloat())
        val transformSlice = RenderSystem.getDynamicUniforms().writeTransform(modelMat, Vector4f(1f, 1f, 1f, 1f))
        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()
        val pass = encoder.createRenderPass(
            { "ai_model" }, colorView, Optional.empty(), depthView, OptionalDouble.empty()
        )
        pass.setPipeline(RenderPipelines.ENTITY_CUTOUT)
        RenderSystem.bindDefaultUniforms(pass)
        pass.setUniform("DynamicTransforms", transformSlice)
        val overlayView = mc.gameRenderer.overlayTexture().textureView
        val lightView = mc.gameRenderer.lightmap()
        val lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val quadBuf = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val tm = mc.textureManager
        for (batch in gpuBatches) {
            val tex = tm.getTexture(batch.texId)
            val texView = tex?.textureView ?: continue
            val texSampler = tex.sampler
            pass.bindTexture("Sampler0", texView, texSampler)
            pass.bindTexture("Sampler1", overlayView, texSampler)
            pass.bindTexture("Sampler2", lightView, lightSampler)
            pass.setVertexBuffer(0, batch.vb.slice())
            pass.setIndexBuffer(quadBuf.getBuffer(batch.quadCount), quadBuf.type())
            pass.drawIndexed(0, 0, batch.quadCount * 6, 1)
        }
        pass.close()
        encoder.submit()
    }
    private fun buildGpuBatches(s: AISceneData) {
        for (b in gpuBatches) b.vb.close()
        val device = RenderSystem.getDevice()
        val whiteTex = Identifier.fromNamespaceAndPath("lwjgl-assimp", "textures/white.png")
        val grouped = LinkedHashMap<Identifier, MutableList<FloatArray>>()
        val overlay = OverlayTexture.NO_OVERLAY
        val overlayU = (overlay and 0xFFFF).toShort()
        val overlayV = ((overlay shr 16) and 0xFFFF).toShort()
        val lightU: Short = 0xF0.toShort()
        val lightV: Short = 0xF0.toShort()
        fun walk(node: AINodeGraph, parent: AIMat4) {
            val world = parent * node.transform
            val jm = world.toJoml()
            val nm = Matrix3f(jm)
            val tmp = Vector3f()
            for (mi in node.meshIndices) {
                val mesh = s.meshes[mi]
                val mat = s.materials.getOrNull(mesh.materialIndex)
                val bc = mat?.baseColorFactor
                val br = bc?.x ?: 1f; val bg = bc?.y ?: 1f; val bb = bc?.z ?: 1f; val ba = bc?.w ?: 1f
                val texId = texIds[mesh.materialIndex] ?: whiteTex
                val list = grouped.getOrPut(texId) { mutableListOf() }
                val verts = mesh.vertices
                val indices = mesh.indices
                var i = 0
                while (i + 2 < indices.size) {
                    for (k in 0..3) {
                        val vtx = verts[indices[i + minOf(k, 2)]]
                        jm.transformPosition(tmp.set(vtx.position.x, vtx.position.y, vtx.position.z))
                        val cr = (vtx.color.x * br * 255f).toInt().coerceIn(0, 255)
                        val cg = (vtx.color.y * bg * 255f).toInt().coerceIn(0, 255)
                        val cb = (vtx.color.z * bb * 255f).toInt().coerceIn(0, 255)
                        val ca = (ba * 255f).toInt().coerceIn(0, 255)
                        val tn = Vector3f(vtx.normal.x, vtx.normal.y, vtx.normal.z).mul(nm).normalize()
                        list.add(floatArrayOf(
                            tmp.x, tmp.y, tmp.z,
                            Float.fromBits((ca shl 24) or (cb shl 16) or (cg shl 8) or cr),
                            vtx.texCoord0.x, vtx.texCoord0.y,
                            Float.fromBits((overlayV.toInt() shl 16) or (overlayU.toInt() and 0xFFFF)),
                            Float.fromBits((lightV.toInt() shl 16) or (lightU.toInt() and 0xFFFF)),
                            Float.fromBits(((0).toByte().toInt() shl 24) or ((((tn.z * 127f).toInt().toByte().toInt() and 0xFF) shl 16)) or (((tn.y * 127f).toInt().toByte().toInt() and 0xFF) shl 8) or ((tn.x * 127f).toInt().toByte().toInt() and 0xFF))
                        ))
                    }
                    i += 3
                }
            }
            for (child in node.children) walk(child, world)
        }
        walk(s.rootNode, AIMat4.identity())
        totalQuads = 0
        gpuBatches = grouped.map { (texId, vertList) ->
            val qc = vertList.size / 4
            totalQuads += qc
            val buf = MemoryUtil.memAlloc(vertList.size * VERT_STRIDE)
            for (v in vertList) {
                buf.putFloat(v[0]).putFloat(v[1]).putFloat(v[2])
                buf.putInt(v[3].toRawBits())
                buf.putFloat(v[4]).putFloat(v[5])
                buf.putInt(v[6].toRawBits())
                buf.putInt(v[7].toRawBits())
                buf.putInt(v[8].toRawBits())
            }
            buf.flip()
            val vb = device.createBuffer({ "ai_vb" }, GpuBuffer.USAGE_VERTEX, buf)
            MemoryUtil.memFree(buf)
            GpuBatch(vb, qc, texId)
        }
    }
    private fun registerTextures(s: AISceneData, basePath: String) {
        val tm = Minecraft.getInstance().textureManager
        for ((matIdx, mat) in s.materials.withIndex()) {
            val albedoInfo = mat.getTexture(AITexType.ALBEDO) ?: continue
            val texPath = albedoInfo.path
            try {
                val nativeImg: NativeImage? = if (texPath.startsWith("*")) {
                    val embIdx = texPath.substring(1).toIntOrNull() ?: continue
                    val emb = s.embeddedTextures.getOrNull(embIdx) ?: continue
                    if (emb.height == 0) decodeBytes(emb.data) else decodeRaw(emb.width, emb.height, emb.data)
                } else {
                    val file = File(if (File(texPath).isAbsolute) texPath else "$basePath/$texPath")
                    if (!file.exists()) continue
                    decodeBytes(file.readBytes())
                }
                if (nativeImg == null) continue
                val id = Identifier.fromNamespaceAndPath("lwjgl-assimp", "dynamic/ai_tex_$matIdx")
                tm.register(id, DynamicTexture({ "ai_tex_$matIdx" }, nativeImg))
                texIds[matIdx] = id
            } catch (_: Exception) {}
        }
    }
    fun info(): List<String> {
        val lines = mutableListOf<String>()
        lines.add("loaded=$loaded meshes=${scene?.meshes?.size ?: 0} mats=${scene?.materials?.size ?: 0}")
        lines.add("anims=${scene?.animations?.size ?: 0} bones=${scene?.skeleton?.boneCount ?: 0}")
        lines.add("textures=${texIds.size} batches=${gpuBatches.size} quads=$totalQuads")
        scene?.materials?.forEachIndexed { i, m ->
            lines.add("  mat[$i] ${m.name} tex=${m.textures.keys} registered=${texIds.containsKey(i)}")
        }
        return lines
    }
    fun destroy() { unload() }
    companion object {
        private const val VERT_STRIDE = 36
        fun decodeBytes(data: ByteArray): NativeImage? {
            val direct = MemoryUtil.memAlloc(data.size)
            direct.put(data).flip()
            MemoryStack.stackPush().use { stack ->
                val w = stack.mallocInt(1); val h = stack.mallocInt(1); val ch = stack.mallocInt(1)
                val pixels = stbi_load_from_memory(direct, w, h, ch, 4)
                MemoryUtil.memFree(direct)
                if (pixels == null) return null
                val width = w.get(0); val height = h.get(0)
                val img = NativeImage(NativeImage.Format.RGBA, width, height, false)
                for (y in 0 until height) for (x in 0 until width) {
                    val off = (y * width + x) * 4
                    val r = pixels.get(off).toInt() and 0xFF
                    val g = pixels.get(off + 1).toInt() and 0xFF
                    val b = pixels.get(off + 2).toInt() and 0xFF
                    val a = pixels.get(off + 3).toInt() and 0xFF
                    img.setPixelABGR(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
                }
                stbi_image_free(pixels)
                return img
            }
        }
        fun decodeRaw(w: Int, h: Int, data: ByteArray): NativeImage {
            val img = NativeImage(NativeImage.Format.RGBA, w, h, false)
            for (y in 0 until h) for (x in 0 until w) {
                val off = (y * w + x) * 4
                val r = data[off].toInt() and 0xFF
                val g = data[off + 1].toInt() and 0xFF
                val b = data[off + 2].toInt() and 0xFF
                val a = data[off + 3].toInt() and 0xFF
                img.setPixelABGR(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
            }
            return img
        }
    }
}
