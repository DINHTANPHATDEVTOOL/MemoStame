package com.mipastudio.memostamp.core.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mipastudio.memostamp.MainActivity
import com.mipastudio.memostamp.R

/**
 * Android Firebase Cloud Messaging background service.
 * Handles incoming push messages when the app is in background or terminated,
 * filters duplicate notifications via PushEventDeduper, validates safe routes,
 * and launches MainActivity for cloud data reconciliation.
 */
class MemoStampFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Zero token logging
        PushTokenManager.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val eventId = data["event_id"] ?: remoteMessage.messageId ?: ""

        // 1. Deduplication: Check if this event was already displayed (e.g., via Supabase Realtime)
        if (!PushEventDeduper.shouldNotify(eventId)) {
            return
        }

        // 2. Strict Route Validation: Reject arbitrary or unsafe target screens
        val rawRoute = data["route"]?.trim()?.uppercase()
        val targetRoute = when (rawRoute) {
            "CHAT" -> "CHAT"
            "FRIENDS" -> "FRIENDS"
            else -> "FRIENDS" // Fallback to safe known screen
        }

        val targetUserId = data["target_user_id"]?.trim() ?: data["actor_id"]?.trim()

        // 3. Prepare display title and body from payload
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: if (targetRoute == "CHAT") "💬 Tin nhắn mới" else "🤝 Lời mời kết bạn mới"

        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: if (targetRoute == "CHAT") "Bạn có một tin nhắn mới từ bạn bè." else "Bạn có một tương tác mới trên MemoStamp."

        // 4. Ensure notification channels exist
        MemoStampNotificationManager.createNotificationChannels(applicationContext)

        // 5. Build Intent with safe routing extras
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", targetRoute)
            if (!targetUserId.isNullOrBlank()) {
                putExtra("TARGET_USER_ID", targetUserId)
            }
        }

        val channelId = if (targetRoute == "CHAT") {
            MemoStampNotificationManager.CHANNEL_ID_MESSAGES
        } else {
            MemoStampNotificationManager.CHANNEL_ID_INTERACTIONS
        }

        val requestCode = if (!targetUserId.isNullOrBlank()) {
            ("push_" + targetRoute + "_" + targetUserId).hashCode()
        } else {
            ("push_" + targetRoute + "_" + eventId).hashCode()
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (targetRoute == "CHAT") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            notificationManager.notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Missing notification permission on Android 13+
        }
    }
}
