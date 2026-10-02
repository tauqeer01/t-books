package com.bookflow.app.core.util

import android.content.Context
import android.os.Build
import com.bookflow.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash log. Nothing leaves the device unless the user shares a report from Settings.
 *
 * The previous default handler still runs afterwards, so Android (and Play Console's Android vitals)
 * receive the crash as usual.
 */
object CrashLog {
    private const val DIR = "crash_logs"
    private const val MAX_LOGS = 5

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Newest first. */
    fun logs(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles { f -> f.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** Plain-text report of the newest crash plus device details, for the user to share. */
    fun latestReport(context: Context): String? = logs(context).firstOrNull()?.readText()

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val time = Date()
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("BookFlow crash report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(time)}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }
        File(dir, "crash_${time.time}.txt").writeText(report)
        logs(context).drop(MAX_LOGS).forEach { it.delete() }
    }
}
