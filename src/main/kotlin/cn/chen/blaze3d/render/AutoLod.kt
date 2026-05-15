package cn.chen.blaze3d.render
import cn.chen.blaze3d.core.MeshData
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.core.Vertex
import cn.chen.blaze3d.math.Vec3
import cn.chen.blaze3d.math.Vec4
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
data class LodLevel(val ratio: Float, val mesh: MeshData, val distance: Float) {
    val triangleCount get() = mesh.indices.size / 3
}
data class LodChain(val levels: List<LodLevel>) {
    fun bestLevel(distance: Float): LodLevel = levels.firstOrNull { it.distance >= distance } ?: levels.last()
}
object AutoLod {
    fun buildChain(mesh: MeshData, ratios: List<Float> = listOf(0.55f, 0.25f, 0.1f), thresholds: List<Float> = listOf(16f, 48f, 128f)): LodChain {
        val levels = ArrayList<LodLevel>(ratios.size + 1)
        levels.add(LodLevel(1f, mesh, 0f))
        for ((index, ratio) in ratios.withIndex()) {
            val simplified = simplify(mesh, ratio)
            val distance = thresholds.getOrElse(index) { 256f }
            levels.add(LodLevel(ratio, simplified, distance))
        }
        return LodChain(levels)
    }
    fun simplify(mesh: MeshData, ratio: Float): MeshData {
        val targetTris = max(8, (mesh.indices.size / 3 * ratio).toInt())
        return clusterSimplify(mesh, targetTris)
    }
    private fun clusterSimplify(mesh: MeshData, targetTriangles: Int): MeshData {
        if (mesh.vertices.isEmpty()) return mesh
        val bounds = computeBounds(mesh)
        val gridSize = computeGridSize(bounds, targetTriangles)
        val clusters = HashMap<Long, ClusterAccumulator>()
        val vertexCluster = IntArray(mesh.vertices.size)
        val clusterIndices = HashMap<Long, Int>()
        for ((vi, vertex) in mesh.vertices.withIndex()) {
            val key = gridKey(vertex.position, bounds, gridSize)
            val accumulator = clusters.getOrPut(key) { ClusterAccumulator() }
            accumulator.add(vertex)
            vertexCluster[vi] = clusterIndices.getOrPut(key) { clusterIndices.size }
        }
        val mergedVertices = ArrayList<Vertex>(clusters.size)
        val orderedKeys = clusterIndices.entries.sortedBy { it.value }.map { it.key }
        for (key in orderedKeys) clusters[key]?.finalize()?.let { mergedVertices.add(it) }
        val mergedIndices = ArrayList<Int>()
        var i = 0
        while (i < mesh.indices.size - 2) {
            val ca = vertexCluster[mesh.indices[i]]
            val cb = vertexCluster[mesh.indices[i + 1]]
            val cc = vertexCluster[mesh.indices[i + 2]]
            if (ca != cb && cb != cc && ca != cc) {
                mergedIndices.add(ca); mergedIndices.add(cb); mergedIndices.add(cc)
            }
            i += 3
        }
        if (mergedIndices.isEmpty()) return mesh
        return MeshData(mesh.name + "_lod_${targetTriangles}", mergedVertices, mergedIndices.toIntArray(), mesh.materialIndex, mesh.bones)
    }
    private fun computeBounds(mesh: MeshData): FloatArray {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (vertex in mesh.vertices) {
            val p = vertex.position
            if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y; if (p.z < minZ) minZ = p.z
            if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y; if (p.z > maxZ) maxZ = p.z
        }
        return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
    }
    private fun computeGridSize(bounds: FloatArray, targetTriangles: Int): Float {
        val sizeX = max(1e-3f, bounds[3] - bounds[0]); val sizeY = max(1e-3f, bounds[4] - bounds[1]); val sizeZ = max(1e-3f, bounds[5] - bounds[2])
        val volume = sizeX * sizeY * sizeZ
        val ratio = max(0.05f, min(1f, targetTriangles / 5000f))
        return max(1e-3f, Math.cbrt(volume.toDouble() / (targetTriangles * ratio)).toFloat())
    }
    private fun gridKey(position: Vec3, bounds: FloatArray, cellSize: Float): Long {
        val ix = ((position.x - bounds[0]) / cellSize).toInt()
        val iy = ((position.y - bounds[1]) / cellSize).toInt()
        val iz = ((position.z - bounds[2]) / cellSize).toInt()
        return (ix.toLong() and 0xFFFFF) or ((iy.toLong() and 0xFFFFF) shl 20) or ((iz.toLong() and 0xFFFFF) shl 40)
    }
    private class ClusterAccumulator {
        private var count = 0
        private var px = 0f; private var py = 0f; private var pz = 0f
        private var nx = 0f; private var ny = 0f; private var nz = 0f
        private var tx = 0f; private var ty = 0f; private var tz = 0f
        private var bx = 0f; private var by = 0f; private var bz = 0f
        private var ux = 0f; private var uy = 0f
        private var cr = 0f; private var cg = 0f; private var cb = 0f; private var ca = 0f
        private val boneAccum = IntArray(4) { -1 }
        private val boneWeightAccum = FloatArray(4)
        private var firstVertex: Vertex? = null
        fun add(vertex: Vertex) {
            if (firstVertex == null) firstVertex = vertex
            count++
            px += vertex.position.x; py += vertex.position.y; pz += vertex.position.z
            nx += vertex.normal.x; ny += vertex.normal.y; nz += vertex.normal.z
            tx += vertex.tangent.x; ty += vertex.tangent.y; tz += vertex.tangent.z
            bx += vertex.bitangent.x; by += vertex.bitangent.y; bz += vertex.bitangent.z
            ux += vertex.texCoord0.x; uy += vertex.texCoord0.y
            cr += vertex.color.x; cg += vertex.color.y; cb += vertex.color.z; ca += vertex.color.w
            for (i in 0..3) if (vertex.boneIds[i] >= 0 && vertex.boneWeights[i] > 0f) {
                val slot = boneAccum.indexOf(vertex.boneIds[i])
                if (slot >= 0) boneWeightAccum[slot] += vertex.boneWeights[i]
                else {
                    val emptySlot = boneAccum.indexOf(-1)
                    if (emptySlot >= 0) { boneAccum[emptySlot] = vertex.boneIds[i]; boneWeightAccum[emptySlot] = vertex.boneWeights[i] }
                }
            }
        }
        fun finalize(): Vertex {
            val inv = 1f / count
            val nl = sqrt(nx * nx + ny * ny + nz * nz).let { if (it < 1e-5f) 1f else it }
            val tl = sqrt(tx * tx + ty * ty + tz * tz).let { if (it < 1e-5f) 1f else it }
            val bl = sqrt(bx * bx + by * by + bz * bz).let { if (it < 1e-5f) 1f else it }
            val weights = normalizeBoneWeights(boneWeightAccum)
            val baseVertex = firstVertex ?: Vertex()
            return Vertex(
                Vec3(px * inv, py * inv, pz * inv),
                Vec3(nx / nl, ny / nl, nz / nl),
                Vec3(tx / tl, ty / tl, tz / tl),
                Vec3(bx / bl, by / bl, bz / bl),
                Vec3(ux * inv, uy * inv, baseVertex.texCoord0.z),
                Vec3(),
                Vec4(cr * inv, cg * inv, cb * inv, ca * inv),
                boneAccum.copyOf(),
                weights
            )
        }
        private fun normalizeBoneWeights(weights: FloatArray): FloatArray {
            val total = weights.sum()
            return if (total <= 1e-5f) floatArrayOf(1f, 0f, 0f, 0f) else FloatArray(4) { weights[it] / total }
        }
    }
}
class LodSelector(val chain: LodChain) {
    fun select(distance: Float, sizeOnScreen: Float = 1f): LodLevel {
        val biased = distance / max(sizeOnScreen, 0.05f)
        return chain.bestLevel(biased)
    }
    fun summary(): String = "lod levels=${chain.levels.size} tri=${chain.levels.joinToString(",") { "${it.triangleCount}" }}"
}
