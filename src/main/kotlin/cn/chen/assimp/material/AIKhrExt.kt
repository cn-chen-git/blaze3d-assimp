package cn.chen.assimp.material
import cn.chen.assimp.math.AIVec3
data class AIKhrClearcoat(
    val factor: Float = 0f,
    val roughnessFactor: Float = 0f,
    val texture: AITexInfo? = null,
    val roughnessTexture: AITexInfo? = null,
    val normalTexture: AITexInfo? = null
)
data class AIKhrSheen(
    val colorFactor: AIVec3 = AIVec3(),
    val roughnessFactor: Float = 0f,
    val colorTexture: AITexInfo? = null,
    val roughnessTexture: AITexInfo? = null
)
data class AIKhrTransmission(
    val factor: Float = 0f,
    val texture: AITexInfo? = null
)
data class AIKhrVolume(
    val thicknessFactor: Float = 0f,
    val attenuationDistance: Float = Float.MAX_VALUE,
    val attenuationColor: AIVec3 = AIVec3(1f, 1f, 1f),
    val thicknessTexture: AITexInfo? = null
)
data class AIKhrSpecular(
    val factor: Float = 1f,
    val colorFactor: AIVec3 = AIVec3(1f, 1f, 1f),
    val texture: AITexInfo? = null,
    val colorTexture: AITexInfo? = null
)
data class AIKhrIridescence(
    val factor: Float = 0f,
    val ior: Float = 1.3f,
    val thicknessMinimum: Float = 100f,
    val thicknessMaximum: Float = 400f,
    val texture: AITexInfo? = null,
    val thicknessTexture: AITexInfo? = null
)
data class AIKhrAnisotropy(
    val strength: Float = 0f,
    val rotation: Float = 0f,
    val texture: AITexInfo? = null
)
data class AIKhrEmissiveStrength(val strength: Float = 1f)
data class AIKhrUnlit(val enabled: Boolean = false)
data class AIKhrExt(
    val clearcoat: AIKhrClearcoat? = null,
    val sheen: AIKhrSheen? = null,
    val transmission: AIKhrTransmission? = null,
    val volume: AIKhrVolume? = null,
    val specular: AIKhrSpecular? = null,
    val iridescence: AIKhrIridescence? = null,
    val anisotropy: AIKhrAnisotropy? = null,
    val emissiveStrength: AIKhrEmissiveStrength? = null,
    val unlit: AIKhrUnlit? = null
)
