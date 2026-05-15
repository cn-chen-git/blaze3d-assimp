package cn.chen.blaze3d.player
import cn.chen.blaze3d.mmd.MmdAction
import cn.chen.blaze3d.render.WorldRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.Entity
import java.io.File
import java.util.UUID
data class PlayerModelProject(val id: String, val uuid: String, val playerName: String, val model: String, val stage: String, val motions: List<String>, val poses: List<String>, val scale: Float, val hideVanilla: Boolean, val firstPerson: Boolean, val defaultVmd: Boolean)
class AssetInstallState(val root: File, val projects: List<PlayerModelProject>, val installed: Boolean)
object PlayerAssetInstaller {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    fun prepare(): AssetInstallState {
        val root = File(Minecraft.getInstance().gameDirectory, "config/blaze3d-model")
        val players = File(root, "models/player")
        val sample = File(players, "default/project.json")
        val installed = !sample.isFile
        if (installed) writeSample(sample)
        return AssetInstallState(root, scan(players), installed)
    }
    fun scan(players: File = File(Minecraft.getInstance().gameDirectory, "config/blaze3d-model/models/player")): List<PlayerModelProject> {
        if (!players.isDirectory) return emptyList()
        return players.walkTopDown().filter { it.isFile && it.name == "project.json" }.mapNotNull { runCatching { decode(it.parentFile.name, json.parseToJsonElement(it.readText()).jsonObject) }.getOrNull() }.toList()
    }
    private fun writeSample(file: File) {
        file.parentFile.mkdirs()
        File(file.parentFile, "motions").mkdirs()
        File(file.parentFile, "textures").mkdirs()
        file.writeText(json.encodeToString(JsonObject.serializer(), encode(PlayerModelProject("default", "", "", "model.pmx", "../../stage/default/stage.pmx", listOf("motions/idle.vmd", "motions/walk.vmd", "motions/sprint.vmd"), emptyList(), 1f, true, true, true))))
        val stage = File(Minecraft.getInstance().gameDirectory, "config/blaze3d-model/models/stage/default")
        stage.mkdirs()
        File(stage, "stage-project.json").writeText(json.encodeToString(JsonObject.serializer(), buildJsonObject { put("stage", "stage.pmx"); put("camera", "camera.vmd"); put("light", true); put("shadow", true) }))
    }
    private fun encode(p: PlayerModelProject) = buildJsonObject {
        put("uuid", p.uuid); put("playerName", p.playerName); put("model", p.model); put("stage", p.stage); put("motions", kotlinx.serialization.json.JsonArray(p.motions.map { JsonPrimitive(it) })); put("poses", kotlinx.serialization.json.JsonArray(p.poses.map { JsonPrimitive(it) })); put("scale", p.scale); put("hideVanilla", p.hideVanilla); put("firstPerson", p.firstPerson); put("defaultVmd", p.defaultVmd)
    }
    private fun decode(id: String, o: JsonObject) = PlayerModelProject(id, str(o, "uuid"), str(o, "playerName"), str(o, "model"), str(o, "stage"), arr(o, "motions"), arr(o, "poses"), o["scale"]?.jsonPrimitive?.floatOrNull ?: 1f, bool(o, "hideVanilla", true), bool(o, "firstPerson", true), bool(o, "defaultVmd", true))
    private fun str(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.content ?: ""
    private fun bool(o: JsonObject, k: String, d: Boolean) = o[k]?.jsonPrimitive?.booleanOrNull ?: d
    private fun arr(o: JsonObject, k: String) = o[k]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}
class PlayerReplacementSystem {
    var enabled = true
    var hideVanilla = true
    var firstPerson = true
    var activeProject: PlayerModelProject? = null; private set
    var projects: List<PlayerModelProject> = emptyList(); private set
    var loadedProjectId = ""; private set
    private var lastScan = 0L
    var hiddenPlayers = 0; private set
    var expression = "neutral"; private set
    var expressionWeight = 0f; private set
    var mouthWeight = 0f; private set
    var blinkWeight = 0f; private set
    var armSocket = "右手首"; private set
    var offhandSocket = "左手首"; private set
    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastScan < 1000L) return
        projects = PlayerAssetInstaller.scan()
        lastScan = now
    }
    fun select(id: String) { activeProject = projects.firstOrNull { it.id == id }; loadedProjectId = activeProject?.id ?: loadedProjectId }
    fun bindLocal(renderer: WorldRenderer) {
        refresh()
        val player = Minecraft.getInstance().player ?: return
        val project = find(player) ?: projects.firstOrNull() ?: return
        activeProject = project
        hideVanilla = project.hideVanilla
        firstPerson = project.firstPerson
        val root = File(Minecraft.getInstance().gameDirectory, "config/blaze3d-model/models/player/${project.id}")
        val model = File(root, project.model)
        if (model.isFile && renderer.modelPath != model.path) renderer.loadAsync(model.path)
        renderer.instance.followPlayer = true
        renderer.instance.scale = project.scale
        renderer.mmdActions.enabled = project.defaultVmd
        loadedProjectId = project.id
    }
    fun tick(renderer: WorldRenderer) {
        if (!enabled) return
        bindLocal(renderer)
        updateExpressions(renderer)
        hiddenPlayers = if (hideVanilla && activeProject != null) 1 else 0
    }
    fun shouldHideVanilla(stateName: String?, stateUuid: UUID?): Boolean {
        if (!enabled || !hideVanilla) return false
        val p = activeProject ?: return false
        return (p.playerName.isBlank() || p.playerName == stateName) && (p.uuid.isBlank() || p.uuid.equals(stateUuid?.toString(), true))
    }
    fun shouldHideVanilla(entity: Avatar) = shouldHideVanilla(entity.name.string, entity.uuid)
    private fun find(entity: Entity): PlayerModelProject? {
        val name = entity.name.string
        val uuid = entity.uuid.toString()
        return projects.firstOrNull { it.uuid.equals(uuid, true) } ?: projects.firstOrNull { it.playerName == name }
    }
    private fun updateExpressions(renderer: WorldRenderer) {
        val player = Minecraft.getInstance().player ?: return
        blinkWeight = if (player.tickCount % 96 in 0..4) 1f else 0f
        mouthWeight = if (player.isUsingItem || player.swinging) 1f else 0f
        expression = when (renderer.mmdActions.current) {
            MmdAction.HURT, MmdAction.DIE -> "pain"
            MmdAction.SWING_LEFT, MmdAction.SWING_RIGHT -> "angry"
            MmdAction.SPRINT, MmdAction.JUMP, MmdAction.FALL -> "focus"
            else -> if (mouthWeight > 0f) "mouth" else if (blinkWeight > 0f) "blink" else "neutral"
        }
        expressionWeight = maxOf(blinkWeight, mouthWeight)
    }
    fun itemSockets(right: String, left: String) { armSocket = right; offhandSocket = left }
    fun status() = "enabled=$enabled project=$loadedProjectId hide=$hideVanilla firstPerson=$firstPerson hidden=$hiddenPlayers expr=$expression:${"%.2f".format(expressionWeight)} mouth=${"%.2f".format(mouthWeight)} blink=${"%.2f".format(blinkWeight)} right=$armSocket left=$offhandSocket"
}
object PlayerReplacementRuntime {
    val system = PlayerReplacementSystem()
    fun shouldHideVanilla(entity: Avatar) = system.shouldHideVanilla(entity)
}
