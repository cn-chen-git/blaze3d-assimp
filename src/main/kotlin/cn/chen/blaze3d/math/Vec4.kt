package cn.chen.blaze3d.math
import java.nio.FloatBuffer
data class Vec4(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    operator fun plus(o: Vec4) = Vec4(x + o.x, y + o.y, z + o.z, w + o.w)
    operator fun times(s: Float) = Vec4(x * s, y * s, z * s, w * s)
    fun set(nx: Float, ny: Float, nz: Float, nw: Float): Vec4 { x = nx; y = ny; z = nz; w = nw; return this }
    fun putTo(buf: FloatBuffer) { buf.put(x); buf.put(y); buf.put(z); buf.put(w) }
    fun toFloatArray() = floatArrayOf(x, y, z, w)
    fun xyz() = Vec3(x, y, z)
}
