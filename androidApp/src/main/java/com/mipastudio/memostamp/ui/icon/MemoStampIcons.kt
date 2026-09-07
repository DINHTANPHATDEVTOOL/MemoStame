package com.mipastudio.memostamp.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mipastudio.memostamp.domain.model.MemoStampLegacyMigration

/**
 * Custom vector iconography designed specifically for MemoStamp identity.
 */
object MemoStampCustomIcons {

    /**
     * Vintage perforated postage stamp outline.
     */
    val Stamp: ImageVector by lazy {
        ImageVector.Builder(
            name = "CustomStamp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                // Perforated stamp outline geometry
                moveTo(20f, 4f)
                lineTo(19f, 4f)
                curveTo(19f, 4.55f, 18.55f, 5f, 18f, 5f)
                curveTo(17.45f, 5f, 17f, 4.55f, 17f, 4f)
                lineTo(15f, 4f)
                curveTo(15f, 4.55f, 14.55f, 5f, 14f, 5f)
                curveTo(13.45f, 5f, 13f, 4.55f, 13f, 4f)
                lineTo(11f, 4f)
                curveTo(11f, 4.55f, 10.55f, 5f, 10f, 5f)
                curveTo(9.45f, 5f, 9f, 4.55f, 9f, 4f)
                lineTo(7f, 4f)
                curveTo(7f, 4.55f, 6.55f, 5f, 6f, 5f)
                curveTo(5.45f, 5f, 5f, 4.55f, 5f, 4f)
                lineTo(4f, 4f)
                curveTo(3.45f, 4f, 3f, 4.45f, 3f, 5f)
                lineTo(3f, 6f)
                curveTo(3.55f, 6f, 4f, 6.45f, 4f, 7f)
                curveTo(4f, 7.55f, 3.55f, 8f, 3f, 8f)
                lineTo(3f, 10f)
                curveTo(3.55f, 10f, 4f, 10.45f, 4f, 11f)
                curveTo(4f, 11.55f, 3.55f, 12f, 3f, 12f)
                lineTo(3f, 14f)
                curveTo(3.55f, 14f, 4f, 14.45f, 4f, 15f)
                curveTo(4f, 15.55f, 3.55f, 16f, 3f, 16f)
                lineTo(3f, 18f)
                curveTo(3.55f, 18f, 4f, 18.45f, 4f, 19f)
                curveTo(4f, 19.55f, 3.55f, 20f, 3f, 20f)
                lineTo(4f, 20f)
                curveTo(4.45f, 20f, 5f, 19.55f, 5f, 19f)
                curveTo(5f, 18.45f, 5.45f, 18f, 6f, 18f)
                curveTo(6.55f, 18f, 7f, 18.45f, 7f, 19f)
                lineTo(9f, 19f)
                curveTo(9f, 18.45f, 9.45f, 18f, 10f, 18f)
                curveTo(10.55f, 18f, 11f, 18.45f, 11f, 19f)
                lineTo(13f, 19f)
                curveTo(13f, 18.45f, 13.45f, 18f, 14f, 18f)
                curveTo(14.55f, 18f, 15f, 18.45f, 15f, 19f)
                lineTo(17f, 19f)
                curveTo(17f, 18.45f, 17.45f, 18f, 18f, 18f)
                curveTo(18.55f, 18f, 19f, 18.45f, 19f, 19f)
                lineTo(20f, 20f)
                curveTo(20.55f, 20f, 21f, 19.55f, 21f, 19f)
                lineTo(21f, 17f)
                curveTo(20.45f, 17f, 20f, 16.55f, 20f, 16f)
                curveTo(20f, 15.45f, 20.45f, 15f, 21f, 15f)
                lineTo(21f, 13f)
                curveTo(20.45f, 13f, 20f, 12.55f, 20f, 12f)
                curveTo(20f, 11.45f, 20.45f, 11f, 21f, 11f)
                lineTo(21f, 9f)
                curveTo(20.45f, 9f, 20f, 8.55f, 20f, 8f)
                curveTo(20f, 7.45f, 20.45f, 7f, 21f, 7f)
                lineTo(21f, 5f)
                curveTo(21f, 4.45f, 20.55f, 4f, 20f, 4f)
                close()

                // Inner picture inset frame
                moveTo(18f, 7f)
                lineTo(6f, 7f)
                lineTo(6f, 16f)
                lineTo(18f, 16f)
                close()
            }
        }.build()
    }

    /**
     * Circular postmark cancellation mark with wave lines.
     */
    val Postmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "CustomPostmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f
            ) {
                // Circular seal
                moveTo(8f, 4f)
                curveTo(4.69f, 4f, 2f, 6.69f, 2f, 10f)
                curveTo(2f, 13.31f, 4.69f, 16f, 8f, 16f)
                curveTo(11.31f, 16f, 14f, 13.31f, 14f, 10f)
                curveTo(14f, 6.69f, 11.31f, 4f, 8f, 4f)
                close()
                moveTo(8f, 14.5f)
                curveTo(5.51f, 14.5f, 3.5f, 12.49f, 3.5f, 10f)
                curveTo(3.5f, 7.51f, 5.51f, 5.5f, 8f, 5.5f)
                curveTo(10.49f, 5.5f, 12.5f, 7.51f, 12.5f, 10f)
                curveTo(12.5f, 12.49f, 10.49f, 14.5f, 8f, 14.5f)
                close()

                // Center star or date mark
                moveTo(8f, 8f)
                lineTo(8.6f, 9.5f)
                lineTo(10.2f, 9.5f)
                lineTo(8.9f, 10.4f)
                lineTo(9.4f, 12f)
                lineTo(8f, 11f)
                lineTo(6.6f, 12f)
                lineTo(7.1f, 10.4f)
                lineTo(5.8f, 9.5f)
                lineTo(7.4f, 9.5f)
                close()

                // Cancellation wavy flight lines
                moveTo(16f, 7f)
                curveTo(17f, 6.5f, 18.5f, 6.5f, 20f, 7f)
                lineTo(22f, 7f)
                lineTo(22f, 8.2f)
                lineTo(20f, 8.2f)
                curveTo(18.5f, 7.7f, 17f, 7.7f, 16f, 8.2f)
                close()

                moveTo(15f, 10f)
                curveTo(16.5f, 9.5f, 18.5f, 9.5f, 20f, 10f)
                lineTo(22f, 10f)
                lineTo(22f, 11.2f)
                lineTo(20f, 11.2f)
                curveTo(18.5f, 10.7f, 16.5f, 10.7f, 15f, 11.2f)
                close()

                moveTo(16f, 13f)
                curveTo(17f, 12.5f, 18.5f, 12.5f, 20f, 13f)
                lineTo(22f, 13f)
                lineTo(22f, 14.2f)
                lineTo(20f, 14.2f)
                curveTo(18.5f, 13.7f, 17f, 13.7f, 16f, 14.2f)
                close()
            }
        }.build()
    }

    /**
     * Stamp with reply arrow icon.
     */
    val ReplyStamp: ImageVector by lazy {
        ImageVector.Builder(
            name = "CustomReplyStamp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f
            ) {
                // Upper-right stamp
                moveTo(10f, 2f)
                lineTo(21f, 2f)
                curveTo(21.55f, 2f, 22f, 2.45f, 22f, 3f)
                lineTo(22f, 14f)
                curveTo(22f, 14.55f, 21.55f, 15f, 21f, 15f)
                lineTo(14f, 15f)
                lineTo(14f, 13f)
                lineTo(20f, 13f)
                lineTo(20f, 4f)
                lineTo(12f, 4f)
                lineTo(12f, 7f)
                lineTo(10f, 7f)
                close()

                // Reply arrow curving up towards the stamp
                moveTo(9f, 9f)
                lineTo(9f, 13f)
                curveTo(13f, 13f, 15f, 15.5f, 16f, 19f)
                curveTo(14.5f, 16.5f, 12.5f, 15.5f, 9f, 15.5f)
                lineTo(9f, 19.5f)
                lineTo(3f, 14.25f)
                close()
            }
        }.build()
    }
}

