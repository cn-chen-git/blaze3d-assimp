package cn.chen.blaze3d.advanced
import cn.chen.blaze3d.core.BonePose
import cn.chen.blaze3d.math.Mat4
import cn.chen.blaze3d.render.ModelInstance
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import java.util.UUID
enum class AttachmentTarget { NONE, PLAYER, ENTITY, BLOCK }
class Attachment(var target: AttachmentTarget = AttachmentTarget.NONE, var entityId: Int = -1, var blockPos: BlockPos? = null, var socket: String = "", val offset: ModelInstance = ModelInstance()) {
    fun apply(instance: ModelInstance, sockets: SocketRegistry) {
        val mc = Minecraft.getInstance()
        val entity = when (target) {
            AttachmentTarget.PLAYER -> mc.player
            AttachmentTarget.ENTITY -> mc.level?.getEntity(entityId)
            else -> null
        }
        val op = offset.pos; val orr = offset.rot
        if (entity != null) {
            instance.pos.set(entity.x.toFloat() + op.x, entity.y.toFloat() + op.y, entity.z.toFloat() + op.z)
            instance.rot.set(orr.x, entity.yRot + orr.y, orr.z)
        }
        val bp = blockPos
        if (target == AttachmentTarget.BLOCK && bp != null) instance.pos.set(bp.x + 0.5f + op.x, bp.y.toFloat() + op.y, bp.z + 0.5f + op.z)
        sockets.socket(socket)?.let { val m = it.m; instance.rot.x += m[6]; instance.rot.y += m[2]; instance.rot.z += m[1] }
    }
}
class SocketRegistry {
    private val sockets = LinkedHashMap<String, Mat4>()
    fun update(pose: BonePose?) {
        sockets.clear()
        if (pose == null) return
        for ((name, bone) in pose.bones) sockets[name] = pose.boneMatrices[bone.index]
    }
    fun socket(name: String) = sockets[name]
    fun names(): List<String> = sockets.keys.toList()
}
class InstanceManager {
    private val instances = LinkedHashMap<UUID, ModelInstance>()
    fun create(): Pair<UUID, ModelInstance> {
        val id = UUID.randomUUID()
        val instance = ModelInstance()
        instances[id] = instance
        return id to instance
    }
    fun remove(id: UUID) { instances.remove(id) }
    fun clear() = instances.clear()
    fun all(): Collection<ModelInstance> = instances.values
    fun count() = instances.size
}
class AnimationSoundEvent(val animation: String, val time: Double, val sound: Identifier, val volume: Float = 1f, val pitch: Float = 1f) {
    var firedCycle = -1
}
class AnimationAudioBus {
    private val events = ArrayList<AnimationSoundEvent>()
    fun add(event: AnimationSoundEvent) { events.add(event) }
    fun clear() = events.clear()
    fun tick(animation: String?, time: Double, pos: ModelInstance) {
        if (animation == null) return
        val level = Minecraft.getInstance().level ?: return
        val cycle = time.toInt()
        for (event in events) {
            if (event.animation != animation || time < event.time || event.firedCycle == cycle) continue
            event.firedCycle = cycle
            level.playLocalSound(pos.pos.x.toDouble(), pos.pos.y.toDouble(), pos.pos.z.toDouble(), SoundEvent.createVariableRangeEvent(event.sound), SoundSource.NEUTRAL, event.volume, event.pitch, false)
        }
    }
    fun count() = events.size
}
class IkFkController {
    var enabled = false
    var targetEntity: Entity? = null
    var targetBlock: BlockPos? = null
    var weight = 1f
    fun solve(instance: ModelInstance) {
        if (!enabled) return
        val block = targetBlock
        val target = targetEntity?.position() ?: if (block != null) net.minecraft.world.phys.Vec3(block.x + 0.5, block.y + 0.5, block.z + 0.5) else return
        val dx = target.x.toFloat() - instance.pos.x; val dy = target.y.toFloat() - instance.pos.y; val dz = target.z.toFloat() - instance.pos.z
        val yaw = Math.toDegrees(kotlin.math.atan2(dz.toDouble(), dx.toDouble())).toFloat() - 90f
        val pitch = -Math.toDegrees(kotlin.math.atan2(dy.toDouble(), kotlin.math.sqrt((dx * dx + dz * dz).toDouble()))).toFloat()
        val w = weight.coerceIn(0f, 1f)
        instance.rot.y += (yaw - instance.rot.y) * w; instance.rot.x += (pitch - instance.rot.x) * w
    }
}
