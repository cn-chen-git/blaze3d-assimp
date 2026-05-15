package cn.chen.blaze3d.render
import cn.chen.blaze3d.core.NodeGraph
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.material.AlphaMode
import cn.chen.blaze3d.math.Mat4
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix3f
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class BatchCompiler(private val texReg: TextureRegistry) {
    class Result(
        val batches: List<GpuBatch>,
        val passRanges: Map<RenderPass, IntRange>,
        val mergedVbo: GpuBuffer,
        val boundCenter: Vector3f,
        val boundRadius: Float,
        val totalQuads: Int,
        val hasSkinning: Boolean
    ) { fun close() = mergedVbo.close() }
    private data class BatchKey(val texId: Identifier, val normalId: Identifier?, val pass: RenderPass)
    private class CollectedMesh(val key: BatchKey, val usesAiVertexFormat: Boolean) {
        var buf: ByteBuffer = MemoryUtil.memAlloc(INIT_BUF)
        val aabbMin = Vector3f(Float.MAX_VALUE)
        val aabbMax = Vector3f(-Float.MAX_VALUE)
        var vertexCount = 0
        fun expandBound(x: Float, y: Float, z: Float) {
            if (x < aabbMin.x) aabbMin.x = x; if (y < aabbMin.y) aabbMin.y = y; if (z < aabbMin.z) aabbMin.z = z
            if (x > aabbMax.x) aabbMax.x = x; if (y > aabbMax.y) aabbMax.y = y; if (z > aabbMax.z) aabbMax.z = z
        }
        fun ensure(n: Int) {
            if (buf.remaining() >= n) return
            var cap = buf.capacity()
            while (cap - buf.position() < n) cap *= 2
            val nb = MemoryUtil.memAlloc(cap)
            buf.flip(); nb.put(buf); MemoryUtil.memFree(buf); buf = nb
        }
        companion object { const val INIT_BUF = 4096 }
    }
    private val tmp = Vector3f(); private val tn = Vector3f(); private val tt = Vector3f(); private val tb = Vector3f(); private val tc = Vector3f()
    private val nm = Matrix3f()
    private lateinit var scene: SceneData
    private var overlayPacked: Int = 0
    private val globalMin = Vector3f(); private val globalMax = Vector3f()
    private val grouped = LinkedHashMap<BatchKey, CollectedMesh>()
    fun compile(s: SceneData): Result {
        scene = s
        val hasBones = (s.skeleton?.boneCount ?: 0) > 0
        grouped.clear()
        globalMin.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        globalMax.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val overlay = OverlayTexture.NO_OVERLAY
        overlayPacked = (((overlay shr 16) and 0xFFFF) shl 16) or (overlay and 0xFFFF)
        walk(s.rootNode, Mat4.identity())
        val sorted = grouped.values.sortedBy { it.key.pass.order }
        var totalBytes = 0
        for (cm in sorted) totalBytes += cm.buf.position()
        val mergedBuf = MemoryUtil.memAlloc(totalBytes)
        var vertOffset = 0; var totalQuads = 0
        val batches = ArrayList<GpuBatch>(sorted.size)
        for (cm in sorted) {
            val qc = cm.vertexCount / 4
            totalQuads += qc
            val base = vertOffset
            cm.buf.flip()
            mergedBuf.put(cm.buf)
            MemoryUtil.memFree(cm.buf)
            vertOffset += cm.vertexCount
            batches.add(GpuBatch(base, qc, cm.key.texId, cm.key.normalId, cm.key.pass, cm.aabbMin, cm.aabbMax))
        }
        mergedBuf.flip()
        val mergedVbo = RenderSystem.getDevice().createBuffer({ "model_merged_vbo" }, GpuBuffer.USAGE_VERTEX, mergedBuf)
        MemoryUtil.memFree(mergedBuf)
        val passRanges = buildPassRanges(batches)
        val center = Vector3f(globalMin).add(globalMax).mul(0.5f)
        val radius = Vector3f(globalMax).sub(globalMin).length() * 0.5f
        return Result(batches, passRanges, mergedVbo, center, radius, totalQuads, hasBones)
    }
    private fun walk(node: NodeGraph, parent: Mat4) {
        val world = parent * node.transform
        val jm = world.toJoml()
        nm.set(jm)
        for (mi in node.meshIndices) {
            val mesh = scene.meshes[mi]
            val mIdx = mesh.materialIndex
            val mat = scene.materials.getOrNull(mIdx)
            val bc = mat?.baseColorFactor
            val br = bc?.x ?: 1f; val bg = bc?.y ?: 1f; val bb = bc?.z ?: 1f; val ba = bc?.w ?: 1f
            val ds = mat?.doubleSided ?: false
            val useAi = mesh.hasBones || texReg.hasNormal(mIdx)
            val translucent = ((mat?.khrExtensions?.transmission?.factor ?: 0f) > 0.01f) || ba < 0.99f || (mat?.alphaMode == AlphaMode.BLEND && texReg.hasAlphaPixels(mIdx))
            val pass = when {
                translucent -> if (useAi) RenderPass.AI_TRANSLUCENT else RenderPass.TRANSLUCENT
                ds -> if (useAi) RenderPass.AI_OPAQUE else RenderPass.OPAQUE
                else -> if (useAi) RenderPass.AI_OPAQUE_CULL else RenderPass.OPAQUE_CULL
            }
            val texId = texReg[mIdx]
            val normalId = if (useAi) texReg.getNormal(mIdx) else null
            val key = BatchKey(texId, normalId, pass)
            val cm = grouped.getOrPut(key) { CollectedMesh(key, useAi) }
            val outlineCm = if (useAi && ds) {
                val ok = BatchKey(texId, normalId, RenderPass.AI_OUTLINE)
                grouped.getOrPut(ok) { CollectedMesh(ok, useAi) }
            } else null
            val verts = mesh.vertices; val indices = mesh.indices
            val stride = if (useAi) AI_VERT_STRIDE else STD_VERT_STRIDE
            var i = 0
            while (i + 2 < indices.size) {
                for (k in 0..3) {
                    val vtx = verts[indices[i + minOf(k, 2)]]
                    jm.transformPosition(tmp.set(vtx.position.x, vtx.position.y, vtx.position.z))
                    val tx = tmp.x; val ty = tmp.y; val tz = tmp.z
                    if (tx < globalMin.x) globalMin.x = tx; if (ty < globalMin.y) globalMin.y = ty; if (tz < globalMin.z) globalMin.z = tz
                    if (tx > globalMax.x) globalMax.x = tx; if (ty > globalMax.y) globalMax.y = ty; if (tz > globalMax.z) globalMax.z = tz
                    cm.expandBound(tx, ty, tz)
                    val cr = (vtx.color.x * br * 255f).toInt().coerceIn(0, 255)
                    val cg = (vtx.color.y * bg * 255f).toInt().coerceIn(0, 255)
                    val cb2 = (vtx.color.z * bb * 255f).toInt().coerceIn(0, 255)
                    val ca = (vtx.color.w * ba * 255f).toInt().coerceIn(0, 255)
                    val colorPacked = (ca shl 24) or (cb2 shl 16) or (cg shl 8) or cr
                    tn.set(vtx.normal.x, vtx.normal.y, vtx.normal.z).mul(nm).normalize()
                    val nPack = packSnorm4(tn.x, tn.y, tn.z, 0f)
                    if (useAi) {
                        tt.set(vtx.tangent.x, vtx.tangent.y, vtx.tangent.z).mul(nm).normalize()
                        tb.set(vtx.bitangent.x, vtx.bitangent.y, vtx.bitangent.z).mul(nm)
                        val handedness = if (tc.set(tn).cross(tt).dot(tb) < 0f) -1f else 1f
                        val tPack = packSnorm4(tt.x, tt.y, tt.z, handedness)
                        val bIds = packBoneIds(vtx.boneIds); val bW = packBoneWeights(vtx.boneWeights)
                        cm.ensure(stride)
                        writeAiVert(cm.buf, tx, ty, tz, colorPacked, vtx.texCoord0.x, vtx.texCoord0.y, overlayPacked, nPack, tPack, bIds, bW)
                        cm.vertexCount++
                        if (outlineCm != null) {
                            outlineCm.expandBound(tx, ty, tz)
                            outlineCm.ensure(stride)
                            writeAiVert(outlineCm.buf, tx, ty, tz, colorPacked, vtx.texCoord0.x, vtx.texCoord0.y, overlayPacked, nPack, tPack, bIds, bW)
                            outlineCm.vertexCount++
                        }
                    } else {
                        cm.ensure(stride)
                        writeStdVert(cm.buf, tx, ty, tz, colorPacked, vtx.texCoord0.x, vtx.texCoord0.y, overlayPacked, nPack)
                        cm.vertexCount++
                    }
                }
                i += 3
            }
        }
        for (child in node.children) walk(child, world)
    }
    companion object {
        private const val STD_VERT_STRIDE = 36
        private const val AI_VERT_STRIDE = 48
        private fun writeAiVert(b: ByteBuffer, x: Float, y: Float, z: Float, color: Int, u: Float, v: Float, overlay: Int, normal: Int, tangent: Int, ids: Int, w: Int) {
            b.putFloat(x).putFloat(y).putFloat(z).putInt(color).putFloat(u).putFloat(v).putInt(overlay).putInt(0xF000F0).putInt(normal).putInt(tangent).putInt(ids).putInt(w)
        }
        private fun writeStdVert(b: ByteBuffer, x: Float, y: Float, z: Float, color: Int, u: Float, v: Float, overlay: Int, normal: Int) {
            b.putFloat(x).putFloat(y).putFloat(z).putInt(color).putFloat(u).putFloat(v).putInt(overlay).putInt(0xF000F0).putInt(normal)
        }
        private fun packSnorm4(x: Float, y: Float, z: Float, w: Float): Int {
            val bx = (x * 127f).toInt().toByte().toInt() and 0xFF
            val by = (y * 127f).toInt().toByte().toInt() and 0xFF
            val bz = (z * 127f).toInt().toByte().toInt() and 0xFF
            val bw = (w * 127f).toInt().toByte().toInt() and 0xFF
            return (bw shl 24) or (bz shl 16) or (by shl 8) or bx
        }
        private fun packBoneIds(ids: IntArray): Int {
            val a = ids[0].coerceAtLeast(0) and 0xFF; val b = ids[1].coerceAtLeast(0) and 0xFF
            val c = ids[2].coerceAtLeast(0) and 0xFF; val d = ids[3].coerceAtLeast(0) and 0xFF
            return (d shl 24) or (c shl 16) or (b shl 8) or a
        }
        private fun packBoneWeights(w: FloatArray): Int {
            val a = (w[0] * 255f).toInt().coerceIn(0, 255) and 0xFF; val b = (w[1] * 255f).toInt().coerceIn(0, 255) and 0xFF
            val c = (w[2] * 255f).toInt().coerceIn(0, 255) and 0xFF; val d = (w[3] * 255f).toInt().coerceIn(0, 255) and 0xFF
            return (d shl 24) or (c shl 16) or (b shl 8) or a
        }
        private fun buildPassRanges(batches: List<GpuBatch>): Map<RenderPass, IntRange> {
            val ranges = mutableMapOf<RenderPass, IntRange>()
            var start = 0; var currentPass: RenderPass? = null
            for ((i, b) in batches.withIndex()) {
                if (b.pass != currentPass) {
                    if (currentPass != null) ranges[currentPass] = start until i
                    currentPass = b.pass; start = i
                }
            }
            if (currentPass != null) ranges[currentPass] = start until batches.size
            return ranges
        }
    }
}
