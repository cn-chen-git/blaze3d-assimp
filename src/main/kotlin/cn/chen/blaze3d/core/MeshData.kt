package cn.chen.blaze3d.core
import cn.chen.blaze3d.math.Mat4
data class BoneInfo(val name: String, val offsetMatrix: Mat4, val index: Int)
class MeshData(
    val name: String,
    val vertices: List<Vertex>,
    val indices: IntArray,
    val materialIndex: Int,
    val bones: List<BoneInfo> = emptyList(),
    val morphTargets: List<MorphTarget> = emptyList()
) {
    val hasBones get() = bones.isNotEmpty()
    val hasMorph get() = morphTargets.isNotEmpty()
    val vertexCount get() = vertices.size
    val indexCount get() = indices.size
}
