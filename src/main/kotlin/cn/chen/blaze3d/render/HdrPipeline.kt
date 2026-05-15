package cn.chen.blaze3d.render
import org.joml.Vector3f
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
data class HdrSettings(var exposure: Float = 1.0f, var bloomThreshold: Float = 1.0f, var bloomIntensity: Float = 0.6f, var bloomKnee: Float = 0.5f, var saturation: Float = 1.05f, var gamma: Float = 2.2f, var tonemapper: Tonemapper = Tonemapper.ACES_FITTED)
enum class Tonemapper { REINHARD, REINHARD_EXTENDED, ACES, ACES_FITTED, UNCHARTED2, AGX }
object ToneMapping {
    private fun reinhard(c: Vector3f): Vector3f = Vector3f(c.x / (1f + c.x), c.y / (1f + c.y), c.z / (1f + c.z))
    private fun reinhardExtended(c: Vector3f, whitePoint: Float = 4.0f): Vector3f {
        val w2 = whitePoint * whitePoint
        return Vector3f(c.x * (1f + c.x / w2) / (1f + c.x), c.y * (1f + c.y / w2) / (1f + c.y), c.z * (1f + c.z / w2) / (1f + c.z))
    }
    private fun aces(c: Vector3f): Vector3f {
        val a = 2.51f; val b = 0.03f; val cc = 2.43f; val d = 0.59f; val e = 0.14f
        return Vector3f(((c.x * (a * c.x + b)) / (c.x * (cc * c.x + d) + e)).coerceIn(0f, 1f), ((c.y * (a * c.y + b)) / (c.y * (cc * c.y + d) + e)).coerceIn(0f, 1f), ((c.z * (a * c.z + b)) / (c.z * (cc * c.z + d) + e)).coerceIn(0f, 1f))
    }
    private fun acesFitted(c: Vector3f): Vector3f {
        val input = Vector3f(0.59719f * c.x + 0.35458f * c.y + 0.04823f * c.z, 0.07600f * c.x + 0.90834f * c.y + 0.01566f * c.z, 0.02840f * c.x + 0.13383f * c.y + 0.83777f * c.z)
        val a = Vector3f(input.x * (input.x + 0.0245786f) - 0.000090537f, input.y * (input.y + 0.0245786f) - 0.000090537f, input.z * (input.z + 0.0245786f) - 0.000090537f)
        val b = Vector3f(input.x * (0.983729f * input.x + 0.4329510f) + 0.238081f, input.y * (0.983729f * input.y + 0.4329510f) + 0.238081f, input.z * (0.983729f * input.z + 0.4329510f) + 0.238081f)
        val mid = Vector3f(a.x / b.x, a.y / b.y, a.z / b.z)
        return Vector3f((1.60475f * mid.x - 0.53108f * mid.y - 0.07367f * mid.z).coerceIn(0f, 1f), (-0.10208f * mid.x + 1.10813f * mid.y - 0.00605f * mid.z).coerceIn(0f, 1f), (-0.00327f * mid.x - 0.07276f * mid.y + 1.07602f * mid.z).coerceIn(0f, 1f))
    }
    private fun uncharted2(c: Vector3f): Vector3f {
        fun u2(v: Float): Float { val A = 0.15f; val B = 0.50f; val C = 0.10f; val D = 0.20f; val E = 0.02f; val F = 0.30f; return ((v * (A * v + C * B) + D * E) / (v * (A * v + B) + D * F)) - E / F }
        val w = u2(11.2f)
        return Vector3f(u2(c.x * 2f) / w, u2(c.y * 2f) / w, u2(c.z * 2f) / w)
    }
    private fun agx(c: Vector3f): Vector3f {
        fun agxF(v: Float): Float {
            val x = v.coerceIn(0f, 1f)
            return ((x * (x * (x * 17.5128f - 4.4863f) + 0.8094f) + 0.04f) / (x * (x * (x * 12.6939f - 2.7028f) + 0.4567f) + 0.022f)).coerceIn(0f, 1f)
        }
        return Vector3f(agxF(c.x), agxF(c.y), agxF(c.z))
    }
    fun apply(color: Vector3f, settings: HdrSettings): Vector3f {
        val exposed = Vector3f(color).mul(settings.exposure)
        val mapped = when (settings.tonemapper) {
            Tonemapper.REINHARD -> reinhard(exposed)
            Tonemapper.REINHARD_EXTENDED -> reinhardExtended(exposed)
            Tonemapper.ACES -> aces(exposed)
            Tonemapper.ACES_FITTED -> acesFitted(exposed)
            Tonemapper.UNCHARTED2 -> uncharted2(exposed)
            Tonemapper.AGX -> agx(exposed)
        }
        val luminance = 0.2126f * mapped.x + 0.7152f * mapped.y + 0.0722f * mapped.z
        val saturated = Vector3f(luminance + (mapped.x - luminance) * settings.saturation, luminance + (mapped.y - luminance) * settings.saturation, luminance + (mapped.z - luminance) * settings.saturation)
        val invGamma = 1f / settings.gamma
        return Vector3f(saturated.x.pow(invGamma), saturated.y.pow(invGamma), saturated.z.pow(invGamma))
    }
    private fun Float.pow(p: Float): Float = if (this < 0f) 0f else this.toDouble().pow(p.toDouble()).toFloat()
}
class BloomChain(val mips: Int = 6, val baseWidth: Int = 1280, val baseHeight: Int = 720) {
    data class Mip(val width: Int, val height: Int)
    val descriptors: Array<Mip>
    init {
        descriptors = Array(mips) { Mip(max(1, baseWidth shr (it + 1)), max(1, baseHeight shr (it + 1))) }
    }
    fun mipExtent(index: Int): Pair<Int, Int>? { val mip = descriptors.getOrNull(index) ?: return null; return mip.width to mip.height }
}
class BloomPass(val chain: BloomChain) {
    fun gatherWeights(): FloatArray = FloatArray(chain.mips) { exp((-it.toFloat()).toDouble()).toFloat() }
    fun threshold(luminance: Float, knee: Float, base: Float): Float {
        val soft = (luminance - base + knee).coerceAtLeast(0f) * (luminance - base + knee).coerceAtLeast(0f) / (4f * knee + 1e-6f)
        return max(luminance - base, soft) / max(luminance, 1e-6f)
    }
}
class HdrPipeline(val settings: HdrSettings = HdrSettings(), val bloom: BloomChain = BloomChain()) {
    val bloomPass = BloomPass(bloom)
    fun composite(scene: Vector3f, bloomContribution: Vector3f): Vector3f {
        val combined = Vector3f(scene).add(Vector3f(bloomContribution).mul(settings.bloomIntensity))
        return ToneMapping.apply(combined, settings)
    }
    fun luminance(color: Vector3f): Float = 0.2126f * color.x + 0.7152f * color.y + 0.0722f * color.z
    fun status() = "hdr expo=${settings.exposure} bloom=${settings.bloomIntensity} tonemap=${settings.tonemapper}"
}
