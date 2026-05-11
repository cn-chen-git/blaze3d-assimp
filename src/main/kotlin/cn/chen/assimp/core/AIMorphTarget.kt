package cn.chen.assimp.core
import cn.chen.assimp.math.AIVec3
class AIMorphTarget(
    val name: String,
    val positions: List<AIVec3>,
    val normals: List<AIVec3>,
    val tangents: List<AIVec3>
)
class AIMorphAnim(
    val meshIndex: Int,
    val targets: List<AIMorphTarget>,
    val weights: FloatArray
) {
    fun blendPositions(basePositions: List<AIVec3>): List<AIVec3> {
        val result = basePositions.map { AIVec3(it.x, it.y, it.z) }.toMutableList()
        for ((ti, target) in targets.withIndex()) {
            val w = weights.getOrElse(ti) { 0f }
            if (w == 0f) continue
            for (vi in result.indices) {
                result[vi] = result[vi] + target.positions[vi] * w
            }
        }
        return result
    }
    fun blendNormals(baseNormals: List<AIVec3>): List<AIVec3> {
        val result = baseNormals.map { AIVec3(it.x, it.y, it.z) }.toMutableList()
        for ((ti, target) in targets.withIndex()) {
            val w = weights.getOrElse(ti) { 0f }
            if (w == 0f) continue
            for (vi in result.indices) {
                result[vi] = (result[vi] + target.normals[vi] * w).normalize()
            }
        }
        return result
    }
}
