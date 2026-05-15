package cn.chen.blaze3d.mmd
class MmdRuntime {
    var state = MmdRuntimeState(); private set
    var physics = MmdPhysicsRuntime(0, 0, 0, 0f, 0); private set
    var stageFrame = 0
    var expressionOverride = ""
    var mouthWeight = 0f
    var blinkWeight = 0f
    private var lastMotion: MmdMotion? = null
    private var morphHint = 0
    private var cameraHint = 0
    private var lightHint = 0
    private var shadowHint = 0
    private var ikHint = 0
    fun bind(model: MmdModelMeta?) {
        if (model == null) {
            physics = MmdPhysicsRuntime(0, 0, 0, 0f, 0)
            return
        }
        val groups = model.rigidBodies.map { it.group }.distinct().size
        physics = MmdPhysicsRuntime(model.physicsChains.size, model.rigidBodies.count { it.mode != 0 }, model.physicsChains.sumOf { it.boneIndices.size }, 0.35f, groups)
    }
    fun update(motion: MmdMotion?, seconds: Double) {
        if (motion == null) return
        if (motion !== lastMotion) { lastMotion = motion; morphHint = 0; cameraHint = 0; lightHint = 0; shadowHint = 0; ikHint = 0 }
        val frame = (seconds * 30.0).toInt()
        val morph = motion.morphs.getOrNull(findFrame(motion.morphs.size, morphHint, frame) { motion.morphs[it].frame }.also { morphHint = it })
        val camera = motion.cameras.getOrNull(findFrame(motion.cameras.size, cameraHint, frame) { motion.cameras[it].frame }.also { cameraHint = it })
        val light = motion.lights.getOrNull(findFrame(motion.lights.size, lightHint, frame) { motion.lights[it].frame }.also { lightHint = it })
        val shadow = motion.shadows.getOrNull(findFrame(motion.shadows.size, shadowHint, frame) { motion.shadows[it].frame }.also { shadowHint = it })
        val ik = motion.ikFrames.getOrNull(findFrame(motion.ikFrames.size, ikHint, frame) { motion.ikFrames[it].frame }.also { ikHint = it })
        val expr = if (expressionOverride.isNotBlank()) expressionOverride else morph?.morphName ?: state.expression
        val weight = maxOf(morph?.weight ?: state.expressionWeight, mouthWeight, blinkWeight)
        state = MmdRuntimeState(expr, weight, camera?.frame ?: state.cameraFrame, light?.frame ?: state.lightFrame, shadow?.frame ?: state.shadowFrame, ik?.frame ?: state.ikFrame)
    }
    private fun findFrame(size: Int, hint: Int, frame: Int, at: (Int) -> Int): Int {
        if (size == 0) return -1
        var i = hint.coerceIn(0, size - 1)
        if (at(i) > frame) i = 0
        while (i + 1 < size && at(i + 1) <= frame) i++
        return i
    }
}
