package cn.chen.assimp.anim
import cn.chen.assimp.core.*
import cn.chen.assimp.math.AIMat4
import cn.chen.assimp.math.AIQuat
import cn.chen.assimp.math.AIVec3
class AIAnimBlender(private val skeleton: AIBonePose, private val rootNode: AINodeGraph) {
    data class AIAnimLayer(val clip: AIAnimClip, var time: Double, var weight: Float, var speed: Float = 1f, var loop: Boolean = true)
    private val layers = mutableListOf<AIAnimLayer>()
    fun addLayer(clip: AIAnimClip, weight: Float = 1f, speed: Float = 1f, loop: Boolean = true): Int {
        layers.add(AIAnimLayer(clip, 0.0, weight, speed, loop))
        return layers.size - 1
    }
    fun removeLayer(index: Int) { if (index in layers.indices) layers.removeAt(index) }
    fun setWeight(index: Int, weight: Float) { if (index in layers.indices) layers[index].weight = weight }
    fun update(deltaTime: Double) {
        for (layer in layers) {
            layer.time += deltaTime * layer.speed
            val duration = layer.clip.durationSeconds
            if (layer.time > duration) {
                layer.time = if (layer.loop) layer.time % duration else duration
            }
        }
        blendBones()
    }
    private fun blendBones() {
        val totalWeight = layers.sumOf { it.weight.toDouble() }.toFloat()
        if (totalWeight <= 0f) return
        for (i in 0 until skeleton.boneCount) skeleton.boneMatrices[i] = AIMat4.identity()
        for (layer in layers) {
            if (layer.weight <= 0f) continue
            val normalizedWeight = layer.weight / totalWeight
            val ticks = if (layer.clip.ticksPerSecond > 0.0) layer.clip.ticksPerSecond else 25.0
            val animTime = (layer.time * ticks) % layer.clip.duration
            val tempMatrices = Array(skeleton.boneCount) { AIMat4.identity() }
            computeLayerBones(animTime, layer.clip, rootNode, AIMat4.identity(), tempMatrices)
            for (i in 0 until skeleton.boneCount) {
                val src = tempMatrices[i].m
                val dst = skeleton.boneMatrices[i].m
                for (j in 0..15) dst[j] += src[j] * normalizedWeight
            }
        }
    }
    private fun computeLayerBones(animTime: Double, clip: AIAnimClip, node: AINodeGraph, parent: AIMat4, out: Array<AIMat4>) {
        val channel = clip.getChannel(node.name)
        val nodeTransform = channel?.computeTransform(animTime) ?: node.transform
        val global = parent * nodeTransform
        skeleton.bones[node.name]?.let { bone ->
            out[bone.index] = skeleton.globalInverseTransform * global * bone.offsetMatrix
        }
        for (child in node.children) computeLayerBones(animTime, clip, child, global, out)
    }
    fun clear() { layers.clear() }
}
