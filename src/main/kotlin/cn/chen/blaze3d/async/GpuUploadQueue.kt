package cn.chen.blaze3d.async
import java.util.concurrent.ConcurrentLinkedQueue
object GpuUploadQueue {
    private val jobs = ConcurrentLinkedQueue<() -> Unit>()
    fun enqueue(job: () -> Unit) { jobs.add(job) }
    fun drain(maxJobs: Int = 4) {
        var count = 0
        while (count < maxJobs) {
            val job = jobs.poll() ?: return
            job()
            count++
        }
    }
}
