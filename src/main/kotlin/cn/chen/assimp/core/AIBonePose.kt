package cn.chen.assimp.core
import cn.chen.assimp.math.AIMat4
class AIBonePose(val bones: Map<String, AIBoneInfo>, val globalInverseTransform: AIMat4) {
    val boneCount get() = bones.size
    val boneMatrices = Array(bones.size) { AIMat4.identity() }
    var revision: Int = 0; private set
    fun markDirty() { revision++ }
    private var stack: Array<AIMat4?> = arrayOfNulls(64)
    private val identity = AIMat4()
    private val tmpNode = AIMat4()
    private val tmpInter = AIMat4()
    fun computeBoneTransforms(animation: AIAnimClip, timeInSeconds: Double, rootNode: AINodeGraph) {
        val ticks = if (animation.ticksPerSecond > 0.0) animation.ticksPerSecond else 25.0
        val animTime = (timeInSeconds * ticks) % animation.duration
        identity.setIdentity()
        traverseInto(animTime, animation, rootNode, identity, 0)
        revision++
    }
    private fun traverseInto(animTime: Double, anim: AIAnimClip, node: AINodeGraph, parent: AIMat4, depth: Int) {
        val channel = anim.getChannel(node.name)
        if (channel != null) channel.computeTransformInto(animTime, tmpNode) else tmpNode.copyFrom(node.transform)
        val slot = slotAt(depth)
        parent.mulInto(tmpNode, slot)
        val bone = bones[node.name]
        if (bone != null) {
            globalInverseTransform.mulInto(slot, tmpInter)
            tmpInter.mulInto(bone.offsetMatrix, boneMatrices[bone.index])
        }
        for (child in node.children) traverseInto(animTime, anim, child, slot, depth + 1)
    }
    private fun slotAt(depth: Int): AIMat4 {
        if (depth >= stack.size) stack = stack.copyOf(stack.size * 2)
        var s = stack[depth]
        if (s == null) { s = AIMat4(); stack[depth] = s }
        return s
    }
}
