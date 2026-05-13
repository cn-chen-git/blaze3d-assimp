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
    private var lastBl = -1
    private var lastSl = -1
    private var dirty = false
    fun init() {
        release()
        cpuBuf = MemoryUtil.memAlloc(BYTES)
        lastMat.identity(); lastBl = 15; lastSl = 15
        write(lastMat, lastBl, lastSl)
        gpuBuf = RenderSystem.getDevice().createBuffer({ "ai_object" }, GpuBuffer.USAGE_UNIFORM, cpuBuf!!)
        dirty = false
    }
    fun update(mat: Matrix4f, blockLight: Int, skyLight: Int) {
        if (gpuBuf == null) init()
        val bl = blockLight.coerceIn(0, 15); val sl = skyLight.coerceIn(0, 15)
        if (lastMat.equals(mat) && lastBl == bl && lastSl == sl) return
        lastMat.set(mat); lastBl = bl; lastSl = sl
        write(mat, bl, sl)
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
        dirty = false; lastBl = -1; lastSl = -1
    }
    private fun write(mat: Matrix4f, bl: Int, sl: Int) {
        val buf = cpuBuf ?: return
        buf.clear(); mat.get(buf); buf.position(64)
        buf.putFloat(bl.toFloat()).putFloat(sl.toFloat()).putFloat(1f).putFloat(0f)
        buf.flip()
    }
    companion object { const val BYTES = 80 }
}
