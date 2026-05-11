package cn.chen.assimp.core
import cn.chen.assimp.material.AIPbrMat
import cn.chen.assimp.material.AIEmbeddedTex
class AISceneData(
    val meshes: List<AIMeshData>,
    val materials: List<AIPbrMat>,
    val animations: List<AIAnimClip>,
    val rootNode: AINodeGraph,
    val skeleton: AIBonePose?,
    val embeddedTextures: List<AIEmbeddedTex> = emptyList()
) {
    val hasAnimations get() = animations.isNotEmpty()
    val hasSkeleton get() = skeleton != null
    fun getAnimation(name: String) = animations.find { it.name == name }
    fun getAnimation(index: Int) = animations.getOrNull(index)
}
