package cn.chen.assimp.render
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.*
class AIEnvironmentMap {
    fun init() = ensureInitialized()
    fun bind(rp: RenderPass) = bindGlobal(rp)
    fun release() {}
    companion object {
        private val IRRADIANCE_ID = Identifier.fromNamespaceAndPath("lwjgl-assimp", "dynamic/ai_irradiance")
        private val PREFILTER_ID = Identifier.fromNamespaceAndPath("lwjgl-assimp", "dynamic/ai_prefilter")
        private val BRDF_ID = Identifier.fromNamespaceAndPath("lwjgl-assimp", "dynamic/ai_brdf_lut")
        private var initialized = false
        fun ensureInitialized() {
            if (initialized) return
            val tm = Minecraft.getInstance().textureManager
            tm.register(IRRADIANCE_ID, DynamicTexture({ "ai_irradiance" }, genIrradiance(128, 64)))
            tm.register(PREFILTER_ID, DynamicTexture({ "ai_prefilter" }, genPrefilterAtlas(128, 64, MIP_LEVELS)))
            tm.register(BRDF_ID, DynamicTexture({ "ai_brdf_lut" }, genBrdfLut(128)))
            initialized = true
        }
        fun bindGlobal(rp: RenderPass) {
            val tm = Minecraft.getInstance().textureManager
            val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            tm.getTexture(IRRADIANCE_ID)?.let { rp.bindTexture("IrradianceMap", it.textureView, sampler) }
            tm.getTexture(PREFILTER_ID)?.let { rp.bindTexture("PrefilterMap", it.textureView, sampler) }
            tm.getTexture(BRDF_ID)?.let { rp.bindTexture("BrdfLut", it.textureView, sampler) }
        }
        fun disposeGlobal() {
            if (!initialized) return
            val tm = Minecraft.getInstance().textureManager
            tm.release(IRRADIANCE_ID); tm.release(PREFILTER_ID); tm.release(BRDF_ID)
            initialized = false
        }
        private const val PI = Math.PI.toFloat()
        private const val RGBM_RANGE = 8f
        const val MIP_LEVELS = 5
        private fun skyHDR(dir: FloatArray): FloatArray {
            val y = dir[1].coerceIn(-1f, 1f)
            val sunDir = floatArrayOf(0.35f, 0.75f, 0.25f); normalize3(sunDir)
            val cosAngle = dot3(dir, sunDir).coerceIn(-1f, 1f)
            if (y < -0.01f) {
                val t = (-y).coerceIn(0f, 1f)
                return floatArrayOf(lerp(0.4f, 0.08f, t), lerp(0.35f, 0.06f, t), lerp(0.3f, 0.05f, t))
            }
            val t = y.coerceIn(0f, 1f)
            val skyR = lerp(1.1f, 0.3f, t * t)
            val skyG = lerp(0.95f, 0.45f, t * t)
            val skyB = lerp(0.8f, 1.2f, t)
            val sunCore = cosAngle.coerceAtLeast(0f).pow(256f) * 8.0f
            val sunGlow = cosAngle.coerceAtLeast(0f).pow(64f) * 3.0f
            val sunHalo = cosAngle.coerceAtLeast(0f).pow(8f) * 0.6f
            return floatArrayOf(
                skyR + sunCore + sunGlow * 1.0f + sunHalo * 0.9f,
                skyG + sunCore * 0.95f + sunGlow * 0.85f + sunHalo * 0.7f,
                skyB + sunCore * 0.8f + sunGlow * 0.5f + sunHalo * 0.35f
            )
        }
        private fun encodeRGBM(r: Float, g: Float, b: Float): Int {
            val maxC = maxOf(r, g, b, 1e-6f)
            val m = (maxC / RGBM_RANGE).coerceIn(0f, 1f)
            val mQ = (ceil(m * 255f) / 255f).coerceAtLeast(1f / 255f)
            val s = 1f / (mQ * RGBM_RANGE)
            val ri = ((r * s).coerceIn(0f, 1f) * 255f).toInt()
            val gi = ((g * s).coerceIn(0f, 1f) * 255f).toInt()
            val bi = ((b * s).coerceIn(0f, 1f) * 255f).toInt()
            val ai = (mQ * 255f).toInt().coerceIn(0, 255)
            return (ai shl 24) or (bi shl 16) or (gi shl 8) or ri
        }
        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun uvToDir(u: Float, v: Float): FloatArray {
            val theta = (1f - v) * PI
            val phi = (u * 2f - 1f) * PI
            val sinT = sin(theta)
            return floatArrayOf(sinT * cos(phi), cos(theta), sinT * sin(phi))
        }
        private fun genIrradiance(w: Int, h: Int): NativeImage {
            val img = NativeImage(NativeImage.Format.RGBA, w, h, false)
            val samples = 512
            for (py in 0 until h) for (px in 0 until w) {
                val n = uvToDir(px.toFloat() / (w - 1), py.toFloat() / (h - 1))
                val up = if (abs(n[1]) < 0.999f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
                val tx = cross(up, n); normalize3(tx)
                val ty = cross(n, tx)
                var r = 0f; var g = 0f; var b = 0f
                for (i in 0 until samples) {
                    val xi = hammersley(i, samples)
                    val cosT = sqrt(1f - xi[1]); val sinT = sqrt(xi[1])
                    val phi = 2f * PI * xi[0]
                    val lx = sinT * cos(phi); val ly = sinT * sin(phi); val lz = cosT
                    val dir = floatArrayOf(tx[0]*lx+ty[0]*ly+n[0]*lz, tx[1]*lx+ty[1]*ly+n[1]*lz, tx[2]*lx+ty[2]*ly+n[2]*lz)
                    val c = skyHDR(dir)
                    r += c[0]; g += c[1]; b += c[2]
                }
                val inv = 1f / samples
                img.setPixelABGR(px, py, encodeRGBM(r * inv, g * inv, b * inv))
            }
            return img
        }
        private fun genPrefilterAtlas(w: Int, levelH: Int, levels: Int): NativeImage {
            val img = NativeImage(NativeImage.Format.RGBA, w, levelH * levels, false)
            val samples = 256
            for (lv in 0 until levels) {
                val roughness = lv.toFloat() / (levels - 1).coerceAtLeast(1)
                val a = roughness.coerceAtLeast(0.01f).let { it * it }
                val yOff = lv * levelH
                for (py in 0 until levelH) for (px in 0 until w) {
                    val n = uvToDir(px.toFloat() / (w - 1), py.toFloat() / (levelH - 1))
                    val v = n.copyOf()
                    val up = if (abs(n[1]) < 0.999f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
                    val tx = cross(up, n); normalize3(tx)
                    val ty = cross(n, tx)
                    var r = 0f; var g = 0f; var b = 0f; var tw = 0f
                    for (i in 0 until samples) {
                        val xi = hammersley(i, samples)
                        val hv = importanceSampleGGX(xi, a, tx, ty, n)
                        val vdh = dot3(v, hv).coerceIn(0f, 1f)
                        val l = floatArrayOf(2f*vdh*hv[0]-v[0], 2f*vdh*hv[1]-v[1], 2f*vdh*hv[2]-v[2])
                        val ndl = dot3(n, l)
                        if (ndl > 0f) { val c = skyHDR(l); r += c[0]*ndl; g += c[1]*ndl; b += c[2]*ndl; tw += ndl }
                    }
                    if (tw > 0f) { r /= tw; g /= tw; b /= tw }
                    img.setPixelABGR(px, yOff + py, encodeRGBM(r, g, b))
                }
            }
            return img
        }
        private fun genBrdfLut(size: Int): NativeImage {
            val img = NativeImage(NativeImage.Format.RGBA, size, size, false)
            val samples = 1024
            for (py in 0 until size) for (px in 0 until size) {
                val ndv = (px.toFloat() / (size - 1)).coerceAtLeast(1e-4f)
                val roughness = (py.toFloat() / (size - 1)).coerceAtLeast(0.045f)
                val a = roughness * roughness
                val v = floatArrayOf(sqrt(1f - ndv * ndv), 0f, ndv)
                val n = floatArrayOf(0f, 0f, 1f)
                val tx = floatArrayOf(1f, 0f, 0f)
                val ty = floatArrayOf(0f, 1f, 0f)
                var scaleSum = 0f; var biasSum = 0f; var sheenDG = 0f
                for (i in 0 until samples) {
                    val xi = hammersley(i, samples)
                    val h = importanceSampleGGX(xi, a, tx, ty, n)
                    val vdh = dot3(v, h).coerceIn(0f, 1f)
                    val l = floatArrayOf(2f*vdh*h[0]-v[0], 2f*vdh*h[1]-v[1], 2f*vdh*h[2]-v[2])
                    val ndl = l[2].coerceIn(0f, 1f); val ndh = h[2].coerceIn(0f, 1f)
                    if (ndl > 0f) {
                        val vis = vSmithCorrelated(ndv, ndl, a * a) * ndl * vdh / (ndh + 1e-7f)
                        val fc = (1f - vdh).pow(5f)
                        scaleSum += vis * (1f - fc); biasSum += vis * fc
                    }
                    val cosT = sqrt(1f - xi[1]); val sinT = sqrt(xi[1])
                    val phi = 2f * PI * xi[0]
                    val lc = floatArrayOf(sinT * cos(phi), sinT * sin(phi), cosT)
                    if (lc[2] > 0f) {
                        val hc = floatArrayOf(v[0]+lc[0], v[1]+lc[1], v[2]+lc[2])
                        val hl = sqrt(hc[0]*hc[0]+hc[1]*hc[1]+hc[2]*hc[2])
                        if (hl > 1e-7f) {
                            hc[0] /= hl; hc[1] /= hl; hc[2] /= hl
                            val ndhc = hc[2].coerceIn(0f, 1f)
                            val invR = 1f / roughness.coerceAtLeast(0.01f)
                            val sin2h = (1f - ndhc * ndhc).coerceAtLeast(0f)
                            sheenDG += (2f+invR)*sin2h.pow(invR*0.5f)/(2f*PI) / (4f*(lc[2]+ndv-lc[2]*ndv).coerceAtLeast(1e-7f)) * lc[2]
                        }
                    }
                }
                val inv = 4f / samples; val invS = 1f / samples
                img.setPixelABGR(px, py, rgba((scaleSum*inv).coerceIn(0f,1f), (biasSum*inv).coerceIn(0f,1f), (sheenDG*invS).coerceIn(0f,1f), 1f))
            }
            return img
        }
        private fun hammersley(i: Int, n: Int): FloatArray {
            var bits = i.toLong()
            bits = (bits shl 16) or (bits ushr 16)
            bits = ((bits and 0x55555555L) shl 1) or ((bits and 0xAAAAAAAAL) ushr 1)
            bits = ((bits and 0x33333333L) shl 2) or ((bits and 0xCCCCCCCCL) ushr 2)
            bits = ((bits and 0x0F0F0F0FL) shl 4) or ((bits and 0xF0F0F0F0L) ushr 4)
            bits = ((bits and 0x00FF00FFL) shl 8) or ((bits and 0xFF00FF00L) ushr 8)
            return floatArrayOf(i.toFloat() / n.coerceAtLeast(1), (bits and 0xFFFFFFFFL).toFloat() / 4294967296f)
        }
        private fun importanceSampleGGX(xi: FloatArray, a: Float, tx: FloatArray, ty: FloatArray, n: FloatArray): FloatArray {
            val a2 = a * a; val phi = 2f * PI * xi[0]
            val cosT = sqrt((1f - xi[1]) / (1f + (a2 - 1f) * xi[1]).coerceAtLeast(1e-7f))
            val sinT = sqrt(1f - cosT * cosT)
            val x = sinT * cos(phi); val y = sinT * sin(phi); val z = cosT
            return floatArrayOf(tx[0]*x+ty[0]*y+n[0]*z, tx[1]*x+ty[1]*y+n[1]*z, tx[2]*x+ty[2]*y+n[2]*z)
        }
        private fun vSmithCorrelated(ndv: Float, ndl: Float, a2: Float): Float {
            val gv = ndl * sqrt(ndv*ndv*(1f-a2)+a2); val gl = ndv * sqrt(ndl*ndl*(1f-a2)+a2)
            return 0.5f / (gv + gl).coerceAtLeast(1e-7f)
        }
        private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
        private fun dot3(a: FloatArray, b: FloatArray) = a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
        private fun normalize3(v: FloatArray) { val l = sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]).coerceAtLeast(1e-7f); v[0]/=l; v[1]/=l; v[2]/=l }
        private fun rgba(r: Float, g: Float, b: Float, a: Float): Int {
            return ((a.coerceIn(0f,1f)*255f).toInt() shl 24) or ((b.coerceIn(0f,1f)*255f).toInt() shl 16) or ((g.coerceIn(0f,1f)*255f).toInt() shl 8) or (r.coerceIn(0f,1f)*255f).toInt()
        }
    }
}
