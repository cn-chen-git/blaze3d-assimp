package cn.chen.blaze3d.loader
import cn.chen.blaze3d.core.SceneData
import java.util.LinkedHashMap
object ModelCache {
    private const val LIMIT = 8
    private val scenes = object : LinkedHashMap<String, SceneData>(LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SceneData>) = size > LIMIT
    }
    var hits = 0; private set
    var misses = 0; private set
    @Synchronized fun get(key: String): SceneData? {
        val scene = scenes[key]
        if (scene == null) misses++ else hits++
        return scene
    }
    @Synchronized fun put(key: String, scene: SceneData): SceneData {
        scenes[key] = scene
        return scene
    }
    @Synchronized fun clear() = scenes.clear()
    val size get() = scenes.size
}
