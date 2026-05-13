package cn.chen.assimp.anim
import cn.chen.assimp.core.AIAnimClip
import cn.chen.assimp.core.AINodeGraph
import cn.chen.assimp.core.AIBonePose
import cn.chen.assimp.math.AIMat4
import cn.chen.assimp.math.AIQuat
import cn.chen.assimp.math.AIVec3
class AIAnimState(val name: String, val clip: AIAnimClip, val loop: Boolean = true, val speed: Float = 1f)
class AIAnimTransition(val from: String, val to: String, val duration: Float, val condition: () -> Boolean)
class AIAnimStateMachine(private val skeleton: AIBonePose, private val rootNode: AINodeGraph) {
    private val states = mutableMapOf<String, AIAnimState>()
    private val transitions = mutableListOf<AIAnimTransition>()
    private var currentState: AIAnimState? = null
    private var nextState: AIAnimState? = null
    private var blendFactor = 0f
    private var transitionDuration = 0f
    private var currentTime = 0.0
    private var nextTime = 0.0
    private var stack: Array<AIMat4?> = arrayOfNulls(64)
    private val identityMat = AIMat4()
    private val tmpLocal = AIMat4()
    private val tmpInter = AIMat4()
    private val pos1 = AIVec3(); private val pos2 = AIVec3()
    private val rot1 = AIQuat(); private val rot2 = AIQuat(); private val rotB = AIQuat()
    private val scl1 = AIVec3(1f,1f,1f); private val scl2 = AIVec3(1f,1f,1f)
    fun addState(state: AIAnimState) { states[state.name] = state }
    fun addTransition(from: String, to: String, duration: Float, condition: () -> Boolean) { transitions.add(AIAnimTransition(from, to, duration, condition)) }
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
    private fun wrapTime(s: AIAnimState) {
        val d = s.clip.durationSeconds
        if (currentTime > d) currentTime = if (s.loop) currentTime % d else d
    }
    private fun wrapNextTime(s: AIAnimState) {
        val d = s.clip.durationSeconds
        if (nextTime > d) nextTime = if (s.loop) nextTime % d else d
    }
    private fun traverseBlend(node: AINodeGraph, c1: AIAnimClip, c2: AIAnimClip, t1: Double, t2: Double, parent: AIMat4, depth: Int) {
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
                AIMat4.trsInto(px, py, pz, rotB.x, rotB.y, rotB.z, rotB.w, sx, sy, sz, local)
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
    private fun slotAt(depth: Int): AIMat4 {
        if (depth >= stack.size) stack = stack.copyOf(stack.size * 2)
        var s = stack[depth]; if (s == null) { s = AIMat4(); stack[depth] = s }
        return s
    }
    val currentStateName get() = currentState?.name
    val isTransitioning get() = nextState != null
    val transitionProgress get() = blendFactor
}
