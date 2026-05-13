package cn.chen.assimp.render
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.LightLayer
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
class AIWorldProbe {
    private val id = Identifier.fromNamespaceAndPath("lwjgl-assimp", "dynamic/ai_world_probe_${System.identityHashCode(this)}")
    private var image: NativeImage? = null
    private var initialized = false
    private val origin = Vector3f()
    private var lastFrame = -REBUILD_INTERVAL
    private var frame = 0
    private var rebuildPhase = -1
    private val lastCenter = Vector3f(Float.NaN, Float.NaN, Float.NaN)
    private var ubo: GpuBuffer? = null
    private var uboMem: ByteBuffer? = null
    private var uboDirty = false
    private val sunDir = Vector3f(0.35f, 1f, 0.25f).normalize()
    private val skyTint = Vector3f(0.55f, 0.7f, 1.0f)
    private val sunTint = Vector3f(1f, 0.95f, 0.88f)
    private var skyFactor = 1f
    private var sunIntensity = 1f
    private var rainLevel = 0f
    fun init() {
        release()
        val img = NativeImage(NativeImage.Format.RGBA, TEX_DIM, TEX_DIM, false)
        for (y in 0 until TEX_DIM) for (x in 0 until TEX_DIM) img.setPixelABGR(x, y, 0x00808080)
        image = img
        val dt = DynamicTexture({ "ai_world_probe" }, img)
        Minecraft.getInstance().textureManager.register(id, dt)
        uboMem = MemoryUtil.memAlloc(UBO_BYTES)
        writeUboDefault(uboMem!!)
        ubo = RenderSystem.getDevice().createBuffer({ "ai_world_probe_ubo" }, GpuBuffer.USAGE_UNIFORM, uboMem!!)
        initialized = true
        uboDirty = false
    }
    fun setEnvironment(sky: Vector3f, sun: Vector3f, skyFac: Float, sunInt: Float, rain: Float) {
        skyTint.set(sky); sunTint.set(sun); skyFactor = skyFac; sunIntensity = sunInt; rainLevel = rain
    }
    fun rebuild(modelCenter: Vector3f, shadowEnabled: Boolean, shadowStrength: Float) {
        if (!initialized) return
        frame++
        if (shadowEnabled) {
            val dx = modelCenter.x - lastCenter.x; val dy = modelCenter.y - lastCenter.y; val dz = modelCenter.z - lastCenter.z
            val moved = !lastCenter.x.isFinite() || dx * dx + dy * dy + dz * dz > MOVE_THRESHOLD * MOVE_THRESHOLD
            if (rebuildPhase < 0 && (moved || frame - lastFrame >= REBUILD_INTERVAL)) {
                rebuildPhase = 0; lastCenter.set(modelCenter)
                val ox = (modelCenter.x - GRID / 2f).toInt(); val oy = (modelCenter.y - GRID / 2f).toInt(); val oz = (modelCenter.z - GRID / 2f).toInt()
                origin.set(ox.toFloat(), oy.toFloat(), oz.toFloat())
            }
            if (rebuildPhase >= 0) {
                sampleSlab(rebuildPhase)
                rebuildPhase++
                if (rebuildPhase >= SLAB_COUNT) {
                    rebuildPhase = -1; lastFrame = frame
                    (Minecraft.getInstance().textureManager.getTexture(id) as? DynamicTexture)?.upload()
                }
            }
        }
        writeUbo(shadowEnabled, shadowStrength)
    }
    private fun sampleSlab(phase: Int) {
        val img = image ?: return
        val level = Minecraft.getInstance().level ?: return
        val ox = origin.x.toInt(); val oy = origin.y.toInt(); val oz = origin.z.toInt()
        val pos = BlockPos.MutableBlockPos()
        val per = GRID / SLAB_COUNT
        val lzS = phase * per; val lzE = lzS + per
        for (lz in lzS until lzE) for (ly in 0 until GRID) for (lx in 0 until GRID) {
            pos.set(ox + lx, oy + ly, oz + lz)
            val bl = level.getBrightness(LightLayer.BLOCK, pos).coerceIn(0, 15)
            val sl = level.getBrightness(LightLayer.SKY, pos).coerceIn(0, 15)
            val state = level.getBlockState(pos)
            val opaque = if (state.canOcclude()) 255 else 0
            val r = bl * 17; val g = sl * 17; val b = opaque; val a = 0
            val sliceX = lz % SLICE_DIM; val sliceY = lz / SLICE_DIM
            val tx = sliceX * GRID + lx; val ty = sliceY * GRID + ly
            img.setPixelABGR(tx, ty, (a shl 24) or (b shl 16) or (g shl 8) or r)
        }
    }
    private fun writeUbo(shadowEnabled: Boolean, shadowStrength: Float) {
        val buf = uboMem ?: return
        buf.clear()
        buf.putFloat(origin.x).putFloat(origin.y).putFloat(origin.z).putFloat(1f)
        buf.putFloat(if (shadowEnabled) 1f else 0f).putFloat(shadowStrength).putFloat(rainLevel.coerceIn(0f, 1f)).putFloat(GRID.toFloat())
        buf.putFloat(sunDir.x).putFloat(sunDir.y).putFloat(sunDir.z).putFloat(0.6f)
        buf.putFloat(skyTint.x).putFloat(skyTint.y).putFloat(skyTint.z).putFloat(skyFactor)
        buf.putFloat(sunTint.x).putFloat(sunTint.y).putFloat(sunTint.z).putFloat(sunIntensity)
        buf.flip()
        uboDirty = true
    }
    fun flush(encoder: CommandEncoder) {
        if (!uboDirty) return
        uboDirty = false
        val buf = uboMem ?: return
        encoder.writeToBuffer(ubo!!.slice(), buf)
    }
    fun bind(rp: RenderPass) {
        if (!initialized) return
        val tex = Minecraft.getInstance().textureManager.getTexture(id) ?: return
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        rp.bindTexture("ProbeMap", tex.textureView, sampler)
    }
    fun uboSlice(): GpuBufferSlice? = ubo?.slice()
    fun setSunDir(x: Float, y: Float, z: Float) { sunDir.set(x, y, z).negate().normalize() }
    fun release() {
        if (initialized) {
            Minecraft.getInstance().textureManager.release(id)
            initialized = false
        }
        image = null
        ubo?.close(); ubo = null
        uboMem?.let { MemoryUtil.memFree(it) }; uboMem = null
        uboDirty = false
        lastCenter.set(Float.NaN, Float.NaN, Float.NaN); lastFrame = -REBUILD_INTERVAL; rebuildPhase = -1
    }
    companion object {
        const val GRID = 16
        const val SLICE_DIM = 4
        const val TEX_DIM = GRID * SLICE_DIM
        const val SLAB_COUNT = 4
        const val REBUILD_INTERVAL = 30
        const val MOVE_THRESHOLD = 1.0f
        const val UBO_BYTES = 16 * 5
        private fun writeUboDefault(buf: ByteBuffer) {
            for (i in 0 until UBO_BYTES / 4) buf.putFloat(0f)
            buf.flip()
        }
    }
}
