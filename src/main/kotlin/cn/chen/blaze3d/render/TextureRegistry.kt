package cn.chen.blaze3d.render
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.api.ResourceIndex
import cn.chen.blaze3d.material.TexType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import java.io.File
class TextureRegistry {
    private val texIds = HashMap<Long, Identifier>()
    private val resolved = HashMap<String, File>()
    private var resourceIndex: ResourceIndex? = null
    private val alphaMatIndices = HashSet<Int>()
    private fun key(matIdx: Int, type: TexType) = (matIdx.toLong() shl 8) or type.ordinal.toLong()
    var loadedTextures = 0; private set
    var missingTextures = 0; private set
    val whiteTex: Identifier = Identifier.fromNamespaceAndPath("blaze3d-model", "textures/white.png")
    val flatNormalTex: Identifier = Identifier.fromNamespaceAndPath("blaze3d-model", "textures/flat_normal.png")
    private val materialTypes = TexType.entries.toTypedArray()
    fun register(s: SceneData, basePath: String) {
        release()
        registerDefaults()
        resourceIndex = ResourceIndex.of(File(basePath))
        val tm = Minecraft.getInstance().textureManager
        for ((matIdx, mat) in s.materials.withIndex()) {
            for (texType in materialTypes) {
                val info = mat.getTexture(texType) ?: continue
                val texPath = info.path
                try {
                    val nativeImg = if (texPath.startsWith("*")) {
                        val embIdx = texPath.substring(1).toIntOrNull() ?: continue
                        val emb = s.embeddedTextures.getOrNull(embIdx) ?: continue
                        if (emb.height == 0) ImageDecoder.decodeBytes(emb.data) else ImageDecoder.decodeRaw(emb.width, emb.height, emb.data)
                    } else {
                        val file = resolve(basePath, texPath)
                        if (!file.exists()) { missingTextures++; continue }
                        ImageDecoder.decodeBytes(file.readBytes())
                    }
                    if (nativeImg == null) continue
                    if (texType == TexType.ALBEDO && nativeImg.format() == NativeImage.Format.RGBA) {
                        var semiCount = 0; var total = 0
                        for (y in 0 until nativeImg.height step 4) {
                            for (x in 0 until nativeImg.width step 4) {
                                total++
                                val px = nativeImg.getPixel(x, y)
                                if ((px ushr 24) and 0xFF < 200) semiCount++
                            }
                        }
                        if (total > 0 && semiCount.toFloat() / total > 0.01f) alphaMatIndices.add(matIdx)
                    }
                    val suffix = texType.name.lowercase()
                    val id = Identifier.fromNamespaceAndPath("blaze3d-model", "dynamic/model_${suffix}_$matIdx")
                    tm.register(id, DynamicTexture({ "model_${suffix}_$matIdx" }, nativeImg))
                    texIds[key(matIdx, texType)] = id
                    loadedTextures++
                } catch (_: Exception) {}
            }
        }
    }
    private fun registerDefaults() {
        val tm = Minecraft.getInstance().textureManager
        registerSolidTex(tm, flatNormalTex, "flat_normal", 0x80, 0x80, 0xFF, 0xFF)
    }
    private fun resolve(basePath: String, texPath: String): File {
        val raw = File(texPath)
        if (raw.isAbsolute) return raw
        val key = "$basePath|$texPath"
        resolved[key]?.let { return it }
        val direct = File(basePath, texPath)
        if (direct.exists()) { resolved[key] = direct; return direct }
        val target = raw.name.lowercase()
        val found = resourceIndex?.find(target) ?: direct
        resolved[key] = found
        return found
    }
    private fun registerSolidTex(tm: TextureManager, id: Identifier, name: String, r: Int, g: Int, b: Int, a: Int) {
        if (texIds.values.any { it == id }) return
        val img = NativeImage(NativeImage.Format.RGBA, 1, 1, false)
        img.setPixelABGR(0, 0, (a shl 24) or (b shl 16) or (g shl 8) or r)
        tm.register(id, DynamicTexture({ name }, img))
    }
    fun release() {
        val tm = Minecraft.getInstance().textureManager
        for ((_, id) in texIds) tm.release(id)
        texIds.clear()
        resolved.clear()
        resourceIndex = null
        alphaMatIndices.clear()
        loadedTextures = 0
        missingTextures = 0
    }
    operator fun get(matIdx: Int): Identifier = texIds[key(matIdx, TexType.ALBEDO)] ?: whiteTex
    fun getNormal(matIdx: Int): Identifier = texIds[key(matIdx, TexType.NORMAL)] ?: flatNormalTex
    fun hasNormal(matIdx: Int) = texIds.containsKey(key(matIdx, TexType.NORMAL))
    fun hasAlphaPixels(matIdx: Int) = alphaMatIndices.contains(matIdx)
    val size get() = texIds.size
    fun status() = "textures loaded=$loadedTextures missing=$missingTextures registered=${texIds.size}"
}
