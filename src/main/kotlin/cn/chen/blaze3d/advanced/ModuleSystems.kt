package cn.chen.blaze3d.advanced
import cn.chen.blaze3d.anim.AnimationLibrary
import cn.chen.blaze3d.api.ModelFormats
import cn.chen.blaze3d.api.ModelPackage
import cn.chen.blaze3d.mmd.MmdAction
import cn.chen.blaze3d.render.WorldRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
class ModuleSystems {
    val policy = ServerPolicy()
    val sandbox = ResourceSandbox()
    val diagnostics = Diagnostics()
    val downloader = OnlineModelDownloader(sandbox, diagnostics)
    val packages = ModelPackageManager(sandbox, diagnostics)
    val actionEditor = ActionStateMachineEditor()
    val recorder = ReplayRecorder()
    val equipment = EquipmentSocketSystem()
    val vr = VrFirstPersonSystem()
    val entityReplacement = EntityReplacementSystem()
    val blockDecor = BlockDecorSystem()
    val interaction = InteractionIkSelector()
    val optimizer = ImportOptimizer()
    val compat = CompatLayer()
    val preview = PreviewScene()
    fun tick(renderer: WorldRenderer) {
        downloader.tick()
        actionEditor.tick(renderer)
        recorder.tick(renderer)
        equipment.tick(renderer)
        vr.tick(renderer)
        entityReplacement.tick(renderer)
        blockDecor.tick(renderer)
        interaction.tick(renderer)
        optimizer.tick(renderer)
        preview.tick(renderer)
    }
    fun status() = listOf(policy.status(), sandbox.status(), diagnostics.status(), downloader.status(), packages.status(), actionEditor.status(), recorder.status(), equipment.status(), vr.status(), entityReplacement.status(), blockDecor.status(), interaction.status(), optimizer.status(), compat.status(), preview.status())
}
class ServerPolicy {
    var enabled = true
    var allowClientModels = true
    var allowRemoteDownload = false
    var allowVr = true
    var allowEntityReplace = true
    var allowBlockDecor = true
    var maxModelBytes = 64L * 1024L * 1024L
    var maxTextureSize = 8192
    var maxVertices = 500000
    var syncDistance = 128
    fun checkModel(file: File) = enabled && allowClientModels && file.isFile && file.length() <= maxModelBytes
    fun status() = "policy enabled=$enabled clientModels=$allowClientModels remoteDownload=$allowRemoteDownload vr=$allowVr entity=$allowEntityReplace block=$allowBlockDecor maxModel=$maxModelBytes maxTexture=$maxTextureSize maxVertices=$maxVertices syncDistance=$syncDistance"
}
class ResourceSandbox {
    private val root get() = File(Minecraft.getInstance().gameDirectory, "config/blaze3d-model").canonicalFile
    var checked = 0; private set
    var rejected = 0; private set
    val allowed = ModelFormats.SANDBOX_EXTENSIONS
    fun safe(file: File): Boolean {
        checked++
        val ok = runCatching {
            val canonical = file.canonicalFile
            val ext = ModelFormats.extension(canonical.name)
            canonical.path.startsWith(root.path) && allowed.any { ext == it || canonical.name.endsWith(".$it", true) }
        }.getOrDefault(false)
        if (!ok) rejected++
        return ok
    }
    fun extension(name: String) = ModelFormats.extension(name)
    fun rootDir() = root.also { it.mkdirs() }
    fun status() = "sandbox root=${root.path} checked=$checked rejected=$rejected allowed=${allowed.size}"
}
class Diagnostics {
    private val events = ArrayDeque<String>()
    var errors = 0; private set
    var warnings = 0; private set
    fun error(text: String) { errors++; push("error:$text") }
    fun warn(text: String) { warnings++; push("warn:$text") }
    fun info(text: String) = push("info:$text")
    fun recent() = events.toList()
    private fun push(text: String) {
        if (events.size > 64) events.removeFirst()
        events.addLast(text.take(512))
    }
    fun status() = "diagnostics errors=$errors warnings=$warnings recent=${events.lastOrNull() ?: "-"}"
}
class OnlineModelDownloader(private val sandbox: ResourceSandbox, private val diagnostics: Diagnostics) {
    var url = ""
    var targetName = "download.zip"
    var running = false; private set
    var downloadedBytes = 0L; private set
    fun start(policy: ServerPolicy) {
        if (!policy.allowRemoteDownload || running || url.isBlank()) return
        val out = File(sandbox.rootDir(), "downloads/$targetName")
        if (!sandbox.safe(out)) return
        running = true
        Thread {
            runCatching {
                out.parentFile.mkdirs()
                URI(url).toURL().openStream().use { input -> out.outputStream().use { output -> downloadedBytes = input.copyTo(output) } }
                diagnostics.info("downloaded ${out.name} $downloadedBytes")
            }.onFailure { diagnostics.error(it.message ?: "download failed") }
            running = false
        }.start()
    }
    fun tick() {}
    fun status() = "downloader running=$running url=$url target=$targetName bytes=$downloadedBytes"
}
class ModelPackageManager(private val sandbox: ResourceSandbox, private val diagnostics: Diagnostics) {
    var lastImport = ""
    var lastExport = ""
    var lastMainAsset = ""
    var lastMotionCount = 0
    fun importZip(zip: File): Int {
        if (!sandbox.safe(zip) || !zip.isFile) return 0
        val root = File(sandbox.rootDir(), "imported/${zip.nameWithoutExtension}")
        var count = 0
        runCatching {
            ZipInputStream(zip.inputStream()).use { stream ->
                var entry: ZipEntry? = stream.nextEntry
                while (entry != null) {
                    val current = entry ?: break
                    val out = File(root, current.name).canonicalFile
                    if (out.path.startsWith(root.canonicalPath) && !current.isDirectory) {
                        out.parentFile.mkdirs()
                        out.outputStream().use { stream.copyTo(it) }
                        count++
                    }
                    entry = stream.nextEntry
                }
            }
            lastImport = root.path
            lastMainAsset = ModelPackage.mainAsset(root)?.path ?: ""
            lastMotionCount = AnimationLibrary.scan(root).size
        }.onFailure { diagnostics.error(it.message ?: "import failed") }
        return count
    }
    fun mainAsset(path: String): String {
        lastMainAsset = ModelPackage.mainAsset(File(path))?.path ?: ""
        lastMotionCount = AnimationLibrary.scan(File(path)).size
        return lastMainAsset
    }
    fun exportProject(project: File): File {
        val out = File(sandbox.rootDir(), "exports/${project.name}.zip")
        runCatching {
            out.parentFile.mkdirs()
            ZipOutputStream(out.outputStream()).use { zip ->
                project.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry(project.toPath().relativize(file.toPath()).toString()))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            lastExport = out.path
        }.onFailure { diagnostics.error(it.message ?: "export failed") }
        return out
    }
    fun status() = "packages import=$lastImport export=$lastExport main=$lastMainAsset motions=$lastMotionCount"
}
data class ActionTransition(var from: MmdAction, var to: MmdAction, var priority: Int = 0, var fade: Float = 0.18f, var loop: Boolean = true)
class ActionStateMachineEditor {
    val transitions = ArrayList<ActionTransition>()
    var selected = MmdAction.IDLE
    var activePriority = 0
    fun add(from: MmdAction, to: MmdAction) { transitions.add(ActionTransition(from, to, transitions.size)) }
    fun tick(renderer: WorldRenderer) { activePriority = transitions.firstOrNull { it.to == renderer.mmdActions.current }?.priority ?: 0 }
    fun status() = "actionState selected=$selected transitions=${transitions.size} activePriority=$activePriority"
}
class ReplayRecorder {
    val frames = ArrayList<ReplayFrame>()
    var recording = false
    var playing = false
    var frame = 0
    fun tick(renderer: WorldRenderer) {
        if (recording) frames.add(ReplayFrame(frame++, renderer.instance.pos.x, renderer.instance.pos.y, renderer.instance.pos.z, renderer.instance.rot.x, renderer.instance.rot.y, renderer.instance.rot.z, renderer.mmdActions.current.name, renderer.mmdRuntime.state.expression))
        if (playing && frames.isNotEmpty()) {
            val f = frames[frame++ % frames.size]
            renderer.instance.pos.set(f.x, f.y, f.z)
            renderer.instance.rot.set(f.rx, f.ry, f.rz)
            renderer.mmdRuntime.expressionOverride = f.expression
        }
    }
    fun clear() { frames.clear(); frame = 0 }
    fun status() = "replay recording=$recording playing=$playing frames=${frames.size} frame=$frame"
}
data class ReplayFrame(val frame: Int, val x: Float, val y: Float, val z: Float, val rx: Float, val ry: Float, val rz: Float, val action: String, val expression: String)
class EquipmentSocketSystem {
    val sockets = linkedMapOf("mainhand" to "右手首", "offhand" to "左手首", "helmet" to "頭", "chest" to "上半身", "legs" to "下半身", "feet" to "左足", "elytra" to "上半身2")
    var firstPersonHands = true
    var vrHands = true
    var boundItems = 0; private set
    fun tick(renderer: WorldRenderer) {
        val player = Minecraft.getInstance().player ?: return
        boundItems = listOf(player.mainHandItem, player.offhandItem).count { !it.isEmpty }
        renderer.playerReplacement.itemSockets(sockets["mainhand"] ?: "", sockets["offhand"] ?: "")
    }
    fun status() = "equipment firstPersonHands=$firstPersonHands vrHands=$vrHands boundItems=$boundItems sockets=${sockets.size}"
}
class VrFirstPersonSystem {
    var enabled = true
    var detected = false; private set
    var firstPersonBody = true
    var cameraHeadFollow = true
    var handScale = 1f
    var ipd = 0.064f
    fun tick(renderer: WorldRenderer) {
        detected = System.getProperty("vivecraft") != null || System.getProperty("mcvr") != null || System.getenv("VR_ENABLED") == "1"
        if (enabled && (detected || firstPersonBody)) renderer.ik.enabled = true
    }
    fun status() = "vr enabled=$enabled detected=$detected firstPersonBody=$firstPersonBody cameraHeadFollow=$cameraHeadFollow handScale=$handScale ipd=$ipd"
}
data class EntityReplacement(val entityType: String, val model: String, val project: String = "", val scale: Float = 1f, val hideVanilla: Boolean = true)
class EntityReplacementSystem {
    val replacements = LinkedHashMap<String, EntityReplacement>()
    var enabled = true
    var matchedEntities = 0; private set
    fun add(type: String, model: String) { replacements[type] = EntityReplacement(type, model) }
    fun tick(renderer: WorldRenderer) {
        if (!enabled) return
        val level = Minecraft.getInstance().level ?: return
        matchedEntities = 0
        if (matchedEntities == 0 && replacements.isEmpty()) add("minecraft:zombie", renderer.modelPath)
        level.entitiesForRendering().take(32).forEach { entity ->
            val id = entity.type.toString()
            if (replacements.containsKey(id)) renderer.instances.create().second.pos.set(entity.x.toFloat(), entity.y.toFloat(), entity.z.toFloat())
        }
    }
    fun status() = "entityReplace enabled=$enabled rules=${replacements.size} matched=$matchedEntities"
}
data class BlockDecor(val id: UUID, val block: BlockPos, val model: String, val scale: Float = 1f)
class BlockDecorSystem {
    val decorations = LinkedHashMap<UUID, BlockDecor>()
    var enabled = true
    fun addHere(model: String) {
        val player = Minecraft.getInstance().player ?: return
        val pos = player.blockPosition()
        val id = UUID.randomUUID()
        decorations[id] = BlockDecor(id, pos, model)
    }
    fun tick(renderer: WorldRenderer) {
        if (!enabled) return
        decorations.values.take(16).forEach { decor ->
            val inst = renderer.instances.create().second
            inst.pos.set(decor.block.x + 0.5f, decor.block.y.toFloat(), decor.block.z + 0.5f)
            inst.scale = decor.scale
        }
    }
    fun status() = "blockDecor enabled=$enabled decorations=${decorations.size}"
}
class InteractionIkSelector {
    var footIk = true
    var headEyeFollow = true
    var selectedBone = ""
    var selectedMaterial = ""
    var hitDistance = 0f
    fun tick(renderer: WorldRenderer) {
        val player = Minecraft.getInstance().player ?: return
        if (headEyeFollow) renderer.ik.targetEntity = player
        if (footIk) renderer.instance.pos.y = renderer.instance.pos.y.coerceAtLeast(player.blockY.toFloat())
        selectedBone = renderer.sockets.names().firstOrNull() ?: selectedBone
        hitDistance = renderer.instance.distanceSq(player.x, player.y, player.z).toFloat()
    }
    fun status() = "interaction footIk=$footIk headEyeFollow=$headEyeFollow bone=$selectedBone material=$selectedMaterial hitDistance=$hitDistance"
}
class ImportOptimizer {
    var autoLod = true
    var compressTextures = true
    var mergeMaterials = true
    var generatedLods = 0; private set
    var compressedTextures = 0; private set
    fun tick(renderer: WorldRenderer) {
        if (autoLod) generatedLods = renderer.acceleration?.clusters?.count { it.lodLevel > 0 } ?: 0
        if (compressTextures) compressedTextures = renderer.renderInfo().firstOrNull()?.substringAfter("textures=", "0")?.substringBefore(" ")?.toIntOrNull() ?: compressedTextures
    }
    fun status() = "optimizer autoLod=$autoLod generatedLods=$generatedLods compressTextures=$compressTextures compressed=$compressedTextures mergeMaterials=$mergeMaterials"
}
class CompatLayer {
    val formats = linkedMapOf("vrm" to "VRM/VRoid", "bbmodel" to "Blockbench", "geo.json" to "GeckoLib", "figura.json" to "Figura")
    var lastDetected = ""
    fun detect(file: File): String {
        lastDetected = formats.entries.firstOrNull { file.name.endsWith(it.key, true) }?.value ?: "Assimp"
        return lastDetected
    }
    fun status() = "compat formats=${formats.values.joinToString()} last=$lastDetected"
}
class PreviewScene {
    var enabled = true
    var room = "black_stage"
    var lightYaw = 35f
    var lightPitch = 45f
    var floorShadow = true
    var cameraDistance = 5f
    fun tick(renderer: WorldRenderer) { if (enabled && renderer.instance.previewPinned) renderer.pinPreview(cameraDistance) }
    fun status() = "preview enabled=$enabled room=$room lightYaw=$lightYaw lightPitch=$lightPitch floorShadow=$floorShadow cameraDistance=$cameraDistance"
}
