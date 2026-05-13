package cn.chen.assimp.render
import cn.chen.assimp.anim.AIAnimator
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.loader.AIModelLoader
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.joml.Vector3f
import java.io.File
class AIWorldRenderer {
    var scene: AISceneData? = null; private set
    var animator: AIAnimator? = null; private set
    var loaded = false; private set
    val instance = AIModelInstance()
    private val texReg = AITextureRegistry()
    private val compiler = AIBatchCompiler(texReg)
    private val boneBuffer = AIBoneBuffer()
    var playerReflection = false
    var dynamicLights = true
    var rimIntensity = 0.4f
    var bloomIntensity = 2.0f
    var blockShadows = true
    var shadowStrength = 0.6f
    private var compiled: AIBatchCompiler.Result? = null
    private var lastTime = System.nanoTime()
    private val tmpCamVec = Vector3f()
    private val tmpCamLook = Vector3f(0f, 0f, -1f)
    fun load(path: String) {
        if (loaded) unload()
        scene = AIModelLoader.loadFromFile(path)
        loaded = true
        val s = scene!!
        texReg.register(s, File(path).parent ?: "")
        compiled = compiler.compile(s)
        if (s.hasAnimations) {
            animator = AIAnimator(s)
            animator!!.play(0)
            animator!!.loop = true
        }
        val hasSkinning = compiled!!.hasSkinning
        if (hasSkinning) {
            boneBuffer.init(s)
        }
    }
    fun unload() {
        boneBuffer.release()
        compiled?.close()
        compiled = null
        texReg.release()
        scene = null; animator = null; loaded = false
    }
    fun render(ctx: LevelRenderContext) {
        val c = compiled ?: return
        if (!loaded || !instance.visible) return
        val now = System.nanoTime()
        val dt = (now - lastTime) / 1e9; lastTime = now
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera()
        val cam = camera.position()
        val look = camera.forwardVector()
        tmpCamLook.set(look.x(), look.y(), look.z())
        val cullDist = CULL_DISTANCE + c.boundRadius * instance.scale
        if (instance.distanceSq(cam.x, cam.y, cam.z) > cullDist * cullDist) return
        animator?.update(dt)
        val hasAnim = animator != null && boneBuffer.hasBones()
        if (hasAnim) boneBuffer.update()
        val modelMat = instance.buildModelMatrix(cam.x, cam.y, cam.z)
        val objectMat = instance.buildObjectMatrix()
        tmpCamVec.set(cam.x.toFloat(), cam.y.toFloat(), cam.z.toFloat())
        if (hasAnim) {
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            boneBuffer.flush(encoder)
            encoder.submit()
        }
        AIGpuPassRenderer.render(c.batches, c.passRanges, c.mergedVbo, modelMat, objectMat, tmpCamVec, tmpCamLook, instance.scale, boneBuffer.slice(), null, null, null, null, null, null, null)
    }
    fun info(): List<String> {
        val lines = mutableListOf<String>()
        val s = scene
        lines.add("loaded=$loaded meshes=${s?.meshes?.size ?: 0} mats=${s?.materials?.size ?: 0}")
        lines.add("anims=${s?.animations?.size ?: 0} bones=${s?.skeleton?.boneCount ?: 0}")
        val c = compiled
        if (c != null) {
            val counts = c.passRanges.map { (p, r) -> "${p.name}=${if (r.isEmpty()) 0 else r.last - r.first + 1}" }
            lines.add("textures=${texReg.size} batches=${c.batches.size}(${counts.joinToString(" ")}) quads=${c.totalQuads}")
            lines.add("bound center=(${c.boundCenter.x},${c.boundCenter.y},${c.boundCenter.z}) radius=${c.boundRadius}")
        }
        lines.add("pos=${instance.pos} scale=${instance.scale} rot=${instance.rot}")
        lines.add("fx: lights=$dynamicLights player=$playerReflection rim=$rimIntensity bloom=$bloomIntensity blockShadow=$blockShadows shadowStrength=$shadowStrength")
        s?.materials?.forEachIndexed { i, m ->
            lines.add("  mat[$i] ${m.name} alpha=${m.alphaMode} emissive=${m.emissiveFactor.toList()} ds=${m.doubleSided} tex=${m.textures.keys}")
        }
        return lines
    }
    fun destroy() { unload() }
    companion object {
        private const val CULL_DISTANCE = 256.0
    }
}
