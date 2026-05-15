package cn.chen.blaze3d.client
import cn.chen.blaze3d.async.GpuUploadQueue
import cn.chen.blaze3d.async.RenderWorker
import cn.chen.blaze3d.api.ModelFormatRegistry
import cn.chen.blaze3d.dae.DaeSupport
import cn.chen.blaze3d.fbx.FbxSupport
import cn.chen.blaze3d.gltf.GltfSupport
import cn.chen.blaze3d.loader.UniversalModelLoader
import cn.chen.blaze3d.obj.ObjSupport
import cn.chen.blaze3d.physics.BulletWorld
import cn.chen.blaze3d.render.WorldRenderer
import cn.chen.blaze3d.sync.ModelSync
import cn.chen.blaze3d.ysm.YsmSupport
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
object Runtime {
    val renderer = WorldRenderer()
    fun init() {
        ModelFormatRegistry.register(GltfSupport, FbxSupport, ObjSupport, DaeSupport, YsmSupport, UniversalModelLoader)
        ModelSync.init { renderer.applyRemote(it) }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            GpuUploadQueue.drain()
            BulletWorld.update(0.05f)
        })
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(LevelRenderEvents.AfterSolidFeatures { ctx -> renderer.render(ctx) })
    }
    fun destroy() {
        renderer.destroy()
        BulletWorld.destroy()
        RenderWorker.shutdown()
    }
}
