package cn.chen.assimp.debug
import cn.chen.assimp.render.AIWorldRenderer
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.joml.Vector3f
object DeBug {
    private var modelPath = ""
    private val renderer = AIWorldRenderer()
    fun init() {
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(LevelRenderEvents.AfterSolidFeatures { ctx -> renderer.render(ctx) })
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(ClientCommands.literal("ai")
                .then(ClientCommands.literal("load").then(ClientCommands.argument("path", StringArgumentType.greedyString()).executes { c ->
                    val path = StringArgumentType.getString(c, "path")
                    try { renderer.load(path); modelPath = path; msg(c.source, "§a加载: $path") }
                    catch (e: Exception) { msg(c.source, "§c加载失败: ${e.message}"); e.printStackTrace() }; 1
                }))
                .then(ClientCommands.literal("pos").then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                    .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                        .then(ClientCommands.argument("z", FloatArgumentType.floatArg()).executes { c ->
                            renderer.instance.pos = Vector3f(FloatArgumentType.getFloat(c, "x"), FloatArgumentType.getFloat(c, "y"), FloatArgumentType.getFloat(c, "z"))
                            msg(c.source, "§a位置: ${renderer.instance.pos}"); 1
                        }))))
                .then(ClientCommands.literal("scale").then(ClientCommands.argument("s", FloatArgumentType.floatArg()).executes { c ->
                    renderer.instance.scale = FloatArgumentType.getFloat(c, "s"); msg(c.source, "§a缩放: ${renderer.instance.scale}"); 1
                }))
                .then(ClientCommands.literal("rot").then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                    .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                        .then(ClientCommands.argument("z", FloatArgumentType.floatArg()).executes { c ->
                            renderer.instance.rot = Vector3f(FloatArgumentType.getFloat(c, "x"), FloatArgumentType.getFloat(c, "y"), FloatArgumentType.getFloat(c, "z"))
                            msg(c.source, "§a旋转: ${renderer.instance.rot}"); 1
                        }))))
                .then(ClientCommands.literal("anim").then(ClientCommands.argument("i", IntegerArgumentType.integer(0)).executes { c ->
                    renderer.animator?.play(IntegerArgumentType.getInteger(c, "i")); msg(c.source, "§a播放动画"); 1
                }))
                .then(ClientCommands.literal("stop").executes { c -> renderer.animator?.stop(); msg(c.source, "§a停止"); 1 })
                .then(ClientCommands.literal("here").executes { c ->
                    Minecraft.getInstance().player?.let { p -> renderer.instance.pos = Vector3f(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()) }
                    msg(c.source, "§a位置: ${renderer.instance.pos}"); 1
                })
                .then(ClientCommands.literal("info").executes { c ->
                    msg(c.source, "§e路径=$modelPath")
                    renderer.info().forEach { msg(c.source, "§7$it") }; 1
                })
                .then(ClientCommands.literal("unload").executes { c -> renderer.unload(); modelPath = ""; msg(c.source, "§c已卸载"); 1 })
            )
        }
    }
    private fun msg(src: net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, text: String) {
        src.sendFeedback(Component.literal("[AI] $text"))
    }
}
