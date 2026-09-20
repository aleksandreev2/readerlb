package com.readerlb.app.update

import android.content.Context
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.readerlb.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val downloadUrl: String,
    val sha256: String?,
    val notes: String
)

class UpdateManager(
    private val context: Context
) {
    fun checkLatest(): UpdateInfo? {
        val connection = open(
            "https://api.github.com/repos/" +
                "aleksandreev2/readerlb/releases/latest"
        )

        return connection.useConnection { http ->
            when (http.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    return@useConnection null
                }
                in 200..299 -> Unit
                else -> error(
                    "GitHub вернул HTTP " +
                        http.responseCode
                )
            }

            val payload = http.inputStream
                .bufferedReader()
                .use { it.readText() }
            val json = JSONObject(payload)

            val tagName = json
                .optString("tag_name")
                .trim()
            val versionName = tagName
                .removePrefix("v")
                .removePrefix("V")
                .trim()

            if (
                versionName.isBlank() ||
                !isVersionNewer(
                    latest = versionName,
                    current = BuildConfig.VERSION_NAME
                )
            ) {
                return@useConnection null
            }

            val assets = json.getJSONArray("assets")
            var selected: JSONObject? = null

            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset
                    .optString("name")
                    .lowercase()

                if (!name.endsWith(".apk")) {
                    continue
                }

                if (
                    selected == null ||
                    name.contains("readerlb")
                ) {
                    selected = asset
                }

                if (name.contains("readerlb")) {
                    break
                }
            }

            val apk = selected
                ?: error(
                    "В релизе нет APK ReaderLB"
                )

            val downloadUrl = apk
                .optString("browser_download_url")
                .takeIf(String::isNotBlank)
                ?: error(
                    "В релизе отсутствует ссылка на APK"
                )

            val digest = apk
                .optString("digest")
                .takeIf {
                    it.startsWith(
                        "sha256:",
                        ignoreCase = true
                    )
                }
                ?.substringAfter(':')
                ?.lowercase()

            UpdateInfo(
                versionName = versionName,
                tagName = tagName,
                downloadUrl = downloadUrl,
                sha256 = digest,
                notes = json.optString("body")
            )
        }
    }

    fun download(
        info: UpdateInfo
    ): File {
        val directory = File(
            context.cacheDir,
            "updates"
        ).apply {
            require(
                exists() || mkdirs()
            ) {
                "Не удалось создать папку обновлений"
            }
        }

        val partial = File(
            directory,
            "ReaderLB-${info.versionName}.apk.part"
        )
        val target = File(
            directory,
            "ReaderLB-${info.versionName}.apk"
        )

        partial.delete()
        target.delete()

        val digest = MessageDigest
            .getInstance("SHA-256")

        val connection = open(info.downloadUrl)
        connection.useConnection { http ->
            require(http.responseCode in 200..299) {
                "Не удалось скачать APK: HTTP " +
                    http.responseCode
            }

            http.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(
                            buffer,
                            0,
                            count
                        )
                        output.write(
                            buffer,
                            0,
                            count
                        )
                    }
                }
            }
        }

        require(partial.length() > 0L) {
            "GitHub вернул пустой APK"
        }

        val actualSha = digest
            .digest()
            .joinToString("") {
                "%02x".format(it)
            }

        info.sha256?.let { expected ->
            require(
                actualSha.equals(
                    expected,
                    ignoreCase = true
                )
            ) {
                "SHA-256 обновления не совпадает"
            }
        }

        validateDownloadedPackage(partial)

        require(
            partial.renameTo(target)
        ) {
            "Не удалось завершить загрузку APK"
        }

        return target
    }

    fun canRequestInstallPackages(): Boolean =
        Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O ||
            context.packageManager
                .canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse(
                "package:${context.packageName}"
            )
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

    fun requestInstall(
        apk: File
    ) {
        runCatching {
            requestInstallWithPackageInstaller(apk)
        }.onFailure {
            // OEM fallback for devices whose PackageInstaller session flow
            // is broken or modified. Android still requires user approval.
            context.startActivity(
                legacyInstallIntent(apk)
            )
        }
    }

    private fun requestInstallWithPackageInstaller(
        apk: File
    ) {
        val installer =
            context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
            }

        val sessionId =
            installer.createSession(params)
        val session =
            installer.openSession(sessionId)

        try {
            apk.inputStream().buffered().use { input ->
                session.openWrite(
                    "ReaderLB-update.apk",
                    0L,
                    apk.length()
                ).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val callbackIntent = Intent(
                context,
                UpdateInstallReceiver::class.java
            ).apply {
                action =
                    UpdateInstallReceiver.ACTION_INSTALL_RESULT
            }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_MUTABLE
                )

            session.commit(pendingIntent.intentSender)
        } finally {
            session.close()
        }
    }

    private fun legacyInstallIntent(
        apk: File
    ): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apk
        )

        @Suppress("DEPRECATION")
        return Intent(
            Intent.ACTION_INSTALL_PACKAGE
        )
            .setData(uri)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
    }

    private fun validateDownloadedPackage(
        apk: File
    ) {
        val flags = if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = context.packageManager
            .getPackageArchiveInfo(
                apk.absolutePath,
                flags
            )

        require(
            packageInfo?.packageName ==
                context.packageName
        ) {
            "Загруженный APK имеет другой package name"
        }

        val currentInfo =
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                flags
            )

        val currentCertificates =
            signingCertificateDigests(currentInfo)
        val incomingCertificates =
            signingCertificateDigests(
                requireNotNull(packageInfo)
            )

        require(
            currentCertificates.isNotEmpty() &&
                incomingCertificates.isNotEmpty() &&
                currentCertificates.any(
                    incomingCertificates::contains
                )
        ) {
            "Подпись обновления не совпадает с установленной " +
                "версией ReaderLB. Старые тестовые сборки " +
                "0.4.x подписывались разными ключами; их " +
                "нужно удалить один раз перед переходом на " +
                "стабильную ветку обновлений."
        }
    }

    private fun signingCertificateDigests(
        info: android.content.pm.PackageInfo
    ): Set<String> {
        val signatures = if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {
            val signingInfo =
                info.signingInfo
                    ?: return emptySet()

            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
                    .orEmpty()
                    .toList()
            } else {
                signingInfo.signingCertificateHistory
                    .orEmpty()
                    .toList()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
                .orEmpty()
                .toList()
        }

        return signatures.map { signature ->
            MessageDigest
                .getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") {
                    "%02x".format(it)
                }
        }.toSet()
    }

    private fun open(
        url: String
    ): HttpURLConnection =
        (URL(url).openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            setRequestProperty(
                "User-Agent",
                "ReaderLB/${BuildConfig.VERSION_NAME}"
            )
        }
}

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (
            intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )
        ) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation =
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
                    ) {
                        intent.getParcelableExtra(
                            Intent.EXTRA_INTENT,
                            Intent::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(
                            Intent.EXTRA_INTENT
                        )
                    }

                confirmation
                    ?.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                    ?.let(context::startActivity)
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(
                    context,
                    "ReaderLB обновлён",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                val message = intent.getStringExtra(
                    PackageInstaller.EXTRA_STATUS_MESSAGE
                ) ?: "установка отменена"

                Toast.makeText(
                    context,
                    "Не удалось обновить ReaderLB: $message",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT =
            "com.readerlb.app.UPDATE_INSTALL_RESULT"
    }
}

internal fun isVersionNewer(
    latest: String,
    current: String
): Boolean {
    fun parts(value: String): List<Int> =
        value
            .substringBefore('-')
            .split('.')
            .map {
                it.toIntOrNull() ?: 0
            }

    val left = parts(latest)
    val right = parts(current)
    val count = maxOf(
        left.size,
        right.size
    )

    for (index in 0 until count) {
        val leftPart = left
            .getOrElse(index) { 0 }
        val rightPart = right
            .getOrElse(index) { 0 }

        if (leftPart != rightPart) {
            return leftPart > rightPart
        }
    }

    return false
}

private inline fun <T> HttpURLConnection
    .useConnection(
        block: (HttpURLConnection) -> T
    ): T =
    try {
        block(this)
    } finally {
        disconnect()
    }
