package cn.chen.assimp.core
import cn.chen.assimp.math.AIMat4
data class AIBoneInfo(val name: String, val offsetMatrix: AIMat4, val index: Int)
class AIMeshData(
    val name: String,
    val vertices: List<AIVertex>,
    val indices: IntArray,
    val materialIndex: Int,
    val bones: List<AIBoneInfo> = emptyList(),
    val morphTargets: List<AIMorphTarget> = emptyList()
) {
    val hasBones get() = bones.isNotEmpty()
    val hasMorph get() = morphTargets.isNotEmpty()
    val vertexCount get() = vertices.size
    val indexCount get() = indices.size
}
