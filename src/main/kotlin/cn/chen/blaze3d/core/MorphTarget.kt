package cn.chen.blaze3d.core
import cn.chen.blaze3d.math.Vec3
class MorphTarget(
    val name: String,
    val positions: List<Vec3>,
    val normals: List<Vec3>,
    val tangents: List<Vec3>
)
class MorphAnim(
    val meshIndex: Int,
    val targets: List<MorphTarget>,
    val weights: FloatArray
) {
    fun blendPositions(basePositions: List<Vec3>): List<Vec3> {
        val n = basePositions.size
        val result = ArrayList<Vec3>(n).apply { for (v in basePositions) add(Vec3(v.x, v.y, v.z)) }
        for (ti in targets.indices) {
            val w = if (ti < weights.size) weights[ti] else 0f
            if (w == 0f) continue
            val pts = targets[ti].positions
            for (vi in 0 until n) {
                val r = result[vi]; val t = pts[vi]
                r.x += t.x * w; r.y += t.y * w; r.z += t.z * w
            }
        }
        return result
    }
    fun blendNormals(baseNormals: List<Vec3>): List<Vec3> {
        val n = baseNormals.size
        val result = ArrayList<Vec3>(n).apply { for (v in baseNormals) add(Vec3(v.x, v.y, v.z)) }
        for (ti in targets.indices) {
            val w = if (ti < weights.size) weights[ti] else 0f
            if (w == 0f) continue
            val nrm = targets[ti].normals
            for (vi in 0 until n) {
                val r = result[vi]; val t = nrm[vi]
                r.x += t.x * w; r.y += t.y * w; r.z += t.z * w
                r.normalizeInPlace()
            }
        }
        return result
    }
}
