package cn.chen.blaze3d.mmd
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
data class MmdProjectConfig(val model: String, val stage: String, val motions: List<String>, val poses: List<String>, val toon: Boolean, val outline: Boolean, val physics: Boolean, val scale: Float, val actionMotions: Map<String, String>)
object MmdProjectConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }
    fun load(file: File): MmdProjectConfig {
        val obj = json.parseToJsonElement(file.readText()) as JsonObject
        return MmdProjectConfig(
            str(obj, "model"),
            str(obj, "stage"),
            arr(obj, "motions"),
            arr(obj, "poses"),
            bool(obj, "toon", true),
            bool(obj, "outline", true),
            bool(obj, "physics", true),
            obj["scale"]?.jsonPrimitive?.floatOrNull ?: 1f,
            map(obj, "actions")
        )
    }
    private fun str(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.content ?: ""
    private fun bool(o: JsonObject, k: String, d: Boolean) = o[k]?.jsonPrimitive?.booleanOrNull ?: d
    private fun arr(o: JsonObject, k: String) = o[k]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    private fun map(o: JsonObject, k: String) = (o[k] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
}
