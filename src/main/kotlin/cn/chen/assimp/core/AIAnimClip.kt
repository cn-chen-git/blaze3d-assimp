package cn.chen.assimp.core
import cn.chen.assimp.math.AIVec3
import cn.chen.assimp.math.AIQuat
import cn.chen.assimp.math.AIMat4
data class AIVecKey(val time: Double, val value: AIVec3)
data class AIQuatKey(val time: Double, val value: AIQuat)
class AIAnimChannel(
    val nodeName: String,
    val positionKeys: List<AIVecKey>,
    val rotationKeys: List<AIQuatKey>,
    val scalingKeys: List<AIVecKey>
) {
    fun interpolatePosition(time: Double): AIVec3 {
        if (positionKeys.size == 1) return positionKeys[0].value
        val (i, f) = findKeyframe(positionKeys.map { it.time }, time)
        return positionKeys[i].value.lerp(positionKeys[i + 1].value, f)
    }
    fun interpolateRotation(time: Double): AIQuat {
        if (rotationKeys.size == 1) return rotationKeys[0].value
        val (i, f) = findKeyframe(rotationKeys.map { it.time }, time)
        return rotationKeys[i].value.slerp(rotationKeys[i + 1].value, f)
    }
    fun interpolateScaling(time: Double): AIVec3 {
        if (scalingKeys.size == 1) return scalingKeys[0].value
        val (i, f) = findKeyframe(scalingKeys.map { it.time }, time)
        return scalingKeys[i].value.lerp(scalingKeys[i + 1].value, f)
    }
    fun computeTransform(time: Double): AIMat4 {
        val t = interpolatePosition(time)
        val r = interpolateRotation(time)
        val s = interpolateScaling(time)
        return AIMat4.translation(t.x, t.y, t.z) * r.toMatrix() * AIMat4.scaling(s.x, s.y, s.z)
    }
    private fun findKeyframe(times: List<Double>, time: Double): Pair<Int, Float> {
        for (i in 0 until times.size - 1) {
            if (time < times[i + 1]) {
                val delta = times[i + 1] - times[i]
                val factor = if (delta > 0.0) ((time - times[i]) / delta).toFloat() else 0f
                return i to factor
            }
        }
        return (times.size - 2).coerceAtLeast(0) to 1f
    }
}
class AIAnimClip(
    val name: String,
    val duration: Double,
    val ticksPerSecond: Double,
    val channels: List<AIAnimChannel>
) {
    fun getChannel(nodeName: String) = channels.find { it.nodeName == nodeName }
    val durationSeconds get() = if (ticksPerSecond > 0.0) duration / ticksPerSecond else duration
}
