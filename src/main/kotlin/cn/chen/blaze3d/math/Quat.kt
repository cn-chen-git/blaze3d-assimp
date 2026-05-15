package cn.chen.blaze3d.math
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
data class Quat(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    fun normalize(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l > 0f) Quat(x / l, y / l, z / l, w / l) else this
    }
    fun slerp(o: Quat, t: Float): Quat { val r = Quat(); slerpInto(o, t, r); return r }
    fun slerpInto(o: Quat, t: Float, out: Quat) = slerpComponentsInto(x, y, z, w, o.x, o.y, o.z, o.w, t, out)
    fun set(nx: Float, ny: Float, nz: Float, nw: Float): Quat { x = nx; y = ny; z = nz; w = nw; return this }
    fun toMatrix(): Mat4 { val m = Mat4(); toMatrixInto(m); return m }
    fun toMatrixInto(out: Mat4) {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        val m = out.m
        m[0] = 1f - 2f * (yy + zz); m[1] = 2f * (xy + wz); m[2] = 2f * (xz - wy); m[3] = 0f
        m[4] = 2f * (xy - wz); m[5] = 1f - 2f * (xx + zz); m[6] = 2f * (yz + wx); m[7] = 0f
        m[8] = 2f * (xz + wy); m[9] = 2f * (yz - wx); m[10] = 1f - 2f * (xx + yy); m[11] = 0f
        m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
    }
    companion object {
        fun slerpComponentsInto(ax: Float, ay: Float, az: Float, aw: Float, bx: Float, by: Float, bz: Float, bw: Float, t: Float, out: Quat) {
            var dot = ax * bx + ay * by + az * bz + aw * bw
            var ox = bx; var oy = by; var oz = bz; var ow = bw
            if (dot < 0f) { dot = -dot; ox = -ox; oy = -oy; oz = -oz; ow = -ow }
            if (dot > 0.9995f) {
                val rx = ax + (ox - ax) * t; val ry = ay + (oy - ay) * t; val rz = az + (oz - az) * t; val rw = aw + (ow - aw) * t
                val l = sqrt(rx*rx + ry*ry + rz*rz + rw*rw)
                if (l > 0f) { val k = 1f / l; out.x = rx * k; out.y = ry * k; out.z = rz * k; out.w = rw * k } else { out.x = ax; out.y = ay; out.z = az; out.w = aw }
                return
            }
            val theta0 = acos(dot); val theta = theta0 * t
            val sinTheta0 = sin(theta0); val invSin = 1f / sinTheta0
            val sinTheta = sin(theta)
            val s0 = cos(theta) - dot * sinTheta * invSin
            val s1 = sinTheta * invSin
            out.x = ax * s0 + ox * s1; out.y = ay * s0 + oy * s1; out.z = az * s0 + oz * s1; out.w = aw * s0 + ow * s1
        }
        fun fromMatrix(m: Mat4): Quat {
            val mm = m.m
            val trace = mm[0] + mm[5] + mm[10]
            return when {
                trace > 0f -> {
                    val s = 0.5f / sqrt(trace + 1f)
                    Quat((mm[6] - mm[9]) * s, (mm[8] - mm[2]) * s, (mm[1] - mm[4]) * s, 0.25f / s)
                }
                mm[0] > mm[5] && mm[0] > mm[10] -> {
                    val s = 2f * sqrt(1f + mm[0] - mm[5] - mm[10])
                    Quat(0.25f * s, (mm[4] + mm[1]) / s, (mm[8] + mm[2]) / s, (mm[6] - mm[9]) / s)
                }
                mm[5] > mm[10] -> {
                    val s = 2f * sqrt(1f + mm[5] - mm[0] - mm[10])
                    Quat((mm[4] + mm[1]) / s, 0.25f * s, (mm[9] + mm[6]) / s, (mm[8] - mm[2]) / s)
                }
                else -> {
                    val s = 2f * sqrt(1f + mm[10] - mm[0] - mm[5])
                    Quat((mm[8] + mm[2]) / s, (mm[9] + mm[6]) / s, 0.25f * s, (mm[1] - mm[4]) / s)
                }
            }
        }
    }
}
