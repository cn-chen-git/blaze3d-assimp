package cn.chen.assimp.render
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.material.AIPbrMat
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class AIMaterialBuffer {
    private val buffers = mutableListOf<GpuBuffer>()
    fun init(scene: AISceneData) {
        release()
        val device = RenderSystem.getDevice()
        for ((i, m) in scene.materials.withIndex()) {
            val buf = MemoryUtil.memAlloc(MAT_BYTES)
            writeMaterial(buf, m)
            buf.flip()
            buffers.add(device.createBuffer({ "ai_mat_$i" }, GpuBuffer.USAGE_UNIFORM, buf))
            MemoryUtil.memFree(buf)
        }
        if (buffers.isEmpty()) {
            val buf = MemoryUtil.memAlloc(MAT_BYTES)
            writeDefault(buf); buf.flip()
            buffers.add(device.createBuffer({ "ai_mat_default" }, GpuBuffer.USAGE_UNIFORM, buf))
            MemoryUtil.memFree(buf)
        }
    }
    fun slice(matIdx: Int): GpuBufferSlice = buffers[matIdx.coerceIn(0, buffers.size - 1)].slice()
    fun release() { for (b in buffers) b.close(); buffers.clear() }
    companion object {
        const val MAT_BYTES = 80
        private fun writeMaterial(buf: ByteBuffer, m: AIPbrMat) {
            buf.putFloat(m.baseColorFactor.x).putFloat(m.baseColorFactor.y).putFloat(m.baseColorFactor.z).putFloat(m.baseColorFactor.w)
            val es = m.khrExtensions.emissiveStrength?.strength ?: m.emissiveStrength
            buf.putFloat(m.emissiveFactor.getOrElse(0) { 0f }).putFloat(m.emissiveFactor.getOrElse(1) { 0f }).putFloat(m.emissiveFactor.getOrElse(2) { 0f }).putFloat(es)
            buf.putFloat(m.metallicFactor).putFloat(m.roughnessFactor).putFloat(m.normalScale).putFloat(m.alphaCutoff)
            val cc = m.khrExtensions.clearcoat
            val sh = m.khrExtensions.sheen
            val tr = m.khrExtensions.transmission
            buf.putFloat(m.ior).putFloat(cc?.factor ?: 0f).putFloat(if (sh != null) 1f else 0f).putFloat(tr?.factor ?: 0f)
            buf.putFloat(sh?.colorFactor?.x ?: 0f).putFloat(sh?.colorFactor?.y ?: 0f).putFloat(sh?.colorFactor?.z ?: 0f).putFloat(cc?.roughnessFactor ?: 0f)
        }
        private fun writeDefault(buf: ByteBuffer) {
            buf.putFloat(1f).putFloat(1f).putFloat(1f).putFloat(1f)
            buf.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f)
            buf.putFloat(0f).putFloat(1f).putFloat(1f).putFloat(0.5f)
            buf.putFloat(1.5f).putFloat(0f).putFloat(0f).putFloat(0f)
            buf.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f)
        }
    }
}
