package cn.chen.assimp.render
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class AIObjectBuffer {
    private var gpuBuf: GpuBuffer? = null
    private var cpuBuf: ByteBuffer? = null
    private val lastMat = Matrix4f()
    private var dirty = false
    fun init() {
        release()
        cpuBuf = MemoryUtil.memAlloc(64)
        lastMat.identity()
        writeToCpu(lastMat)
        gpuBuf = RenderSystem.getDevice().createBuffer({ "ai_object" }, GpuBuffer.USAGE_UNIFORM, cpuBuf!!)
    }
    fun update(mat: Matrix4f) {
        if (gpuBuf == null) init()
        if (lastMat.equals(mat)) return
        lastMat.set(mat)
        writeToCpu(mat)
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
    private fun writeToCpu(mat: Matrix4f) {
        val buf = cpuBuf ?: return
        buf.clear(); mat.get(buf); buf.position(64).flip()
    }
}
