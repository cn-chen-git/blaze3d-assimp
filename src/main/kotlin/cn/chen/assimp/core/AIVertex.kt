package cn.chen.assimp.core
import cn.chen.assimp.math.AIVec3
import cn.chen.assimp.math.AIVec4
data class AIVertex(
    val position: AIVec3 = AIVec3(),
    val normal: AIVec3 = AIVec3(0f, 1f, 0f),
    val tangent: AIVec3 = AIVec3(1f, 0f, 0f),
    val bitangent: AIVec3 = AIVec3(0f, 0f, 1f),
    val texCoord0: AIVec3 = AIVec3(),
    val texCoord1: AIVec3 = AIVec3(),
    val color: AIVec4 = AIVec4(1f, 1f, 1f, 1f),
    val boneIds: IntArray = IntArray(4) { -1 },
    val boneWeights: FloatArray = FloatArray(4) { 0f }
) {
    companion object {
        const val STRIDE = (3 + 3 + 3 + 3 + 3 + 3 + 4 + 4 + 4) * 4
        const val MAX_BONES_PER_VERTEX = 4
    }
}
