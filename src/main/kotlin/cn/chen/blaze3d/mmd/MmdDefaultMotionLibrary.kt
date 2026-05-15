package cn.chen.blaze3d.mmd
import net.minecraft.client.Minecraft
import java.io.File
import java.lang.Thread
object MmdDefaultMotionLibrary {
    private const val BASE = "assets/blaze3d-model/mmd/default_anim"
    val names = listOf("idle", "walk", "sprint", "sneak", "swingRight", "swingLeft", "elytraFly", "swim", "onClimbable", "onClimbableUp", "onClimbableDown", "sleep", "ride", "die", "crawl", "lieDown", "onHorse", "itemActive_minecraft.bow_Left_using", "itemActive_minecraft.iron_sword_Right_swinging", "itemActive_minecraft.shield_Left_using", "itemActive_minecraft.shield_Right_using")
    fun load(): Map<String, MmdMotion> {
        val out = HashMap<String, MmdMotion>()
        for (name in names) {
            val file = extract(name) ?: continue
            val motion = MmdVmdParser.parse(file)
            out[name] = motion
            out[motion.name] = motion
        }
        return out
    }
    private fun extract(name: String): File? {
        val mc = Minecraft.getInstance()
        val out = File(mc.gameDirectory, "config/blaze3d-model/default_anim/$name.vmd")
        if (out.isFile) return out
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("$BASE/$name.vmd") ?: return null
        out.parentFile.mkdirs()
        stream.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }
}
