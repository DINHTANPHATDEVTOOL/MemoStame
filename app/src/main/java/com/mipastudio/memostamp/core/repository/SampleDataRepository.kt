package com.mipastudio.memostamp.core.repository

import com.mipastudio.memostamp.core.model.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object SampleDataRepository {

    val currentUser = User(
        id = "user_phat",
        username = "phat_creative",
        displayName = "Phat ✦",
        avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
        bio = "Capturing quiet afternoons & postage moments.",
        stats = UserStats(memories = 142, collections = 8, friends = 24, receivedStamps = 37)
    )

    val sampleStamps: SnapshotStateList<Stamp> = mutableStateListOf(
        Stamp(
            id = "stamp_1",
            stampNumber = "#DL-2026-00192",
            title = "Da Lat Night",
            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
            creatorId = "user_phat",
            creatorName = "Phat",
            creatorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            ownerId = "user_phat",
            ownerName = "Phat",
            createdDate = "12 Aug 2026",
            memoryDate = "12.08.26",
            location = "Da Lat, Vietnam",
            caption = "Một buổi chiều không có kế hoạch.",
            type = StampType.PERSONAL,
            templateId = "classic_post",
            borderStyle = "perforated",
            collectionId = "col_travel",
            collectionName = "Summer 2026",
            edition = "Original #003",
            elements = listOf(
                StampElement("el_1", "text", "DA LAT • 12.08.26", 0.5f, 0.82f, rotation = -1f, colorHex = "#D94E41"),
                StampElement("el_2", "badge", "MEMORY SERIES", 0.5f, 0.89f, colorHex = "#2B5B84"),
                StampElement("el_3", "sticker", "✿", 0.15f, 0.15f, rotation = 12f, colorHex = "#D4A340")
            ),
            tags = listOf("Dalat", "Trip", "Friends"),
            tradeHistory = listOf(
                TradeRecord("Phat", "Phat", "12 Aug 2026", "Created initial stamp")
            )
        ),
        Stamp(
            id = "stamp_2",
            stampNumber = "#SG-2026-00088",
            title = "Saigon Sunset Coffee",
            imageUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600",
            creatorId = "user_minh",
            creatorName = "Minh",
            creatorAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
            ownerId = "user_phat",
            ownerName = "Phat",
            createdDate = "10 Aug 2026",
            memoryDate = "10.08.26",
            location = "District 1, HCMC",
            caption = "Cà phê bệt chiều thứ 7 cùng Minh.",
            type = StampType.FRIEND,
            templateId = "airmail",
            borderStyle = "perforated",
            collectionId = "col_friends",
            collectionName = "Best Friends",
            edition = "Friend Edition #012",
            elements = listOf(
                StampElement("el_10", "text", "SAIGON COFFEE", 0.5f, 0.85f, colorHex = "#2B5B84"),
                StampElement("el_11", "badge", "AIR MAIL ✦", 0.8f, 0.12f, colorHex = "#D94E41")
            ),
            tradeHistory = listOf(
                TradeRecord("Minh", "Phat", "11 Aug 2026", "Gifted memory via Airmail envelope")
            )
        ),
        Stamp(
            id = "stamp_3",
            stampNumber = "#VT-2026-00304",
            title = "Vung Tau Beach Sunrise",
            imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
            creatorId = "user_phat",
            creatorName = "Phat",
            creatorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            ownerId = "user_phat",
            ownerName = "Phat",
            createdDate = "02 Aug 2026",
            memoryDate = "02.08.26",
            location = "Vung Tau, Vietnam",
            caption = "Đón bình minh 5h30 sáng.",
            type = StampType.SHARED_MEMORY,
            templateId = "polaroid",
            borderStyle = "dashed",
            collectionId = "col_travel",
            collectionName = "Summer 2026",
            edition = "Shared Edition #001"
        ),
        Stamp(
            id = "stamp_4",
            stampNumber = "#GRAD-2026-0001",
            title = "Graduation Ceremony",
            imageUrl = "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=600",
            creatorId = "user_phat",
            creatorName = "Phat",
            creatorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
            ownerId = "user_phat",
            ownerName = "Phat",
            createdDate = "15 Jul 2026",
            memoryDate = "15.07.26",
            location = "University Hall",
            caption = "Hành trình 4 năm chính thức khép lại.",
            type = StampType.EVENT,
            templateId = "passport",
            borderStyle = "double_line",
            collectionId = "col_events",
            collectionName = "Milestones",
            edition = "Official Milestone #001"
        )
    )

    val sampleCollections = listOf(
        StampCollection(
            id = "col_travel",
            title = "Summer 2026 ✈",
            description = "Hành trình rong đuổi những ngày hè rực rỡ.",
            coverImageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
            iconEmoji = "✈",
            stampCount = 12,
            totalCapacity = 20,
            category = "Travel"
        ),
        StampCollection(
            id = "col_friends",
            title = "Best Friends ♡",
            description = "Tem nhận được từ hội bạn thân.",
            coverImageUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600",
            iconEmoji = "♡",
            stampCount = 8,
            totalCapacity = 15,
            category = "Social"
        ),
        StampCollection(
            id = "col_events",
            title = "Life Milestones 🎓",
            description = "Những sự kiện quan trọng trong đời.",
            coverImageUrl = "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=600",
            iconEmoji = "🎓",
            stampCount = 4,
            totalCapacity = 10,
            category = "Milestone"
        )
    )

    val sampleFriends = listOf(
        User("user_minh", "minh_postale", "Minh Nguyen ✦", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150", "Coffee & Vintage stamps collector.", UserStats(84, 5, 18, 22), isFriend = true),
        User("user_huy", "huy_wanderer", "Huy Tran ✈", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150", "Collecting memories across Asia.", UserStats(110, 7, 30, 45), isFriend = true),
        User("user_linh", "linh_sakura", "Linh Le ✿", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150", "Doodles, flowers & soft daylight.", UserStats(95, 6, 21, 19), isFriend = true),
        User("user_mai", "mai_scrapbook", "Mai Pham 📮", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150", "Scrapbook enthusiast & traveler.", UserStats(60, 4, 15, 12), isFriend = false)
    )

    val sampleTrades = mutableStateListOf(
        TradeRequest(
            tradeId = "tr_101",
            senderId = "user_minh",
            senderName = "Minh Nguyen",
            senderAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
            receiverId = "user_phat",
            offeredStamp = Stamp(
                id = "stamp_offer_1",
                stampNumber = "#HN-2026-00441",
                title = "Hanoi Autumn Rain",
                imageUrl = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=600",
                creatorId = "user_minh",
                creatorName = "Minh",
                ownerId = "user_minh",
                ownerName = "Minh",
                createdDate = "05 Aug 2026",
                memoryDate = "05.08.26",
                location = "Hanoi, Vietnam",
                caption = "Mùa thu Hà Nội thoảng mùi hoa sữa.",
                type = StampType.PERSONAL,
                templateId = "vintage",
                borderStyle = "perforated"
            ),
            requestedStamp = sampleStamps[0],
            status = "PENDING",
            createdAt = "2 hours ago"
        )
    )

    val samplePassport = MemoryPassport(
        ownerName = "Phat ✦",
        username = "@phat_creative",
        avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
        stats = UserStats(memories = 142, collections = 8, friends = 24, receivedStamps = 37),
        visas = listOf(
            PassportVisa("Da Lat", "12 Aug 2026", "✈ Travel", "#DL-2026-00192"),
            PassportVisa("Saigon", "10 Aug 2026", "☕ Coffee", "#SG-2026-00088"),
            PassportVisa("Vung Tau", "02 Aug 2026", "🏖 Beach", "#VT-2026-00304"),
            PassportVisa("University", "15 Jul 2026", "🎓 Graduation", "#GRAD-2026-0001"),
            PassportVisa("Tet 2026", "10 Feb 2026", "🧧 Festival", "#TET-2026-0012")
        )
    )

    fun addStamp(stamp: Stamp) {
        sampleStamps.add(0, stamp)
    }
}
