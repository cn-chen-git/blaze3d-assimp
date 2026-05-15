package cn.chen.blaze3d.async
import cn.chen.blaze3d.api.ModelFormatRegistry
import cn.chen.blaze3d.api.ModelLoadResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
object RenderWorker {
    private val counter = AtomicInteger()
    private val pending = ConcurrentHashMap<String, CompletableFuture<ModelLoadResult>>()
    private val workers: ExecutorService = Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() - 1), ThreadFactory { task ->
        Thread(task, "blaze3d-worker-${counter.incrementAndGet()}").apply { isDaemon = true }
    })
    fun load(path: String): CompletableFuture<ModelLoadResult> {
        pending[path]?.takeIf { !it.isDone && !it.isCancelled }?.let { return it }
        val future = CompletableFuture.supplyAsync({ ModelFormatRegistry.load(path) }, workers)
        pending[path] = future
        future.whenComplete { _, _ -> pending.remove(path, future) }
        return future
    }
    fun shutdown() { pending.clear(); workers.shutdownNow() }
}
