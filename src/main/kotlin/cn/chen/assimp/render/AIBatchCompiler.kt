package cn.chen.assimp.render
import cn.chen.assimp.core.AINodeGraph
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.material.AIAlphaMode
import cn.chen.assimp.material.AITexType
import cn.chen.assimp.math.AIMat4
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix3f
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class AIBatchCompiler(private val texReg: AITextureRegistry) {
    class Result(
        val batches: List<AIGpuBatch>,
        val passRanges: Map<AIRenderPass, IntRange>,
        val mergedVbo: GpuBuffer,
        val boundCenter: Vector3f,
        val boundRadius: Float,
        val totalQuads: Int,
        val hasSkinning: Boolean
    ) {
        fun close() = mergedVbo.close()
    }
    private data class BatchKey(val texId: Identifier, val normalId: Identifier?, val mrId: Identifier?, val emissiveId: Identifier?, val pass: AIRenderPass, val doubleSided: Boolean, val matIdx: Int)
    private class CollectedMesh(val key: BatchKey, val isPbr: Boolean) {
        val verts = mutableListOf<FloatArray>()
        val aabbMin = Vector3f(Float.MAX_VALUE)
        val aabbMax = Vector3f(-Float.MAX_VALUE)
        fun expandBound(v: Vector3f) { aabbMin.min(v); aabbMax.max(v) }
    }
    fun compile(s: AISceneData): Result {
        val hasBones = (s.skeleton?.boneCount ?: 0) > 0
        val grouped = LinkedHashMap<BatchKey, CollectedMesh>()
        val overlay = OverlayTexture.NO_OVERLAY
        val overlayU = (overlay and 0xFFFF).toShort()
        val overlayV = ((overlay shr 16) and 0xFFFF).toShort()
        val lightBits = Float.fromBits((0xF0.toShort().toInt() shl 16) or (0xF0.toShort().toInt() and 0xFFFF))
        val overlayBits = Float.fromBits((overlayV.toInt() shl 16) or (overlayU.toInt() and 0xFFFF))
        val globalMin = Vector3f(Float.MAX_VALUE); val globalMax = Vector3f(-Float.MAX_VALUE)
        fun walk(node: AINodeGraph, parent: AIMat4) {
            val world = parent * node.transform
            val jm = world.toJoml()
            val nm = Matrix3f(jm)
            val tmp = Vector3f()
            for (mi in node.meshIndices) {
                val mesh = s.meshes[mi]
                val mIdx = mesh.materialIndex
                val mat = s.materials.getOrNull(mIdx)
                val bc = mat?.baseColorFactor
                val br = bc?.x ?: 1f; val bg = bc?.y ?: 1f; val bb = bc?.z ?: 1f; val ba = bc?.w ?: 1f
                val ds = mat?.doubleSided ?: false
                val usePbr = true
                val hasTransmission = (mat?.khrExtensions?.transmission?.factor ?: 0f) > 0.01f
                val texHasAlpha = texReg.hasAlphaPixels(mIdx)
                val blendNeedsAlpha = mat?.alphaMode == AIAlphaMode.BLEND && texHasAlpha
                val actuallyTranslucent = hasTransmission || ba < 0.99f || blendNeedsAlpha
                val pass = when {
                    actuallyTranslucent -> if (usePbr) AIRenderPass.PBR_TRANSLUCENT else AIRenderPass.TRANSLUCENT
                    ds -> if (usePbr) AIRenderPass.PBR_OPAQUE else AIRenderPass.OPAQUE
                    else -> if (usePbr) AIRenderPass.PBR_OPAQUE_CULL else AIRenderPass.OPAQUE_CULL
                }
                val key = BatchKey(
                    texReg[mIdx],
                    if (usePbr) texReg.getNormal(mIdx) else null,
                    if (usePbr) texReg.getMR(mIdx) else null,
                    if (usePbr) texReg.getEmissive(mIdx) else null,
                    pass, ds, mIdx
                )
                val cm = grouped.getOrPut(key) { CollectedMesh(key, usePbr) }
                val verts = mesh.vertices; val indices = mesh.indices
                var i = 0
                while (i + 2 < indices.size) {
                    for (k in 0..3) {
                        val vtx = verts[indices[i + minOf(k, 2)]]
                        jm.transformPosition(tmp.set(vtx.position.x, vtx.position.y, vtx.position.z))
                        globalMin.min(tmp); globalMax.max(tmp); cm.expandBound(tmp)
                        val cr = (vtx.color.x * (if (usePbr) 1f else br) * 255f).toInt().coerceIn(0, 255)
                        val cg = (vtx.color.y * (if (usePbr) 1f else bg) * 255f).toInt().coerceIn(0, 255)
                        val cb = (vtx.color.z * (if (usePbr) 1f else bb) * 255f).toInt().coerceIn(0, 255)
                        val ca = (vtx.color.w * (if (usePbr) 1f else ba) * 255f).toInt().coerceIn(0, 255)
                        val tn = Vector3f(vtx.normal.x, vtx.normal.y, vtx.normal.z).mul(nm).normalize()
                        if (usePbr) {
                            val tt = Vector3f(vtx.tangent.x, vtx.tangent.y, vtx.tangent.z).mul(nm).normalize()
                            val handedness = if (Vector3f(tn).cross(tt).dot(Vector3f(vtx.bitangent.x, vtx.bitangent.y, vtx.bitangent.z).mul(nm)) < 0f) -1f else 1f
                            cm.verts.add(floatArrayOf(
                                tmp.x, tmp.y, tmp.z,
                                Float.fromBits((ca shl 24) or (cb shl 16) or (cg shl 8) or cr),
                                vtx.texCoord0.x, vtx.texCoord0.y,
                                overlayBits, lightBits,
                                Float.fromBits(packSnorm4(tn.x, tn.y, tn.z, 0f)),
                                Float.fromBits(packSnorm4(tt.x, tt.y, tt.z, handedness)),
                                Float.fromBits(packBoneIds(vtx.boneIds)),
                                Float.fromBits(packBoneWeights(vtx.boneWeights))
                            ))
                        } else {
                            cm.verts.add(floatArrayOf(
                                tmp.x, tmp.y, tmp.z,
                                Float.fromBits((ca shl 24) or (cb shl 16) or (cg shl 8) or cr),
                                vtx.texCoord0.x, vtx.texCoord0.y,
                                overlayBits, lightBits,
                                Float.fromBits(packSnorm4(tn.x, tn.y, tn.z, 0f))
                            ))
                        }
                    }
                    i += 3
                }
            }
            for (child in node.children) walk(child, world)
        }
        walk(s.rootNode, AIMat4.identity())
        val sorted = grouped.values.sortedBy { it.key.pass.order }
        val device = RenderSystem.getDevice()
        var totalVerts = 0
        for (cm in sorted) totalVerts += cm.verts.size
        val mergedBuf = MemoryUtil.memAlloc(totalVerts * PBR_VERT_STRIDE)
        var vertOffset = 0; var totalQuads = 0
        val batches = sorted.map { cm ->
            val qc = cm.verts.size / 4
            totalQuads += qc
            val base = vertOffset
            for (v in cm.verts) writeVert(mergedBuf, v, cm.isPbr)
            vertOffset += cm.verts.size
            AIGpuBatch(base, qc, cm.key.texId, cm.key.normalId, cm.key.mrId, cm.key.emissiveId, cm.key.pass, cm.key.doubleSided, cm.key.matIdx, cm.aabbMin, cm.aabbMax)
        }
        mergedBuf.flip()
        val mergedVbo = device.createBuffer({ "ai_merged_vbo" }, GpuBuffer.USAGE_VERTEX, mergedBuf)
        MemoryUtil.memFree(mergedBuf)
        val passRanges = buildPassRanges(batches)
        val center = Vector3f(globalMin).add(globalMax).mul(0.5f)
        val radius = Vector3f(globalMax).sub(globalMin).length() * 0.5f
        return Result(batches, passRanges, mergedVbo, center, radius, totalQuads, hasBones)
    }
    companion object {
        private const val STD_VERT_STRIDE = 36
        private const val PBR_VERT_STRIDE = 48
        private fun writeVert(buf: ByteBuffer, v: FloatArray, pbr: Boolean) {
            buf.putFloat(v[0]).putFloat(v[1]).putFloat(v[2])
            buf.putInt(v[3].toRawBits())
            buf.putFloat(v[4]).putFloat(v[5])
            buf.putInt(v[6].toRawBits())
            buf.putInt(v[7].toRawBits())
            buf.putInt(v[8].toRawBits())
            if (pbr) {
                buf.putInt(v[9].toRawBits())
                buf.putInt(v[10].toRawBits())
                buf.putInt(v[11].toRawBits())
            }
        }
        private fun packSnorm4(x: Float, y: Float, z: Float, w: Float): Int {
            val bx = (x * 127f).toInt().toByte().toInt() and 0xFF
            val by = (y * 127f).toInt().toByte().toInt() and 0xFF
            val bz = (z * 127f).toInt().toByte().toInt() and 0xFF
            val bw = (w * 127f).toInt().toByte().toInt() and 0xFF
            return (bw shl 24) or (bz shl 16) or (by shl 8) or bx
        }
        private fun packBoneIds(ids: IntArray): Int {
            val a = ids[0].coerceIn(0, 255) and 0xFF
            val b = ids[1].coerceIn(0, 255) and 0xFF
            val c = ids[2].coerceIn(0, 255) and 0xFF
            val d = ids[3].coerceIn(0, 255) and 0xFF
            return (d shl 24) or (c shl 16) or (b shl 8) or a
        }
        private fun packBoneWeights(w: FloatArray): Int {
            val a = (w[0] * 255f).toInt().coerceIn(0, 255) and 0xFF
            val b = (w[1] * 255f).toInt().coerceIn(0, 255) and 0xFF
            val c = (w[2] * 255f).toInt().coerceIn(0, 255) and 0xFF
            val d = (w[3] * 255f).toInt().coerceIn(0, 255) and 0xFF
            return (d shl 24) or (c shl 16) or (b shl 8) or a
        }
        private fun buildPassRanges(batches: List<AIGpuBatch>): Map<AIRenderPass, IntRange> {
            val ranges = mutableMapOf<AIRenderPass, IntRange>()
            var start = 0; var currentPass: AIRenderPass? = null
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
