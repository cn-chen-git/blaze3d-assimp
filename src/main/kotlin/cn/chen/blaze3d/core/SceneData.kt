package cn.chen.blaze3d.core
import cn.chen.blaze3d.material.Material
import cn.chen.blaze3d.material.EmbeddedTex
class SceneData(
    val meshes: List<MeshData>,
    val materials: List<Material>,
    val animations: List<AnimClip>,
    val rootNode: NodeGraph,
    val skeleton: BonePose?,
    val embeddedTextures: List<EmbeddedTex> = emptyList()
) {
    private val animationMap = animations.associateBy { it.name }
    val hasAnimations get() = animations.isNotEmpty()
    val hasSkeleton get() = skeleton != null
    fun getAnimation(name: String) = animationMap[name]
    fun getAnimation(index: Int) = animations.getOrNull(index)
}
