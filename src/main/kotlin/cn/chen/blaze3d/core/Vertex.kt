package cn.chen.blaze3d.core
import cn.chen.blaze3d.math.Vec3
import cn.chen.blaze3d.math.Vec4
data class Vertex(
    val position: Vec3 = Vec3(),
    val normal: Vec3 = Vec3(0f, 1f, 0f),
    val tangent: Vec3 = Vec3(1f, 0f, 0f),
    val bitangent: Vec3 = Vec3(0f, 0f, 1f),
    val texCoord0: Vec3 = Vec3(),
    val texCoord1: Vec3 = Vec3(),
    val color: Vec4 = Vec4(1f, 1f, 1f, 1f),
    val boneIds: IntArray = IntArray(4) { -1 },
    val boneWeights: FloatArray = FloatArray(4) { 0f }
) {
    companion object {
        const val STRIDE = (3 + 3 + 3 + 3 + 3 + 3 + 4 + 4 + 4) * 4
        const val MAX_BONES_PER_VERTEX = 4
    }
}
