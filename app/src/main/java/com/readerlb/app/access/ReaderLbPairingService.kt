package com.readerlb.app.access

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import com.readerlb.app.MainActivity
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

/**
 * Foreground pairing flow for ReaderLB's built-in Wireless ADB client.
 *
 * The user stays on Android's Wireless Debugging screen. ReaderLB discovers the
 * local pairing port via mDNS and asks only for the six-digit pairing code
 * through an inline notification action.
 */
class ReaderLbPairingService :
    Service() {
    private val executor =
        Executors.newSingleThreadExecutor()

    private lateinit var nsd:
        NsdManager
    private var discovery:
        NsdManager.DiscoveryListener? =
        null
    @Volatile
    private var pairingPort =
        -1

    override fun onCreate() {
        super.onCreate()
        nsd =
            getSystemService(
                NsdManager::class.java
            )
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_REPLY -> {
                val results =
                    RemoteInput
                        .getResultsFromIntent(
                            intent
                        )
                val code =
                    results
                        ?.getCharSequence(
                            KEY_CODE
                        )
                        ?.toString()
                        ?.trim()
                val port =
                    intent.getIntExtra(
                        EXTRA_PORT,
                        pairingPort
                    )

                if (
                    code?.matches(
                        Regex("\\d{6}")
                    ) == true &&
                    port in 1..65535
                ) {
                    stopDiscovery()
                    update(
                        progressNotification(
                            "Подключаю ReaderLB…",
                            "Выполняю pairing и запускаю прямой доступ."
                        )
                    )
                    executor.execute {
                        pairAndStart(
                            port,
                            code
                        )
                    }
                } else {
                    update(
                        failureNotification(
                            "Код должен состоять из 6 цифр."
                        )
                    )
                }
            }

            ACTION_STOP -> {
                stopDiscovery()
                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            }

            else -> {
                startForeground(
                    NOTIFICATION_ID,
                    searchingNotification()
                )
                startDiscovery()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onDestroy() {
        stopDiscovery()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun pairAndStart(
        port: Int,
        code: String
    ) {
        runCatching {
            ReaderLbAdbClient.pair(
                this,
                port,
                code
            )

            update(
                progressNotification(
                    "Pairing выполнен",
                    "Подключаюсь и запускаю ReaderLB Bridge…"
                )
            )

            ReaderLbAdbClient
                .ensureConnected(
                    this,
                    20_000L
                )

            ReaderLbBridgeLauncher
                .launchViaAdb(
                    this
                )
        }
            .onSuccess {
                update(
                    successNotification()
                )
                sendBroadcast(
                    Intent(
                        ACTION_READY
                    ).setPackage(
                        packageName
                    )
                )
                stopForeground(
                    STOP_FOREGROUND_DETACH
                )
                stopSelf()
            }
            .onFailure { error ->
                ReaderLbBridgeAccess
                    .markError(
                        error.message
                            ?: "Не удалось настроить прямой доступ"
                    )
                ReaderLbAdbClient
                    .disconnect(
                        this
                    )
                update(
                    failureNotification(
                        error.message
                            ?: "Не удалось настроить прямой доступ"
                    )
                )
                stopForeground(
                    STOP_FOREGROUND_DETACH
                )
                stopSelf()
            }
    }

    private fun startDiscovery() {
        if (discovery != null) {
            return
        }

        val listener =
            object :
                NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(
                    serviceType: String
                ) = Unit

                override fun onDiscoveryStopped(
                    serviceType: String
                ) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int
                ) {
                    failure(
                        "Android не запустил поиск pairing-порта ($errorCode)."
                    )
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int
                ) = Unit

                override fun onServiceLost(
                    serviceInfo:
                        NsdServiceInfo
                ) = Unit

                override fun onServiceFound(
                    serviceInfo:
                        NsdServiceInfo
                ) {
                    runCatching {
                        nsd.resolveService(
                            serviceInfo,
                            resolveListener()
                        )
                    }
                }
            }

        discovery = listener
        nsd.discoverServices(
            PAIRING_SERVICE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    private fun resolveListener():
        NsdManager.ResolveListener =
        object :
            NsdManager.ResolveListener {
            override fun onResolveFailed(
                serviceInfo:
                    NsdServiceInfo,
                errorCode: Int
            ) = Unit

            override fun onServiceResolved(
                serviceInfo:
                    NsdServiceInfo
            ) {
                val port =
                    serviceInfo.port
                if (
                    port !in 1..65535 ||
                    !isOccupiedLocalPort(
                        port
                    )
                ) {
                    return
                }

                pairingPort = port
                update(
                    codeNotification(
                        port
                    )
                )
            }
        }

    private fun stopDiscovery() {
        val listener =
            discovery
                ?: return
        discovery = null
        runCatching {
            nsd.stopServiceDiscovery(
                listener
            )
        }
    }

    private fun isOccupiedLocalPort(
        port: Int
    ): Boolean =
        runCatching {
            ServerSocket().use {
                socket ->
                socket.bind(
                    InetSocketAddress(
                        "127.0.0.1",
                        port
                    ),
                    1
                )
            }
            false
        }.getOrDefault(true)

    private fun failure(
        message: String
    ) {
        ReaderLbBridgeAccess
            .markError(message)
        update(
            failureNotification(
                message
            )
        )
        stopForeground(
            STOP_FOREGROUND_DETACH
        )
        stopSelf()
    }

    private fun searchingNotification():
        Notification =
        baseBuilder()
            .setContentTitle(
                "ReaderLB — прямой доступ"
            )
            .setContentText(
                "В Wireless Debugging выберите «Pair device with pairing code»."
            )
            .addAction(
                stopAction()
            )
            .setOngoing(true)
            .build()

    private fun codeNotification(
        port: Int
    ): Notification {
        val remoteInput =
            RemoteInput.Builder(
                KEY_CODE
            )
                .setLabel(
                    "6-значный код"
                )
                .build()

        val intent =
            Intent(
                this,
                ReaderLbPairingService::
                    class.java
            )
                .setAction(
                    ACTION_REPLY
                )
                .putExtra(
                    EXTRA_PORT,
                    port
                )

        val reply =
            PendingIntent
                .getForegroundService(
                    this,
                    101,
                    intent,
                    PendingIntent
                        .FLAG_UPDATE_CURRENT or
                        PendingIntent
                            .FLAG_MUTABLE
                )

        val action =
            Notification.Action
                .Builder(
                    null,
                    "Ввести код",
                    reply
                )
                .addRemoteInput(
                    remoteInput
                )
                .build()

        return baseBuilder()
            .setContentTitle(
                "ReaderLB нашёл Wireless Debugging"
            )
            .setContentText(
                "Введите 6-значный код pairing из настроек."
            )
            .addAction(action)
            .addAction(
                stopAction()
            )
            .setOngoing(true)
            .build()
    }

    private fun progressNotification(
        title: String,
        text: String
    ): Notification =
        baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setProgress(
                0,
                0,
                true
            )
            .build()

    private fun successNotification():
        Notification =
        baseBuilder()
            .setContentTitle(
                "ReaderLB подключён"
            )
            .setContentText(
                "Прямой доступ готов. Wireless Debugging можно выключить до перезагрузки."
            )
            .setContentIntent(
                openAppIntent()
            )
            .setAutoCancel(true)
            .build()

    private fun failureNotification(
        message: String
    ): Notification =
        baseBuilder()
            .setContentTitle(
                "Не удалось подключить ReaderLB"
            )
            .setContentText(
                message.take(180)
            )
            .setContentIntent(
                openAppIntent()
            )
            .setAutoCancel(true)
            .build()

    private fun baseBuilder():
        Notification.Builder =
        Notification.Builder(
            this,
            CHANNEL
        )
            .setSmallIcon(
                android.R.drawable
                    .stat_notify_sync
            )
            .setOnlyAlertOnce(true)

    private fun stopAction():
        Notification.Action {
        val intent =
            Intent(
                this,
                ReaderLbPairingService::
                    class.java
            )
                .setAction(
                    ACTION_STOP
                )
        val pending =
            PendingIntent.getService(
                this,
                102,
                intent,
                PendingIntent
                    .FLAG_UPDATE_CURRENT or
                    PendingIntent
                        .FLAG_IMMUTABLE
            )
        return Notification.Action
            .Builder(
                null,
                "Отмена",
                pending
            )
            .build()
    }

    private fun openAppIntent():
        PendingIntent {
        val intent =
            Intent(
                this,
                MainActivity::class.java
            )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
        return PendingIntent
            .getActivity(
                this,
                103,
                intent,
                PendingIntent
                    .FLAG_UPDATE_CURRENT or
                    PendingIntent
                        .FLAG_IMMUTABLE
            )
    }

    private fun update(
        notification: Notification
    ) {
        getSystemService(
            NotificationManager::class.java
        )
            .notify(
                NOTIFICATION_ID,
                notification
            )
    }

    private fun createChannel() {
        getSystemService(
            NotificationManager::class.java
        )
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Прямой доступ ReaderLB",
                    NotificationManager
                        .IMPORTANCE_HIGH
                ).apply {
                    setSound(
                        null,
                        null
                    )
                    enableVibration(
                        false
                    )
                }
            )
    }

    companion object {
        private const val CHANNEL =
            "readerlb_direct_access"
        private const val NOTIFICATION_ID =
            1201
        private const val ACTION_START =
            "com.readerlb.app.access.START"
        private const val ACTION_REPLY =
            "com.readerlb.app.access.REPLY"
        private const val ACTION_STOP =
            "com.readerlb.app.access.STOP"
        private const val KEY_CODE =
            "pairing_code"
        private const val EXTRA_PORT =
            "pairing_port"
        private const val PAIRING_SERVICE =
            "_adb-tls-pairing._tcp"

        const val ACTION_READY =
            "com.readerlb.app.access.READY"

        fun start(
            context: Context
        ) {
            val intent =
                Intent(
                    context,
                    ReaderLbPairingService::
                        class.java
                )
                    .setAction(
                        ACTION_START
                    )
            context.startForegroundService(
                intent
            )
        }
    }
}
