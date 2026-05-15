package cn.chen.blaze3d.ysm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
@Serializable data class YsmManifest(val files: YsmManifestFiles? = null)
@Serializable data class YsmManifestFiles(val player: YsmManifestPlayer? = null)
@Serializable data class YsmManifestPlayer(val model: Map<String, String> = emptyMap(), val animation: Map<String, String> = emptyMap(), val texture: List<String> = emptyList())
@Serializable data class YsmGeometryFile(@SerialName("minecraft:geometry") val geometries: List<YsmGeometry> = emptyList())
@Serializable data class YsmGeometry(val description: YsmGeometryDesc? = null, val bones: List<YsmBone> = emptyList())
@Serializable data class YsmGeometryDesc(val texture_width: Float = 64f, val texture_height: Float = 64f, val identifier: String? = null)
@Serializable data class YsmBone(val name: String = "", val parent: String = "", val pivot: List<Float> = emptyList(), val rotation: List<Float> = emptyList(), val cubes: List<YsmCube> = emptyList())
@Serializable data class YsmCube(val origin: List<Float> = emptyList(), val size: List<Float> = emptyList(), val pivot: List<Float> = emptyList(), val rotation: List<Float> = emptyList(), val inflate: Float = 0f, val material: Float = 0f, val uv: JsonElement? = null)
@Serializable data class YsmAnimationFile(val animations: Map<String, YsmAnimation> = emptyMap())
@Serializable data class YsmAnimation(val animation_length: Float = 0f, val bones: Map<String, YsmBoneAnim> = emptyMap())
@Serializable data class YsmBoneAnim(val position: JsonElement? = null, val rotation: JsonElement? = null, val scale: JsonElement? = null)
object YsmCodec {
    val Json = Json {
        ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true
        allowSpecialFloatingPointValues = true; allowTrailingComma = true; explicitNulls = false
    }
    fun decodeManifest(text: String): YsmManifest = Json.decodeFromString(YsmManifest.serializer(), text)
    fun decodeGeometry(text: String): YsmGeometryFile = Json.decodeFromString(YsmGeometryFile.serializer(), text)
    fun decodeAnimation(text: String): YsmAnimationFile = Json.decodeFromString(YsmAnimationFile.serializer(), text)
    fun float3(values: List<Float>, fallback: Float = 0f): FloatArray = FloatArray(3) { values.getOrElse(it) { fallback } }
    fun bestPath(player: YsmManifestPlayer?, key: String): String? = when (key) { "main" -> player?.model?.values?.firstOrNull { it.endsWith("main.json", true) }; "arm" -> player?.model?.values?.firstOrNull { it.endsWith("arm.json", true) }; else -> null }
    fun keyframeTimes(element: JsonElement?): DoubleArray {
        val obj = element as? JsonObject ?: return DoubleArray(0)
        val sorted = obj.keys.mapNotNull { it.toDoubleOrNull() }.sorted()
        return DoubleArray(sorted.size) { sorted[it] }
    }
    fun keyframeAt(element: JsonElement?, time: Double): JsonElement? {
        val obj = element as? JsonObject ?: return null
        return obj.entries.firstOrNull { it.key.toDoubleOrNull() == time }?.value
    }
    fun primitiveAsString(element: JsonElement?): String? = (element as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    fun primitiveAsDouble(element: JsonElement?, fallback: Double = 0.0): Double = (element as? kotlinx.serialization.json.JsonPrimitive)?.jsonPrimitive?.doubleOrNull ?: fallback
}
