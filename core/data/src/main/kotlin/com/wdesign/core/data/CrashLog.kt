package com.wdesign.core.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last error report in filesDir/crash/last.txt so users can share it from Settings.
 * - [install] chains a default uncaught-exception handler: it writes the report, then hands the
 *   crash to the previous handler, so real crashes are never swallowed (CI still sees them).
 * - [record] stores non-fatal errors caught by the safe click scope.
 */
object CrashLog {
    private const val TAG = "WDesign"
    @Volatile private var appContext: Context? = null

    fun file(context: Context): File = File(context.filesDir, "crash/last.txt")

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(error, fatal = true, thread = thread.name) }
            if (previous != null) previous.uncaughtException(thread, error) else throw error
        }
    }

    fun record(error: Throwable) {
        Log.e(TAG, "Handled error", error)
        runCatching { write(error, fatal = false, thread = Thread.currentThread().name) }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) { runCatching { file(context).delete() } }

    private fun write(error: Throwable, fatal: Boolean, thread: String) {
        val ctx = appContext ?: return
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("W Design $version · ${if (fatal) "crash" else "handled error"}")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date()))
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · thread $thread")
            appendLine()
            append(trace.take(64_000))
        }
        val f = file(ctx)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }
}
