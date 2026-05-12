package cn.chen.assimp.render
import net.minecraft.resources.Identifier
import org.joml.Vector3f
class AIGpuBatch(
    val baseVertex: Int,
    val quadCount: Int,
    val texId: Identifier,
    val normalMapId: Identifier?,
    val mrMapId: Identifier?,
    val emissiveMapId: Identifier?,
    val pass: AIRenderPass,
    val doubleSided: Boolean,
    val matIdx: Int,
    aabbMin: Vector3f,
    aabbMax: Vector3f
) {
    val aabbCenter = Vector3f(aabbMin).add(aabbMax).mul(0.5f)
    val aabbRadius = Vector3f(aabbMax).sub(aabbMin).length() * 0.5f
}
