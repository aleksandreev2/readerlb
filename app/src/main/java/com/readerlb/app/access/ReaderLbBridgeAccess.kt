package com.readerlb.app.access

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.readerlb.app.storage.RanobeLibBackendKind
import com.readerlb.app.storage.ReaderLbBuiltInAccessState
import com.readerlb.app.storage.RanobeLibBackends
import com.readerlb.app.storage.bridge.ReaderLbBridgeFileBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom

/**
 * Owns ReaderLB's built-in privileged bridge lifecycle.
 */
object ReaderLbBridgeAccess {
    private const val PREFS =
        "readerlb_privileged_bridge"
    private const val KEY_PORT =
        "port"
    private const val KEY_TOKEN =
        "token"

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main
        )

    var state by mutableStateOf(
        ReaderLbBuiltInAccessState.CHECKING
    )
        private set

    var lastError by mutableStateOf<
        String?
    >(null)
        private set

    var pairingPort by mutableStateOf(
        -1
    )
        private set

    val treeUri: Uri
        get() =
            DocumentsContract
                .buildTreeDocumentUri(
                    "com.readerlb.app.ranobelib",
                    "book"
                )

    fun refresh(
        context: Context
    ) {
        if (
            state ==
                ReaderLbBuiltInAccessState.PAIRING ||
            state ==
                ReaderLbBuiltInAccessState.STARTING
        ) {
            return
        }

        state =
            ReaderLbBuiltInAccessState.CHECKING
        lastError = null

        scope.launch {
            val restored =
                withContext(
                    Dispatchers.IO
                ) {
                    restoreBackend(
                        context
                            .applicationContext
                    )
                }

            state =
                if (restored) {
                    ReaderLbBuiltInAccessState.READY
                } else {
                    ReaderLbBuiltInAccessState
                        .DISCONNECTED
                }
        }
    }

    fun startSetup(
        context: Context
    ) {
        state =
            ReaderLbBuiltInAccessState.PAIRING
        lastError = null
        pairingPort = -1

        ReaderLbPairingService.start(
            context.applicationContext
        )

        openDeveloperOptions(
            context
        )
    }

    internal fun markPairingPort(
        port: Int
    ) {
        if (port in 1..65535) {
            pairingPort = port
        }
    }

    fun submitPairingCode(
        context: Context,
        code: String
    ) {
        val port =
            pairingPort
                .takeIf {
                    it in 1..65535
                }
                ?: run {
                    markError(
                        "ReaderLB ещё не нашёл pairing-порт. Оставьте экран Wireless Debugging открытым несколько секунд."
                    )
                    return
                }

        ReaderLbPairingService
            .submitCode(
                context.applicationContext,
                port,
                code
            )
    }

    internal fun markStarting() {
        state =
            ReaderLbBuiltInAccessState.STARTING
        lastError = null
    }

    internal fun markReady(
        context: Context,
        port: Int,
        token: String,
        backend:
            ReaderLbBridgeFileBackend
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putInt(
                KEY_PORT,
                port
            )
            .putString(
                KEY_TOKEN,
                token
            )
            .apply()

        RanobeLibBackends.install(
            RanobeLibBackendKind
                .READERLB_BRIDGE,
            backend
        )

        state =
            ReaderLbBuiltInAccessState.READY
        lastError = null
        pairingPort = -1
    }

    internal fun markError(
        message: String
    ) {
        state =
            ReaderLbBuiltInAccessState.ERROR
        lastError =
            message.take(500)
    }

    fun clear(
        context: Context
    ) {
        RanobeLibBackends.clear(
            RanobeLibBackendKind
                .READERLB_BRIDGE
        )
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()
        state =
            ReaderLbBuiltInAccessState.DISCONNECTED
        lastError = null
        pairingPort = -1
    }

    private fun restoreBackend(
        context: Context
    ): Boolean {
        val preferences =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
        val port =
            preferences.getInt(
                KEY_PORT,
                -1
            )
                .takeIf {
                    it in 1024..65535
                }
                ?: return false
        val token =
            preferences.getString(
                KEY_TOKEN,
                null
            )
                ?.takeIf(
                    String::isNotBlank
                )
                ?: return false

        val backend =
            ReaderLbBridgeFileBackend(
                port,
                token
            )

        if (!backend.ping()) {
            preferences
                .edit()
                .clear()
                .apply()
            RanobeLibBackends.clear(
                RanobeLibBackendKind
                    .READERLB_BRIDGE
            )
            return false
        }

        RanobeLibBackends.install(
            RanobeLibBackendKind
                .READERLB_BRIDGE,
            backend
        )
        return true
    }

    private fun openDeveloperOptions(
        context: Context
    ) {
        val direct =
            Intent(
                "android.settings." +
                    "WIRELESS_DEBUGGING_SETTINGS"
            )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

        val fallback =
            Intent(
                Settings
                    .ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

        val packageManager =
            context.packageManager

        when {
            direct.resolveActivity(
                packageManager
            ) != null ->
                context.startActivity(
                    direct
                )

            fallback.resolveActivity(
                packageManager
            ) != null ->
                context.startActivity(
                    fallback
                )

            else ->
                context.startActivity(
                    Intent(
                        Settings
                            .ACTION_SETTINGS
                    )
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                )
        }
    }
}

