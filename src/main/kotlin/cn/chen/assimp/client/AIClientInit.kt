package cn.chen.assimp.client
import cn.chen.assimp.debug.DeBug
import net.fabricmc.api.ClientModInitializer
object AIClientInit : ClientModInitializer {
    override fun onInitializeClient() {
        DeBug.init()
    }
}
