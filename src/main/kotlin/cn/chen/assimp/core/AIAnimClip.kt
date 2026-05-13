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
    private var posHint = 0
    private var rotHint = 0
    private var sclHint = 0
    private val tmpPos = AIVec3()
    private val tmpRot = AIQuat()
    private val tmpScl = AIVec3(1f, 1f, 1f)
    fun interpolatePosition(time: Double): AIVec3 { val out = AIVec3(); interpolatePositionInto(time, out); return out }
    fun interpolateRotation(time: Double): AIQuat { val out = AIQuat(); interpolateRotationInto(time, out); return out }
    fun interpolateScaling(time: Double): AIVec3 { val out = AIVec3(1f, 1f, 1f); interpolateScalingInto(time, out); return out }
    fun interpolatePositionInto(time: Double, out: AIVec3) {
        val keys = positionKeys; val n = keys.size
        if (n == 0) { out.x = 0f; out.y = 0f; out.z = 0f; return }
        if (n == 1) { val v = keys[0].value; out.x = v.x; out.y = v.y; out.z = v.z; return }
        val i = findIdxPos(time, n); posHint = i
        val k0 = keys[i]; val k1 = keys[i + 1]
        val d = k1.time - k0.time
        val f = if (d > 0.0) ((time - k0.time) / d).toFloat().coerceIn(0f, 1f) else 0f
        val a = k0.value; val b = k1.value
        out.x = a.x + (b.x - a.x) * f; out.y = a.y + (b.y - a.y) * f; out.z = a.z + (b.z - a.z) * f
    }
    fun interpolateRotationInto(time: Double, out: AIQuat) {
        val keys = rotationKeys; val n = keys.size
        if (n == 0) { out.x = 0f; out.y = 0f; out.z = 0f; out.w = 1f; return }
        if (n == 1) { val q = keys[0].value; out.x = q.x; out.y = q.y; out.z = q.z; out.w = q.w; return }
        val i = findIdxRot(time, n); rotHint = i
        val k0 = keys[i]; val k1 = keys[i + 1]
        val d = k1.time - k0.time
        val f = if (d > 0.0) ((time - k0.time) / d).toFloat().coerceIn(0f, 1f) else 0f
        k0.value.slerpInto(k1.value, f, out)
    }
    fun interpolateScalingInto(time: Double, out: AIVec3) {
        val keys = scalingKeys; val n = keys.size
        if (n == 0) { out.x = 1f; out.y = 1f; out.z = 1f; return }
        if (n == 1) { val v = keys[0].value; out.x = v.x; out.y = v.y; out.z = v.z; return }
        val i = findIdxScl(time, n); sclHint = i
        val k0 = keys[i]; val k1 = keys[i + 1]
        val d = k1.time - k0.time
        val f = if (d > 0.0) ((time - k0.time) / d).toFloat().coerceIn(0f, 1f) else 0f
        val a = k0.value; val b = k1.value
        out.x = a.x + (b.x - a.x) * f; out.y = a.y + (b.y - a.y) * f; out.z = a.z + (b.z - a.z) * f
    }
    fun computeTransform(time: Double): AIMat4 { val out = AIMat4(); computeTransformInto(time, out); return out }
    fun computeTransformInto(time: Double, out: AIMat4) {
        interpolatePositionInto(time, tmpPos)
        interpolateRotationInto(time, tmpRot)
        interpolateScalingInto(time, tmpScl)
        val qx = tmpRot.x; val qy = tmpRot.y; val qz = tmpRot.z; val qw = tmpRot.w
        val sx = tmpScl.x; val sy = tmpScl.y; val sz = tmpScl.z
        val xx = qx*qx; val yy = qy*qy; val zz = qz*qz
        val xy = qx*qy; val xz = qx*qz; val yz = qy*qz
        val wx = qw*qx; val wy = qw*qy; val wz = qw*qz
        val m = out.m
        m[0] = (1f-2f*(yy+zz))*sx; m[1] = 2f*(xy+wz)*sy; m[2] = 2f*(xz-wy)*sz; m[3] = tmpPos.x
        m[4] = 2f*(xy-wz)*sx; m[5] = (1f-2f*(xx+zz))*sy; m[6] = 2f*(yz+wx)*sz; m[7] = tmpPos.y
        m[8] = 2f*(xz+wy)*sx; m[9] = 2f*(yz-wx)*sy; m[10] = (1f-2f*(xx+yy))*sz; m[11] = tmpPos.z
        m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
    }
    private fun findIdxPos(time: Double, n: Int): Int {
        val h = posHint
        if (h < n - 1 && time >= positionKeys[h].time && time < positionKeys[h + 1].time) return h
        if (h + 1 < n - 1 && time >= positionKeys[h + 1].time && time < positionKeys[h + 2].time) return h + 1
        var lo = 0; var hi = n - 1
        while (lo < hi - 1) { val mid = (lo + hi) ushr 1; if (positionKeys[mid].time <= time) lo = mid else hi = mid }
        return lo.coerceAtMost(n - 2)
    }
    private fun findIdxRot(time: Double, n: Int): Int {
        val h = rotHint
        if (h < n - 1 && time >= rotationKeys[h].time && time < rotationKeys[h + 1].time) return h
        if (h + 1 < n - 1 && time >= rotationKeys[h + 1].time && time < rotationKeys[h + 2].time) return h + 1
        var lo = 0; var hi = n - 1
        while (lo < hi - 1) { val mid = (lo + hi) ushr 1; if (rotationKeys[mid].time <= time) lo = mid else hi = mid }
        return lo.coerceAtMost(n - 2)
    }
    private fun findIdxScl(time: Double, n: Int): Int {
        val h = sclHint
        if (h < n - 1 && time >= scalingKeys[h].time && time < scalingKeys[h + 1].time) return h
        if (h + 1 < n - 1 && time >= scalingKeys[h + 1].time && time < scalingKeys[h + 2].time) return h + 1
        var lo = 0; var hi = n - 1
        while (lo < hi - 1) { val mid = (lo + hi) ushr 1; if (scalingKeys[mid].time <= time) lo = mid else hi = mid }
        return lo.coerceAtMost(n - 2)
    }
}
class AIAnimClip(
    val name: String,
    val duration: Double,
    val ticksPerSecond: Double,
    val channels: List<AIAnimChannel>
) {
    private val channelMap = channels.associateBy { it.nodeName }
    fun getChannel(nodeName: String) = channelMap[nodeName]
    val durationSeconds get() = if (ticksPerSecond > 0.0) duration / ticksPerSecond else duration
}
