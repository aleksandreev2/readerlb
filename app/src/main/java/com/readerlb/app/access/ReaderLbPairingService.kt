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
import android.os.IBinder
import com.readerlb.app.MainActivity
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

/**
 * Built-in Wireless ADB setup.
 *
 * First setup:
 *  - discover Android's local pairing service
 *  - accept the six-digit code from an inline notification
 *  - pair ReaderLB's persistent ADB identity
 *  - launch ReaderLB Bridge
 *
 * Later boots:
 *  - the same persistent ADB identity is already trusted
 *  - once Wireless Debugging is enabled, discover the connect service
 *  - reconnect and launch ReaderLB Bridge without asking for another code
 */
class ReaderLbPairingService :
    Service() {
    private val executor =
        Executors.newSingleThreadExecutor()

    private lateinit var nsd:
        NsdManager

    private var pairingDiscovery:
        NsdManager.DiscoveryListener? =
        null
    private var connectDiscovery:
        NsdManager.DiscoveryListener? =
        null

    @Volatile
    private var pairingPort =
        -1

    @Volatile
    private var reconnectInProgress =
        false

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
                handlePairingReply(
                    intent
                )
            }

            ACTION_STOP -> {
                stopDiscoveries()
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
                startPairingDiscovery()
                startConnectDiscovery()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onDestroy() {
        stopDiscoveries()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun handlePairingReply(
        intent: Intent
    ) {
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
            ) != true ||
            port !in 1..65535
        ) {
            update(
                failureNotification(
                    "Код должен состоять из 6 цифр."
                )
            )
            return
        }

        stopPairingDiscovery()
        update(
            progressNotification(
                "Подключаю ReaderLB…",
                "Сохраняю pairing и запускаю прямой доступ."
            )
        )

        executor.execute {
            pairAndStart(
                port,
                code
            )
        }
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
                    "Запускаю ReaderLB Bridge…"
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
                completeSuccess()
            }
            .onFailure {
                    error ->
                failSetup(
                    error.message
                        ?: "Не удалось настроить прямой доступ"
                )
            }
    }

    private fun reconnectAndStart(
        port: Int
    ) {
        runCatching {
            ReaderLbAdbClient.connect(
                this,
                port
            )

            update(
                progressNotification(
                    "ReaderLB уже спарен",
                    "Восстанавливаю прямой доступ после перезагрузки…"
                )
            )

            ReaderLbBridgeLauncher
                .launchViaAdb(
                    this
                )
        }
            .onSuccess {
                completeSuccess()
            }
            .onFailure {
                // A failed reconnect normally means this installation has
                // never been paired (or Android revoked the trusted key).
                // Keep pairing discovery alive instead of surfacing an error:
                // the user can simply open "Pair device with pairing code".
                ReaderLbAdbClient
                    .disconnect(this)
                reconnectInProgress =
                    false
                update(
                    searchingNotification()
                )
            }
    }

    private fun completeSuccess() {
        stopDiscoveries()
        reconnectInProgress =
            false

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

    private fun failSetup(
        message: String
    ) {
        stopDiscoveries()
        ReaderLbBridgeAccess
            .markError(message)
        ReaderLbAdbClient
            .disconnect(this)

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

    private fun startPairingDiscovery() {
        if (
            pairingDiscovery != null
        ) {
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
                    failSetup(
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
                            pairingResolveListener()
                        )
                    }
                }
            }

        pairingDiscovery =
            listener
        nsd.discoverServices(
            PAIRING_SERVICE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    private fun startConnectDiscovery() {
        if (
            connectDiscovery != null
        ) {
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
                ) = Unit

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
                            connectResolveListener()
                        )
                    }
                }
            }

        connectDiscovery =
            listener
        nsd.discoverServices(
            CONNECT_SERVICE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    private fun pairingResolveListener():
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
                    !isLocalAdbPort(
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

    private fun connectResolveListener():
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
                    !isLocalAdbPort(
                        port
                    ) ||
                    reconnectInProgress
                ) {
                    return
                }

                reconnectInProgress =
                    true
                executor.execute {
                    reconnectAndStart(
                        port
                    )
                }
            }
        }

    private fun isLocalAdbPort(
        port: Int
    ): Boolean {
        if (
            port !in 1..65535
        ) {
            return false
        }

        return runCatching {
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
    }

    private fun stopPairingDiscovery() {
        val listener =
            pairingDiscovery
                ?: return
        pairingDiscovery = null

        runCatching {
            nsd.stopServiceDiscovery(
                listener
            )
        }
    }

    private fun stopConnectDiscovery() {
        val listener =
            connectDiscovery
                ?: return
        connectDiscovery = null

        runCatching {
            nsd.stopServiceDiscovery(
                listener
            )
        }
    }

    private fun stopDiscoveries() {
        stopPairingDiscovery()
        stopConnectDiscovery()
    }

    private fun searchingNotification():
        Notification =
        baseBuilder()
            .setContentTitle(
                "ReaderLB — прямой доступ"
            )
            .setContentText(
                "Уже подключали ReaderLB? Просто включите Wireless Debugging. В первый раз выберите «Pair device with pairing code»."
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
                "ReaderLB нашёл pairing"
            )
            .setContentText(
                "Введите 6-значный код из Wireless Debugging."
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
                "Прямой доступ готов. Wireless Debugging можно выключить до следующей перезагрузки."
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
        private const val CONNECT_SERVICE =
            "_adb-tls-connect._tcp"

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
