package com.readerlb.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.readerlb.app.EXTRA_OPEN_UPDATES
import com.readerlb.app.MainActivity
import com.readerlb.app.R

class ReleaseMessagingService :
    FirebaseMessagingService() {

    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        val type = message.data["type"]
            ?.trim()
            .orEmpty()

        if (
            type.isNotBlank() &&
            type != "release"
        ) {
            return
        }

        val title =
            message.data["title"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: message.notification?.title
                ?: "Доступно обновление ReaderLB"

        val version =
            message.data["version"]
                ?.trim()
                .orEmpty()

        val body =
            message.data["body"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: message.notification?.body
                ?: if (version.isNotBlank()) {
                    "Новая стабильная версия $version уже доступна."
                } else {
                    "Доступна новая стабильная версия ReaderLB."
                }

        showReleaseNotification(
            title = title,
            body = body
        )
    }

    private fun showReleaseNotification(
        title: String,
        body: String
    ) {
        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Обновления ReaderLB",
                    NotificationManager
                        .IMPORTANCE_DEFAULT
                ).apply {
                    description =
                        "Стабильные релизы ReaderLB"
                }
            )
        }

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )
                .putExtra(
                    EXTRA_OPEN_UPDATES,
                    true
                )
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_notification
                )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(body)
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

        manager.notify(
            NOTIFICATION_ID,
            notification
        )
    }

    private companion object {
        const val CHANNEL_ID =
            "readerlb_releases"
        const val NOTIFICATION_ID =
            2101
    }
}
