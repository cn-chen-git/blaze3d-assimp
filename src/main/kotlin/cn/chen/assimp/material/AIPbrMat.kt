package cn.chen.assimp.material
import cn.chen.assimp.math.AIVec4
enum class AIAlphaMode { OPAQUE, MASK, BLEND }
data class AIPbrMat(
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
    val textures: MutableMap<AITexType, AITexInfo> = mutableMapOf(),
    val khrExtensions: AIKhrExt = AIKhrExt()
) {
    fun hasTexture(type: AITexType) = textures.containsKey(type)
    fun getTexture(type: AITexType) = textures[type]
}
