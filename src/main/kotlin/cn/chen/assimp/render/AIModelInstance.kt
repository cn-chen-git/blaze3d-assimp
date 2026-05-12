package cn.chen.assimp.render
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Vector3f
class AIModelInstance(
    var pos: Vector3f = Vector3f(),
    var scale: Float = 1f,
    var rot: Vector3f = Vector3f(),
    var visible: Boolean = true
) {
    fun buildModelMatrix(camX: Double, camY: Double, camZ: Double): Matrix4f {
        val mat = Matrix4f(RenderSystem.getModelViewStack())
            .translate((pos.x - camX).toFloat(), (pos.y - camY).toFloat(), (pos.z - camZ).toFloat())
            .scale(scale)
        if (rot.x != 0f) mat.rotateX(Math.toRadians(rot.x.toDouble()).toFloat())
        if (rot.y != 0f) mat.rotateY(Math.toRadians(rot.y.toDouble()).toFloat())
        if (rot.z != 0f) mat.rotateZ(Math.toRadians(rot.z.toDouble()).toFloat())
        return mat
    }
    fun buildObjectMatrix(): Matrix4f {
        val mat = Matrix4f().translate(pos).scale(scale)
        if (rot.x != 0f) mat.rotateX(Math.toRadians(rot.x.toDouble()).toFloat())
        if (rot.y != 0f) mat.rotateY(Math.toRadians(rot.y.toDouble()).toFloat())
        if (rot.z != 0f) mat.rotateZ(Math.toRadians(rot.z.toDouble()).toFloat())
        return mat
    }
    fun distanceSq(cx: Double, cy: Double, cz: Double): Double {
        val dx = pos.x - cx; val dy = pos.y - cy; val dz = pos.z - cz
        return dx * dx + dy * dy + dz * dz
    }
}
