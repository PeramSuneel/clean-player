package com.peramsuneel.cleanplayer

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

        val dialog = Dialog(activity)
        val dark = activity.getSharedPreferences("clean_player_preferences", Context.MODE_PRIVATE)
            .getBoolean("dark_theme", true)
        val primary = LiquidGlass.color(activity, R.color.player_primary)
        val secondary = LiquidGlass.color(activity, R.color.player_secondary)
        val surface = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 18))
            background = LiquidGlass.panel(activity, 26)
            elevation = LiquidGlass.elevation(activity) * 2
        }
        surface.addView(TextView(activity).apply {
            text = "Clean Player recovered"
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(primary)
        })
        surface.addView(TextView(activity).apply {
            text = "A crash report was saved from your previous session. You can copy it to share or review later."
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(activity, 9), 0, dp(activity, 13))
        })
        surface.addView(ScrollView(activity).apply {
            isFillViewport = false
            background = LiquidGlass.thumbnail(activity, 16)
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            addView(TextView(activity).apply {
                text = report.take(1800)
                textSize = 11f
                setTextColor(primary)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            })
        }, LinearLayout.LayoutParams(-1, dp(activity, 190)))

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(activity, 16), 0, 0)
        }
        actions.addView(Button(activity).apply {
            text = "Later"
            isAllCaps = false
            setTextColor(primary)
            background = LiquidGlass.button(activity, false)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(activity, 46), 1f).apply { marginEnd = dp(activity, 8) })
        actions.addView(Button(activity).apply {
            text = "Copy report"
            isAllCaps = false
            setTextColor(primary)
            background = LiquidGlass.button(activity, true)
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Clean Player crash report", report))
                reportFile.delete()
                dialog.dismiss()
                Toast.makeText(activity, "Crash report copied", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        surface.addView(actions)

        dialog.setContentView(surface)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (dark) 0.68f else 0.42f)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
