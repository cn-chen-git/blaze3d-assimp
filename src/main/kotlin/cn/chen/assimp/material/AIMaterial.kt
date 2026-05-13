package cn.chen.assimp.material
import cn.chen.assimp.math.AIVec4
enum class AIAlphaMode { OPAQUE, MASK, BLEND }
data class AIMaterial(
    val name: String = "",
    val baseColorFactor: AIVec4 = AIVec4(1f, 1f, 1f, 1f),
    val metallicFactor: Float = 1f,
    val roughnessFactor: Float = 1f,
    val emissiveFactor: FloatArray = floatArrayOf(0f, 0f, 0f),
    val emissiveStrength: Float = 1f,
    val normalScale: Float = 1f,
    val occlusionStrength: Float = 1f,
    val alphaMode: AIAlphaMode = AIAlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f,
    val doubleSided: Boolean = false,
    val ior: Float = 1.5f,
    val opacity: Float = 1f,
    val shininess: Float = 0f,
    val specularFactor: Float = 1f,
    val glossinessFactor: Float = 1f,
    val anisotropyFactor: Float = 0f,
    val textures: MutableMap<AITexType, AITexInfo> = mutableMapOf(),
    val khrExtensions: AIKhrExt = AIKhrExt()
) {
    fun getTexture(type: AITexType) = textures[type]
}
