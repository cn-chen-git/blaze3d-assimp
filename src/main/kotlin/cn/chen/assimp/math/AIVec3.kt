package cn.chen.assimp.math
import java.nio.FloatBuffer
data class AIVec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun plus(o: AIVec3) = AIVec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: AIVec3) = AIVec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = AIVec3(x * s, y * s, z * s)
    fun dot(o: AIVec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: AIVec3) = AIVec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = kotlin.math.sqrt(x * x + y * y + z * z)
    fun normalize(): AIVec3 { val l = length(); return if (l > 0f) AIVec3(x / l, y / l, z / l) else this }
    fun lerp(o: AIVec3, t: Float) = AIVec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun putTo(buf: FloatBuffer) { buf.put(x); buf.put(y); buf.put(z) }
    fun toFloatArray() = floatArrayOf(x, y, z)
}
