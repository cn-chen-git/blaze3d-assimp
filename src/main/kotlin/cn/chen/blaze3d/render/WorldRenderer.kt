package cn.chen.blaze3d.render
import cn.chen.blaze3d.advanced.AccelerationBuilder
import cn.chen.blaze3d.advanced.AccelerationData
import cn.chen.blaze3d.advanced.AnimationAudioBus
import cn.chen.blaze3d.advanced.Attachment
import cn.chen.blaze3d.advanced.IkFkController
import cn.chen.blaze3d.advanced.InstanceManager
import cn.chen.blaze3d.advanced.ModuleSystems
import cn.chen.blaze3d.advanced.SocketRegistry
import cn.chen.blaze3d.async.GpuUploadQueue
import cn.chen.blaze3d.async.RenderWorker
import cn.chen.blaze3d.anim.Animator
import cn.chen.blaze3d.core.SceneData
import cn.chen.blaze3d.api.ModelFormatRegistry
import cn.chen.blaze3d.api.ModelLoadResult
import cn.chen.blaze3d.api.ModelPackage
import cn.chen.blaze3d.api.ModelSupport
import cn.chen.blaze3d.mmd.MmdFormatDetector
import cn.chen.blaze3d.mmd.MmdActionMapper
import cn.chen.blaze3d.mmd.MmdDefaultMotionLibrary
import cn.chen.blaze3d.mmd.MmdMaterialMeta
import cn.chen.blaze3d.mmd.MmdModelMeta
import cn.chen.blaze3d.mmd.MmdModelMetadataParser
import cn.chen.blaze3d.mmd.MmdMotion
import cn.chen.blaze3d.mmd.MmdPose
import cn.chen.blaze3d.mmd.MmdProjectConfig
import cn.chen.blaze3d.mmd.MmdProjectConfigLoader
import cn.chen.blaze3d.mmd.MmdRuntime
import cn.chen.blaze3d.mmd.MmdEditorState
import cn.chen.blaze3d.mmd.MmdStagePlayback
import cn.chen.blaze3d.mmd.MmdVmdParser
import cn.chen.blaze3d.mmd.MmdVpdParser
import cn.chen.blaze3d.physics.PhysicsBinding
import cn.chen.blaze3d.player.PlayerAssetInstaller
import cn.chen.blaze3d.player.PlayerReplacementRuntime
import cn.chen.blaze3d.preset.ModelPresetStore
import cn.chen.blaze3d.sync.ModelStatePayload
import cn.chen.blaze3d.sync.ModelSync
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File
import java.util.concurrent.CompletableFuture
class WorldRenderer {
    var scene: SceneData? = null; private set
    var animator: Animator? = null; private set
    var loaded = false; private set
    var loading = false; private set
    var lastError = ""; private set
    var modelPath = ""; private set
    var modelSupport: ModelSupport? = null; private set
    var acceleration: AccelerationData? = null; private set
    var visibleClusterCount = 0; private set
    var mmdModel: MmdModelMeta? = null; private set
    var mmdMaterials: List<MmdMaterialMeta> = emptyList(); private set
    var mmdMotion: MmdMotion? = null; private set
    var mmdPose: MmdPose? = null; private set
    var mmdProject: MmdProjectConfig? = null; private set
    val mmdRuntime = MmdRuntime()
    val mmdActions = MmdActionMapper()
    private val mmdMotionClips = HashMap<String, MmdMotion>()
    var defaultMmdMotionCount = 0; private set
    val instance = ModelInstance()
    val physics = PhysicsBinding()
    val attachment = Attachment()
    val sockets = SocketRegistry()
    val instances = InstanceManager()
    val audio = AnimationAudioBus()
    val ik = IkFkController()
    val playerReplacement = PlayerReplacementRuntime.system
    val stagePlayback = MmdStagePlayback()
    val editor = MmdEditorState()
    val performance = PerformanceConfig()
    val modules = ModuleSystems()
    private val texReg = TextureRegistry()
    private val compiler = BatchCompiler(texReg)
    private val boneBuffer = BoneBuffer()
    private var compiled: BatchCompiler.Result? = null
    private var pending: CompletableFuture<ModelLoadResult>? = null
    private var lastTime = System.nanoTime()
    private val tmpCamVec = Vector3f()
    private val tmpCamLook = Vector3f(0f, 0f, -1f)
    private val tmpTint = Vector4f()
    fun load(path: String) {
        if (loaded) unload()
        lastError = ""
        val asset = ModelPackage.mainAsset(File(path)) ?: File(path)
        val result = ModelFormatRegistry.load(asset.path)
        bindLoaded(result, asset)
    }
    fun loadAsync(path: String) {
        if (path.isBlank()) return
        lastError = ""
        val asset = ModelPackage.mainAsset(File(path)) ?: File(path)
        if (loading && pending != null && asset.path == modelPath) return
        if (loaded && asset.path == modelPath) return
        loading = true
        pending?.cancel(true)
        pending = RenderWorker.load(asset.path).whenComplete { result, error ->
            loading = false
            if (error == null && result != null) GpuUploadQueue.enqueue { applyLoaded(result) } else lastError = error?.message ?: "load failed"
        }
    }
    private fun applyLoaded(result: ModelLoadResult) {
        unload()
        bindLoaded(result, File(result.path))
    }
    private fun bindLoaded(result: ModelLoadResult, asset: File) {
        val loadedScene = result.scene
        val built = compiler.compile(loadedScene)
        scene = loadedScene
        loaded = true
        modelPath = result.path
        modelSupport = result.support
        mmdModel = if (MmdFormatDetector.isModel(asset.path)) MmdModelMetadataParser.parseModel(asset.path) else null
        mmdMaterials = mmdModel?.materials ?: emptyList()
        acceleration = AccelerationBuilder.build(loadedScene)
        texReg.register(loadedScene, result.baseDir)
        compiled = built
        physics.bind(loadedScene)
        physics.bindMmd(mmdModel?.physicsChains?.size ?: 0, mmdModel?.rigidBodies?.count { it.mode != 0 } ?: 0)
        mmdRuntime.bind(mmdModel)
        performance.sortedMaterials = loadedScene.materials.size
        performance.atlasTextures = texReg.size
        modules.compat.detect(asset)
        animator = loadedScene.takeIf { it.hasAnimations }?.let { Animator(it).apply { play(0); loop = true } }
        if (built.hasSkinning) boneBuffer.init(loadedScene)
    }
    fun unload() {
        pending?.cancel(true); pending = null; loading = false
        boneBuffer.release()
        compiled?.close()
        compiled = null
        texReg.release()
        physics.clear()
        scene = null; animator = null; loaded = false; modelPath = ""; modelSupport = null; acceleration = null; visibleClusterCount = 0; mmdModel = null; mmdMaterials = emptyList(); mmdMotion = null; mmdPose = null; mmdProject = null; mmdRuntime.bind(null)
    }
    fun pinPreview(distance: Float = 4f) {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera()
        val cam = camera.position(); val forward = camera.forwardVector()
        instance.pos.set((cam.x + forward.x() * distance).toFloat(), (cam.y + forward.y() * distance).toFloat(), (cam.z + forward.z() * distance).toFloat())
        instance.previewPinned = true
    }
    fun faceCamera() = Minecraft.getInstance().gameRenderer.mainCamera().let { instance.rot.y = 180f - it.yRot(); instance.rot.x = -it.xRot() }
    fun updateFollowTarget() {
        if (!instance.followPlayer) return
        val player = Minecraft.getInstance().player ?: return
        instance.pos.set(player.x.toFloat(), player.y.toFloat(), player.z.toFloat())
    }
    fun savePreset(name: String) {
        ModelPresetStore.save(presetFile(name), ModelPresetStore.from(modelPath, instance, physics, animator, attachment))
    }
    fun loadPreset(name: String) {
        val file = presetFile(name)
        if (!file.isFile) return
        val preset = ModelPresetStore.load(file)
        if (preset.path.isNotBlank() && preset.path != modelPath) loadAsync(preset.path)
        ModelPresetStore.apply(preset, instance, physics, animator, attachment)
    }
    fun loadMmdMotion(path: String) {
        if (!MmdFormatDetector.isMotion(path)) return
        val motion = MmdVmdParser.parse(File(path))
        mmdMotion = motion
        mmdMotionClips[File(path).nameWithoutExtension] = motion
        val clip = motion.toClip()
        animator?.play(clip)
        scene?.let { it.skeleton?.computeBoneTransforms(clip, 0.0, it.rootNode) }
    }
    fun loadMmdPose(path: String) {
        if (!MmdFormatDetector.isPose(path)) return
        mmdPose = MmdVpdParser.parse(File(path))
        val pose = mmdPose ?: return
        val clip = MmdMotion(path, pose.bones, pose.morphs, emptyList(), emptyList()).toClip()
        animator?.play(clip)
        scene?.let { it.skeleton?.computeBoneTransforms(clip, 0.0, it.rootNode) }
    }
    fun loadMmdProject(path: String) {
        val file = File(path)
        if (!file.isFile) return
        val cfg = MmdProjectConfigLoader.load(file)
        mmdProject = cfg
        stagePlayback.load(cfg)
        loadDefaultMmdMotions()
        if (cfg.model.isNotBlank()) loadAsync(File(file.parentFile, cfg.model).path)
        for (motion in cfg.motions) {
            val motionFile = File(file.parentFile, motion)
            if (motionFile.isFile) {
                val parsed = MmdVmdParser.parse(motionFile)
                mmdMotionClips[motionFile.nameWithoutExtension] = parsed
                mmdMotionClips[parsed.name] = parsed
            }
        }
        if (cfg.motions.isNotEmpty()) loadMmdMotion(File(file.parentFile, cfg.motions.first()).path)
        if (cfg.poses.isNotEmpty()) loadMmdPose(File(file.parentFile, cfg.poses.first()).path)
        instance.scale = cfg.scale
        physics.enabled = cfg.physics
    }
    fun applyRemote(payload: ModelStatePayload) {
        ModelSync.apply(payload, instance)
        if (payload.path.isNotBlank() && payload.path != modelPath) loadAsync(payload.path)
        if (payload.animation.isNotBlank()) animator?.play(payload.animation)
        mmdRuntime.expressionOverride = payload.expression
        mmdRuntime.mouthWeight = payload.expressionWeight
        physics.enabled = payload.physics
        if (payload.preset.isNotBlank()) loadPreset(payload.preset)
    }
    fun installTemplate() = PlayerAssetInstaller.prepare()
    fun scanPlayerProjects() = playerReplacement.refresh(true)
    private fun presetFile(name: String): File {
        val safe = name.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(Minecraft.getInstance().gameDirectory, "config/blaze3d-model/presets/$safe.json")
    }
    fun render(ctx: LevelRenderContext) {
        val c = compiled ?: return
        if (!loaded || !instance.visible) return
        updateFollowTarget()
        attachment.apply(instance, sockets)
        ik.solve(instance)
        physics.sync(instance)
        playerReplacement.tick(this)
        val pr = playerReplacement
        mmdRuntime.expressionOverride = pr.expression; mmdRuntime.mouthWeight = pr.mouthWeight; mmdRuntime.blinkWeight = pr.blinkWeight
        modules.tick(this)
        val mc = Minecraft.getInstance()
        val worldBox = physics.shape?.box(instance.pos, instance.scale) ?: return
        mc.level?.getEntities(null, worldBox.inflate(0.2)) { it != mc.player }?.let { physics.collideEntities(instance, it) }
        val now = System.nanoTime(); val dt = (now - lastTime) / 1e9; lastTime = now
        val camera = mc.gameRenderer.mainCamera()
        val cam = camera.position(); val look = camera.forwardVector()
        tmpCamLook.set(look.x(), look.y(), look.z())
        val cullDist = CULL_DISTANCE + c.boundRadius * instance.scale
        if (instance.distanceSq(cam.x, cam.y, cam.z) > cullDist * cullDist) { performance.culledFrames++; return }
        val frustum = camera.getCullFrustum()
        if (!frustum.isVisible(worldBox)) { performance.culledFrames++; return }
        val lodBias = if (performance.autoLod) performance.lodBias else 0.1f
        visibleClusterCount = acceleration?.visibleClusters(cn.chen.blaze3d.math.Vec3(cam.x.toFloat(), cam.y.toFloat(), cam.z.toFloat()), frustum, instance.scale, lodBias)?.size ?: 0
        animator?.update(dt)
        updateMmdActionMapping()
        val animTime = animator?.currentTime ?: 0.0
        mmdRuntime.update(mmdMotion, animTime); stagePlayback.update(animTime, mmdRuntime)
        audio.tick(animator?.currentAnimation?.name, animTime, instance)
        val hasAnim = animator != null && boneBuffer.hasBones()
        if (hasAnim) { boneBuffer.update(); if (performance.gpuSkinningCache) performance.gpuSkinningCacheHits++ }
        performance.skippedBoneUploads = boneBuffer.skippedUploads
        performance.uploadedBoneFrames = boneBuffer.uploadedFrames
        sockets.update(scene?.skeleton)
        val modelMat = instance.buildModelMatrix(cam.x, cam.y, cam.z)
        val objectMat = instance.buildObjectMatrix()
        tmpCamVec.set(cam.x.toFloat(), cam.y.toFloat(), cam.z.toFloat())
        if (hasAnim) {
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            boneBuffer.flush(encoder); encoder.submit()
        }
        tmpTint.set(instance.tintR, instance.tintG, instance.tintB, instance.tintA)
        GpuPassRenderer.render(c.batches, c.passRanges, c.mergedVbo, modelMat, objectMat, tmpCamVec, tmpCamLook, instance.scale, tmpTint, boneBuffer.slice())
        performance.renderedFrames++
    }
    fun renderInfo(): List<String> {
        val lines = mutableListOf<String>()
        val c = compiled
        if (c != null) {
            val counts = c.passRanges.map { (p, r) -> "${p.name}=${if (r.isEmpty()) 0 else r.last - r.first + 1}" }
            lines.add("textures=${texReg.size} batches=${c.batches.size}(${counts.joinToString(" ")}) quads=${c.totalQuads}")
            lines.add("bound center=(${c.boundCenter.x},${c.boundCenter.y},${c.boundCenter.z}) radius=${c.boundRadius}")
        }
        val support = modelSupport
        if (support != null) lines.add("format=${support.id} features=${support.features.size} resources=${support.resources.size}")
        val mmd = mmdModel
        if (mmd != null || mmdMotion != null || mmdPose != null) lines.add("mmd model=${mmd?.name ?: "-"} bones=${mmd?.bones?.size ?: 0} morphs=${mmd?.morphs?.size ?: 0} bodies=${mmd?.rigidBodies?.size ?: 0} joints=${mmd?.joints?.size ?: 0} materials=${mmdMaterials.size} motion=${mmdMotion?.name ?: "-"} pose=${mmdPose?.name ?: "-"}")
        mmdMotion?.let { lines.add("mmd vmd camera=${it.cameras.size} light=${it.lights.size} shadow=${it.shadows.size} ik=${it.ikFrames.size}") }
        if (mmd != null) lines.add("mmd toon=${mmd.toonProfile.celEnabled} outline=${mmd.toonProfile.outlineMaterials} sphere=${mmd.toonProfile.sphereTextures.size} chains=${mmd.physicsChains.size} hair=${mmd.physicsChains.count { it.type.name == "HAIR" }} skirt=${mmd.physicsChains.count { it.type.name == "SKIRT" }} stage=${mmd.stageProfile.stage}")
        lines.add("mmd runtime expr=${mmdRuntime.state.expression}:${mmdRuntime.state.expressionWeight} camera=${mmdRuntime.state.cameraFrame} light=${mmdRuntime.state.lightFrame} shadow=${mmdRuntime.state.shadowFrame} ik=${mmdRuntime.state.ikFrame} writeback=${mmdRuntime.physics.writeBackBones} wind=${mmdRuntime.physics.windStrength}")
        lines.add("mmd stage ${stagePlayback.status()}")
        lines.add("player replacement ${playerReplacement.status()}")
        lines.add("editor ${editor.status()}")
        lines.add("performance ${performance.status()}")
        for (line in modules.status()) lines.add(line)
        lines.add("mmd action=${mmdActions.current} motion=${mmdActions.motionName} switches=${mmdActions.switches} curves=${mmdMotion?.boneCurves?.size ?: 0}")
        lines.add("mmd default motions=$defaultMmdMotionCount")
        lines.add("instances=${instances.count()} clusters=$visibleClusterCount/${acceleration?.clusters?.size ?: 0} bvh=${acceleration?.nodes?.size ?: 0} sockets=${sockets.names().size} sounds=${audio.count()} sync=${ModelSync.received}")
        lines.add("pos=${instance.pos} scale=${instance.scale} rot=${instance.rot}")
        return lines
    }
    fun textureStatus() = texReg.status()
    fun destroy() { unload() }
    private fun updateMmdActionMapping() {
        if (!instance.followPlayer) return
        val player = Minecraft.getInstance().player ?: return
        if (defaultMmdMotionCount == 0) loadDefaultMmdMotions()
        if (!mmdActions.update(player, mmdProject)) return
        val motion = mmdMotionClips[mmdActions.motionName] ?: mmdMotionClips["${mmdActions.motionName}.vmd"] ?: return
        mmdMotion = motion
        animator?.crossFade(motion.toClip(), 0.18f)
    }
    private fun loadDefaultMmdMotions() {
        if (defaultMmdMotionCount > 0) return
        val loaded = MmdDefaultMotionLibrary.load()
        mmdMotionClips.putAll(loaded)
        defaultMmdMotionCount = loaded.size
    }
    companion object {
        private const val CULL_DISTANCE = 256.0
    }
}
