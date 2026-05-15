package cn.chen.blaze3d.advanced
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.core.MeshData
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.math.AABB
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Vec3
class BvhRefitter(private val scene: SceneData, private val data: AccelerationData) {
    private val parentMap: IntArray = buildParents()
    private val clusterSamples: Array<IntArray> = buildSamples()
    private val tmpVec = Vec3()
    private val tmpVec2 = Vec3()
    var lastRevision = Int.MIN_VALUE; private set
    var refitInvocations = 0L; private set
    var clusterUpdates = 0L; private set
    private fun buildParents(): IntArray {
        val parents = IntArray(data.nodes.size) { -1 }
        for ((idx, node) in data.nodes.withIndex()) { if (!node.leaf) { if (node.left >= 0) parents[node.left] = idx; if (node.right >= 0) parents[node.right] = idx } }
        return parents
    }
    private fun buildSamples(): Array<IntArray> {
        return Array(data.clusters.size) { i ->
            val cluster = data.clusters[i]
            val mesh = scene.meshes.getOrNull(cluster.meshIndex) ?: return@Array IntArray(0)
            val seen = HashSet<Int>(cluster.triangleCount * 3 / 8 + 8)
            for (t in 0 until cluster.triangleCount) for (k in 0..2) {
                val idx = mesh.indices[(cluster.triangleStart + t) * 3 + k]
                if (mesh.vertices.getOrNull(idx)?.boneIds?.any { it >= 0 } == true) seen.add(idx)
                if (seen.size > 16) break
            }
            seen.toIntArray()
        }
    }
    fun refit(pose: BonePose?, nodeTransforms: Map<Int, Mat4>? = null) {
        refitInvocations++
        if (pose != null && pose.revision == lastRevision) return
        if (pose != null) lastRevision = pose.revision
        recomputeLeafBounds(pose, nodeTransforms)
        propagateUpwards()
    }
    private fun recomputeLeafBounds(pose: BonePose?, nodeTransforms: Map<Int, Mat4>?) {
        for ((idx, node) in data.nodes.withIndex()) {
            if (!node.leaf) continue
            val cluster = data.clusters.getOrNull(node.clusterIndex) ?: continue
            val mesh = scene.meshes.getOrNull(cluster.meshIndex) ?: continue
            val bounds = node.bounds
            bounds.min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            bounds.max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            val samples = clusterSamples[node.clusterIndex]
            if (samples.isEmpty() || pose == null) { extendFromCluster(mesh, cluster, bounds, nodeTransforms?.get(cluster.meshIndex)) }
            else extendFromSamples(mesh, samples, pose, bounds)
            clusterUpdates++
        }
    }
    private fun extendFromCluster(mesh: MeshData, cluster: MeshCluster, bounds: AABB, transform: Mat4?) {
        if (transform == null) {
            for (t in 0 until cluster.triangleCount) for (k in 0..2) {
                val p = mesh.vertices[mesh.indices[(cluster.triangleStart + t) * 3 + k]].position
                bounds.expand(p.x, p.y, p.z)
            }
        } else {
            for (t in 0 until cluster.triangleCount) for (k in 0..2) {
                val p = mesh.vertices[mesh.indices[(cluster.triangleStart + t) * 3 + k]].position
                transform.transformPointInto(p.x, p.y, p.z, tmpVec); bounds.expand(tmpVec)
            }
        }
    }
    private fun extendFromSamples(mesh: MeshData, samples: IntArray, pose: BonePose, bounds: AABB) {
        val mats = pose.boneMatrices
        for (vid in samples) {
            val vtx = mesh.vertices.getOrNull(vid) ?: continue
            val ids = vtx.boneIds; val weights = vtx.boneWeights
            var px = 0f; var py = 0f; var pz = 0f; var totalWeight = 0f
            for (b in 0 until 4) {
                val bid = ids[b]; val w = weights[b]
                if (bid < 0 || w <= 0f || bid >= mats.size) continue
                mats[bid].transformPointInto(vtx.position.x, vtx.position.y, vtx.position.z, tmpVec2)
                px += tmpVec2.x * w; py += tmpVec2.y * w; pz += tmpVec2.z * w; totalWeight += w
            }
            if (totalWeight > 0f) bounds.expand(px / totalWeight, py / totalWeight, pz / totalWeight) else bounds.expand(vtx.position)
        }
    }
    private fun propagateUpwards() {
        for (i in data.nodes.indices.reversed()) {
            val node = data.nodes[i]; if (node.leaf) continue
            val b = node.bounds; b.min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE); b.max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            if (node.left >= 0) b.merge(data.nodes[node.left].bounds)
            if (node.right >= 0) b.merge(data.nodes[node.right].bounds)
        }
    }
    fun status() = "refit invocations=$refitInvocations clusters=$clusterUpdates samples=${clusterSamples.sumOf { it.size }} nodes=${data.nodes.size}"
}
