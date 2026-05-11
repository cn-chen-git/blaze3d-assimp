package cn.chen.assimp.math
import java.nio.FloatBuffer
data class AIVec4(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    operator fun plus(o: AIVec4) = AIVec4(x + o.x, y + o.y, z + o.z, w + o.w)
    operator fun times(s: Float) = AIVec4(x * s, y * s, z * s, w * s)
    fun putTo(buf: FloatBuffer) { buf.put(x); buf.put(y); buf.put(z); buf.put(w) }
    fun toFloatArray() = floatArrayOf(x, y, z, w)
    fun xyz() = AIVec3(x, y, z)
}
