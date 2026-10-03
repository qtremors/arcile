package dev.qtremors.arcile.core.runtime

import java.io.File

/** A PID plus its kernel start time prevents a reused PID from owning old work. */
object ProcessOwnership {
    val token: String? by lazy {
        runCatching {
            val pid = File("/proc/self").canonicalFile.name
            val boot = runCatching { bootId() }.getOrNull()
            "$pid-${startTime(File("/proc/$pid/stat"))}" + (boot?.let { "-$it" } ?: "")
        }.getOrNull()
    }

    fun isAlive(owner: String): Boolean {
        val parts = owner.split('-')
        if (parts.size !in 2..3 || parts.take(2).any { it.toLongOrNull() == null }) return true
        if (parts.size == 3) {
            val sameBoot = runCatching { parts[2] == bootId() }.getOrNull() ?: return true
            if (!sameBoot) return false
        }
        val stat = File("/proc/${parts[0]}/stat")
        if (!stat.exists()) {
            // File.exists also returns false for access denial. Check the PID
            // before treating a protected process as abandoned.
            return try {
                android.system.Os.kill(parts[0].toInt(), 0)
                true
            } catch (error: android.system.ErrnoException) {
                error.errno != android.system.OsConstants.ESRCH
            } catch (_: Exception) {
                true
            }
        }
        // An unreadable live process is not permission to delete its work.
        return runCatching { startTime(stat) == parts[1] }.getOrDefault(true)
    }

    private fun startTime(stat: File): String = stat.readText()
        .substringAfterLast(") ").split(' ')[19].also { require(it.toLongOrNull() != null) }

    private fun bootId(): String = File("/proc/sys/kernel/random/boot_id").readText().trim().replace("-", "")
}
