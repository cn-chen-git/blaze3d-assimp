package cn.chen.assimp.render
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
class AIShadowMap {
    private var tex: GpuTexture? = null
    private var texView: GpuTextureView? = null
    val size = 1024
    fun init() {
        release()
        val usage = GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST
        tex = RenderSystem.getDevice().createTexture({ "ai_shadow_atlas" }, usage, GpuFormat.D32_FLOAT, size, size, 1, 1)
        texView = RenderSystem.getDevice().createTextureView(tex!!)
    }
    fun texture(): GpuTexture? = tex
    fun view(): GpuTextureView? = texView
    fun sampler() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    fun release() {
        texView?.close(); texView = null
        tex?.close(); tex = null
    }
}
