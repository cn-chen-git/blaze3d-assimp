package cn.chen.blaze3d.preset
import cn.chen.blaze3d.advanced.Attachment
import cn.chen.blaze3d.advanced.AttachmentTarget
import cn.chen.blaze3d.render.ModelInstance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
data class ModelPreset(val path: String = "", val position: FloatArray = floatArrayOf(0f, 0f, 0f), val rotation: FloatArray = floatArrayOf(0f, 0f, 0f), val scale: Float = 1f, val color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f), val visible: Boolean = true, val physics: Boolean = true, val entityCollision: Boolean = true, val animation: String = "", val animationSpeed: Float = 1f, val loop: Boolean = true, val attachment: String = "NONE", val socket: String = "")
object ModelPresetStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    fun from(path: String, instance: ModelInstance, physics: cn.chen.blaze3d.physics.PhysicsBinding, anim: cn.chen.blaze3d.anim.Animator?, attachment: Attachment) = ModelPreset(
        path,
        floatArrayOf(instance.pos.x, instance.pos.y, instance.pos.z),
        floatArrayOf(instance.rot.x, instance.rot.y, instance.rot.z),
        instance.scale,
        floatArrayOf(instance.tintR, instance.tintG, instance.tintB, instance.tintA),
        instance.visible,
        physics.enabled,
        physics.entityCollision,
        anim?.currentAnimation?.name ?: "",
        anim?.speed ?: 1f,
        anim?.loop ?: true,
        attachment.target.name,
        attachment.socket
    )
    fun apply(preset: ModelPreset, instance: ModelInstance, physics: cn.chen.blaze3d.physics.PhysicsBinding, anim: cn.chen.blaze3d.anim.Animator?, attachment: Attachment) {
        instance.pos.set(preset.position.getOrElse(0) { 0f }, preset.position.getOrElse(1) { 0f }, preset.position.getOrElse(2) { 0f })
        instance.rot.set(preset.rotation.getOrElse(0) { 0f }, preset.rotation.getOrElse(1) { 0f }, preset.rotation.getOrElse(2) { 0f })
        instance.scale = preset.scale
        instance.tintR = preset.color.getOrElse(0) { 1f }; instance.tintG = preset.color.getOrElse(1) { 1f }; instance.tintB = preset.color.getOrElse(2) { 1f }; instance.tintA = preset.color.getOrElse(3) { 1f }
        instance.visible = preset.visible
        physics.enabled = preset.physics; physics.entityCollision = preset.entityCollision
        if (preset.animation.isNotBlank()) anim?.play(preset.animation)
        anim?.speed = preset.animationSpeed; anim?.loop = preset.loop
        attachment.target = runCatching { AttachmentTarget.valueOf(preset.attachment) }.getOrDefault(AttachmentTarget.NONE)
        attachment.socket = preset.socket
    }
    fun save(file: File, preset: ModelPreset) { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(JsonObject.serializer(), encode(preset))) }
    fun load(file: File): ModelPreset = decode(json.parseToJsonElement(file.readText()).jsonObject)
    private fun encode(p: ModelPreset) = buildJsonObject {
        put("path", JsonPrimitive(p.path)); put("position", array(p.position)); put("rotation", array(p.rotation)); put("scale", JsonPrimitive(p.scale)); put("color", array(p.color))
        put("visible", JsonPrimitive(p.visible)); put("physics", JsonPrimitive(p.physics)); put("entityCollision", JsonPrimitive(p.entityCollision)); put("animation", JsonPrimitive(p.animation)); put("animationSpeed", JsonPrimitive(p.animationSpeed)); put("loop", JsonPrimitive(p.loop)); put("attachment", JsonPrimitive(p.attachment)); put("socket", JsonPrimitive(p.socket))
    }
    private fun decode(o: JsonObject) = ModelPreset(
        o["path"]?.jsonPrimitive?.content ?: "",
        floats(o["position"]?.jsonArray, 3, 0f),
        floats(o["rotation"]?.jsonArray, 3, 0f),
        o["scale"]?.jsonPrimitive?.float ?: 1f,
        floats(o["color"]?.jsonArray, 4, 1f),
        o["visible"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
        o["physics"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
        o["entityCollision"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
        o["animation"]?.jsonPrimitive?.content ?: "",
        o["animationSpeed"]?.jsonPrimitive?.float ?: 1f,
        o["loop"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
        o["attachment"]?.jsonPrimitive?.content ?: "NONE",
        o["socket"]?.jsonPrimitive?.content ?: ""
    )
    private fun array(values: FloatArray) = JsonArray(values.map { JsonPrimitive(it) })
    private fun floats(values: JsonArray?, size: Int, fallback: Float) = FloatArray(size) { i -> values?.getOrNull(i)?.jsonPrimitive?.float ?: fallback }
}
