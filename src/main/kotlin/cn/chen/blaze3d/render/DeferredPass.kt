package cn.chen.blaze3d.render
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.atomic.AtomicLong
enum class GBufferAttachment(val index: Int, val label: String) { ALBEDO(0, "model_gbuffer_albedo"), NORMAL(1, "model_gbuffer_normal"), MATERIAL(2, "model_gbuffer_material"), MOTION(3, "model_gbuffer_motion") }
data class DeferredSize(val width: Int, val height: Int) { val pixelCount get() = width.toLong() * height }
class DeferredGBuffer(val size: DeferredSize) {
    private val attachmentIds = HashMap<GBufferAttachment, Identifier>()
    var prepared: Boolean = false; private set
    fun id(attachment: GBufferAttachment): Identifier? = attachmentIds[attachment]
    fun bindIds(albedo: Identifier, normal: Identifier, material: Identifier, motion: Identifier) {
        attachmentIds[GBufferAttachment.ALBEDO] = albedo
        attachmentIds[GBufferAttachment.NORMAL] = normal
        attachmentIds[GBufferAttachment.MATERIAL] = material
        attachmentIds[GBufferAttachment.MOTION] = motion
        prepared = true
    }
    fun release() { attachmentIds.clear(); prepared = false }
}
data class DeferredLightParams(val sunDirection: Vector3f, val sunColor: Vector3f, val ambient: Vector3f, val emissiveBoost: Float, val shadowStrength: Float, val ssaoRadius: Float)
class DeferredPass(val gbuffer: DeferredGBuffer) {
    private val frameCounter = AtomicLong()
    var lastFrame = 0L; private set
    private val previousViewProjection = Matrix4f()
    private val currentViewProjection = Matrix4f()
    var motionMagnitude: Float = 0f; private set
    fun beginFrame(viewProjection: Matrix4f) {
        previousViewProjection.set(currentViewProjection)
        currentViewProjection.set(viewProjection)
        lastFrame = frameCounter.incrementAndGet()
    }
    fun motionVectorFor(world: Vector3f): Vector4f {
        val previous = Vector4f(world.x, world.y, world.z, 1f).mul(previousViewProjection)
        val current = Vector4f(world.x, world.y, world.z, 1f).mul(currentViewProjection)
        val dx = (current.x / current.w) - (previous.x / previous.w)
        val dy = (current.y / current.w) - (previous.y / previous.w)
        motionMagnitude = kotlin.math.max(motionMagnitude, kotlin.math.sqrt(dx * dx + dy * dy))
        return Vector4f(dx, dy, 0f, 1f)
    }
    fun lightFactors(params: DeferredLightParams, normal: Vector3f): Vector3f {
        val nDotL = kotlin.math.max(0f, normal.dot(params.sunDirection))
        val diffuse = Vector3f(params.sunColor).mul(nDotL)
        return diffuse.add(params.ambient)
    }
    fun status() = "deferred frames=${frameCounter.get()} motion=${motionMagnitude} size=${gbuffer.size.width}x${gbuffer.size.height}"
}
object DeferredShaders {
    fun acquireDefaultParams(): DeferredLightParams = DeferredLightParams(
        sunDirection = Vector3f(0.3f, 1f, 0.4f).normalize(),
        sunColor = Vector3f(1.05f, 0.95f, 0.85f),
        ambient = Vector3f(0.18f, 0.2f, 0.24f),
        emissiveBoost = 1.4f,
        shadowStrength = 0.65f,
        ssaoRadius = 0.6f
    )
    fun samplerFor(): FilterMode = FilterMode.LINEAR
}
object DeferredAttachments {
    private val knownAttachments = HashMap<Identifier, GBufferAttachment>()
    fun mark(attachment: GBufferAttachment, id: Identifier) { knownAttachments[id] = attachment }
    fun classify(id: Identifier): GBufferAttachment? = knownAttachments[id]
    fun clear() { knownAttachments.clear() }
}
class DeferredSession {
    private val gbuffer = DeferredGBuffer(DeferredSize(Minecraft.getInstance().window.width, Minecraft.getInstance().window.height))
    val pass: DeferredPass = DeferredPass(gbuffer)
    fun beginFrame(viewProjection: Matrix4f) { pass.beginFrame(viewProjection) }
    fun computeContribution(normal: Vector3f, params: DeferredLightParams): Vector3f = pass.lightFactors(params, normal)
    fun status(): String = pass.status()
}
