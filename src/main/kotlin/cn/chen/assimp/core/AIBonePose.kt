package cn.chen.assimp.core
import cn.chen.assimp.math.AIMat4
class AIBonePose(val bones: Map<String, AIBoneInfo>, val globalInverseTransform: AIMat4) {
    val boneCount get() = bones.size
    val boneMatrices = Array(bones.size) { AIMat4.identity() }
    fun computeBoneTransforms(animation: AIAnimClip, timeInSeconds: Double, rootNode: AINodeGraph) {
        val ticks = if (animation.ticksPerSecond > 0.0) animation.ticksPerSecond else 25.0
        val timeInTicks = timeInSeconds * ticks
        val animTime = timeInTicks % animation.duration
        traverseNode(animTime, animation, rootNode, AIMat4.identity())
    }
    private fun traverseNode(animTime: Double, anim: AIAnimClip, node: AINodeGraph, parentTransform: AIMat4) {
        val channel = anim.getChannel(node.name)
        val nodeTransform = channel?.computeTransform(animTime) ?: node.transform
        val globalTransform = parentTransform * nodeTransform
        bones[node.name]?.let { bone ->
            boneMatrices[bone.index] = globalInverseTransform * globalTransform * bone.offsetMatrix
        }
        for (child in node.children) traverseNode(animTime, anim, child, globalTransform)
    }
}
