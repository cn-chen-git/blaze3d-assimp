package cn.chen.assimp.core
import cn.chen.assimp.math.AIMat4
import cn.chen.assimp.math.AIQuat
import cn.chen.assimp.math.AIVec3
class AIBonePose(val bones: Map<String, AIBoneInfo>, val globalInverseTransform: AIMat4) {
    val boneCount get() = bones.size
    val boneMatrices = Array(bones.size) { AIMat4.identity() }
    var revision: Int = 0; private set
    fun markDirty() { revision++ }
    private var stack: Array<AIMat4?> = arrayOfNulls(64)
    private val identity = AIMat4()
    private val tmpNode = AIMat4()
    private val tmpInter = AIMat4()
    private val pos1 = AIVec3(); private val pos2 = AIVec3()
    private val rot1 = AIQuat(); private val rot2 = AIQuat(); private val rotB = AIQuat()
    private val scl1 = AIVec3(1f,1f,1f); private val scl2 = AIVec3(1f,1f,1f)
    fun computeBoneTransforms(animation: AIAnimClip, timeInSeconds: Double, rootNode: AINodeGraph) {
        val ticks = if (animation.ticksPerSecond > 0.0) animation.ticksPerSecond else 25.0
        val animTime = (timeInSeconds * ticks) % animation.duration
        identity.setIdentity()
        traverseInto(animTime, animation, rootNode, identity, 0)
        revision++
    }
    fun computeBoneTransformsBlend(clipA: AIAnimClip, timeA: Double, clipB: AIAnimClip, timeB: Double, factor: Float, rootNode: AINodeGraph) {
        val tA = ticksTime(clipA, timeA); val tB = ticksTime(clipB, timeB)
        val f = factor.coerceIn(0f, 1f)
        identity.setIdentity()
        traverseBlend(tA, clipA, tB, clipB, f, rootNode, identity, 0)
        revision++
    }
    private fun ticksTime(c: AIAnimClip, t: Double): Double {
        val ticks = if (c.ticksPerSecond > 0.0) c.ticksPerSecond else 25.0
        return (t * ticks) % c.duration
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
    private fun traverseBlend(tA: Double, a: AIAnimClip, tB: Double, b: AIAnimClip, f: Float, node: AINodeGraph, parent: AIMat4, depth: Int) {
        val chA = a.getChannel(node.name); val chB = b.getChannel(node.name)
        val local = tmpNode
        when {
            chA != null && chB != null -> {
                chA.interpolatePositionInto(tA, pos1); chB.interpolatePositionInto(tB, pos2)
                chA.interpolateRotationInto(tA, rot1); chB.interpolateRotationInto(tB, rot2)
                chA.interpolateScalingInto(tA, scl1); chB.interpolateScalingInto(tB, scl2)
                rot1.slerpInto(rot2, f, rotB)
                val px = pos1.x + (pos2.x - pos1.x) * f
                val py = pos1.y + (pos2.y - pos1.y) * f
                val pz = pos1.z + (pos2.z - pos1.z) * f
                val sx = scl1.x + (scl2.x - scl1.x) * f
                val sy = scl1.y + (scl2.y - scl1.y) * f
                val sz = scl1.z + (scl2.z - scl1.z) * f
                AIMat4.trsInto(px, py, pz, rotB.x, rotB.y, rotB.z, rotB.w, sx, sy, sz, local)
            }
            chA != null -> chA.computeTransformInto(tA, local)
            chB != null -> chB.computeTransformInto(tB, local)
            else -> local.copyFrom(node.transform)
        }
        val slot = slotAt(depth)
        parent.mulInto(local, slot)
        val bone = bones[node.name]
        if (bone != null) {
            globalInverseTransform.mulInto(slot, tmpInter)
            tmpInter.mulInto(bone.offsetMatrix, boneMatrices[bone.index])
        }
        for (child in node.children) traverseBlend(tA, a, tB, b, f, child, slot, depth + 1)
    }
    private fun slotAt(depth: Int): AIMat4 {
        if (depth >= stack.size) stack = stack.copyOf(stack.size * 2)
        var s = stack[depth]
        if (s == null) { s = AIMat4(); stack[depth] = s }
        return s
    }
}
