package cn.chen.blaze3d.math
import java.nio.FloatBuffer
import kotlin.math.sqrt
data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    fun normalize(): Vec3 { val l = sqrt(x * x + y * y + z * z); return if (l > 0f) Vec3(x / l, y / l, z / l) else this }
    fun normalizeInPlace(): Vec3 { val l = sqrt(x * x + y * y + z * z); if (l > 0f) { val k = 1f / l; x *= k; y *= k; z *= k }; return this }
    fun lerp(o: Vec3, t: Float) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun set(nx: Float, ny: Float, nz: Float): Vec3 { x = nx; y = ny; z = nz; return this }
    fun set(o: Vec3): Vec3 { x = o.x; y = o.y; z = o.z; return this }
    fun putTo(buf: FloatBuffer) { buf.put(x); buf.put(y); buf.put(z) }
    fun toFloatArray() = floatArrayOf(x, y, z)
}
