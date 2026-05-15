package cn.chen.blaze3d.physics
import com.jme3.bullet.PhysicsSpace
import com.jme3.math.Vector3f
object BulletWorld {
    var space: PhysicsSpace? = null
        private set
    var enabled = false
        private set
    fun init() {
        if (enabled) return
        runCatching {
            BulletNative.load()
            if (!BulletNative.loaded) return
            space = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
            space?.setGravity(Vector3f(0f, -9.81f, 0f))
            enabled = true
        }.onFailure { enabled = false; space = null }
    }
    fun update(delta: Float) { if (enabled) space?.update(delta.coerceIn(0f, 0.1f), 4) }
    fun destroy() { space?.destroy(); space = null; enabled = false }
}
