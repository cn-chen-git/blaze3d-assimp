package cn.chen.blaze3d.material
import cn.chen.blaze3d.math.Vec4
enum class AlphaMode { OPAQUE, MASK, BLEND }
data class Material(
    val name: String = "",
    val baseColorFactor: Vec4 = Vec4(1f, 1f, 1f, 1f),
    val metallicFactor: Float = 1f,
    val roughnessFactor: Float = 1f,
    val emissiveFactor: FloatArray = floatArrayOf(0f, 0f, 0f),
    val emissiveStrength: Float = 1f,
    val normalScale: Float = 1f,
    val occlusionStrength: Float = 1f,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f,
    val doubleSided: Boolean = false,
    val ior: Float = 1.5f,
    val opacity: Float = 1f,
    val shininess: Float = 0f,
    val specularFactor: Float = 1f,
    val glossinessFactor: Float = 1f,
    val anisotropyFactor: Float = 0f,
    val textures: MutableMap<TexType, TexInfo> = mutableMapOf(),
    val khrExtensions: KhrExt = KhrExt()
) {
    fun getTexture(type: TexType) = textures[type]
}
