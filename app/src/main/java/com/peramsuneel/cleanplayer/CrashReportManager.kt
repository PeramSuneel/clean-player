package com.peramsuneel.cleanplayer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File

/**
 * Stores the last uncaught exception locally and offers it for copying on the next launch.
 * This does not intercept or suppress Android's normal crash handling.
 */
object CrashReportManager {
    private const val REPORT_FILE = "clean-player-last-crash.txt"
    @Volatile private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildString {
                    appendLine("Clean Player crash report")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${error.javaClass.name}")
                    appendLine("Message: ${error.message.orEmpty()}")
                    appendLine()
                    append(error.stackTraceToString())
                }
                File(appContext.filesDir, REPORT_FILE).writeText(report)
            } catch (_: Exception) {
                // Never replace the original crash with a report-writing failure.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun showIfPresent(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val reportFile = File(activity.applicationContext.filesDir, REPORT_FILE)
        if (!reportFile.isFile) return
        val report = try {
            reportFile.readText()
        } catch (_: Exception) {
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Clean Player recovered")
            .setMessage("The app saved a crash report from its previous session.\n\n${report.take(1800)}")
            .setPositiveButton("Copy report") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Clean Player crash report", report))
                reportFile.delete()
                Toast.makeText(activity, "Crash report copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
