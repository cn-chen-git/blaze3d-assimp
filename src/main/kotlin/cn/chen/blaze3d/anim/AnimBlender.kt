package cn.chen.blaze3d.anim
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.core.NodeGraph
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Vec3
import kotlin.math.sqrt
class AnimBlender(private val skeleton: BonePose, private val rootNode: NodeGraph) {
    class AnimLayer(val clip: AnimClip, var time: Double, var weight: Float, var speed: Float = 1f, var loop: Boolean = true) { var animTime: Double = 0.0 }
    private val layers = mutableListOf<AnimLayer>()
    private var stack: Array<Mat4?> = arrayOfNulls(64)
    private val identityMat = Mat4()
    private val tmpLocal = Mat4()
    private val tmpInter = Mat4()
    private val tmpPos = Vec3()
    private val tmpRot = Quat()
    private val tmpScl = Vec3(1f, 1f, 1f)
    fun addLayer(clip: AnimClip, weight: Float = 1f, speed: Float = 1f, loop: Boolean = true): Int {
        layers.add(AnimLayer(clip, 0.0, weight, speed, loop)); return layers.size - 1
    }
    fun removeLayer(index: Int) { if (index in layers.indices) layers.removeAt(index) }
    fun setWeight(index: Int, weight: Float) { if (index in layers.indices) layers[index].weight = weight }
    fun layerCount() = layers.size
    fun update(deltaTime: Double) {
        for (layer in layers) {
            layer.time += deltaTime * layer.speed
            val d = layer.clip.durationSeconds
            if (layer.time > d) layer.time = if (layer.loop) layer.time % d else d
            val ticks = if (layer.clip.ticksPerSecond > 0.0) layer.clip.ticksPerSecond else 25.0
            layer.animTime = (layer.time * ticks) % layer.clip.duration
        }
        identityMat.setIdentity()
        traverse(rootNode, identityMat, 0)
        skeleton.markDirty()
    }
    private fun traverse(node: NodeGraph, parent: Mat4, depth: Int) {
        val local = tmpLocal
        var totalW = 0f
        var bpx = 0f; var bpy = 0f; var bpz = 0f
        var bsx = 0f; var bsy = 0f; var bsz = 0f
        var refRx = 0f; var refRy = 0f; var refRz = 0f; var refRw = 0f
        var raX = 0f; var raY = 0f; var raZ = 0f; var raW = 0f
        var first = true
        for (layer in layers) {
            val w = layer.weight; if (w <= 0f) continue
            val ch = layer.clip.getChannel(node.name) ?: continue
            ch.interpolatePositionInto(layer.animTime, tmpPos)
            ch.interpolateRotationInto(layer.animTime, tmpRot)
            ch.interpolateScalingInto(layer.animTime, tmpScl)
            bpx += tmpPos.x * w; bpy += tmpPos.y * w; bpz += tmpPos.z * w
            bsx += tmpScl.x * w; bsy += tmpScl.y * w; bsz += tmpScl.z * w
            if (first) {
                refRx = tmpRot.x; refRy = tmpRot.y; refRz = tmpRot.z; refRw = tmpRot.w
                raX = refRx * w; raY = refRy * w; raZ = refRz * w; raW = refRw * w
                first = false
            } else {
                val d = tmpRot.x * refRx + tmpRot.y * refRy + tmpRot.z * refRz + tmpRot.w * refRw
                val s = if (d < 0f) -w else w
                raX += tmpRot.x * s; raY += tmpRot.y * s; raZ += tmpRot.z * s; raW += tmpRot.w * s
            }
            totalW += w
        }
        if (totalW > 0f) {
            val inv = 1f / totalW
            bpx *= inv; bpy *= inv; bpz *= inv
            bsx *= inv; bsy *= inv; bsz *= inv
            val rlen = sqrt(raX*raX + raY*raY + raZ*raZ + raW*raW)
            val rinv = if (rlen > 0f) 1f / rlen else 0f
            val qx = raX * rinv; val qy = raY * rinv; val qz = raZ * rinv; val qw = if (rlen > 0f) raW * rinv else 1f
            Mat4.trsInto(bpx, bpy, bpz, qx, qy, qz, qw, bsx, bsy, bsz, local)
        } else local.copyFrom(node.transform)
        val global = slotAt(depth)
        parent.mulInto(local, global)
        val bone = skeleton.bones[node.name]
        if (bone != null) {
            skeleton.globalInverseTransform.mulInto(global, tmpInter)
            tmpInter.mulInto(bone.offsetMatrix, skeleton.boneMatrices[bone.index])
        }
        for (child in node.children) traverse(child, global, depth + 1)
    }
    private fun slotAt(depth: Int): Mat4 {
        if (depth >= stack.size) stack = stack.copyOf(stack.size * 2)
        var s = stack[depth]; if (s == null) { s = Mat4(); stack[depth] = s }
        return s
    }
    fun clear() { layers.clear() }
}
