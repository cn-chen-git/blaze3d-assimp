package cn.chen.blaze3d.math
import org.joml.Matrix4f
import java.nio.FloatBuffer
import kotlin.math.tan
class Mat4(val m: FloatArray = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)) {
    operator fun times(o: Mat4): Mat4 { val r = Mat4(FloatArray(16)); mulInto(o, r); return r }
    fun mulInto(o: Mat4, out: Mat4) {
        val a = m; val b = o.m; val r = out.m
        val a00=a[0]; val a01=a[1]; val a02=a[2]; val a03=a[3]
        val a10=a[4]; val a11=a[5]; val a12=a[6]; val a13=a[7]
        val a20=a[8]; val a21=a[9]; val a22=a[10]; val a23=a[11]
        val a30=a[12]; val a31=a[13]; val a32=a[14]; val a33=a[15]
        val b00=b[0]; val b01=b[1]; val b02=b[2]; val b03=b[3]
        val b10=b[4]; val b11=b[5]; val b12=b[6]; val b13=b[7]
        val b20=b[8]; val b21=b[9]; val b22=b[10]; val b23=b[11]
        val b30=b[12]; val b31=b[13]; val b32=b[14]; val b33=b[15]
        r[0]=a00*b00+a01*b10+a02*b20+a03*b30; r[1]=a00*b01+a01*b11+a02*b21+a03*b31
        r[2]=a00*b02+a01*b12+a02*b22+a03*b32; r[3]=a00*b03+a01*b13+a02*b23+a03*b33
        r[4]=a10*b00+a11*b10+a12*b20+a13*b30; r[5]=a10*b01+a11*b11+a12*b21+a13*b31
        r[6]=a10*b02+a11*b12+a12*b22+a13*b32; r[7]=a10*b03+a11*b13+a12*b23+a13*b33
        r[8]=a20*b00+a21*b10+a22*b20+a23*b30; r[9]=a20*b01+a21*b11+a22*b21+a23*b31
        r[10]=a20*b02+a21*b12+a22*b22+a23*b32; r[11]=a20*b03+a21*b13+a22*b23+a23*b33
        r[12]=a30*b00+a31*b10+a32*b20+a33*b30; r[13]=a30*b01+a31*b11+a32*b21+a33*b31
        r[14]=a30*b02+a31*b12+a32*b22+a33*b32; r[15]=a30*b03+a31*b13+a32*b23+a33*b33
    }
    fun copyFrom(o: Mat4) = apply { System.arraycopy(o.m, 0, m, 0, 16) }
    fun setIdentity() = apply { val r = m; r[0]=1f;r[1]=0f;r[2]=0f;r[3]=0f; r[4]=0f;r[5]=1f;r[6]=0f;r[7]=0f; r[8]=0f;r[9]=0f;r[10]=1f;r[11]=0f; r[12]=0f;r[13]=0f;r[14]=0f;r[15]=1f }
    fun transformPoint(v: Vec3): Vec3 {
        val w = m[12] * v.x + m[13] * v.y + m[14] * v.z + m[15]
        return Vec3(
            (m[0] * v.x + m[1] * v.y + m[2] * v.z + m[3]) / w,
            (m[4] * v.x + m[5] * v.y + m[6] * v.z + m[7]) / w,
            (m[8] * v.x + m[9] * v.y + m[10] * v.z + m[11]) / w
        )
    }
    fun transformPointInto(x: Float, y: Float, z: Float, out: Vec3): Vec3 {
        val w = m[12] * x + m[13] * y + m[14] * z + m[15]
        out.x = (m[0] * x + m[1] * y + m[2] * z + m[3]) / w
        out.y = (m[4] * x + m[5] * y + m[6] * z + m[7]) / w
        out.z = (m[8] * x + m[9] * y + m[10] * z + m[11]) / w
        return out
    }
    fun transformDir(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[4] * v.x + m[5] * v.y + m[6] * v.z,
        m[8] * v.x + m[9] * v.y + m[10] * v.z
    )
    fun inverse(): Mat4 { val r = Mat4(FloatArray(16)); inverseInto(r); return r }
    fun inverseInto(out: Mat4): Boolean {
        val inv = out.m
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
        if (det == 0f) { out.setIdentity(); return false }
        val invDet = 1f / det
        for (i in 0..15) inv[i] *= invDet
        return true
    }
    fun transpose(): Mat4 {
        val r = FloatArray(16)
        for (i in 0..3) for (j in 0..3) r[i * 4 + j] = m[j * 4 + i]
        return Mat4(r)
    }
    fun toJoml() = Matrix4f(m[0],m[4],m[8],m[12], m[1],m[5],m[9],m[13], m[2],m[6],m[10],m[14], m[3],m[7],m[11],m[15])
    fun putTo(buf: FloatBuffer) { for (v in m) buf.put(v) }
    fun toFloatArray() = m.copyOf()
    companion object {
        fun identity() = Mat4()
        fun translation(x: Float, y: Float, z: Float) = Mat4(floatArrayOf(1f,0f,0f,x, 0f,1f,0f,y, 0f,0f,1f,z, 0f,0f,0f,1f))
        fun scaling(x: Float, y: Float, z: Float) = Mat4(floatArrayOf(x,0f,0f,0f, 0f,y,0f,0f, 0f,0f,z,0f, 0f,0f,0f,1f))
        fun trsInto(px: Float, py: Float, pz: Float, qx: Float, qy: Float, qz: Float, qw: Float, sx: Float, sy: Float, sz: Float, out: Mat4) {
            val xx = qx*qx; val yy = qy*qy; val zz = qz*qz
            val xy = qx*qy; val xz = qx*qz; val yz = qy*qz
            val wx = qw*qx; val wy = qw*qy; val wz = qw*qz
            val m = out.m
            m[0] = (1f-2f*(yy+zz))*sx; m[1] = 2f*(xy+wz)*sy; m[2] = 2f*(xz-wy)*sz; m[3] = px
            m[4] = 2f*(xy-wz)*sx; m[5] = (1f-2f*(xx+zz))*sy; m[6] = 2f*(yz+wx)*sz; m[7] = py
            m[8] = 2f*(xz+wy)*sx; m[9] = 2f*(yz-wx)*sy; m[10] = (1f-2f*(xx+yy))*sz; m[11] = pz
            m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
        }
        fun perspective(fov: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val t = tan(fov * 0.5f)
            val r = Mat4(FloatArray(16))
            r.m[0] = 1f / (aspect * t); r.m[5] = 1f / t
            r.m[10] = -(far + near) / (far - near); r.m[11] = -2f * far * near / (far - near)
            r.m[14] = -1f; r.m[15] = 0f
            return r
        }
        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalize()
            val s = f.cross(up).normalize()
            val u = s.cross(f)
            val r = Mat4()
            r.m[0] = s.x; r.m[1] = s.y; r.m[2] = s.z; r.m[3] = -s.dot(eye)
            r.m[4] = u.x; r.m[5] = u.y; r.m[6] = u.z; r.m[7] = -u.dot(eye)
            r.m[8] = -f.x; r.m[9] = -f.y; r.m[10] = -f.z; r.m[11] = f.dot(eye)
            r.m[12] = 0f; r.m[13] = 0f; r.m[14] = 0f; r.m[15] = 1f
            return r
        }
    }
}