/**
 * Resolves a semantic icon key to the corresponding Android Compose ImageVector.
 */
object MemoStampIcons {

    fun resolve(rawKey: String?): ImageVector {
        val mappedKey = MemoStampLegacyMigration.mapLegacyCollectionIcon(rawKey)

        return when (mappedKey.lowercase()) {
            "location" -> Icons.Outlined.LocationOn
            "search" -> Icons.Outlined.Search
            "camera" -> Icons.Outlined.CameraAlt
            "gallery" -> Icons.Outlined.PhotoLibrary
            "share" -> Icons.Outlined.Share
            "favorite" -> Icons.Outlined.FavoriteBorder
            "favorite_filled", "heart" -> Icons.Outlined.Favorite
            "comment" -> Icons.Outlined.ChatBubbleOutline
            "chat" -> Icons.Outlined.Chat
            "settings" -> Icons.Outlined.Settings
            "notification" -> Icons.Outlined.Notifications
            "lock" -> Icons.Outlined.Lock
            "privacy" -> Icons.Outlined.Security
            "delete" -> Icons.Outlined.Delete
            "edit" -> Icons.Outlined.Edit
            "check" -> Icons.Outlined.Check
            "retry" -> Icons.Outlined.Refresh
            "success" -> Icons.Outlined.CheckCircle
            "warning" -> Icons.Outlined.Warning
            "error" -> Icons.Outlined.ErrorOutline
            "info" -> Icons.Outlined.Info
            "cloud" -> Icons.Outlined.Cloud
            "calendar" -> Icons.Outlined.CalendarToday
            "map" -> Icons.Outlined.Map
            "qr_code" -> Icons.Outlined.QrCode
            "report" -> Icons.Outlined.Flag
            "block" -> Icons.Outlined.Block
            "close" -> Icons.Outlined.Close
            "back" -> Icons.Outlined.Close
            "filter" -> Icons.Outlined.FilterList
            "more" -> Icons.Outlined.MoreVert

            // Postal & Brand
            "stamp" -> MemoStampCustomIcons.Stamp
            "postmark" -> MemoStampCustomIcons.Postmark
            "mail" -> Icons.Outlined.Mail
            "reply_stamp" -> MemoStampCustomIcons.ReplyStamp
            "album_book" -> Icons.Outlined.MenuBook
            "collection" -> Icons.Outlined.Folder
            "passport" -> Icons.Outlined.AssignmentInd
            "trade" -> Icons.Outlined.Sync
            "friends" -> Icons.Outlined.People
            "profile" -> Icons.Outlined.Person
            "theme" -> Icons.Outlined.Palette

            // Categories & Topics
            "travel" -> Icons.Outlined.Flight
            "cafe" -> Icons.Outlined.LocalCafe
            "beach" -> Icons.Outlined.BeachAccess
            "education" -> Icons.Outlined.School
            "nature" -> Icons.Outlined.Forest
            "art" -> Icons.Outlined.Palette
            "special" -> Icons.Outlined.AutoAwesome
            "flower" -> Icons.Outlined.LocalFlorist
            "food" -> Icons.Outlined.Restaurant
            "lifestyle" -> Icons.Outlined.Park
            "celebration" -> Icons.Outlined.Celebration

            // Moods
            "happy" -> Icons.Outlined.SentimentSatisfied
            "love" -> Icons.Outlined.Favorite
            "chill" -> Icons.Outlined.Coffee
            "excited" -> Icons.Outlined.LocalFireDepartment
            "nostalgic" -> Icons.Outlined.History
            "peaceful" -> Icons.Outlined.Spa

            else -> Icons.Outlined.CollectionsBookmark
        }
    }

    fun resolve(iconKey: com.mipastudio.memostamp.domain.model.MemoStampIconKey): ImageVector = resolve(iconKey.key)
}

/**
 * Standard MemoStamp Icon Component.
 * Supports theme color inheritance, dark mode, scalable sizes, and localized accessibility.
 */
@Composable
fun MemoStampIcon(
    iconKey: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val vector = MemoStampIcons.resolve(iconKey)
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun MemoStampIcon(
    iconKey: com.mipastudio.memostamp.domain.model.MemoStampIconKey,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MemoStampIcon(
        iconKey = iconKey.key,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
