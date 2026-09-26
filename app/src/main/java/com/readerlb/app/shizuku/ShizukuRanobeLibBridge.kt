package com.readerlb.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.readerlb.app.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import rikka.shizuku.Shizuku

enum class ShizukuRanobeLibState {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    CONNECTING,
    READY,
    RANOBELIB_NOT_FOUND,
    ERROR
}

data class ShizukuRanobeLibStatus(
    val state: ShizukuRanobeLibState,
    val message: String
) {
    val ready: Boolean
        get() =
            state ==
                ShizukuRanobeLibState
                    .READY
}

class ShizukuRanobeLibBridge(
    context: Context,
    private val onStatusChanged: (
        ShizukuRanobeLibStatus
    ) -> Unit
) : RanobeLibPrivilegedFiles, AutoCloseable {

    private val appContext =
        context.applicationContext

    @Volatile
    private var service:
        IRanobeLibPrivilegedService? =
        null

    @Volatile
    private var binding = false

    private var started = false

    private val userServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                RanobeLibPrivilegedService::
                    class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix(
                "ranobelib"
            )
            .debuggable(
                BuildConfig.DEBUG
            )
            .version(
                BuildConfig.VERSION_CODE
            )

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?
            ) {
                binding = false

                service =
                    binder
                        ?.takeIf(
                            IBinder::pingBinder
                        )
                        ?.let {
                            IRanobeLibPrivilegedService
                                .Stub
                                .asInterface(it)
                        }

                emitCurrentStatus()
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {
                service = null
                binding = false
                emitCurrentStatus()
            }
        }

    private val binderReceivedListener =
        Shizuku
            .OnBinderReceivedListener {
                refresh()
            }

    private val binderDeadListener =
        Shizuku
            .OnBinderDeadListener {
                service = null
                binding = false
                emitCurrentStatus()
            }

    private val permissionListener =
        Shizuku
            .OnRequestPermissionResultListener {
                    requestCode,
                    grantResult ->
                if (
                    requestCode !=
                    REQUEST_CODE
                ) {
                    return@OnRequestPermissionResultListener
                }

                if (
                    grantResult ==
                    PackageManager
                        .PERMISSION_GRANTED
                ) {
                    bindIfReady()
                } else {
                    emit(
                        ShizukuRanobeLibStatus(
                            state =
                                ShizukuRanobeLibState
                                    .PERMISSION_DENIED,
                            message =
                                "Доступ Shizuku для ReaderLB не выдан."
                        )
                    )
                }
            }

    fun start() {
        if (started) {
            refresh()
            return
        }

        started = true

        Shizuku
            .addBinderReceivedListenerSticky(
                binderReceivedListener
            )
        Shizuku
            .addBinderDeadListener(
                binderDeadListener
            )
        Shizuku
            .addRequestPermissionResultListener(
                permissionListener
            )

        refresh()
    }

    fun refresh() {
        emitCurrentStatus()

        if (
            hasPermission() &&
            service == null
        ) {
            bindIfReady()
        }
    }

    fun requestAccess() {
        when (
            currentStatus().state
        ) {
            ShizukuRanobeLibState
                .NOT_INSTALLED -> {
                openShizukuOrDownload()
            }

            ShizukuRanobeLibState
                .NOT_RUNNING -> {
                openShizukuOrDownload()
            }

            ShizukuRanobeLibState
                .PERMISSION_REQUIRED -> {
                runCatching {
                    Shizuku
                        .requestPermission(
                            REQUEST_CODE
                        )
                }.onFailure {
                    emitError(it)
                }
            }

            ShizukuRanobeLibState
                .PERMISSION_DENIED -> {
                openShizukuOrDownload()
            }

            ShizukuRanobeLibState
                .CONNECTING -> Unit

            ShizukuRanobeLibState
                .READY,
            ShizukuRanobeLibState
                .RANOBELIB_NOT_FOUND -> {
                bindIfReady(
                    force = true
                )
            }

            ShizukuRanobeLibState
                .ERROR -> {
                refresh()
            }
        }
    }

    fun openShizukuOrDownload() {
        val launchIntent =
            if (
                isShizukuInstalled()
            ) {
                appContext
                    .packageManager
                    .getLaunchIntentForPackage(
                        SHIZUKU_PACKAGE
                    )
            } else {
                null
            }

        val intent =
            launchIntent
                ?: Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        SHIZUKU_DOWNLOAD_URL
                    )
                )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        appContext.startActivity(
            intent
        )
    }

    fun currentStatus():
        ShizukuRanobeLibStatus {
        if (
            !isShizukuInstalled()
        ) {
            return ShizukuRanobeLibStatus(
                state =
                    ShizukuRanobeLibState
                        .NOT_INSTALLED,
                message =
                    "Shizuku не установлен."
            )
        }

        val binderAlive =
            runCatching {
                Shizuku.pingBinder()
            }.getOrDefault(false)

        if (!binderAlive) {
            return ShizukuRanobeLibStatus(
                state =
                    ShizukuRanobeLibState
                        .NOT_RUNNING,
                message =
                    "Shizuku установлен, но сервис не запущен."
            )
        }

        val permission =
            runCatching {
                Shizuku
                    .checkSelfPermission()
            }.getOrElse {
                return ShizukuRanobeLibStatus(
                    state =
                        ShizukuRanobeLibState
                            .ERROR,
                    message =
                        "Не удалось проверить доступ Shizuku."
                )
            }

        if (
            permission !=
            PackageManager
                .PERMISSION_GRANTED
        ) {
            val deniedPermanently =
                runCatching {
                    Shizuku
                        .shouldShowRequestPermissionRationale()
                }.getOrDefault(false)

            return ShizukuRanobeLibStatus(
                state =
                    if (
                        deniedPermanently
                    ) {
                        ShizukuRanobeLibState
                            .PERMISSION_DENIED
                    } else {
                        ShizukuRanobeLibState
                            .PERMISSION_REQUIRED
                    },
                message =
                    if (
                        deniedPermanently
                    ) {
                        "Разрешение Shizuku для ReaderLB было отклонено."
                    } else {
                        "ReaderLB готов запросить разрешение Shizuku."
                    }
            )
        }

        val current =
            service

        if (
            current == null
        ) {
            return ShizukuRanobeLibStatus(
                state =
                    ShizukuRanobeLibState
                        .CONNECTING,
                message =
                    "Подключаю ReaderLB к Shizuku…"
            )
        }

        return runCatching {
            if (
                current.rootExists()
            ) {
                ShizukuRanobeLibStatus(
                    state =
                        ShizukuRanobeLibState
                            .READY,
                    message =
                        "Полный доступ к локальной библиотеке RanobeLib получен."
                )
            } else {
                ShizukuRanobeLibStatus(
                    state =
                        ShizukuRanobeLibState
                            .RANOBELIB_NOT_FOUND,
                    message =
                        "Доступ Shizuku есть, но папка локальных книг RanobeLib пока не найдена."
                )
            }
        }.getOrElse {
            ShizukuRanobeLibStatus(
                state =
                    ShizukuRanobeLibState
                        .ERROR,
                message =
                    "Shizuku подключён, но проверка папки RanobeLib завершилась ошибкой."
            )
        }
    }

    override fun listNames(
        relativePath: String = ""
    ): List<String> =
        requireService()
            .listNames(
                relativePath
            )
            .toList()

    override fun exists(
        relativePath: String
    ): Boolean =
        requireService()
            .exists(
                relativePath
            )

    override fun isDirectory(
        relativePath: String
    ): Boolean =
        requireService()
            .isDirectory(
                relativePath
            )

    override fun length(
        relativePath: String
    ): Long =
        requireService()
            .length(
                relativePath
            )

    override fun mkdirs(
        relativePath: String
    ): Boolean =
        requireService()
            .mkdirs(
                relativePath
            )

    override fun deleteRecursively(
        relativePath: String
    ): Boolean =
        requireService()
            .deleteRecursively(
                relativePath
            )

    override fun rename(
        fromRelativePath: String,
        toRelativePath: String
    ): Boolean =
        requireService()
            .rename(
                fromRelativePath,
                toRelativePath
            )

    override fun openInput(
        relativePath: String
    ): InputStream =
        ParcelFileDescriptor
            .AutoCloseInputStream(
                requireService()
                    .openRead(
                        relativePath
                    )
            )

    override fun openOutput(
        relativePath: String
    ): OutputStream =
        ParcelFileDescriptor
            .AutoCloseOutputStream(
                requireService()
                    .openWrite(
                        relativePath
                    )
            )

    override fun readBytes(
        relativePath: String,
        limitBytes: Long
    ): ByteArray =
        openInput(
            relativePath
        ).buffered().use {
                input ->
            val output =
                ByteArrayOutputStream()
            val buffer =
                ByteArray(
                    64 * 1024
                )
            var total = 0L

            while (true) {
                val count =
                    input.read(
                        buffer
                    )
                if (count < 0) {
                    break
                }
                if (count == 0) {
                    continue
                }

                total += count
                require(
                    total <=
                        limitBytes
                ) {
                    "Локальный файл слишком большой"
                }

                output.write(
                    buffer,
                    0,
                    count
                )
            }

            output.toByteArray()
        }

    override fun writeBytes(
        relativePath: String,
        bytes: ByteArray
    ) {
        openOutput(
            relativePath
        ).buffered().use {
            it.write(bytes)
            it.flush()
        }

        require(
            length(relativePath) ==
                bytes.size.toLong()
        ) {
            "Размер файла после записи не совпадает"
        }
    }

    private fun bindIfReady(
        force: Boolean = false
    ) {
        if (
            !hasPermission()
        ) {
            emitCurrentStatus()
            return
        }

        if (
            binding &&
            !force
        ) {
            return
        }

        if (
            service != null &&
            !force
        ) {
            emitCurrentStatus()
            return
        }

        binding = true
        emit(
            ShizukuRanobeLibStatus(
                state =
                    ShizukuRanobeLibState
                        .CONNECTING,
                message =
                    "Подключаю ReaderLB к Shizuku…"
            )
        )

        runCatching {
            Shizuku.bindUserService(
                userServiceArgs,
                serviceConnection
            )
        }.onFailure {
            binding = false
            emitError(it)
        }
    }

    private fun hasPermission():
        Boolean =
        isShizukuInstalled() &&
            runCatching {
                Shizuku.pingBinder() &&
                    Shizuku
                        .checkSelfPermission() ==
                    PackageManager
                        .PERMISSION_GRANTED
            }.getOrDefault(false)

    private fun isShizukuInstalled():
        Boolean =
        runCatching {
            @Suppress(
                "DEPRECATION"
            )
            appContext
                .packageManager
                .getPackageInfo(
                    SHIZUKU_PACKAGE,
                    0
                )
            true
        }.getOrDefault(false)

    private fun requireService():
        IRanobeLibPrivilegedService =
        service
            ?: error(
                "Shizuku ещё не предоставил доступ к RanobeLib"
            )

    private fun emitCurrentStatus() {
        emit(
            currentStatus()
        )
    }

    private fun emitError(
        throwable: Throwable
    ) {
        emit(
            ShizukuRanobeLibStatus(
                state =
                    ShizukuRanobeLibState
                        .ERROR,
                message =
                    throwable.message
                        ?: "Ошибка Shizuku"
            )
        )
    }

    private fun emit(
        status:
            ShizukuRanobeLibStatus
    ) {
        onStatusChanged(
            status
        )
    }

    override fun close() {
        if (!started) {
            return
        }

        started = false

        runCatching {
            Shizuku
                .removeBinderReceivedListener(
                    binderReceivedListener
                )
        }
        runCatching {
            Shizuku
                .removeBinderDeadListener(
                    binderDeadListener
                )
        }
        runCatching {
            Shizuku
                .removeRequestPermissionResultListener(
                    permissionListener
                )
        }
    }

    private companion object {
        const val REQUEST_CODE =
            0x524C
        const val SHIZUKU_PACKAGE =
            "moe.shizuku.privileged.api"
        const val SHIZUKU_DOWNLOAD_URL =
            "https://shizuku.rikka.app/download/"
    }
}
