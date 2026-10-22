package app.tuneroam.radio.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reports kept on the device, with no third-party service: an uncaught exception
 * is written to `files/crashes/` before the process dies, and the settings screen can
 * share the latest one. Only the newest [KEEP] reports are kept.
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val KEEP = 5

    /** Share sheets and chat apps choke on huge text; a stack trace fits well within this. */
    private const val SHARE_LIMIT = 60_000

    data class Report(val writtenAtMs: Long, val text: String)

    fun install(context: Context) {
        val dir = File(context.applicationContext.filesDir, DIR)
        val details = deviceDetails(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Writing must never stop the system's own handler from running.
            runCatching { write(dir, System.currentTimeMillis(), report(details, thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun latest(context: Context): Report? {
        val file = files(File(context.filesDir, DIR)).firstOrNull() ?: return null
        return runCatching { Report(file.lastModified(), file.readText().take(SHARE_LIMIT)) }.getOrNull()
    }

    fun clear(context: Context) {
        files(File(context.filesDir, DIR)).forEach(File::delete)
    }

    internal fun write(dir: File, nowMs: Long, text: String) {
        dir.mkdirs()
        File(dir, "crash-$nowMs.txt").writeText(text)
        files(dir).drop(KEEP).forEach(File::delete)
    }

    internal fun report(details: String, thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("Time: $stamp")
            appendLine(details)
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
    }

    private fun deviceDetails(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return "App: ${context.packageName} $version\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /** Newest first. */
    private fun files(dir: File): List<File> =
        dir.listFiles { file -> file.name.startsWith("crash-") }
            ?.sortedByDescending { it.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: 0L }
            .orEmpty()
}
