package cn.chen.blaze3d.client
import net.fabricmc.api.ClientModInitializer
object ClientInit : ClientModInitializer {
    override fun onInitializeClient() {
        Runtime.init()
    }
}
