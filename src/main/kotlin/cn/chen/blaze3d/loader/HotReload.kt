package cn.chen.blaze3d.loader
import cn.chen.blaze3d.core.SceneData
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import cn.chen.blaze3d.api.ModelFormatRegistry
data class HotReloadEvent(val path: String, val timestamp: Long, val cause: String, val nextScene: SceneData?)
fun interface HotReloadCallback { fun onReload(event: HotReloadEvent) }
class HotReloadEntry(val path: String, val absolute: File, val callbacks: CopyOnWriteArrayList<HotReloadCallback> = CopyOnWriteArrayList(), var lastTimestamp: Long = absolute.lastModified())
object HotReload {
    private val entries: ConcurrentHashMap<String, HotReloadEntry> = ConcurrentHashMap()
    private val watchedDirs: ConcurrentHashMap<Path, WatchKey> = ConcurrentHashMap()
    private val running: AtomicBoolean = AtomicBoolean(false)
    private val service: WatchService = java.nio.file.FileSystems.getDefault().newWatchService()
    private var thread: Thread? = null
    var lastEventTime: Long = 0L; private set
    val reloadCount: AtomicLong = AtomicLong()
    val errorCount: AtomicLong = AtomicLong()
    fun watch(path: String, callback: HotReloadCallback): HotReloadEntry {
        val absolute = File(path).absoluteFile
        val entry = entries.computeIfAbsent(path) { HotReloadEntry(path, absolute) }
        entry.callbacks.add(callback)
        registerDirectory(absolute.parentFile ?: File("."))
        ensureRunning()
        return entry
    }
    fun unwatch(path: String, callback: HotReloadCallback) {
        val entry = entries[path] ?: return
        entry.callbacks.remove(callback)
        if (entry.callbacks.isEmpty()) entries.remove(path)
    }
    fun pause() { running.set(false); thread?.interrupt() }
    fun resume() { ensureRunning() }
    fun status() = "hot reload active=${running.get()} watched=${entries.size} reloads=${reloadCount.get()} errors=${errorCount.get()}"
    private fun registerDirectory(directory: File) {
        try {
            if (!directory.exists()) return
            val key = directory.toPath().register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE)
            watchedDirs[directory.toPath()] = key
        } catch (t: Throwable) { errorCount.incrementAndGet() }
    }
    private fun ensureRunning() {
        if (running.compareAndSet(false, true)) {
            thread = Thread({ pollLoop() }, "ai-hotreload").apply { isDaemon = true; start() }
        }
    }
    private fun pollLoop() {
        while (running.get()) {
            try {
                val key = service.take()
                val parent = watchedDirs.entries.firstOrNull { it.value == key }?.key ?: continue
                for (event in key.pollEvents()) {
                    val name = (event.context() as? Path)?.toString() ?: continue
                    val absolute = parent.resolve(name).toFile().absoluteFile
                    val entry = entries.values.firstOrNull { it.absolute == absolute } ?: continue
                    handleChange(entry, event.kind().name())
                }
                key.reset()
            } catch (interrupt: InterruptedException) {
                running.set(false); break
            } catch (t: Throwable) {
                errorCount.incrementAndGet(); Thread.sleep(50)
            }
        }
    }
    private fun handleChange(entry: HotReloadEntry, cause: String) {
        val timestamp = entry.absolute.lastModified()
        if (timestamp == entry.lastTimestamp) return
        entry.lastTimestamp = timestamp
        val event = try {
            val rebuilt = ModelFormatRegistry.detect(entry.path)?.load(cn.chen.blaze3d.api.ModelLoadContext(entry.path))
            HotReloadEvent(entry.path, timestamp, cause, rebuilt)
        } catch (t: Throwable) {
            errorCount.incrementAndGet(); HotReloadEvent(entry.path, timestamp, cause, null)
        }
        ModelCache.clear()
        for (callback in entry.callbacks) {
            try { callback.onReload(event); reloadCount.incrementAndGet() } catch (t: Throwable) { errorCount.incrementAndGet() }
        }
        lastEventTime = timestamp
    }
}
