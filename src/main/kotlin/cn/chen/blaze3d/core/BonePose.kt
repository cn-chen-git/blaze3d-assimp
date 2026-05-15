package cn.chen.blaze3d.core
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Vec3
class BonePose(val bones: Map<String, BoneInfo>, val globalInverseTransform: Mat4) {
    val boneCount get() = bones.size
    val boneMatrices = Array(bones.size) { Mat4.identity() }
    var revision: Int = 0; private set
    fun markDirty() { revision++ }
    private var stack: Array<Mat4?> = arrayOfNulls(64)
    private val identity = Mat4()
    private val tmpNode = Mat4()
    private val tmpInter = Mat4()
    private val pos1 = Vec3(); private val pos2 = Vec3()
    private val rot1 = Quat(); private val rot2 = Quat(); private val rotB = Quat()
    private val scl1 = Vec3(1f,1f,1f); private val scl2 = Vec3(1f,1f,1f)
    fun computeBoneTransforms(animation: AnimClip, timeInSeconds: Double, rootNode: NodeGraph) {
        identity.setIdentity()
        traverseInto(ticksTime(animation, timeInSeconds), animation, rootNode, identity, 0)
        revision++
    }
    fun computeBoneTransformsBlend(clipA: AnimClip, timeA: Double, clipB: AnimClip, timeB: Double, factor: Float, rootNode: NodeGraph) {
        identity.setIdentity()
        traverseBlend(ticksTime(clipA, timeA), clipA, ticksTime(clipB, timeB), clipB, factor.coerceIn(0f, 1f), rootNode, identity, 0)
        revision++
    }
    private fun ticksTime(c: AnimClip, t: Double): Double {
        val ticks = if (c.ticksPerSecond > 0.0) c.ticksPerSecond else 25.0
        return (t * ticks) % c.duration
    }
    private fun applyBone(name: String, world: Mat4) {
        val bone = bones[name] ?: return
        globalInverseTransform.mulInto(world, tmpInter)
        tmpInter.mulInto(bone.offsetMatrix, boneMatrices[bone.index])
    }
    private fun traverseInto(animTime: Double, anim: AnimClip, node: NodeGraph, parent: Mat4, depth: Int) {
        val channel = anim.getChannel(node.name)
        if (channel != null) channel.computeTransformInto(animTime, tmpNode) else tmpNode.copyFrom(node.transform)
        val slot = slotAt(depth)
        parent.mulInto(tmpNode, slot)
        applyBone(node.name, slot)
        for (child in node.children) traverseInto(animTime, anim, child, slot, depth + 1)
    }
    private fun traverseBlend(tA: Double, a: AnimClip, tB: Double, b: AnimClip, f: Float, node: NodeGraph, parent: Mat4, depth: Int) {
        val chA = a.getChannel(node.name); val chB = b.getChannel(node.name)
        val local = tmpNode
        when {
            chA != null && chB != null -> {
                chA.interpolatePositionInto(tA, pos1); chB.interpolatePositionInto(tB, pos2)
                chA.interpolateRotationInto(tA, rot1); chB.interpolateRotationInto(tB, rot2)
                chA.interpolateScalingInto(tA, scl1); chB.interpolateScalingInto(tB, scl2)
                rot1.slerpInto(rot2, f, rotB)
                Mat4.trsInto(
                    pos1.x + (pos2.x - pos1.x) * f, pos1.y + (pos2.y - pos1.y) * f, pos1.z + (pos2.z - pos1.z) * f,
                    rotB.x, rotB.y, rotB.z, rotB.w,
                    scl1.x + (scl2.x - scl1.x) * f, scl1.y + (scl2.y - scl1.y) * f, scl1.z + (scl2.z - scl1.z) * f,
                    local
                )
            }
            chA != null -> chA.computeTransformInto(tA, local)
            chB != null -> chB.computeTransformInto(tB, local)
            else -> local.copyFrom(node.transform)
        }
        val slot = slotAt(depth)
        parent.mulInto(local, slot)
        applyBone(node.name, slot)
        for (child in node.children) traverseBlend(tA, a, tB, b, f, child, slot, depth + 1)
    }
    private fun slotAt(depth: Int): Mat4 {
        if (depth >= stack.size) stack = stack.copyOf(stack.size * 2)
        return stack[depth] ?: Mat4().also { stack[depth] = it }
    }
}
