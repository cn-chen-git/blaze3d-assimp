package cn.chen.blaze3d.anim
import cn.chen.blaze3d.core.AnimClip
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.core.NodeGraph
import cn.chen.blaze3d.core.SceneData
import java.util.ArrayDeque
typealias Trigger = (ParameterSet) -> Boolean
typealias ClipResolver = (String) -> AnimClip?
@DslMarker annotation class StateMachineMarker
class ParameterSet {
    private val numerics = HashMap<String, Float>()
    private val booleans = HashMap<String, Boolean>()
    private val strings = HashMap<String, String>()
    fun setFloat(name: String, value: Float) { numerics[name] = value }
    fun setBool(name: String, value: Boolean) { booleans[name] = value }
    fun setString(name: String, value: String) { strings[name] = value }
    fun float(name: String, fallback: Float = 0f): Float = numerics[name] ?: fallback
    fun bool(name: String, fallback: Boolean = false): Boolean = booleans[name] ?: fallback
    fun string(name: String, fallback: String = ""): String = strings[name] ?: fallback
    operator fun get(name: String): Float = float(name)
    fun snapshot(): String = (numerics.entries.joinToString(",") { "${it.key}=${it.value}" } + "|" + booleans.entries.joinToString(",") { "${it.key}=${it.value}" })
}
@StateMachineMarker class AnimationStateBuilder(val name: String) {
    var clipName: String? = null
    var loop: Boolean = true
    var speed: Float = 1f
    var enterCallback: ((ParameterSet) -> Unit)? = null
    var exitCallback: ((ParameterSet) -> Unit)? = null
    var maskBones: List<String> = emptyList()
    fun onEnter(block: (ParameterSet) -> Unit) { enterCallback = block }
    fun onExit(block: (ParameterSet) -> Unit) { exitCallback = block }
    fun build(): AnimationState = AnimationState(name, clipName, loop, speed, enterCallback, exitCallback, maskBones)
}
class AnimationState(val name: String, val clipName: String?, val loop: Boolean, val speed: Float, val enterCallback: ((ParameterSet) -> Unit)?, val exitCallback: ((ParameterSet) -> Unit)?, val maskBones: List<String>) {
    override fun toString() = "state[$name clip=$clipName loop=$loop speed=$speed]"
}
@StateMachineMarker class AnimationTransitionBuilder(val from: String, val to: String) {
    var duration: Float = 0.2f
    var triggerLambda: Trigger = { true }
    var minTimeInState: Float = 0f
    var atomic: Boolean = false
    var priority: Int = 0
    fun trigger(predicate: Trigger) { triggerLambda = predicate }
    fun build(): AnimationTransition = AnimationTransition(from, to, duration, triggerLambda, minTimeInState, atomic, priority)
}
class AnimationTransition(val from: String, val to: String, val duration: Float, val trigger: Trigger, val minTimeInState: Float, val atomic: Boolean, val priority: Int) {
    override fun toString() = "transition[$from -> $to duration=$duration prio=$priority]"
}
@StateMachineMarker class AnimationStateMachineBuilder {
    private val states = LinkedHashMap<String, AnimationState>()
    private val transitions = ArrayList<AnimationTransition>()
    var initial: String? = null
    fun state(name: String, block: AnimationStateBuilder.() -> Unit = {}) {
        val builder = AnimationStateBuilder(name)
        builder.block()
        states[name] = builder.build()
        if (initial == null) initial = name
    }
    fun transition(pair: Pair<String, String>, block: AnimationTransitionBuilder.() -> Unit) {
        val builder = AnimationTransitionBuilder(pair.first, pair.second)
        builder.block()
        transitions.add(builder.build())
    }
    fun build(): AnimationStateMachine {
        val start = initial ?: error("state machine has no states")
        return AnimationStateMachine(states, transitions.sortedByDescending { it.priority }, start)
    }
}
class AnimationStateMachine(val states: Map<String, AnimationState>, val transitions: List<AnimationTransition>, val initial: String) {
    fun runtime(scene: SceneData, resolver: ClipResolver): AnimationRuntime = AnimationRuntime(this, scene, resolver)
    fun describe(): String = "fsm states=${states.size} transitions=${transitions.size} initial=$initial"
}
class AnimationRuntime(val machine: AnimationStateMachine, val scene: SceneData, val resolver: ClipResolver) {
    val parameters = ParameterSet()
    var currentState: AnimationState; private set
    var nextState: AnimationState? = null; private set
    var blendFactor: Float = 0f; private set
    var blendDuration: Float = 0f; private set
    private var stateTime: Double = 0.0
    private var nextStateTime: Double = 0.0
    private var transitionTimer: Float = 0f
    private val rootNode: NodeGraph = scene.rootNode
    init { currentState = machine.states[machine.initial] ?: error("missing initial state"); currentState.enterCallback?.invoke(parameters) }
    fun setParameter(name: String, value: Float) = parameters.setFloat(name, value)
    fun setBool(name: String, value: Boolean) = parameters.setBool(name, value)
    fun setString(name: String, value: String) = parameters.setString(name, value)
    fun tick(deltaSeconds: Double, pose: BonePose?) {
        stateTime += deltaSeconds * currentState.speed
        val next = nextState
        if (next != null) {
            nextStateTime += deltaSeconds * next.speed
            transitionTimer += deltaSeconds.toFloat()
            blendFactor = (transitionTimer / blendDuration).coerceIn(0f, 1f)
            if (blendFactor >= 1f) finalizeTransition()
        } else evaluateTransitions()
        applyToPose(pose)
    }
    private fun evaluateTransitions() {
        if (stateTime < currentTransitionMinTime()) return
        for (t in machine.transitions) {
            if (t.from != currentState.name) continue
            if (machine.states[t.to] == null) continue
            if (t.trigger(parameters)) { beginTransition(t); return }
        }
    }
    private fun beginTransition(transition: AnimationTransition) {
        val next = machine.states[transition.to] ?: return
        nextState = next
        blendFactor = 0f; transitionTimer = 0f; blendDuration = transition.duration.coerceAtLeast(1e-3f); nextStateTime = 0.0
        next.enterCallback?.invoke(parameters)
    }
    private fun finalizeTransition() {
        val next = nextState ?: return
        currentState.exitCallback?.invoke(parameters)
        currentState = next
        nextState = null; blendFactor = 0f; stateTime = nextStateTime; nextStateTime = 0.0
    }
    private fun applyToPose(pose: BonePose?) {
        pose ?: return
        val current = currentState.clipName?.let { resolver(it) } ?: scene.animations.firstOrNull() ?: return
        val next = nextState?.clipName?.let { resolver(it) }
        if (next == null) pose.computeBoneTransforms(current, looped(current, stateTime), rootNode)
        else pose.computeBoneTransformsBlend(current, looped(current, stateTime), next, looped(next, nextStateTime), blendFactor, rootNode)
    }
    private fun looped(clip: AnimClip, time: Double): Double {
        val durationSec = clip.durationSeconds
        if (durationSec <= 0.0) return time
        return time % durationSec
    }
    private fun currentTransitionMinTime(): Double = machine.transitions.firstOrNull { it.from == currentState.name }?.minTimeInState?.toDouble() ?: 0.0
    fun status(): String = "fsm current=${currentState.name} next=${nextState?.name} blend=$blendFactor"
}
fun animationStateMachine(builder: AnimationStateMachineBuilder.() -> Unit): AnimationStateMachine = AnimationStateMachineBuilder().apply(builder).build()
