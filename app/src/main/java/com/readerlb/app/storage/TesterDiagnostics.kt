package com.readerlb.app.storage

import android.content.Context
import android.os.Build
import com.readerlb.app.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small persistent trace so testers can report failures after restarting the app. */
object TesterDiagnostics {
    private const val KEY = "events"
    private const val MAX_CHARS = 48_000
    private const val MANAGER = "moe.shizuku.privileged.api"
    @Volatile private var crashHandlerInstalled = false

    @Synchronized fun installCrashHandler(context: Context) {
        if (!BuildConfig.TESTER_DIAGNOSTICS || crashHandlerInstalled) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            runCatching { record(app, "crash", "Uncaught on thread=${thread.name}", failure) }
            previous?.uncaughtException(thread, failure)
        }
        crashHandlerInstalled = true
    }

    @Synchronized fun record(context: Context, stage: String, detail: String, error: Throwable? = null) {
        if (!BuildConfig.TESTER_DIAGNOSTICS) return
        val prefs = context.applicationContext.getSharedPreferences("tester_diagnostics", Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val event = buildString {
            append(timestamp).append(" [").append(stage).append("] ").append(detail)
            error?.let { append("\n").append(it.stackTraceToString()) }
            append('\n')
        }
        prefs.edit().putString(KEY, (prefs.getString(KEY, "").orEmpty() + event).takeLast(MAX_CHARS)).apply()
    }

    fun report(context: Context, folderConnected: Boolean, autoUpdateChecks: Boolean): String = buildString {
        appendLine("ReaderLB TESTER diagnostics")
        appendLine("Generated: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date()))
        appendLine("Package: ${BuildConfig.APPLICATION_ID}")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Selected library access: ${if (folderConnected) "connected" else "not connected"}")
        appendLine("Shizuku state: ${ShizukuAccess.state}")
        appendLine("Shizuku service connected: ${ShizukuAccess.isServiceConnected}")
        appendLine("Automatic update checks: ${if (autoUpdateChecks) "enabled" else "disabled"}")
        for (name in listOf(MANAGER, "ru.libappc")) {
            val info = runCatching { context.packageManager.getPackageInfo(name, 0) }
            appendLine("Package $name: " + info.fold(
                onSuccess = { "installed, version=${it.versionName}, code=${it.longVersionCode}" },
                onFailure = { "unavailable: ${it.javaClass.simpleName}: ${it.message}" }
            ))
        }
        appendLine("\nShizuku root diagnostics:")
        appendLine(ShizukuAccess.rootDiagnostics())
        appendLine("\nEvent trace (latest 48 KB):")
        append(context.getSharedPreferences("tester_diagnostics", Context.MODE_PRIVATE).getString(KEY, "No events recorded").orEmpty())
    }
}
