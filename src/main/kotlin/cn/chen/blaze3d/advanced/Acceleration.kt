package cn.chen.blaze3d.advanced
import cn.chen.blaze3d.core.MeshData
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.math.AABB
import cn.chen.blaze3d.math.Vec3
import kotlin.math.abs
data class Ray(val origin: Vec3, val direction: Vec3)
data class RayHit(val meshIndex: Int, val triangleIndex: Int, val distance: Float, val position: Vec3)
class MeshCluster(val meshIndex: Int, val triangleStart: Int, val triangleCount: Int, val bounds: AABB, val lodLevel: Int)
class BvhNode(val bounds: AABB, val left: Int, val right: Int, val clusterIndex: Int) { val leaf get() = clusterIndex >= 0 }
class AccelerationData(val clusters: List<MeshCluster>, val nodes: List<BvhNode>, val bounds: AABB) {
    fun visibleClusters(camera: Vec3, frustum: net.minecraft.client.renderer.culling.Frustum?, scale: Float, lodBias: Float): List<MeshCluster> {
        val out = ArrayList<MeshCluster>(clusters.size)
        val cx = camera.x; val cy = camera.y; val cz = camera.z
        val lodFar = 19600.0 * lodBias; val lodMid = 4096.0 * lodBias
        for (cluster in clusters) {
            val mn = cluster.bounds.min; val mx = cluster.bounds.max
            val ax = mn.x.toDouble() * scale; val ay = mn.y.toDouble() * scale; val az = mn.z.toDouble() * scale
            val bx = mx.x.toDouble() * scale; val by = mx.y.toDouble() * scale; val bz = mx.z.toDouble() * scale
            if (frustum != null) { val mcBox = net.minecraft.world.phys.AABB(ax, ay, az, bx, by, bz); if (!frustum.isVisible(mcBox)) continue }
            val ccx = (ax + bx) * 0.5; val ccy = (ay + by) * 0.5; val ccz = (az + bz) * 0.5
            val dx = ccx - cx; val dy = ccy - cy; val dz = ccz - cz
            val d2 = dx * dx + dy * dy + dz * dz
            val target = if (d2 > lodFar) 2 else if (d2 > lodMid) 1 else 0
            if (cluster.lodLevel >= target) out.add(cluster)
        }
        return out
    }
    fun raycast(scene: SceneData, ray: Ray, maxDistance: Float): RayHit? {
        var best: RayHit? = null
        val stack = IntArray(64); var sp = 0; if (nodes.isNotEmpty()) { stack[sp++] = 0 }
        while (sp > 0) {
            val n = nodes[stack[--sp]]
            if (!n.bounds.intersectsRay(ray, maxDistance)) continue
            if (n.leaf) {
                val cluster = clusters[n.clusterIndex]
                val mesh = scene.meshes.getOrNull(cluster.meshIndex) ?: continue
                val hit = mesh.raycast(cluster, ray, best?.distance ?: maxDistance)
                val current = best
                if (hit != null && (current == null || hit.distance < current.distance)) best = hit
            } else {
                if (sp + 2 > stack.size) continue
                stack[sp++] = n.right; stack[sp++] = n.left
            }
        }
        return best
    }
}
object AccelerationBuilder {
    fun build(scene: SceneData, trianglesPerCluster: Int = 64): AccelerationData {
        val clusters = ArrayList<MeshCluster>()
        val global = AABB()
        for ((meshIndex, mesh) in scene.meshes.withIndex()) {
            var tri = 0
            while (tri * 3 + 2 < mesh.indices.size) {
                val count = minOf(trianglesPerCluster, mesh.indices.size / 3 - tri)
                val bounds = mesh.bounds(tri, count)
                global.merge(bounds)
                val lod = if (count < 16) 2 else if (count < 40) 1 else 0
                clusters.add(MeshCluster(meshIndex, tri, count, bounds, lod))
                tri += count
            }
        }
        val nodes = ArrayList<BvhNode>()
        if (clusters.isNotEmpty()) buildRange(clusters, nodes, 0, clusters.size)
        return AccelerationData(clusters, nodes, global)
    }
    private fun buildRange(clusters: ArrayList<MeshCluster>, nodes: ArrayList<BvhNode>, start: Int, end: Int): Int {
        val bounds = AABB()
        for (i in start until end) bounds.merge(clusters[i].bounds)
        val index = nodes.size
        nodes.add(BvhNode(bounds, -1, -1, -1))
        if (end - start <= 1) { nodes[index] = BvhNode(bounds, -1, -1, start); return index }
        val ex = bounds.max.x - bounds.min.x
        val ey = bounds.max.y - bounds.min.y
        val ez = bounds.max.z - bounds.min.z
        val axis = if (ex >= ey && ex >= ez) 0 else if (ey >= ez) 1 else 2
        clusters.subList(start, end).sortBy {
            val mn = it.bounds.min; val mx = it.bounds.max
            when (axis) { 0 -> (mn.x + mx.x); 1 -> (mn.y + mx.y); else -> (mn.z + mx.z) }
        }
        val mid = (start + end) ushr 1
        val left = buildRange(clusters, nodes, start, mid)
        val right = buildRange(clusters, nodes, mid, end)
        nodes[index] = BvhNode(bounds, left, right, -1)
        return index
    }
}
private fun MeshData.bounds(triangleStart: Int, triangleCount: Int): AABB {
    val b = AABB()
    for (t in 0 until triangleCount) for (k in 0..2) { val p = vertices[indices[(triangleStart + t) * 3 + k]].position; b.expand(p.x, p.y, p.z) }
    return b
}
private fun MeshData.raycast(cluster: MeshCluster, ray: Ray, maxDistance: Float): RayHit? {
    var best: RayHit? = null
    val ox = ray.origin.x; val oy = ray.origin.y; val oz = ray.origin.z
    val dx = ray.direction.x; val dy = ray.direction.y; val dz = ray.direction.z
    for (t in 0 until cluster.triangleCount) {
        val base = (cluster.triangleStart + t) * 3
        val a = vertices[indices[base]].position
        val b = vertices[indices[base + 1]].position
        val c = vertices[indices[base + 2]].position
        val d = rayTri(ox, oy, oz, dx, dy, dz, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, best?.distance ?: maxDistance)
        if (d > 0f) best = RayHit(cluster.meshIndex, cluster.triangleStart + t, d, Vec3(ox + dx * d, oy + dy * d, oz + dz * d))
    }
    return best
}
private fun rayTri(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, maxDistance: Float): Float {
    val e1x = bx - ax; val e1y = by - ay; val e1z = bz - az
    val e2x = cx - ax; val e2y = cy - ay; val e2z = cz - az
    val px = dy * e2z - dz * e2y; val py = dz * e2x - dx * e2z; val pz = dx * e2y - dy * e2x
    val det = e1x * px + e1y * py + e1z * pz
    if (abs(det) < 0.000001f) return -1f
    val inv = 1f / det
    val tx = ox - ax; val ty = oy - ay; val tz = oz - az
    val u = (tx * px + ty * py + tz * pz) * inv
    if (u < 0f || u > 1f) return -1f
    val qx = ty * e1z - tz * e1y; val qy = tz * e1x - tx * e1z; val qz = tx * e1y - ty * e1x
    val v = (dx * qx + dy * qy + dz * qz) * inv
    if (v < 0f || u + v > 1f) return -1f
    val d = (e2x * qx + e2y * qy + e2z * qz) * inv
    return if (d in 0f..maxDistance) d else -1f
}
private fun AABB.intersectsRay(ray: Ray, maxDistance: Float): Boolean {
    var tmin = 0f; var tmax = maxDistance
    val ox = ray.origin.x; val oy = ray.origin.y; val oz = ray.origin.z
    val dx = ray.direction.x; val dy = ray.direction.y; val dz = ray.direction.z
    if (abs(dx) < 0.000001f) { if (ox < min.x || ox > max.x) return false } else {
        val inv = 1f / dx; var a = (min.x - ox) * inv; var b = (max.x - ox) * inv
        if (a > b) { val t = a; a = b; b = t }
        tmin = maxOf(tmin, a); tmax = minOf(tmax, b)
        if (tmax < tmin) return false
    }
    if (abs(dy) < 0.000001f) { if (oy < min.y || oy > max.y) return false } else {
        val inv = 1f / dy; var a = (min.y - oy) * inv; var b = (max.y - oy) * inv
        if (a > b) { val t = a; a = b; b = t }
        tmin = maxOf(tmin, a); tmax = minOf(tmax, b)
        if (tmax < tmin) return false
    }
    if (abs(dz) < 0.000001f) { if (oz < min.z || oz > max.z) return false } else {
        val inv = 1f / dz; var a = (min.z - oz) * inv; var b = (max.z - oz) * inv
        if (a > b) { val t = a; a = b; b = t }
        tmin = maxOf(tmin, a); tmax = minOf(tmax, b)
        if (tmax < tmin) return false
    }
    return true
}
