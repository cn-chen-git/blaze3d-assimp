package cn.chen.assimp.render
import cn.chen.assimp.anim.AIAnimator
import cn.chen.assimp.core.AISceneData
import cn.chen.assimp.loader.AIModelLoader
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.joml.Vector3f
import java.io.File
import kotlin.math.sqrt
class AIWorldRenderer {
    var scene: AISceneData? = null; private set
    var animator: AIAnimator? = null; private set
    var loaded = false; private set
    val instance = AIModelInstance()
    private val texReg = AITextureRegistry()
    private val compiler = AIBatchCompiler(texReg)
    private val boneBuffer = AIBoneBuffer()
    private val materialBuffer = AIMaterialBuffer()
    private val objectBuffer = AIObjectBuffer()
    private val shadowBuffer = AIShadowBuffer()
    private val shadowMap = AIShadowMap()
    private val environmentMap = AIEnvironmentMap()
    private var compiled: AIBatchCompiler.Result? = null
    private var lastTime = System.nanoTime()
    private val tmpShadowCenter = Vector3f()
    private val tmpCamVec = Vector3f()
    private var shadowDirty = true
    private var shadowFrame = 0
    private val lastShadowCenter = Vector3f()
    private var lastShadowRadius = 0f
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
        val hasPbr = compiled!!.passRanges.keys.any { it.isPbr }
        if (hasPbr) {
            boneBuffer.init(s)
            materialBuffer.init(s)
            objectBuffer.init()
            shadowBuffer.init()
            shadowMap.init()
            environmentMap.init()
        }
    }
    fun unload() {
        boneBuffer.release()
        materialBuffer.release()
        objectBuffer.release()
        shadowBuffer.release()
        shadowMap.release()
        environmentMap.release()
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
        animator?.update(dt)
        val hasAnim = animator != null && boneBuffer.hasBones()
        if (hasAnim) boneBuffer.update()
        val cam = Minecraft.getInstance().gameRenderer.mainCamera().position()
        val cullDist = CULL_DISTANCE + c.boundRadius * instance.scale
        if (instance.distanceSq(cam.x, cam.y, cam.z) > cullDist * cullDist) return
        val modelMat = instance.buildModelMatrix(cam.x, cam.y, cam.z)
        val objectMat = instance.buildObjectMatrix()
        tmpCamVec.set(cam.x.toFloat(), cam.y.toFloat(), cam.z.toFloat())
        tmpShadowCenter.set(c.boundCenter); objectMat.transformPosition(tmpShadowCenter)
        val shadowRadius = c.boundRadius * instance.scale
        objectBuffer.update(objectMat)
        val needShadow = checkShadowDirty(tmpShadowCenter, shadowRadius, hasAnim)
        if (needShadow) shadowBuffer.update(tmpShadowCenter, shadowRadius, sqrt(instance.distanceSq(cam.x, cam.y, cam.z)).toFloat(), SHADOW_LIGHT, shadowMap.size)
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        boneBuffer.flush(encoder)
        objectBuffer.flush(encoder)
        shadowBuffer.flush(encoder)
        encoder.submit()
        if (needShadow) AICascadeShadowRenderer.render(c.batches, c.passRanges, c.mergedVbo, boneBuffer.slice(), objectBuffer.slice(), shadowBuffer.slice(), shadowMap, materialBuffer)
        AIGpuPassRenderer.render(c.batches, c.passRanges, c.mergedVbo, modelMat, objectMat, tmpCamVec, instance.scale, boneBuffer.slice(), objectBuffer.slice(), shadowBuffer.slice(), shadowMap.view(), environmentMap, materialBuffer)
    }
    private fun checkShadowDirty(center: Vector3f, radius: Float, hasAnim: Boolean): Boolean {
        shadowFrame++
        if (shadowDirty) { shadowDirty = false; lastShadowCenter.set(center); lastShadowRadius = radius; return true }
        if (hasAnim && shadowFrame % SHADOW_ANIM_INTERVAL == 0) return true
        val dx = center.x - lastShadowCenter.x; val dy = center.y - lastShadowCenter.y; val dz = center.z - lastShadowCenter.z
        if (dx * dx + dy * dy + dz * dz > 0.01f) { lastShadowCenter.set(center); lastShadowRadius = radius; return true }
        return false
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
        s?.materials?.forEachIndexed { i, m ->
            lines.add("  mat[$i] ${m.name} alpha=${m.alphaMode} emissive=${m.emissiveFactor.toList()} ds=${m.doubleSided} tex=${m.textures.keys}")
        }
        return lines
    }
    fun destroy() { unload() }
    companion object {
        private const val CULL_DISTANCE = 256.0
        private const val SHADOW_ANIM_INTERVAL = 3
        private val SHADOW_LIGHT = Vector3f(-0.35f, -1f, -0.25f)
    }
}
