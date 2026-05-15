package cn.chen.blaze3d.core
import cn.chen.blaze3d.math.Vec3
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Mat4
class AnimChannel(
    val nodeName: String,
    val posTimes: DoubleArray,
    val posVals: FloatArray,
    val rotTimes: DoubleArray,
    val rotVals: FloatArray,
    val sclTimes: DoubleArray,
    val sclVals: FloatArray,
    val preState: AnimBehaviour = AnimBehaviour.DEFAULT,
    val postState: AnimBehaviour = AnimBehaviour.DEFAULT
) {
    private var posHint = 0
    private var rotHint = 0
    private var sclHint = 0
    private val tmpPos = Vec3()
    private val tmpRot = Quat()
    private val tmpScl = Vec3(1f, 1f, 1f)
    fun interpolatePositionInto(time: Double, out: Vec3) {
        val t = posTimes; val v = posVals; val n = t.size
        if (n == 0) { out.x = 0f; out.y = 0f; out.z = 0f; return }
        if (n == 1) { out.x = v[0]; out.y = v[1]; out.z = v[2]; return }
        val sampleTime = resolveTime(t, n, time, preState, postState)
        val i = findIdx(t, posHint, n, sampleTime); posHint = i
        val i3 = i * 3; val j3 = i3 + 3
        val d = t[i + 1] - t[i]
        val f = if (d > 0.0) ((sampleTime - t[i]) / d).toFloat().coerceIn(0f, 1f) else 0f
        out.x = v[i3] + (v[j3] - v[i3]) * f
        out.y = v[i3 + 1] + (v[j3 + 1] - v[i3 + 1]) * f
        out.z = v[i3 + 2] + (v[j3 + 2] - v[i3 + 2]) * f
    }
    fun interpolateRotationInto(time: Double, out: Quat) {
        val t = rotTimes; val v = rotVals; val n = t.size
        if (n == 0) { out.x = 0f; out.y = 0f; out.z = 0f; out.w = 1f; return }
        if (n == 1) { out.x = v[0]; out.y = v[1]; out.z = v[2]; out.w = v[3]; return }
        val sampleTime = resolveTime(t, n, time, preState, postState)
        val i = findIdx(t, rotHint, n, sampleTime); rotHint = i
        val i4 = i * 4; val j4 = i4 + 4
        val d = t[i + 1] - t[i]
        val f = if (d > 0.0) ((sampleTime - t[i]) / d).toFloat().coerceIn(0f, 1f) else 0f
        Quat.slerpComponentsInto(v[i4], v[i4 + 1], v[i4 + 2], v[i4 + 3], v[j4], v[j4 + 1], v[j4 + 2], v[j4 + 3], f, out)
    }
    fun interpolateScalingInto(time: Double, out: Vec3) {
        val t = sclTimes; val v = sclVals; val n = t.size
        if (n == 0) { out.x = 1f; out.y = 1f; out.z = 1f; return }
        if (n == 1) { out.x = v[0]; out.y = v[1]; out.z = v[2]; return }
        val sampleTime = resolveTime(t, n, time, preState, postState)
        val i = findIdx(t, sclHint, n, sampleTime); sclHint = i
        val i3 = i * 3; val j3 = i3 + 3
        val d = t[i + 1] - t[i]
        val f = if (d > 0.0) ((sampleTime - t[i]) / d).toFloat().coerceIn(0f, 1f) else 0f
        out.x = v[i3] + (v[j3] - v[i3]) * f
        out.y = v[i3 + 1] + (v[j3 + 1] - v[i3 + 1]) * f
        out.z = v[i3 + 2] + (v[j3 + 2] - v[i3 + 2]) * f
    }
    fun computeTransformInto(time: Double, out: Mat4) {
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
    private fun resolveTime(times: DoubleArray, n: Int, time: Double, pre: AnimBehaviour, post: AnimBehaviour): Double {
        val start = times[0]; val end = times[n - 1]
        if (time in start..end) return time
        val behaviour = if (time < start) pre else post
        if (behaviour == AnimBehaviour.LINEAR && n > 1) return if (time < start) {
            val d = times[1] - start
            if (d > 0.0) start + (time - start).coerceAtLeast(-d) else start
        } else {
            val d = end - times[n - 2]
            if (d > 0.0) end + (time - end).coerceAtMost(d) else end
        }
        if (behaviour == AnimBehaviour.REPEAT && end > start) {
            val len = end - start
            val wrapped = (time - start) % len
            return start + if (wrapped < 0.0) wrapped + len else wrapped
        }
        return if (time < start) start else end
    }
}
class AnimClip(
    val name: String,
    val duration: Double,
    val ticksPerSecond: Double,
    val channels: List<AnimChannel>,
    val meshChannels: List<MeshAnimChannel> = emptyList(),
    val morphChannels: List<MorphAnimChannel> = emptyList()
) {
    private val channelMap: HashMap<String, AnimChannel> = HashMap(channels.size * 2)
    init { for (c in channels) channelMap[c.nodeName] = c }
    fun getChannel(nodeName: String): AnimChannel? = channelMap[nodeName]
    val tps: Double get() = if (ticksPerSecond > 0.0) ticksPerSecond else 25.0
    val durationSeconds: Double get() = duration / tps
}
enum class AnimBehaviour { DEFAULT, CONSTANT, LINEAR, REPEAT }
class MeshAnimChannel(val meshName: String, val times: DoubleArray, val values: IntArray) {
    private var hint = 0
    fun valueAt(time: Double): Int {
        if (times.isEmpty()) return -1
        if (times.size == 1) return values[0]
        var i = hint
        if (i >= times.size - 1 || time < times[i]) i = 0
        while (i < times.size - 1 && time >= times[i + 1]) i++
        hint = i
        return values[i.coerceAtMost(values.size - 1)]
    }
}
class MorphKey(val time: Double, val targetIndices: IntArray, val weights: FloatArray)
class MorphAnimChannel(val meshName: String, val keys: List<MorphKey>) {
    private var hint = 0
    fun weightsAt(time: Double): FloatArray {
        if (keys.isEmpty()) return FloatArray(0)
        if (keys.size == 1 || time <= keys[0].time) return keys[0].weights.copyOf()
        var i = hint
        if (i >= keys.size - 1 || time < keys[i].time) i = 0
        while (i < keys.size - 1 && time >= keys[i + 1].time) i++
        hint = i
        if (i >= keys.size - 1) return keys.last().weights.copyOf()
        val a = keys[i]; val b = keys[i + 1]
        val d = b.time - a.time
        val f = if (d > 0.0) ((time - a.time) / d).toFloat().coerceIn(0f, 1f) else 0f
        val len = maxOf(a.weights.size, b.weights.size)
        val out = FloatArray(len)
        for (j in 0 until len) out[j] = a.weights.getOrElse(j) { 0f } + (b.weights.getOrElse(j) { 0f } - a.weights.getOrElse(j) { 0f }) * f
        return out
    }
}
