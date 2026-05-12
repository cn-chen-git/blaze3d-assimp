package cn.chen.assimp.render
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
class AIShadowBuffer {
    private var gpuBuf: GpuBuffer? = null
    private var cpuBuf: ByteBuffer? = null
    private var dirty = false
    fun init() {
        release()
        cpuBuf = MemoryUtil.memAlloc(BYTES)
        writeDefault(cpuBuf!!)
        gpuBuf = RenderSystem.getDevice().createBuffer({ "ai_shadow" }, GpuBuffer.USAGE_UNIFORM, cpuBuf!!)
    }
    fun update(center: Vector3f, radius: Float, camDistance: Float, lightDir: Vector3f, atlasSize: Int) {
        if (gpuBuf == null) init()
        val buf = cpuBuf ?: return
        buf.clear()
        writeCascades(buf, center, radius, camDistance, lightDir, atlasSize)
        dirty = true
    }
    fun flush(encoder: CommandEncoder) {
        if (!dirty) return
        dirty = false
        val buf = cpuBuf ?: return
        encoder.writeToBuffer(gpuBuf!!.slice(), buf)
    }
    fun slice(): GpuBufferSlice? = gpuBuf?.slice()
    fun release() {
        gpuBuf?.close(); gpuBuf = null
        cpuBuf?.let { MemoryUtil.memFree(it) }; cpuBuf = null
        dirty = false
    }
    companion object {
        const val CASCADES = 2
        const val BYTES = 64 * CASCADES + 16 + 16
        private fun writeDefault(buf: ByteBuffer) {
            for (i in 0 until CASCADES) {
                buf.position(i * 64)
                Matrix4f().get(buf)
            }
            buf.position(64 * CASCADES)
            buf.putFloat(24f).putFloat(64f).putFloat(144f).putFloat(320f)
            buf.putFloat(0f).putFloat(2048f).putFloat(0.0012f).putFloat(1.0f)
            buf.flip()
        }
        private fun writeCascades(buf: ByteBuffer, center: Vector3f, radius: Float, camDistance: Float, lightDir: Vector3f, atlasSize: Int) {
            val base = max(radius, 8f)
            val mul = floatArrayOf(1.0f, 2.2f)
            for (i in 0 until CASCADES) {
                buf.position(i * 64)
                lightMatrix(center, base * mul[i], lightDir).get(buf)
            }
            val d = max(camDistance, 1f)
            buf.position(64 * CASCADES)
            buf.putFloat(d + base * 0.3f).putFloat(d + base * 2.5f).putFloat(d + base * 2.5f).putFloat(d + base * 2.5f)
            buf.putFloat(1f).putFloat(atlasSize.toFloat()).putFloat(0.0015f).putFloat(0.0f)
            buf.flip()
        }
        private fun lightMatrix(center: Vector3f, radius: Float, lightDir: Vector3f): Matrix4f {
            val dir = Vector3f(lightDir)
            if (dir.lengthSquared() < 0.0001f) dir.set(-0.35f, -1f, -0.25f)
            dir.normalize()
            val eye = Vector3f(center).sub(Vector3f(dir).mul(radius * 3f))
            val up = if (abs(dir.y) > 0.92f) Vector3f(0f, 0f, 1f) else Vector3f(0f, 1f, 0f)
            val view = Matrix4f().lookAt(eye, center, up)
            val proj = Matrix4f().ortho(-radius, radius, -radius, radius, 0.1f, radius * 8f)
            return proj.mul(view)
        }
    }
}
