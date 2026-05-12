package cn.chen.assimp.anim
import cn.chen.assimp.core.AIAnimClip
import cn.chen.assimp.core.AISceneData
class AIAnimator(private val scene: AISceneData) {
    var currentAnimation: AIAnimClip? = null; private set
    var currentTime: Double = 0.0; private set
    var speed: Float = 1f
    var loop: Boolean = true
    var playing: Boolean = false; private set
    fun play(index: Int) { currentAnimation = scene.getAnimation(index); currentTime = 0.0; playing = true }
    fun play(name: String) { currentAnimation = scene.getAnimation(name); currentTime = 0.0; playing = true }
    fun stop() { playing = false; currentTime = 0.0 }
    fun pause() { playing = false }
    fun resume() { playing = true }
    fun update(deltaTime: Double) {
        if (!playing || currentAnimation == null || scene.skeleton == null) return
        currentTime += deltaTime * speed
        val anim = currentAnimation!!
        val duration = anim.durationSeconds
        if (currentTime > duration) {
            if (loop) currentTime %= duration else { currentTime = duration; playing = false }
        }
        scene.skeleton!!.computeBoneTransforms(anim, currentTime, scene.rootNode)
    }
    val animationNames get() = scene.animations.map { it.name }
    val animationCount get() = scene.animations.size
    val isFinished get() = !playing && currentTime >= (currentAnimation?.durationSeconds ?: 0.0)
}
