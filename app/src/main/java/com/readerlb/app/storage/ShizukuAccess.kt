package com.readerlb.app.storage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.readerlb.app.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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

    val isServiceConnected: Boolean get() = files != null

    fun rootDiagnostics(): String = files?.let { remote ->
        runCatching { remote.diagnostics() }.getOrElse { "Remote diagnostics failed: ${it.stackTraceToString()}" }
    } ?: "User service is not connected; root paths cannot be checked."

    private fun record(stage: String, detail: String, error: Throwable? = null) {
        appContext?.let { TesterDiagnostics.record(it, stage, detail, error) }
    }
    var state by mutableStateOf(ShizukuAccessState.CHECKING)
        private set

    val treeUri: Uri
        get() = DocumentsContract.buildTreeDocumentUri(
            "${BuildConfig.APPLICATION_ID}.ranobelib", "book"
        )

    val mangaTreeUri: Uri
        get() = DocumentsContract.buildTreeDocumentUri(
            "${BuildConfig.APPLICATION_ID}.ranobelib", "manga"
        )

    val filesTreeUri: Uri
        get() = DocumentsContract.buildTreeDocumentUri(
            "${BuildConfig.APPLICATION_ID}.ranobelib", "files"
        )

    fun service(): IReaderLbFiles = files ?: error("RanobeLib access is disconnected")

    fun ensureContentRoot(context: Context, kind: String): Uri {
        require(kind == "book" || kind == "manga") { "Unsupported library folder" }
        val filesDirectory = DocumentFile.fromTreeUri(context, filesTreeUri)
            ?: error("Подключите Shizuku для доступа к Android/data/ru.libappc/files")
        require(filesDirectory.isDirectory) { "Папка MangaLib files не найдена" }
        val child = filesDirectory.findFile(kind) ?: filesDirectory.createDirectory(kind)
        require(child?.isDirectory == true) { "Не удалось создать files/$kind" }
        service().probe()
        record("shizuku.root", "ready files/$kind")
        return if (kind == "book") treeUri else mangaTreeUri
    }

    fun refresh(context: Context) {
        appContext = context.applicationContext
        record("shizuku.refresh", "state=$state binding=$binding service=${files != null}")
        if (!initialized) {
            initialized = true
            Shizuku.addBinderReceivedListener {
                record("shizuku.binder", "received")
                appContext?.let(::refresh)
            }
            Shizuku.addBinderDeadListener {
                record("shizuku.binder", "dead")
                files = null
                binding = false
                appContext?.let(::refresh)
            }
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                record("shizuku.permission", "requestCode=$requestCode result=$grantResult")
                if (requestCode == PERMISSION_REQUEST) appContext?.let(::refresh)
            }
        }
        val installed = try {
            val info = context.packageManager.getPackageInfo(PACKAGE, 0)
            record("shizuku.manager", "installed version=${info.versionName} code=${info.longVersionCode}")
            true
        } catch (_: PackageManager.NameNotFoundException) {
            record("shizuku.manager", "not installed or not visible")
            false
        }
        val ping = runCatching { Shizuku.pingBinder() }
        record("shizuku.ping", "result=${ping.getOrNull()}", ping.exceptionOrNull())
        if (ping.getOrDefault(false) != true) {
            disconnect(if (installed) ShizukuAccessState.STOPPED
                else ShizukuAccessState.NOT_INSTALLED)
            return
        }
        val permission = runCatching { Shizuku.checkSelfPermission() }
        record("shizuku.permission", "check=${permission.getOrNull()}", permission.exceptionOrNull())
        if (permission.getOrDefault(
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
        if (binding) {
            record("shizuku.bind", "already in progress")
            return
        }
        binding = true
        state = ShizukuAccessState.CONNECTING
        record("shizuku.bind", "requesting user service")
        runCatching {
            Shizuku.bindUserService(
                Shizuku.UserServiceArgs(
                    ComponentName(context, ShizukuFileService::class.java)
                ).processNameSuffix("ranobelib_files").tag("ranobelib_files")
                    .version(6).daemon(false),
                connection
            )
            scope.launch {
                delay(15_000)
                if (binding && files == null) {
                    record("shizuku.bind", "still waiting for onServiceConnected after 15 seconds")
                }
            }
        }.onFailure {
            record("shizuku.bind", "request failed", it)
            binding = false
            state = ShizukuAccessState.STOPPED
        }
    }

    fun requestPermission() {
        if (state == ShizukuAccessState.PERMISSION_REQUIRED) {
            record("shizuku.permission", "requesting")
            runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
                .onFailure {
                    record("shizuku.permission", "request failed", it)
                    appContext?.let(::refresh)
                }
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
            record("shizuku.bind", "connected component=$name binderAlive=${binder.isBinderAlive}")
            binding = false
            files = IReaderLbFiles.Stub.asInterface(binder)
            files?.let(::probe)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            record("shizuku.bind", "disconnected component=$name")
            disconnect(ShizukuAccessState.STOPPED)
        }
    }

    private fun probe(remote: IReaderLbFiles) {
        state = ShizukuAccessState.CHECKING
        record("shizuku.probe", "starting")
        scope.launch {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching { remote.probe() }
            }
            record("shizuku.probe", "result=${result.getOrNull()} durationMs=${System.currentTimeMillis() - started}; ${rootDiagnostics()}", result.exceptionOrNull())
            if (files === remote) {
                state = if (result.getOrDefault(false)) ShizukuAccessState.READY
                    else ShizukuAccessState.FOLDER_MISSING
            }
        }
    }

    private fun disconnect(newState: ShizukuAccessState) {
        if (state != newState || files != null) record("shizuku.state", "$state -> $newState")
        files = null
        binding = false
        state = newState
    }
}
