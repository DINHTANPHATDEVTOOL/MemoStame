package com.mipastudio.memostamp.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mipastudio.memostamp.MainActivity
import com.mipastudio.memostamp.domain.model.DirectMessage

object MemoStampNotificationManager {

    const val CHANNEL_ID_INTERACTIONS = "memostamp_interactions_channel"
    const val CHANNEL_NAME_INTERACTIONS = "MemoStamp Hoạt động & Tương tác"

    const val CHANNEL_ID_MESSAGES = "memostamp_messages_channel"
    const val CHANNEL_NAME_MESSAGES = "MemoStamp Tin nhắn trực tiếp"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel 1: Messages
            val msgChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                CHANNEL_NAME_MESSAGES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi có tin nhắn mới từ bạn bè"
                enableVibration(true)
                setShowBadge(true)
            }

            // Channel 2: Interactions (Friend requests, Trades, Stamp posts)
            val interactionChannel = NotificationChannel(
                CHANNEL_ID_INTERACTIONS,
                CHANNEL_NAME_INTERACTIONS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo lời mời kết bạn, trao đổi tem và tương tác"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(msgChannel)
            notificationManager.createNotificationChannel(interactionChannel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun sendNewMessageNotification(
        context: Context,
        message: DirectMessage
    ) {
        createNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "CHAT")
            putExtra("TARGET_USER_ID", message.senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("chat_" + message.senderId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previewText = if (!message.stampTitle.isNullOrBlank()) {
            if (message.text.isNotBlank()) "📮 [Tem: ${message.stampTitle}] ${message.text}" else "📮 Đã gửi một con tem: ${message.stampTitle}"
        } else {
            message.text
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(com.mipastudio.memostamp.R.mipmap.ic_launcher)
            .setContentTitle("💬 ${message.senderName}")
            .setContentText(previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(("msg_" + message.senderId).hashCode(), builder.build())
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun sendFriendRequestNotification(
        context: Context,
        senderName: String,
        senderId: String
    ) {
        createNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "FRIENDS")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("req_" + senderId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🤝 Lời mời kết bạn mới"
        val body = "$senderName đã gửi cho bạn lời mời kết bạn. Chạm để xem và kết nối!"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INTERACTIONS)
            .setSmallIcon(com.mipastudio.memostamp.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(("req_" + senderId).hashCode(), builder.build())
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun sendFriendAcceptedNotification(
        context: Context,
        friendName: String,
        friendId: String
    ) {
        createNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "CHAT")
            putExtra("TARGET_USER_ID", friendId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("accepted_" + friendId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🎉 Đã kết nối bạn bè!"
        val body = "$friendName đã chấp nhận lời mời kết bạn của bạn. Gửi ngay một bức thư tem chào hỏi!"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INTERACTIONS)
            .setSmallIcon(com.mipastudio.memostamp.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(("accepted_" + friendId).hashCode(), builder.build())
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun sendTradeNotification(
        context: Context,
        title: String = "💌 Nhận được tem bưu thiếp mới!",
        body: String = "Một người bạn vừa gửi cho bạn một bức tem kỷ niệm. Chạm để mở!"
    ) {
        createNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "FRIENDS")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INTERACTIONS)
            .setSmallIcon(com.mipastudio.memostamp.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
