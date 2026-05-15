package cn.chen.blaze3d.mmd
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Vec3
import java.io.File
enum class MmdFormat { PMX, PMD, VMD, VPD, OTHER }
data class MmdBoneFrame(val boneName: String, val frame: Int, val position: Vec3, val rotation: Quat)
data class MmdBezier(val x1: Int, val y1: Int, val x2: Int, val y2: Int)
data class MmdBoneInterpolation(val x: MmdBezier, val y: MmdBezier, val z: MmdBezier, val rotation: MmdBezier)
data class MmdBoneFrameCurve(val boneName: String, val frame: Int, val interpolation: MmdBoneInterpolation)
data class MmdMorphFrame(val morphName: String, val frame: Int, val weight: Float)
data class MmdCameraFrame(val frame: Int, val distance: Float, val position: Vec3, val rotation: Vec3, val fov: Int)
data class MmdLightFrame(val frame: Int, val color: Vec3, val position: Vec3)
data class MmdShadowFrame(val frame: Int, val mode: Int, val distance: Float)
data class MmdIkEnable(val name: String, val enabled: Boolean)
data class MmdIkFrame(val frame: Int, val show: Boolean, val iks: List<MmdIkEnable>)
data class MmdMotion(val name: String, val bones: List<MmdBoneFrame>, val morphs: List<MmdMorphFrame>, val cameras: List<MmdCameraFrame>, val lights: List<MmdLightFrame>, val shadows: List<MmdShadowFrame> = emptyList(), val ikFrames: List<MmdIkFrame> = emptyList(), val boneCurves: List<MmdBoneFrameCurve> = emptyList()) {
    val durationFrame = maxOf(bones.maxOfOrNull { it.frame } ?: 0, morphs.maxOfOrNull { it.frame } ?: 0, cameras.maxOfOrNull { it.frame } ?: 0, lights.maxOfOrNull { it.frame } ?: 0, shadows.maxOfOrNull { it.frame } ?: 0, ikFrames.maxOfOrNull { it.frame } ?: 0)
    fun toClip(): AnimClip = MmdMotionCompiler.compile(this)
}
data class MmdPose(val name: String, val bones: List<MmdBoneFrame>, val morphs: List<MmdMorphFrame>)
data class MmdMaterialMeta(val materialName: String, val toonTexture: String = "", val sphereTexture: String = "", val sphereMode: String = "", val edgeColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f), val edgeSize: Float = 0f, val doubleSided: Boolean = true)
data class MmdBoneMeta(val name: String, val englishName: String = "", val parentIndex: Int = -1, val layer: Int = 0, val flags: Int = 0, val position: Vec3 = Vec3(), val targetIndex: Int = -1, val ik: MmdIkMeta? = null)
data class MmdIkMeta(val targetIndex: Int, val loopCount: Int, val limitRadian: Float, val links: List<MmdIkLinkMeta>)
data class MmdIkLinkMeta(val boneIndex: Int, val limited: Boolean, val min: Vec3 = Vec3(), val max: Vec3 = Vec3())
data class MmdMorphMeta(val name: String, val englishName: String = "", val category: Int = 0, val type: Int = 0, val offsetCount: Int = 0)
data class MmdRigidBodyMeta(val name: String, val englishName: String = "", val boneIndex: Int = -1, val group: Int = 0, val mask: Int = 0, val shape: Int = 0, val size: Vec3 = Vec3(), val position: Vec3 = Vec3(), val rotation: Vec3 = Vec3(), val mass: Float = 0f, val mode: Int = 0)
data class MmdJointMeta(val name: String, val englishName: String = "", val type: Int = 0, val rigidBodyA: Int = -1, val rigidBodyB: Int = -1, val position: Vec3 = Vec3(), val rotation: Vec3 = Vec3())
data class MmdPhysicsChain(val name: String, val type: MmdPhysicsChainType, val boneIndices: List<Int>, val rigidBodyIndices: List<Int>, val jointIndices: List<Int>)
enum class MmdPhysicsChainType { HAIR, SKIRT, CLOTH, ACCESSORY, BODY }
data class MmdToonProfile(val celEnabled: Boolean, val outlineEnabled: Boolean, val doubleSidedTransparent: Boolean, val toonTextures: List<String>, val sphereTextures: List<String>, val outlineMaterials: Int)
data class MmdStageProfile(val stage: Boolean, val staticScene: Boolean, val hasBackdrop: Boolean, val hasFloor: Boolean, val propBones: Int)
data class MmdRuntimeState(val expression: String = "", val expressionWeight: Float = 0f, val cameraFrame: Int = 0, val lightFrame: Int = 0, val shadowFrame: Int = 0, val ikFrame: Int = 0)
data class MmdPhysicsRuntime(val chainCount: Int, val dynamicBodies: Int, val writeBackBones: Int, val windStrength: Float, val collisionGroups: Int)
data class MmdModelMeta(val format: MmdFormat, val name: String, val englishName: String, val comment: String, val englishComment: String, val materials: List<MmdMaterialMeta>, val bones: List<MmdBoneMeta>, val morphs: List<MmdMorphMeta>, val rigidBodies: List<MmdRigidBodyMeta>, val joints: List<MmdJointMeta>, val physicsChains: List<MmdPhysicsChain> = emptyList(), val toonProfile: MmdToonProfile = MmdToonProfile(false, false, false, emptyList(), emptyList(), 0), val stageProfile: MmdStageProfile = MmdStageProfile(false, false, false, false, 0))
object MmdFormatDetector {
    fun detect(path: String): MmdFormat = when (File(path).extension.lowercase()) {
        "pmx" -> MmdFormat.PMX
        "pmd" -> MmdFormat.PMD
        "vmd" -> MmdFormat.VMD
        "vpd" -> MmdFormat.VPD
        else -> MmdFormat.OTHER
    }
    fun isModel(path: String) = detect(path) == MmdFormat.PMX || detect(path) == MmdFormat.PMD
    fun isMotion(path: String) = detect(path) == MmdFormat.VMD
    fun isPose(path: String) = detect(path) == MmdFormat.VPD
}
