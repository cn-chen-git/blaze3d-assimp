package cn.chen.assimp.math
import org.joml.Matrix4f
import java.nio.FloatBuffer
class AIMat4(val m: FloatArray = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)) {
    operator fun times(o: AIMat4): AIMat4 {
        val r = FloatArray(16)
        for (i in 0..3) for (j in 0..3) {
            r[i * 4 + j] = m[i * 4] * o.m[j] + m[i * 4 + 1] * o.m[4 + j] + m[i * 4 + 2] * o.m[8 + j] + m[i * 4 + 3] * o.m[12 + j]
        }
        return AIMat4(r)
    }
    fun transformPoint(v: AIVec3): AIVec3 {
        val w = m[12] * v.x + m[13] * v.y + m[14] * v.z + m[15]
        return AIVec3(
            (m[0] * v.x + m[1] * v.y + m[2] * v.z + m[3]) / w,
            (m[4] * v.x + m[5] * v.y + m[6] * v.z + m[7]) / w,
            (m[8] * v.x + m[9] * v.y + m[10] * v.z + m[11]) / w
        )
    }
    fun transformDir(v: AIVec3) = AIVec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[4] * v.x + m[5] * v.y + m[6] * v.z,
        m[8] * v.x + m[9] * v.y + m[10] * v.z
    )
    fun inverse(): AIMat4 {
        val inv = FloatArray(16)
        inv[0] = m[5]*m[10]*m[15] - m[5]*m[11]*m[14] - m[9]*m[6]*m[15] + m[9]*m[7]*m[14] + m[13]*m[6]*m[11] - m[13]*m[7]*m[10]
        inv[4] = -m[4]*m[10]*m[15] + m[4]*m[11]*m[14] + m[8]*m[6]*m[15] - m[8]*m[7]*m[14] - m[12]*m[6]*m[11] + m[12]*m[7]*m[10]
        inv[8] = m[4]*m[9]*m[15] - m[4]*m[11]*m[13] - m[8]*m[5]*m[15] + m[8]*m[7]*m[13] + m[12]*m[5]*m[11] - m[12]*m[7]*m[9]
        inv[12] = -m[4]*m[9]*m[14] + m[4]*m[10]*m[13] + m[8]*m[5]*m[14] - m[8]*m[6]*m[13] - m[12]*m[5]*m[10] + m[12]*m[6]*m[9]
        inv[1] = -m[1]*m[10]*m[15] + m[1]*m[11]*m[14] + m[9]*m[2]*m[15] - m[9]*m[3]*m[14] - m[13]*m[2]*m[11] + m[13]*m[3]*m[10]
        inv[5] = m[0]*m[10]*m[15] - m[0]*m[11]*m[14] - m[8]*m[2]*m[15] + m[8]*m[3]*m[14] + m[12]*m[2]*m[11] - m[12]*m[3]*m[10]
        inv[9] = -m[0]*m[9]*m[15] + m[0]*m[11]*m[13] + m[8]*m[1]*m[15] - m[8]*m[3]*m[13] - m[12]*m[1]*m[11] + m[12]*m[3]*m[9]
        inv[13] = m[0]*m[9]*m[14] - m[0]*m[10]*m[13] - m[8]*m[1]*m[14] + m[8]*m[2]*m[13] + m[12]*m[1]*m[10] - m[12]*m[2]*m[9]
        inv[2] = m[1]*m[6]*m[15] - m[1]*m[7]*m[14] - m[5]*m[2]*m[15] + m[5]*m[3]*m[14] + m[13]*m[2]*m[7] - m[13]*m[3]*m[6]
        inv[6] = -m[0]*m[6]*m[15] + m[0]*m[7]*m[14] + m[4]*m[2]*m[15] - m[4]*m[3]*m[14] - m[12]*m[2]*m[7] + m[12]*m[3]*m[6]
        inv[10] = m[0]*m[5]*m[15] - m[0]*m[7]*m[13] - m[4]*m[1]*m[15] + m[4]*m[3]*m[13] + m[12]*m[1]*m[7] - m[12]*m[3]*m[5]
        inv[14] = -m[0]*m[5]*m[14] + m[0]*m[6]*m[13] + m[4]*m[1]*m[14] - m[4]*m[2]*m[13] - m[12]*m[1]*m[6] + m[12]*m[2]*m[5]
        inv[3] = -m[1]*m[6]*m[11] + m[1]*m[7]*m[10] + m[5]*m[2]*m[11] - m[5]*m[3]*m[10] - m[9]*m[2]*m[7] + m[9]*m[3]*m[6]
        inv[7] = m[0]*m[6]*m[11] - m[0]*m[7]*m[10] - m[4]*m[2]*m[11] + m[4]*m[3]*m[10] + m[8]*m[2]*m[7] - m[8]*m[3]*m[6]
        inv[11] = -m[0]*m[5]*m[11] + m[0]*m[7]*m[9] + m[4]*m[1]*m[11] - m[4]*m[3]*m[9] - m[8]*m[1]*m[7] + m[8]*m[3]*m[5]
        inv[15] = m[0]*m[5]*m[10] - m[0]*m[6]*m[9] - m[4]*m[1]*m[10] + m[4]*m[2]*m[9] + m[8]*m[1]*m[6] - m[8]*m[2]*m[5]
        val det = m[0]*inv[0] + m[1]*inv[4] + m[2]*inv[8] + m[3]*inv[12]
        if (det == 0f) return AIMat4()
        val invDet = 1f / det
        return AIMat4(FloatArray(16) { inv[it] * invDet })
    }
    fun transpose(): AIMat4 {
        val r = FloatArray(16)
        for (i in 0..3) for (j in 0..3) r[i * 4 + j] = m[j * 4 + i]
        return AIMat4(r)
    }
    fun toJoml() = Matrix4f(m[0],m[4],m[8],m[12], m[1],m[5],m[9],m[13], m[2],m[6],m[10],m[14], m[3],m[7],m[11],m[15])
    fun putTo(buf: FloatBuffer) { for (v in m) buf.put(v) }
    fun toFloatArray() = m.copyOf()
    companion object {
        fun identity() = AIMat4()
        fun translation(x: Float, y: Float, z: Float) = AIMat4(floatArrayOf(1f,0f,0f,x, 0f,1f,0f,y, 0f,0f,1f,z, 0f,0f,0f,1f))
        fun scaling(x: Float, y: Float, z: Float) = AIMat4(floatArrayOf(x,0f,0f,0f, 0f,y,0f,0f, 0f,0f,z,0f, 0f,0f,0f,1f))
        fun perspective(fov: Float, aspect: Float, near: Float, far: Float): AIMat4 {
            val t = kotlin.math.tan(fov * 0.5f)
            val r = AIMat4(FloatArray(16))
            r.m[0] = 1f / (aspect * t); r.m[5] = 1f / t
            r.m[10] = -(far + near) / (far - near); r.m[11] = -2f * far * near / (far - near)
            r.m[14] = -1f; r.m[15] = 0f
            return r
        }
        fun lookAt(eye: AIVec3, center: AIVec3, up: AIVec3): AIMat4 {
            val f = (center - eye).normalize()
            val s = f.cross(up).normalize()
            val u = s.cross(f)
            val r = AIMat4()
            r.m[0] = s.x; r.m[1] = s.y; r.m[2] = s.z; r.m[3] = -s.dot(eye)
            r.m[4] = u.x; r.m[5] = u.y; r.m[6] = u.z; r.m[7] = -u.dot(eye)
            r.m[8] = -f.x; r.m[9] = -f.y; r.m[10] = -f.z; r.m[11] = f.dot(eye)
            r.m[12] = 0f; r.m[13] = 0f; r.m[14] = 0f; r.m[15] = 1f
            return r
        }
    }
}
 
