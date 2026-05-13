package cn.chen.assimp.core
import cn.chen.assimp.math.AIVec3
import cn.chen.assimp.math.AIQuat
import cn.chen.assimp.math.AIMat4
class AIAnimChannel(
    val nodeName: String,
    val posTimes: DoubleArray,
    val posVals: FloatArray,
    val rotTimes: DoubleArray,
    val rotVals: FloatArray,
    val sclTimes: DoubleArray,
    val sclVals: FloatArray
) {
    private var posHint = 0
    private var rotHint = 0
    private var sclHint = 0
    private val tmpPos = AIVec3()
    private val tmpRot = AIQuat()
    private val tmpScl = AIVec3(1f, 1f, 1f)
    fun interpolatePositionInto(time: Double, out: AIVec3) {
        val t = posTimes; val v = posVals; val n = t.size
        if (n == 0) { out.x = 0f; out.y = 0f; out.z = 0f; return }
        if (n == 1) { out.x = v[0]; out.y = v[1]; out.z = v[2]; return }
        val i = findIdx(t, posHint, n, time); posHint = i
        val i3 = i * 3; val j3 = i3 + 3
        val d = t[i + 1] - t[i]
        val f = if (d > 0.0) ((time - t[i]) / d).toFloat().coerceIn(0f, 1f) else 0f
        out.x = v[i3] + (v[j3] - v[i3]) * f
        out.y = v[i3 + 1] + (v[j3 + 1] - v[i3 + 1]) * f
        out.z = v[i3 + 2] + (v[j3 + 2] - v[i3 + 2]) * f
    }
    fun interpolateRotationInto(time: Double, out: AIQuat) {
        val t = rotTimes; val v = rotVals; val n = t.size
        if (n == 0) { out.x = 0f; out.y = 0f; out.z = 0f; out.w = 1f; return }
        if (n == 1) { out.x = v[0]; out.y = v[1]; out.z = v[2]; out.w = v[3]; return }
        val i = findIdx(t, rotHint, n, time); rotHint = i
        val i4 = i * 4; val j4 = i4 + 4
        val d = t[i + 1] - t[i]
        val f = if (d > 0.0) ((time - t[i]) / d).toFloat().coerceIn(0f, 1f) else 0f
        AIQuat.slerpComponentsInto(v[i4], v[i4 + 1], v[i4 + 2], v[i4 + 3], v[j4], v[j4 + 1], v[j4 + 2], v[j4 + 3], f, out)
    }
    fun interpolateScalingInto(time: Double, out: AIVec3) {
        val t = sclTimes; val v = sclVals; val n = t.size
        if (n == 0) { out.x = 1f; out.y = 1f; out.z = 1f; return }
        if (n == 1) { out.x = v[0]; out.y = v[1]; out.z = v[2]; return }
        val i = findIdx(t, sclHint, n, time); sclHint = i
        val i3 = i * 3; val j3 = i3 + 3
        val d = t[i + 1] - t[i]
        val f = if (d > 0.0) ((time - t[i]) / d).toFloat().coerceIn(0f, 1f) else 0f
        out.x = v[i3] + (v[j3] - v[i3]) * f
        out.y = v[i3 + 1] + (v[j3 + 1] - v[i3 + 1]) * f
        out.z = v[i3 + 2] + (v[j3 + 2] - v[i3 + 2]) * f
    }
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
    private fun findIdx(times: DoubleArray, hint: Int, n: Int, time: Double): Int {
        if (hint < n - 1 && time >= times[hint] && time < times[hint + 1]) return hint
        if (hint + 1 < n - 1 && time >= times[hint + 1] && time < times[hint + 2]) return hint + 1
        var lo = 0; var hi = n - 1
        while (lo < hi - 1) { val mid = (lo + hi) ushr 1; if (times[mid] <= time) lo = mid else hi = mid }
        return lo.coerceAtMost(n - 2)
    }
}
class AIAnimClip(
    val name: String,
    val duration: Double,
    val ticksPerSecond: Double,
    val channels: List<AIAnimChannel>
) {
    private val channelMap: HashMap<String, AIAnimChannel> = HashMap(channels.size * 2)
    init { for (c in channels) channelMap[c.nodeName] = c }
    fun getChannel(nodeName: String): AIAnimChannel? = channelMap[nodeName]
    val tps: Double get() = if (ticksPerSecond > 0.0) ticksPerSecond else 25.0
    val durationSeconds: Double get() = duration / tps
}