internal object ReaderLbBridgeLauncher {
    fun launchViaAdb(
        context: Context
    ): ReaderLbBridgeFileBackend {
        val appContext =
            context.applicationContext

        ReaderLbBridgeAccess
            .markStarting()

        val port =
            reserveLoopbackPort()
        val token =
            randomToken()
        val apk =
            appContext
                .applicationInfo
                .sourceDir

        val probeCommand =
            "CLASSPATH=" +
                shellQuote(apk) +
                " /system/bin/app_process " +
                "/system/bin " +
                "--nice-name=readerlb_probe " +
                "com.readerlb.app.storage.bridge." +
                "ReaderLbBridgeMain --probe"

        val probeResult =
            ReaderLbAdbClient.runShell(
                appContext,
                probeCommand
            )

        require(
            probeResult
                .lineSequence()
                .any {
                    it.startsWith(
                        "READERLB_BRIDGE_READY:"
                    )
                }
        ) {
            if (
                "READERLB_BRIDGE_ERROR:" +
                    "book_unavailable" in
                probeResult
            ) {
                "ReaderLB получил ADB-доступ, но Android не дал shell читать и записывать Android/data/ru.libappc/files/book. Проверьте, что в RanobeLib есть скачанная книга; на некоторых прошивках shell-доступ дополнительно ограничен."
            } else {
                "Android не смог запустить ReaderLB Bridge через app_process."
            }
        }

        val command =
            "CLASSPATH=" +
                shellQuote(apk) +
                " nohup setsid /system/bin/app_process " +
                "/system/bin " +
                "--nice-name=readerlb_bridge " +
                "com.readerlb.app.storage.bridge." +
                "ReaderLbBridgeMain " +
                port +
                " " +
                shellQuote(token) +
                " </dev/null >/dev/null " +
                "2>&1 &"

        ReaderLbAdbClient.runShell(
            appContext,
            command
        )

        val backend =
            ReaderLbBridgeFileBackend(
                port,
                token
            )

        repeat(50) {
            if (backend.ping()) {
                ReaderLbBridgeAccess
                    .markReady(
                        appContext,
                        port,
                        token,
                        backend
                    )
                ReaderLbAdbClient
                    .disconnect(
                        appContext
                    )
                return backend
            }
            Thread.sleep(100)
        }

        throw IllegalStateException(
            "ReaderLB Bridge не запустился"
        )
    }

    private fun reserveLoopbackPort(): Int =
        ServerSocket(
            0,
            1,
            InetAddress
                .getLoopbackAddress()
        ).use {
            it.localPort
        }

    private fun randomToken(): String {
        val bytes =
            ByteArray(32)
        SecureRandom()
            .nextBytes(bytes)
        return bytes.joinToString("") {
            "%02x".format(
                it.toInt() and 0xff
            )
        }
    }
}
