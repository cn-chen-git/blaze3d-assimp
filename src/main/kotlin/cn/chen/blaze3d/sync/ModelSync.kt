package cn.chen.blaze3d.sync
import cn.chen.blaze3d.render.ModelInstance
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
data class ModelStatePayload(val path: String, val x: Float, val y: Float, val z: Float, val rx: Float, val ry: Float, val rz: Float, val scale: Float, val r: Float, val g: Float, val b: Float, val a: Float, val animation: String, val expression: String = "", val expressionWeight: Float = 0f, val physics: Boolean = true, val preset: String = "") : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<ModelStatePayload>(Identifier.fromNamespaceAndPath("blaze3d-model", "model_state"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ModelStatePayload> = StreamCodec.ofMember({ value, buf -> value.write(buf) }, { buf -> read(buf) })
        private fun read(buf: RegistryFriendlyByteBuf) = ModelStatePayload(buf.readUtf(4096), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(256), buf.readUtf(256), buf.readFloat(), buf.readBoolean(), buf.readUtf(512))
    }
    private fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(path, 4096)
        buf.writeFloat(x); buf.writeFloat(y); buf.writeFloat(z)
        buf.writeFloat(rx); buf.writeFloat(ry); buf.writeFloat(rz)
        buf.writeFloat(scale)
        buf.writeFloat(r); buf.writeFloat(g); buf.writeFloat(b); buf.writeFloat(a)
        buf.writeUtf(animation, 256)
        buf.writeUtf(expression, 256)
        buf.writeFloat(expressionWeight)
        buf.writeBoolean(physics)
        buf.writeUtf(preset, 512)
    }
}
object ModelSync {
    var enabled = true
    var received = 0; private set
    fun init(apply: (ModelStatePayload) -> Unit) {
        PayloadTypeRegistry.clientboundPlay().register(ModelStatePayload.TYPE, ModelStatePayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(ModelStatePayload.TYPE) { payload, context ->
            context.client().execute { received++; apply(payload) }
        }
    }
    fun capture(path: String, instance: ModelInstance, animation: String?) = ModelStatePayload(path, instance.pos.x, instance.pos.y, instance.pos.z, instance.rot.x, instance.rot.y, instance.rot.z, instance.scale, instance.tintR, instance.tintG, instance.tintB, instance.tintA, animation ?: "")
    fun apply(payload: ModelStatePayload, instance: ModelInstance) {
        instance.pos.set(payload.x, payload.y, payload.z)
        instance.rot.set(payload.rx, payload.ry, payload.rz)
        instance.scale = payload.scale
        instance.tintR = payload.r; instance.tintG = payload.g; instance.tintB = payload.b; instance.tintA = payload.a
    }
}
