package cn.chen.assimp.math
import kotlin.math.*
data class AIQuat(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    fun normalize(): AIQuat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l > 0f) AIQuat(x / l, y / l, z / l, w / l) else this
    }
    fun slerp(o: AIQuat, t: Float): AIQuat {
        var dot = x * o.x + y * o.y + z * o.z + w * o.w
        var ox = o.x; var oy = o.y; var oz = o.z; var ow = o.w
        if (dot < 0f) { dot = -dot; ox = -ox; oy = -oy; oz = -oz; ow = -ow }
        if (dot > 0.9995f) return AIQuat(x + (ox - x) * t, y + (oy - y) * t, z + (oz - z) * t, w + (ow - w) * t).normalize()
        val theta0 = acos(dot); val theta = theta0 * t
        val sinTheta = sin(theta); val sinTheta0 = sin(theta0)
        val s0 = cos(theta) - dot * sinTheta / sinTheta0
        val s1 = sinTheta / sinTheta0
        return AIQuat(x * s0 + ox * s1, y * s0 + oy * s1, z * s0 + oz * s1, w * s0 + ow * s1)
    }
    fun toMatrix(): AIMat4 {
        val m = AIMat4()
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        m.m[0] = 1f - 2f * (yy + zz); m.m[1] = 2f * (xy + wz); m.m[2] = 2f * (xz - wy); m.m[3] = 0f
        m.m[4] = 2f * (xy - wz); m.m[5] = 1f - 2f * (xx + zz); m.m[6] = 2f * (yz + wx); m.m[7] = 0f
        m.m[8] = 2f * (xz + wy); m.m[9] = 2f * (yz - wx); m.m[10] = 1f - 2f * (xx + yy); m.m[11] = 0f
        m.m[12] = 0f; m.m[13] = 0f; m.m[14] = 0f; m.m[15] = 1f
        return m
    }
    companion object {
        fun fromMatrix(m: AIMat4): AIQuat {
            val trace = m.m[0] + m.m[5] + m.m[10]
            return when {
                trace > 0f -> {
                    val s = 0.5f / sqrt(trace + 1f)
                    AIQuat((m.m[6] - m.m[9]) * s, (m.m[8] - m.m[2]) * s, (m.m[1] - m.m[4]) * s, 0.25f / s)
                }
                m.m[0] > m.m[5] && m.m[0] > m.m[10] -> {
                    val s = 2f * sqrt(1f + m.m[0] - m.m[5] - m.m[10])
                    AIQuat(0.25f * s, (m.m[4] + m.m[1]) / s, (m.m[8] + m.m[2]) / s, (m.m[6] - m.m[9]) / s)
                }
                m.m[5] > m.m[10] -> {
                    val s = 2f * sqrt(1f + m.m[5] - m.m[0] - m.m[10])
                    AIQuat((m.m[4] + m.m[1]) / s, 0.25f * s, (m.m[9] + m.m[6]) / s, (m.m[8] - m.m[2]) / s)
                }
                else -> {
                    val s = 2f * sqrt(1f + m.m[10] - m.m[0] - m.m[5])
                    AIQuat((m.m[8] + m.m[2]) / s, (m.m[9] + m.m[6]) / s, 0.25f * s, (m.m[1] - m.m[4]) / s)
                }
            }
        }
    }
}
