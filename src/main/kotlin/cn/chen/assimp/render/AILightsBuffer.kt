package cn.chen.assimp.render
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class AILightsBuffer {
    private var gpuBuf: GpuBuffer? = null
    private var cpuBuf: ByteBuffer? = null
    private var dirty = false
    fun init() {
        release()
        cpuBuf = MemoryUtil.memAlloc(BYTES)
        writeDefault(cpuBuf!!)
        gpuBuf = RenderSystem.getDevice().createBuffer({ "ai_lights" }, GpuBuffer.USAGE_UNIFORM, cpuBuf!!)
        dirty = false
    }
    fun update(camPos: Vector3f, playerEnabled: Boolean, playerPos: Vector3f, playerColor: Vector3f, playerIntensity: Float, playerRadius: Float, lights: List<AILightCollector.Light>, rimIntensity: Float, bloomIntensity: Float) {
        if (gpuBuf == null) init()
        val buf = cpuBuf ?: return
        buf.clear()
        val count = minOf(lights.size, MAX_LIGHTS)
        buf.putFloat(count.toFloat()).putFloat(if (playerEnabled) 1f else 0f).putFloat(bloomIntensity).putFloat(rimIntensity)
        buf.putFloat(camPos.x).putFloat(camPos.y).putFloat(camPos.z).putFloat(0f)
        buf.putFloat(playerPos.x).putFloat(playerPos.y + 0.9f).putFloat(playerPos.z).putFloat(playerRadius)
        buf.putFloat(playerColor.x).putFloat(playerColor.y).putFloat(playerColor.z).putFloat(playerIntensity)
        for (i in 0 until MAX_LIGHTS) {
            if (i < count) { val l = lights[i]; buf.putFloat(l.x).putFloat(l.y).putFloat(l.z).putFloat(l.range) } else { buf.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f) }
        }
        for (i in 0 until MAX_LIGHTS) {
            if (i < count) { val l = lights[i]; buf.putFloat(l.r).putFloat(l.g).putFloat(l.b).putFloat(l.intensity) } else { buf.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f) }
        }
        buf.flip()
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
        const val MAX_LIGHTS = 8
        const val BYTES = 16 * (4 + MAX_LIGHTS * 2)
        private fun writeDefault(buf: ByteBuffer) {
            for (i in 0 until BYTES / 4) buf.putFloat(0f)
            buf.flip()
        }
    }
}
