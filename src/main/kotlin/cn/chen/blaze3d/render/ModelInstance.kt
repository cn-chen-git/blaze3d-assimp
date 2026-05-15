package cn.chen.blaze3d.render
import cn.chen.blaze3d.core.Transform
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Vector3f
class ModelInstance(
    override var pos: Vector3f = Vector3f(),
    override var scale: Float = 1f,
    override var rot: Vector3f = Vector3f(),
    var visible: Boolean = true,
    var tintR: Float = 1f,
    var tintG: Float = 1f,
    var tintB: Float = 1f,
    var tintA: Float = 1f,
    var followPlayer: Boolean = false,
    var previewPinned: Boolean = false
) : Transform {
    fun reset() {
        pos.set(0f, 0f, 0f); rot.set(0f, 0f, 0f); scale = 1f
        tintR = 1f; tintG = 1f; tintB = 1f; tintA = 1f
        visible = true; followPlayer = false; previewPinned = false
    }
    private fun applyRotation(mat: Matrix4f): Matrix4f {
        if (rot.x != 0f) mat.rotateX(Math.toRadians(rot.x.toDouble()).toFloat())
        if (rot.y != 0f) mat.rotateY(Math.toRadians(rot.y.toDouble()).toFloat())
        if (rot.z != 0f) mat.rotateZ(Math.toRadians(rot.z.toDouble()).toFloat())
        return mat
    }
    fun buildModelMatrix(camX: Double, camY: Double, camZ: Double): Matrix4f =
        applyRotation(Matrix4f(RenderSystem.getModelViewStack()).translate((pos.x - camX).toFloat(), (pos.y - camY).toFloat(), (pos.z - camZ).toFloat()).scale(scale))
    fun buildObjectMatrix(): Matrix4f = applyRotation(Matrix4f().translate(pos).scale(scale))
    fun distanceSq(cx: Double, cy: Double, cz: Double): Double {
        val dx = pos.x - cx; val dy = pos.y - cy; val dz = pos.z - cz
        return dx * dx + dy * dy + dz * dz
    }
}
