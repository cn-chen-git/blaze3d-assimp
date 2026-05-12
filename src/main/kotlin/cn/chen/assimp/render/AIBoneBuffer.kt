package cn.chen.assimp.render
import cn.chen.assimp.core.AIBonePose
import cn.chen.assimp.core.AISceneData
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class AIBoneBuffer {
    private var gpuBuf: GpuBuffer? = null
    private var cpuBuf: ByteBuffer? = null
    private var boneCount = 0
    private var bonePose: AIBonePose? = null
    private var dirty = false
    fun init(scene: AISceneData) {
        release()
        bonePose = scene.skeleton
        boneCount = scene.skeleton?.boneCount ?: 0
        val count = boneCount.coerceAtLeast(1).coerceAtMost(AIPipelines.MAX_BONES)
        cpuBuf = MemoryUtil.memAlloc(count * 64)
        val buf = cpuBuf!!
        for (i in 0 until count) writeIdentity(buf, i * 64)
        buf.position(0)
        gpuBuf = RenderSystem.getDevice().createBuffer({ "ai_bones" }, GpuBuffer.USAGE_UNIFORM, buf)
    }
    fun update() {
        val buf = cpuBuf ?: return
        val pose = bonePose ?: return
        val count = boneCount.coerceAtMost(AIPipelines.MAX_BONES)
        buf.clear()
        val matrices = pose.boneMatrices
        for (i in 0 until count) {
            if (i < matrices.size) {
                val m = matrices[i].m
                buf.putFloat(m[0]).putFloat(m[4]).putFloat(m[8]).putFloat(m[12])
                buf.putFloat(m[1]).putFloat(m[5]).putFloat(m[9]).putFloat(m[13])
                buf.putFloat(m[2]).putFloat(m[6]).putFloat(m[10]).putFloat(m[14])
                buf.putFloat(m[3]).putFloat(m[7]).putFloat(m[11]).putFloat(m[15])
            } else writeIdentity(buf, i * 64)
        }
        buf.flip()
        dirty = true
    }
    fun flush(encoder: CommandEncoder) {
        if (!dirty) return
        dirty = false
        val buf = cpuBuf ?: return
        val gpu = gpuBuf ?: return
        encoder.writeToBuffer(gpu.slice(), buf)
    }
    fun slice(): GpuBufferSlice? = gpuBuf?.slice()
    fun hasBones() = boneCount > 0
    fun release() {
        gpuBuf?.close(); gpuBuf = null
        cpuBuf?.let { MemoryUtil.memFree(it) }; cpuBuf = null
        boneCount = 0; bonePose = null; dirty = false
    }
    companion object {
        private fun writeIdentity(buf: ByteBuffer, offset: Int) {
            buf.position(offset)
            buf.putFloat(1f).putFloat(0f).putFloat(0f).putFloat(0f)
            buf.putFloat(0f).putFloat(1f).putFloat(0f).putFloat(0f)
            buf.putFloat(0f).putFloat(0f).putFloat(1f).putFloat(0f)
            buf.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f)
        }
    }
}
