package com.mipastudio.memostamp.core.notification

import com.mipastudio.memostamp.domain.model.DirectMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoStampNotificationManagerTest {

    @Test
    fun testNotificationChannelConstants() {
        assertEquals("memostamp_interactions_channel", MemoStampNotificationManager.CHANNEL_ID_INTERACTIONS)
        assertEquals("memostamp_messages_channel", MemoStampNotificationManager.CHANNEL_ID_MESSAGES)
        assertEquals("MemoStamp Hoạt động & Tương tác", MemoStampNotificationManager.CHANNEL_NAME_INTERACTIONS)
        assertEquals("MemoStamp Tin nhắn trực tiếp", MemoStampNotificationManager.CHANNEL_NAME_MESSAGES)
    }

    @Test
    fun testMessageNotificationPreviewFormatting_withStampTitleAndText() {
        val msg = DirectMessage(
            id = "msg_001",
            senderId = "sender_1",
            senderName = "Minh Anh",
            senderAvatar = "",
            recipientId = "recipient_1",
            recipientName = "Phat Nguyen",
            recipientAvatar = "",
            text = "Kỷ niệm tuyệt vời!",
            stampId = "stamp_123",
            stampTitle = "Đà Lạt Mộng Mơ",
            stampImageUrl = "http://example.com/img.jpg",
            stampLocation = "Đà Lạt",
            createdAt = 1000L,
            isRead = false
        )

        val previewText = if (!msg.stampTitle.isNullOrBlank()) {
            if (msg.text.isNotBlank()) "📮 [Tem: ${msg.stampTitle}] ${msg.text}" else "📮 Đã gửi một con tem: ${msg.stampTitle}"
        } else {
            msg.text
        }

        assertEquals("📮 [Tem: Đà Lạt Mộng Mơ] Kỷ niệm tuyệt vời!", previewText)
    }

    @Test
    fun testMessageNotificationPreviewFormatting_withStampTitleOnly() {
        val msg = DirectMessage(
            id = "msg_002",
            senderId = "sender_1",
            senderName = "Minh Anh",
            senderAvatar = "",
            recipientId = "recipient_1",
            recipientName = "Phat Nguyen",
            recipientAvatar = "",
            text = "",
            stampId = "stamp_123",
            stampTitle = "Biển Nha Trang",
            stampImageUrl = "http://example.com/img.jpg",
            stampLocation = "Nha Trang",
            createdAt = 2000L,
            isRead = false
        )

        val previewText = if (!msg.stampTitle.isNullOrBlank()) {
            if (msg.text.isNotBlank()) "📮 [Tem: ${msg.stampTitle}] ${msg.text}" else "📮 Đã gửi một con tem: ${msg.stampTitle}"
        } else {
            msg.text
        }

        assertEquals("📮 Đã gửi một con tem: Biển Nha Trang", previewText)
    }

    @Test
    fun testMessageNotificationPreviewFormatting_withTextOnly() {
        val msg = DirectMessage(
            id = "msg_003",
            senderId = "sender_1",
            senderName = "Minh Anh",
            senderAvatar = "",
            recipientId = "recipient_1",
            recipientName = "Phat Nguyen",
            recipientAvatar = "",
            text = "Xin chào bạn!",
            stampId = null,
            stampTitle = null,
            stampImageUrl = null,
            stampLocation = null,
            createdAt = 3000L,
            isRead = false
        )

        val previewText = if (!msg.stampTitle.isNullOrBlank()) {
            if (msg.text.isNotBlank()) "📮 [Tem: ${msg.stampTitle}] ${msg.text}" else "📮 Đã gửi một con tem: ${msg.stampTitle}"
        } else {
            msg.text
        }

        assertEquals("Xin chào bạn!", previewText)
    }
}
