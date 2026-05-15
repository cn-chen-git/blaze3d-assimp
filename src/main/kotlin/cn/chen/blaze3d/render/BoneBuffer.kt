package cn.chen.blaze3d.render
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.core.SceneData
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class BoneBuffer {
    private var gpuBuf: GpuBuffer? = null
    private var cpuBuf: ByteBuffer? = null
    private var boneCount = 0
    private var bonePose: BonePose? = null
    private var dirty = false
    private var lastRevision = Int.MIN_VALUE
    var skippedUploads = 0; private set
    var uploadedFrames = 0; private set
    fun init(scene: SceneData) {
        release()
        bonePose = scene.skeleton
        boneCount = scene.skeleton?.boneCount ?: 0
        val count = boneCount.coerceAtLeast(1).coerceAtMost(Pipelines.MAX_BONES)
        val buf = MemoryUtil.memAlloc(count * 64)
        cpuBuf = buf
        for (i in 0 until count) writeIdentity(buf, i * 64)
        buf.position(0)
        gpuBuf = RenderSystem.getDevice().createBuffer({ "model_bones" }, GpuBuffer.USAGE_UNIFORM, buf)
        lastRevision = Int.MIN_VALUE
        dirty = true
    }
    fun update() {
        val buf = cpuBuf ?: return
        val pose = bonePose ?: return
        if (pose.revision == lastRevision) { skippedUploads++; return }
        lastRevision = pose.revision
        val count = boneCount.coerceAtMost(Pipelines.MAX_BONES)
        val matrices = pose.boneMatrices
        val filled = minOf(count, matrices.size)
        buf.clear()
        for (i in 0 until filled) {
            val m = matrices[i].m
            buf.putFloat(m[0]).putFloat(m[4]).putFloat(m[8]).putFloat(m[12])
            buf.putFloat(m[1]).putFloat(m[5]).putFloat(m[9]).putFloat(m[13])
            buf.putFloat(m[2]).putFloat(m[6]).putFloat(m[10]).putFloat(m[14])
            buf.putFloat(m[3]).putFloat(m[7]).putFloat(m[11]).putFloat(m[15])
        }
        for (i in filled until count) writeIdentity(buf, i * 64)
        buf.flip()
        dirty = true
    }
    fun flush(encoder: CommandEncoder) {
        if (!dirty) { skippedUploads++; return }
        dirty = false
        val buf = cpuBuf ?: return
        val gpu = gpuBuf ?: return
        encoder.writeToBuffer(gpu.slice(), buf)
        uploadedFrames++
    }
    fun slice(): GpuBufferSlice? = gpuBuf?.slice()
    fun hasBones() = boneCount > 0
    fun release() {
        gpuBuf?.close(); gpuBuf = null
        cpuBuf?.let { MemoryUtil.memFree(it) }; cpuBuf = null
        boneCount = 0; bonePose = null; dirty = false; lastRevision = Int.MIN_VALUE; skippedUploads = 0; uploadedFrames = 0
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
