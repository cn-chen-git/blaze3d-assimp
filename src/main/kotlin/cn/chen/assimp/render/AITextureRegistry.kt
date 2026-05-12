package cn.chen.assimp.render
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.material.AITexType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import java.io.File
class AITextureRegistry {
    private data class TexKey(val matIdx: Int, val type: AITexType)
    private val texIds = mutableMapOf<TexKey, Identifier>()
    private val alphaMatIndices = mutableSetOf<Int>()
    val whiteTex: Identifier = Identifier.fromNamespaceAndPath("lwjgl-assimp", "textures/white.png")
    val flatNormalTex: Identifier = Identifier.fromNamespaceAndPath("lwjgl-assimp", "textures/flat_normal.png")
    val defaultMrTex: Identifier = Identifier.fromNamespaceAndPath("lwjgl-assimp", "textures/default_mr.png")
    val blackTex: Identifier = Identifier.fromNamespaceAndPath("lwjgl-assimp", "textures/black.png")
    private val pbrTypes = arrayOf(AITexType.ALBEDO, AITexType.NORMAL, AITexType.METALLIC_ROUGHNESS, AITexType.EMISSIVE)
    fun register(s: AISceneData, basePath: String) {
        release()
        registerDefaults()
        val tm = Minecraft.getInstance().textureManager
        for ((matIdx, mat) in s.materials.withIndex()) {
            for (texType in pbrTypes) {
                val info = mat.getTexture(texType) ?: continue
                val texPath = info.path
                try {
                    val nativeImg = if (texPath.startsWith("*")) {
                        val embIdx = texPath.substring(1).toIntOrNull() ?: continue
                        val emb = s.embeddedTextures.getOrNull(embIdx) ?: continue
                        if (emb.height == 0) AIImageDecoder.decodeBytes(emb.data) else AIImageDecoder.decodeRaw(emb.width, emb.height, emb.data)
                    } else {
                        val file = File(if (File(texPath).isAbsolute) texPath else "$basePath/$texPath")
                        if (!file.exists()) continue
                        AIImageDecoder.decodeBytes(file.readBytes())
                    }
                    if (nativeImg == null) continue
                    if (texType == AITexType.ALBEDO && nativeImg.format() == NativeImage.Format.RGBA) {
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
                    val id = Identifier.fromNamespaceAndPath("lwjgl-assimp", "dynamic/ai_${suffix}_$matIdx")
                    tm.register(id, DynamicTexture({ "ai_${suffix}_$matIdx" }, nativeImg))
                    texIds[TexKey(matIdx, texType)] = id
                } catch (_: Exception) {}
            }
        }
    }
    private fun registerDefaults() {
        val tm = Minecraft.getInstance().textureManager
        registerSolidTex(tm, flatNormalTex, "flat_normal", 0x80, 0x80, 0xFF, 0xFF)
        registerSolidTex(tm, defaultMrTex, "default_mr", 0xFF, 0x80, 0x00, 0xFF)
        registerSolidTex(tm, blackTex, "black", 0x00, 0x00, 0x00, 0xFF)
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
        alphaMatIndices.clear()
    }
    operator fun get(matIdx: Int): Identifier = texIds[TexKey(matIdx, AITexType.ALBEDO)] ?: whiteTex
    fun getNormal(matIdx: Int): Identifier = texIds[TexKey(matIdx, AITexType.NORMAL)] ?: flatNormalTex
    fun getMR(matIdx: Int): Identifier = texIds[TexKey(matIdx, AITexType.METALLIC_ROUGHNESS)] ?: defaultMrTex
    fun getEmissive(matIdx: Int): Identifier = texIds[TexKey(matIdx, AITexType.EMISSIVE)] ?: whiteTex
    fun hasNormal(matIdx: Int) = texIds.containsKey(TexKey(matIdx, AITexType.NORMAL))
    fun hasMR(matIdx: Int) = texIds.containsKey(TexKey(matIdx, AITexType.METALLIC_ROUGHNESS))
    fun hasEmissive(matIdx: Int) = texIds.containsKey(TexKey(matIdx, AITexType.EMISSIVE))
    fun hasPbrTextures(matIdx: Int) = hasNormal(matIdx) || hasMR(matIdx) || hasEmissive(matIdx)
    fun has(matIdx: Int) = texIds.containsKey(TexKey(matIdx, AITexType.ALBEDO))
    fun hasAlphaPixels(matIdx: Int) = alphaMatIndices.contains(matIdx)
    val size get() = texIds.size
}
