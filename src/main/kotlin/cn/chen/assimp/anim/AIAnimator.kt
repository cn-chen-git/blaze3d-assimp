package cn.chen.assimp.anim
import cn.chen.assimp.core.AIAnimClip
import cn.chen.assimp.core.AISceneData
class AIAnimator(private val scene: AISceneData) {
    var currentAnimation: AIAnimClip? = null; private set
    var currentTime: Double = 0.0; private set
    var nextAnimation: AIAnimClip? = null; private set
    var nextTime: Double = 0.0; private set
    var transitionDuration: Float = 0f; private set
    var transitionElapsed: Float = 0f; private set
    var speed: Float = 1f
    var loop: Boolean = true
    var playing: Boolean = false; private set
    fun play(index: Int) { play(scene.getAnimation(index)) }
    fun play(name: String) { play(scene.getAnimation(name)) }
    fun play(clip: AIAnimClip?) {
        currentAnimation = clip; currentTime = 0.0; playing = true
        nextAnimation = null; nextTime = 0.0; transitionDuration = 0f; transitionElapsed = 0f
    }
    fun crossFade(index: Int, duration: Float) = crossFade(scene.getAnimation(index), duration)
    fun crossFade(name: String, duration: Float) = crossFade(scene.getAnimation(name), duration)
    fun crossFade(clip: AIAnimClip?, duration: Float) {
        if (clip == null) return
        if (currentAnimation == null) { play(clip); return }
        if (duration <= 0f) { play(clip); return }
        nextAnimation = clip; nextTime = 0.0
        transitionDuration = duration; transitionElapsed = 0f; playing = true
    }
    fun stop() { playing = false; currentTime = 0.0; nextAnimation = null; transitionDuration = 0f }
    fun pause() { playing = false }
    fun resume() { playing = true }
    fun update(deltaTime: Double) {
        if (!playing || currentAnimation == null || scene.skeleton == null) return
        val dt = deltaTime * speed
        currentTime += dt
        val anim = currentAnimation!!; val duration = anim.durationSeconds
        if (currentTime > duration) {
            if (loop) currentTime %= duration else { currentTime = duration; playing = false }
        }
        val nxt = nextAnimation
        if (nxt != null && transitionDuration > 0f) {
            nextTime += dt
            val nd = nxt.durationSeconds
            if (nextTime > nd) nextTime = if (loop) nextTime % nd else nd
            transitionElapsed += dt.toFloat()
            val f = (transitionElapsed / transitionDuration).coerceIn(0f, 1f)
            scene.skeleton!!.computeBoneTransformsBlend(anim, currentTime, nxt, nextTime, f, scene.rootNode)
            if (f >= 1f) {
                currentAnimation = nxt; currentTime = nextTime
                nextAnimation = null; nextTime = 0.0; transitionDuration = 0f; transitionElapsed = 0f
            }
        } else {
            scene.skeleton!!.computeBoneTransforms(anim, currentTime, scene.rootNode)
        }
    }
    val animationNames get() = scene.animations.map { it.name }
    val animationCount get() = scene.animations.size
    val isFinished get() = !playing && currentTime >= (currentAnimation?.durationSeconds ?: 0.0)
}
