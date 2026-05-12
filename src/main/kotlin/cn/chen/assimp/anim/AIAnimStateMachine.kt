package cn.chen.assimp.anim
import cn.chen.assimp.core.AIAnimClip
import cn.chen.assimp.core.AINodeGraph
import cn.chen.assimp.core.AIBonePose
import cn.chen.assimp.math.AIMat4
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
    fun addState(state: AIAnimState) { states[state.name] = state }
    fun addTransition(from: String, to: String, duration: Float, condition: () -> Boolean) {
        transitions.add(AIAnimTransition(from, to, duration, condition))
    }
    fun setState(name: String) { currentState = states[name]; currentTime = 0.0; nextState = null; blendFactor = 0f }
    fun update(deltaTime: Double) {
        val cur = currentState ?: return
        if (nextState != null) {
            blendFactor += (deltaTime / transitionDuration).toFloat()
            if (blendFactor >= 1f) {
                currentState = nextState; currentTime = nextTime; nextState = null; blendFactor = 0f
            } else {
                currentTime += deltaTime * cur.speed
                nextTime += deltaTime * nextState!!.speed
                blendCompute()
                return
            }
        }
        for (t in transitions) {
            if (t.from == cur.name && t.condition()) {
                nextState = states[t.to]; transitionDuration = t.duration; nextTime = 0.0; blendFactor = 0f
                break
            }
        }
        currentTime += deltaTime * cur.speed
        val duration = cur.clip.durationSeconds
        if (currentTime > duration) currentTime = if (cur.loop) currentTime % duration else duration
        skeleton.computeBoneTransforms(cur.clip, currentTime, rootNode)
    }
    private fun blendCompute() {
        val curClip = currentState!!.clip
        val nxtClip = nextState!!.clip
        val ticks1 = if (curClip.ticksPerSecond > 0.0) curClip.ticksPerSecond else 25.0
        val ticks2 = if (nxtClip.ticksPerSecond > 0.0) nxtClip.ticksPerSecond else 25.0
        val animTime1 = (currentTime * ticks1) % curClip.duration
        val animTime2 = (nextTime * ticks2) % nxtClip.duration
        traverseBlend(rootNode, curClip, nxtClip, animTime1, animTime2, AIMat4.identity())
    }
    private fun traverseBlend(node: AINodeGraph, clip1: AIAnimClip, clip2: AIAnimClip, t1: Double, t2: Double, parent: AIMat4) {
        val ch1 = clip1.getChannel(node.name)
        val ch2 = clip2.getChannel(node.name)
        val tr1 = ch1?.computeTransform(t1) ?: node.transform
        val tr2 = ch2?.computeTransform(t2) ?: node.transform
        val blended = lerpMatrix(tr1, tr2, blendFactor)
        val global = parent * blended
        skeleton.bones[node.name]?.let { bone ->
            skeleton.boneMatrices[bone.index] = skeleton.globalInverseTransform * global * bone.offsetMatrix
        }
        for (child in node.children) traverseBlend(child, clip1, clip2, t1, t2, global)
    }
    private fun lerpMatrix(a: AIMat4, b: AIMat4, t: Float): AIMat4 {
        val r = FloatArray(16)
        for (i in 0..15) r[i] = a.m[i] * (1f - t) + b.m[i] * t
        return AIMat4(r)
    }
    val currentStateName get() = currentState?.name
    val isTransitioning get() = nextState != null
}
