package cn.chen.blaze3d.render
import net.minecraft.resources.Identifier
import org.joml.Vector3f
class GpuBatch(
    val baseVertex: Int,
    val quadCount: Int,
    val texId: Identifier,
    val normalMapId: Identifier?,
    val pass: RenderPass,
    aabbMin: Vector3f,
    aabbMax: Vector3f
) {
    val aabbCenter = Vector3f(aabbMin).add(aabbMax).mul(0.5f)
    val aabbRadius = Vector3f(aabbMax).sub(aabbMin).length() * 0.5f
}
