package cn.chen.blaze3d.render
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.core.MeshData
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveAction
class GpuSkinning(private val scene: SceneData) {
    private val meshRanges: IntArray
    val totalVertexCount: Int
    val flatPositions: FloatArray
    val flatNormals: FloatArray
    val flatTangents: FloatArray
    private val flatBoneIds: IntArray
    private val flatBoneWeights: FloatArray
    val skinnedPositions: FloatArray
    val skinnedNormals: FloatArray
    val skinnedTangents: FloatArray
    private val staticVertex: BooleanArray
    private val pool: ForkJoinPool = ForkJoinPool.commonPool()
    private var lastRevision = Int.MIN_VALUE
    private val dispatchCount = AtomicInteger()
    private val skinCount = AtomicInteger()
    init {
        var count = 0; meshRanges = IntArray(scene.meshes.size + 1)
        for ((idx, mesh) in scene.meshes.withIndex()) { meshRanges[idx] = count; count += mesh.vertices.size }
        meshRanges[scene.meshes.size] = count
        totalVertexCount = count
        flatPositions = FloatArray(count * 3); flatNormals = FloatArray(count * 3); flatTangents = FloatArray(count * 3)
        flatBoneIds = IntArray(count * 4); flatBoneWeights = FloatArray(count * 4); staticVertex = BooleanArray(count)
        skinnedPositions = FloatArray(count * 3); skinnedNormals = FloatArray(count * 3); skinnedTangents = FloatArray(count * 3)
        flatten()
    }
    private fun flatten() {
        var vi = 0
        for (mesh in scene.meshes) for (vtx in mesh.vertices) {
            flatPositions[vi * 3] = vtx.position.x; flatPositions[vi * 3 + 1] = vtx.position.y; flatPositions[vi * 3 + 2] = vtx.position.z
            flatNormals[vi * 3] = vtx.normal.x; flatNormals[vi * 3 + 1] = vtx.normal.y; flatNormals[vi * 3 + 2] = vtx.normal.z
            flatTangents[vi * 3] = vtx.tangent.x; flatTangents[vi * 3 + 1] = vtx.tangent.y; flatTangents[vi * 3 + 2] = vtx.tangent.z
            var hasWeight = false
            for (b in 0..3) { flatBoneIds[vi * 4 + b] = vtx.boneIds[b]; flatBoneWeights[vi * 4 + b] = vtx.boneWeights[b]; if (vtx.boneIds[b] >= 0 && vtx.boneWeights[b] > 0f) hasWeight = true }
            staticVertex[vi] = !hasWeight
            skinnedPositions[vi * 3] = flatPositions[vi * 3]; skinnedPositions[vi * 3 + 1] = flatPositions[vi * 3 + 1]; skinnedPositions[vi * 3 + 2] = flatPositions[vi * 3 + 2]
            skinnedNormals[vi * 3] = flatNormals[vi * 3]; skinnedNormals[vi * 3 + 1] = flatNormals[vi * 3 + 1]; skinnedNormals[vi * 3 + 2] = flatNormals[vi * 3 + 2]
            skinnedTangents[vi * 3] = flatTangents[vi * 3]; skinnedTangents[vi * 3 + 1] = flatTangents[vi * 3 + 1]; skinnedTangents[vi * 3 + 2] = flatTangents[vi * 3 + 2]
            vi++
        }
    }
    fun dispatch(pose: BonePose, threshold: Int = 1024) {
        if (pose.revision == lastRevision) return
        lastRevision = pose.revision
        dispatchCount.incrementAndGet()
        if (totalVertexCount <= threshold) { skinRange(0, totalVertexCount, pose.boneMatrices); return }
        pool.invoke(SkinTask(0, totalVertexCount, threshold, pose.boneMatrices))
    }
    fun dispatchSerial(pose: BonePose) {
        if (pose.revision == lastRevision) return
        lastRevision = pose.revision
        dispatchCount.incrementAndGet()
        skinRange(0, totalVertexCount, pose.boneMatrices)
    }
    private inner class SkinTask(val start: Int, val end: Int, val threshold: Int, val mats: Array<Mat4>) : RecursiveAction() {
        override fun compute() {
            val span = end - start
            if (span <= threshold) { skinRange(start, end, mats); return }
            val mid = start + span / 2
            invokeAll(SkinTask(start, mid, threshold, mats), SkinTask(mid, end, threshold, mats))
        }
    }
    private fun skinRange(from: Int, to: Int, mats: Array<Mat4>) {
        val matCount = mats.size
        var localSkinCount = 0
        for (i in from until to) {
            if (staticVertex[i]) continue
            val pX = flatPositions[i * 3]; val pY = flatPositions[i * 3 + 1]; val pZ = flatPositions[i * 3 + 2]
            val nX = flatNormals[i * 3]; val nY = flatNormals[i * 3 + 1]; val nZ = flatNormals[i * 3 + 2]
            val tX = flatTangents[i * 3]; val tY = flatTangents[i * 3 + 1]; val tZ = flatTangents[i * 3 + 2]
            var sumW = 0f; var ox = 0f; var oy = 0f; var oz = 0f; var nox = 0f; var noy = 0f; var noz = 0f; var tox = 0f; var toy = 0f; var toz = 0f
            for (b in 0..3) {
                val bid = flatBoneIds[i * 4 + b]; val w = flatBoneWeights[i * 4 + b]
                if (bid < 0 || w <= 0f || bid >= matCount) continue
                val m = mats[bid].m; sumW += w
                val wpx = m[0] * pX + m[1] * pY + m[2] * pZ + m[3]
                val wpy = m[4] * pX + m[5] * pY + m[6] * pZ + m[7]
                val wpz = m[8] * pX + m[9] * pY + m[10] * pZ + m[11]
                val ww = m[12] * pX + m[13] * pY + m[14] * pZ + m[15]
                ox += (wpx / ww) * w; oy += (wpy / ww) * w; oz += (wpz / ww) * w
                nox += (m[0] * nX + m[1] * nY + m[2] * nZ) * w; noy += (m[4] * nX + m[5] * nY + m[6] * nZ) * w; noz += (m[8] * nX + m[9] * nY + m[10] * nZ) * w
                tox += (m[0] * tX + m[1] * tY + m[2] * tZ) * w; toy += (m[4] * tX + m[5] * tY + m[6] * tZ) * w; toz += (m[8] * tX + m[9] * tY + m[10] * tZ) * w
            }
            if (sumW > 0f) {
                val invW = 1f / sumW
                skinnedPositions[i * 3] = ox * invW; skinnedPositions[i * 3 + 1] = oy * invW; skinnedPositions[i * 3 + 2] = oz * invW
                skinnedNormals[i * 3] = nox * invW; skinnedNormals[i * 3 + 1] = noy * invW; skinnedNormals[i * 3 + 2] = noz * invW
                skinnedTangents[i * 3] = tox * invW; skinnedTangents[i * 3 + 1] = toy * invW; skinnedTangents[i * 3 + 2] = toz * invW
            }
            localSkinCount++
        }
        skinCount.addAndGet(localSkinCount)
    }
    fun positionInto(meshIndex: Int, vertexIndex: Int, out: Vec3) {
        val base = (meshRanges[meshIndex] + vertexIndex) * 3
        out.set(skinnedPositions[base], skinnedPositions[base + 1], skinnedPositions[base + 2])
    }
    fun encodeIntoBuffer(buf: ByteBuffer, vertexIndex: Int) {
        val base = vertexIndex * 3
        buf.putFloat(skinnedPositions[base]); buf.putFloat(skinnedPositions[base + 1]); buf.putFloat(skinnedPositions[base + 2])
        buf.putFloat(skinnedNormals[base]); buf.putFloat(skinnedNormals[base + 1]); buf.putFloat(skinnedNormals[base + 2])
    }
    fun globalVertex(meshIndex: Int, vertexIndex: Int): Int = meshRanges[meshIndex] + vertexIndex
    fun copyToHostBuffer(): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(totalVertexCount * 6 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until totalVertexCount) encodeIntoBuffer(buf, i)
        buf.flip(); return buf
    }
    fun status() = "skin dispatch=${dispatchCount.get()} verts=$totalVertexCount skinned=${skinCount.get()}"
}
