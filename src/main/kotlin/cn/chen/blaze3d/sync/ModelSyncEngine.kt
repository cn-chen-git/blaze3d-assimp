package cn.chen.blaze3d.sync
import cn.chen.blaze3d.anim.AnimationRuntime
import cn.chen.blaze3d.render.ModelInstance
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
data class RemoteState(val playerId: UUID, val tick: Long, val instance: ModelInstance, val animationState: String, val parameters: Map<String, Float>, val parametersBool: Map<String, Boolean>) {
    fun encode(): RemoteStatePayload = RemoteStatePayload(playerId.toString(), tick, instance.pos.x, instance.pos.y, instance.pos.z, instance.rot.x, instance.rot.y, instance.rot.z, instance.scale, animationState, parameters.entries.joinToString(",") { "${it.key}=${it.value}" }, parametersBool.entries.joinToString(",") { "${it.key}=${it.value}" })
}
data class RemoteStatePayload(val playerId: String, val tick: Long, val px: Float, val py: Float, val pz: Float, val rx: Float, val ry: Float, val rz: Float, val scale: Float, val state: String, val parameters: String, val booleans: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RemoteStatePayload>(Identifier.fromNamespaceAndPath("blaze3d-model", "remote_state"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RemoteStatePayload> = StreamCodec.ofMember({ value, buf -> value.write(buf) }, { buf -> read(buf) })
        private fun read(buf: RegistryFriendlyByteBuf): RemoteStatePayload = RemoteStatePayload(buf.readUtf(64), buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(128), buf.readUtf(2048), buf.readUtf(1024))
    }
    private fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(playerId, 64); buf.writeLong(tick)
        buf.writeFloat(px); buf.writeFloat(py); buf.writeFloat(pz)
        buf.writeFloat(rx); buf.writeFloat(ry); buf.writeFloat(rz)
        buf.writeFloat(scale)
        buf.writeUtf(state, 128); buf.writeUtf(parameters, 2048); buf.writeUtf(booleans, 1024)
    }
}
class RemoteSample(var tick: Long, var time: Long, val payload: RemoteStatePayload)
class RemoteHistory(val maxSamples: Int = 16) {
    private val samples: ArrayDeque<RemoteSample> = ArrayDeque()
    fun push(sample: RemoteSample) {
        samples.addLast(sample)
        while (samples.size > maxSamples) samples.removeFirst()
    }
    fun interpolate(targetTime: Long): RemoteStatePayload? {
        if (samples.isEmpty()) return null
        if (samples.size == 1) return samples.first().payload
        var before: RemoteSample? = null; var after: RemoteSample? = null
        for (sample in samples) { if (sample.time <= targetTime) before = sample else { after = sample; break } }
        if (before == null) return samples.first().payload
        if (after == null) return samples.last().payload
        val span = (after.time - before.time).coerceAtLeast(1L)
        val alpha = ((targetTime - before.time).toFloat() / span).coerceIn(0f, 1f)
        return lerp(before.payload, after.payload, alpha)
    }
    private fun lerp(a: RemoteStatePayload, b: RemoteStatePayload, t: Float): RemoteStatePayload {
        return RemoteStatePayload(a.playerId, b.tick, a.px + (b.px - a.px) * t, a.py + (b.py - a.py) * t, a.pz + (b.pz - a.pz) * t, a.rx + (b.rx - a.rx) * t, a.ry + (b.ry - a.ry) * t, a.rz + (b.rz - a.rz) * t, a.scale + (b.scale - a.scale) * t, b.state, b.parameters, b.booleans)
    }
}
class RemotePlayerState(val playerId: UUID) {
    val instance: ModelInstance = ModelInstance()
    val history: RemoteHistory = RemoteHistory()
    val parameters: HashMap<String, Float> = HashMap()
    val booleans: HashMap<String, Boolean> = HashMap()
    var state: String = ""
    var lastUpdate: Long = 0L
    fun applyPayload(payload: RemoteStatePayload, currentTime: Long) {
        history.push(RemoteSample(payload.tick, currentTime, payload))
        instance.pos.set(payload.px, payload.py, payload.pz)
        instance.rot.set(payload.rx, payload.ry, payload.rz)
        instance.scale = payload.scale
        state = payload.state
        parameters.clear(); booleans.clear()
        if (payload.parameters.isNotBlank()) {
            payload.parameters.split(",").forEach {
                val (k, v) = it.split("=").takeIf { p -> p.size == 2 } ?: return@forEach
                parameters[k] = v.toFloatOrNull() ?: return@forEach
            }
        }
        if (payload.booleans.isNotBlank()) {
            payload.booleans.split(",").forEach {
                val (k, v) = it.split("=").takeIf { p -> p.size == 2 } ?: return@forEach
                booleans[k] = v.toBoolean()
            }
        }
        lastUpdate = currentTime
    }
    fun blendTo(runtime: AnimationRuntime) {
        for ((k, v) in parameters) runtime.setParameter(k, v)
        for ((k, v) in booleans) runtime.setBool(k, v)
    }
}
object ModelSyncEngine {
    val players: ConcurrentHashMap<UUID, RemotePlayerState> = ConcurrentHashMap()
    var enabled = true
    var lastDispatchTime: Long = 0
    var minDispatchIntervalMs: Long = 50
    private var lastEncoded: RemoteState? = null
    fun init() {
        PayloadTypeRegistry.clientboundPlay().register(RemoteStatePayload.TYPE, RemoteStatePayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(RemoteStatePayload.TYPE, RemoteStatePayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(RemoteStatePayload.TYPE) { payload, context ->
            context.client().execute {
                val uuid = runCatching { UUID.fromString(payload.playerId) }.getOrNull() ?: return@execute
                val state = players.getOrPut(uuid) { RemotePlayerState(uuid) }
                state.applyPayload(payload, System.currentTimeMillis())
            }
        }
    }
    fun dispatch(state: RemoteState) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastDispatchTime < minDispatchIntervalMs && !shouldForceDispatch(state)) return
        lastDispatchTime = now
        lastEncoded = state
        try { ClientPlayNetworking.send(state.encode()) } catch (_: Throwable) {}
    }
    fun tick() {
        val now = System.currentTimeMillis()
        val expiry = now - 5000
        players.entries.removeIf { it.value.lastUpdate < expiry }
    }
    fun remoteState(uuid: UUID): RemotePlayerState? = players[uuid]
    fun status(): String = "sync players=${players.size} dispatch=$lastDispatchTime interval=${minDispatchIntervalMs}"
    private fun shouldForceDispatch(state: RemoteState): Boolean {
        val previous = lastEncoded ?: return true
        if (previous.animationState != state.animationState) return true
        val dx = abs(previous.instance.pos.x - state.instance.pos.x)
        val dy = abs(previous.instance.pos.y - state.instance.pos.y)
        val dz = abs(previous.instance.pos.z - state.instance.pos.z)
        return dx > 0.05f || dy > 0.05f || dz > 0.05f
    }
}
