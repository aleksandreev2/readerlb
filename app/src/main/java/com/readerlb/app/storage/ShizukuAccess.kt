package com.readerlb.app.storage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuAccessState {
    CHECKING, NOT_INSTALLED, STOPPED, PERMISSION_REQUIRED, CONNECTING, READY, FOLDER_MISSING
}

/** Owns the Shizuku binder and publishes a verified capability to the UI. */
object ShizukuAccess {
    private const val PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST = 2401
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initialized = false
    private var binding = false
    private var appContext: Context? = null
    @Volatile private var files: IReaderLbFiles? = null
    var state by mutableStateOf(ShizukuAccessState.CHECKING)
        private set

    val treeUri: Uri
        get() = DocumentsContract.buildTreeDocumentUri(
            "com.readerlb.app.ranobelib", "book"
        )

    fun service(): IReaderLbFiles = files ?: error("RanobeLib access is disconnected")

    fun refresh(context: Context) {
        appContext = context.applicationContext
        if (!initialized) {
            initialized = true
            Shizuku.addBinderReceivedListener { appContext?.let(::refresh) }
            Shizuku.addBinderDeadListener {
                files = null
                binding = false
                appContext?.let(::refresh)
            }
            Shizuku.addRequestPermissionResultListener { requestCode, _ ->
                if (requestCode == PERMISSION_REQUEST) appContext?.let(::refresh)
            }
        }
        val installed = try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            disconnect(if (installed) ShizukuAccessState.STOPPED
                else ShizukuAccessState.NOT_INSTALLED)
            return
        }
        if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(
                PackageManager.PERMISSION_DENIED
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            disconnect(ShizukuAccessState.PERMISSION_REQUIRED)
            return
        }
        val current = files
        if (current != null) {
            probe(current)
            return
        }
        if (binding) return
        binding = true
        state = ShizukuAccessState.CONNECTING
        runCatching {
            Shizuku.bindUserService(
                Shizuku.UserServiceArgs(
                    ComponentName(context, ShizukuFileService::class.java)
                ).processNameSuffix("ranobelib_files").tag("ranobelib_files")
                    .version(1).daemon(false),
                connection
            )
        }.onFailure {
            binding = false
            state = ShizukuAccessState.STOPPED
        }
    }

    fun requestPermission() {
        if (state == ShizukuAccessState.PERMISSION_REQUIRED) {
            runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
                .onFailure { appContext?.let(::refresh) }
        }
    }

    fun openManager(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (intent != null) context.startActivity(intent)
    }

    fun openDownload(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        )
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            files = IReaderLbFiles.Stub.asInterface(binder)
            files?.let(::probe)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            disconnect(ShizukuAccessState.STOPPED)
        }
    }

    private fun probe(remote: IReaderLbFiles) {
        state = ShizukuAccessState.CHECKING
        scope.launch {
            val available = withContext(Dispatchers.IO) {
                runCatching { remote.probe() }.getOrDefault(false)
            }
            if (files === remote) {
                state = if (available) ShizukuAccessState.READY
                    else ShizukuAccessState.FOLDER_MISSING
            }
        }
    }

    private fun disconnect(newState: ShizukuAccessState) {
        files = null
        binding = false
        state = newState
    }
}
