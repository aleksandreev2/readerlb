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
import java.security.SecureRandom
import java.util.UUID

/**
 * Owns ReaderLB's built-in privileged bridge lifecycle.
 */
object ReaderLbBridgeAccess {
    private const val PREFS =
        "readerlb_privileged_bridge"
    private const val KEY_SOCKET =
        "socket"
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

        ReaderLbPairingService.start(
            context.applicationContext
        )

        openDeveloperOptions(
            context
        )
    }

    internal fun markStarting() {
        state =
            ReaderLbBuiltInAccessState.STARTING
        lastError = null
    }

    internal fun markReady(
        context: Context,
        socketName: String,
        token: String,
        backend:
            ReaderLbBridgeFileBackend
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_SOCKET,
                socketName
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
    }

    private fun restoreBackend(
        context: Context
    ): Boolean {
        val preferences =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
        val socketName =
            preferences.getString(
                KEY_SOCKET,
                null
            )
                ?.takeIf(
                    String::isNotBlank
                )
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
                socketName,
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

        val socketName =
            "readerlb_" +
                UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .take(24)
        val token =
            randomToken()
        val apk =
            appContext
                .applicationInfo
                .sourceDir

        val command =
            "CLASSPATH=" +
                shellQuote(apk) +
                " nohup app_process " +
                "/system/bin " +
                "--nice-name=readerlb_bridge " +
                "com.readerlb.app.storage.bridge." +
                "ReaderLbBridgeMain " +
                shellQuote(socketName) +
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
                socketName,
                token
            )

        repeat(50) {
            if (backend.ping()) {
                ReaderLbBridgeAccess
                    .markReady(
                        appContext,
                        socketName,
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
