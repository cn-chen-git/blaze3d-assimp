package cn.chen.blaze3d.anim
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.core.NodeGraph
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.math.Quat
import cn.chen.blaze3d.math.Vec3
class AnimState(val name: String, val clip: AnimClip, val loop: Boolean = true, val speed: Float = 1f)
class AnimTransition(val from: String, val to: String, val duration: Float, val condition: () -> Boolean)
class AnimStateMachine(private val skeleton: BonePose, private val rootNode: NodeGraph) {
    private val states = mutableMapOf<String, AnimState>()
    private val transitions = mutableListOf<AnimTransition>()
    private var currentState: AnimState? = null
    private var nextState: AnimState? = null
    private var blendFactor = 0f
    private var transitionDuration = 0f
    private var currentTime = 0.0
    private var nextTime = 0.0
    private var stack: Array<Mat4?> = arrayOfNulls(64)
    private val identityMat = Mat4()
    private val tmpLocal = Mat4()
    private val tmpInter = Mat4()
    private val pos1 = Vec3(); private val pos2 = Vec3()
    private val rot1 = Quat(); private val rot2 = Quat(); private val rotB = Quat()
    private val scl1 = Vec3(1f,1f,1f); private val scl2 = Vec3(1f,1f,1f)
    fun addState(state: AnimState) { states[state.name] = state }
    fun addTransition(from: String, to: String, duration: Float, condition: () -> Boolean) { transitions.add(AnimTransition(from, to, duration, condition)) }
    fun setState(name: String) { currentState = states[name]; currentTime = 0.0; nextState = null; blendFactor = 0f }
    fun crossFade(name: String, duration: Float) {
        val target = states[name] ?: return
        if (currentState == null) { currentState = target; currentTime = 0.0; return }
        if (currentState === target) return
        nextState = target; nextTime = 0.0; transitionDuration = duration.coerceAtLeast(1e-3f); blendFactor = 0f
    }
    fun update(deltaTime: Double) {
        val cur = currentState ?: return
        val nxt = nextState
        if (nxt != null) {
            blendFactor += (deltaTime / transitionDuration).toFloat()
            if (blendFactor >= 1f) { currentState = nxt; currentTime = nextTime; nextState = null; blendFactor = 0f }
            else {
                currentTime += deltaTime * cur.speed
                nextTime += deltaTime * nxt.speed
                wrapTime(cur); wrapNextTime(nxt)
                val ticks1 = if (cur.clip.ticksPerSecond > 0.0) cur.clip.ticksPerSecond else 25.0
                val ticks2 = if (nxt.clip.ticksPerSecond > 0.0) nxt.clip.ticksPerSecond else 25.0
                val t1 = (currentTime * ticks1) % cur.clip.duration
                val t2 = (nextTime * ticks2) % nxt.clip.duration
                identityMat.setIdentity()
                traverseBlend(rootNode, cur.clip, nxt.clip, t1, t2, identityMat, 0)
                skeleton.markDirty()
                return
            }
        }
        for (t in transitions) if (t.from == cur.name && t.condition()) { nextState = states[t.to]; transitionDuration = t.duration.coerceAtLeast(1e-3f); nextTime = 0.0; blendFactor = 0f; break }
        currentTime += deltaTime * cur.speed
        wrapTime(cur)
        skeleton.computeBoneTransforms(cur.clip, currentTime, rootNode)
    }
    private fun wrapTime(s: AnimState) {
        val d = s.clip.durationSeconds
        if (currentTime > d) currentTime = if (s.loop) currentTime % d else d
    }
    private fun wrapNextTime(s: AnimState) {
        val d = s.clip.durationSeconds
        if (nextTime > d) nextTime = if (s.loop) nextTime % d else d
    }
    private fun traverseBlend(node: NodeGraph, c1: AnimClip, c2: AnimClip, t1: Double, t2: Double, parent: Mat4, depth: Int) {
        val ch1 = c1.getChannel(node.name); val ch2 = c2.getChannel(node.name)
        val local = tmpLocal
        when {
            ch1 != null && ch2 != null -> {
                ch1.interpolatePositionInto(t1, pos1); ch2.interpolatePositionInto(t2, pos2)
                ch1.interpolateRotationInto(t1, rot1); ch2.interpolateRotationInto(t2, rot2)
                ch1.interpolateScalingInto(t1, scl1); ch2.interpolateScalingInto(t2, scl2)
                rot1.slerpInto(rot2, blendFactor, rotB)
                val px = pos1.x + (pos2.x - pos1.x) * blendFactor
                val py = pos1.y + (pos2.y - pos1.y) * blendFactor
                val pz = pos1.z + (pos2.z - pos1.z) * blendFactor
                val sx = scl1.x + (scl2.x - scl1.x) * blendFactor
                val sy = scl1.y + (scl2.y - scl1.y) * blendFactor
                val sz = scl1.z + (scl2.z - scl1.z) * blendFactor
                Mat4.trsInto(px, py, pz, rotB.x, rotB.y, rotB.z, rotB.w, sx, sy, sz, local)
            }
            ch1 != null -> ch1.computeTransformInto(t1, local)
            ch2 != null -> ch2.computeTransformInto(t2, local)
            else -> local.copyFrom(node.transform)
        }
        val global = slotAt(depth)
        parent.mulInto(local, global)
        val bone = skeleton.bones[node.name]
        if (bone != null) {
            skeleton.globalInverseTransform.mulInto(global, tmpInter)
            tmpInter.mulInto(bone.offsetMatrix, skeleton.boneMatrices[bone.index])
        }
        for (child in node.children) traverseBlend(child, c1, c2, t1, t2, global, depth + 1)
    }
    private fun slotAt(depth: Int): Mat4 {
        if (depth >= stack.size) stack = stack.copyOf(stack.size * 2)
        var s = stack[depth]; if (s == null) { s = Mat4(); stack[depth] = s }
        return s
    }
    val currentStateName get() = currentState?.name
    val isTransitioning get() = nextState != null
    val transitionProgress get() = blendFactor
}
