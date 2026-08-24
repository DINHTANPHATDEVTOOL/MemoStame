package com.mipastudio.memostamp.core.location

import java.text.Normalizer
import java.util.regex.Pattern
import kotlin.math.*

data class CandidatePlace(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String, // LANDMARK, CAFE, HERITAGE, NATURE, STREET, RESTAURANT
    val popularity: Int, // 1 - 100
    val rating: String,
    val stampTitleSuggestion: String,
    val description: String,
    val keywords: List<String> = emptyList()
)

data class RankedPlaceResult(
    val place: CandidatePlace,
    val distanceMeters: Double?,
    val distanceFormatted: String?,
    val textScore: Double,
    val distanceScore: Double,
    val popularityScore: Double,
    val totalScore: Double
)

object GoogleMapsRankingEngine {

    private val DIACRITICS_PATTERN: Pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

    /**
     * Normalize string: lowercase, remove Vietnamese diacritics, trim spaces, remove punctuation.
     * Example: "Phở Thìn 13 Lò Đúc" -> "pho thin 13 lo duc"
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val lower = input.lowercase().trim()
        val nfdNormalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITICS_PATTERN.matcher(nfdNormalized).replaceAll("")
        return withoutDiacritics
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Calculate Haversine distance in meters between two GPS coordinates.
     * d = 2R * asin(sqrt(sin^2(dLat/2) + cos(lat1)*cos(lat2)*sin^2(dLon/2)))
     */
    fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun formatDistance(meters: Double?): String? {
        if (meters == null) return null
        return if (meters < 1000) {
            "${meters.roundToInt()}m"
        } else {
            val km = meters / 1000.0
            String.format(java.util.Locale.US, "%.1f km", km)
        }
    }

    /**
     * Calculate fuzzy string similarity (0.0 to 100.0) with prefix, token, and Levenshtein matching.
     */
    fun calculateTextScore(query: String, place: CandidatePlace): Double {
        val normQuery = normalize(query)
        if (normQuery.isBlank()) return 50.0

        val normName = normalize(place.name)
        val normAddress = normalize(place.address)
        val normDesc = normalize(place.description)
        val normKeywords = place.keywords.map { normalize(it) }

        // 1. Exact match
        if (normName == normQuery) return 100.0

        // 2. Prefix match (e.g. "starb" -> "Starbucks")
        if (normName.startsWith(normQuery)) {
            val coverage = normQuery.length.toDouble() / normName.length.coerceAtLeast(1)
            return 85.0 + (coverage * 15.0)
        }

        // 3. Keyword / Category exact prefix
        for (kw in normKeywords) {
            if (kw.startsWith(normQuery) || normQuery.startsWith(kw)) {
                return 88.0
            }
        }

        // 4. Token substring match (e.g. "thin" in "Pho Thin")
        val nameTokens = normName.split(" ")
        val queryTokens = normQuery.split(" ")
        val matchedTokens = queryTokens.count { qToken ->
            nameTokens.any { nToken -> nToken.startsWith(qToken) || nToken.contains(qToken) }
        }
        if (matchedTokens > 0) {
            val tokenRatio = matchedTokens.toDouble() / queryTokens.size.coerceAtLeast(1)
            return 60.0 + (tokenRatio * 25.0)
        }

        // 5. Containment in address or description
        if (normAddress.contains(normQuery) || normDesc.contains(normQuery)) {
            return 65.0
        }

        // 6. Fuzzy Levenshtein Distance for typo tolerance
        val minNameTokenDist = nameTokens.minOfOrNull { levenshtein(normQuery, it) } ?: 99
        if (minNameTokenDist <= 2 && normQuery.length >= 3) {
            return 55.0 - (minNameTokenDist * 10.0)
        }

        return 0.0
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    /**
     * Compute Distance Score (0.0 to 100.0)
     * Nearby places get high scores. Within 2km gets strong boost.
     */
    fun calculateDistanceScore(distanceMeters: Double?): Double {
        if (distanceMeters == null) return 50.0
        return when {
            distanceMeters <= 200.0 -> 100.0
            distanceMeters <= 500.0 -> 95.0
            distanceMeters <= 1000.0 -> 90.0
            distanceMeters <= 2000.0 -> 80.0 - ((distanceMeters - 1000.0) / 1000.0 * 20.0) // 80 -> 60
            distanceMeters <= 5000.0 -> 55.0 - ((distanceMeters - 2000.0) / 3000.0 * 25.0) // 55 -> 30
            distanceMeters <= 15000.0 -> 30.0 - ((distanceMeters - 5000.0) / 10000.0 * 20.0) // 30 -> 10
            else -> max(0.0, 10.0 - (distanceMeters / 50000.0))
        }
    }

    /**
     * Rank Candidate Places using Google Maps Multi-Factor Ranking Algorithm:
     * TotalScore = w_text * TextScore + w_distance * DistanceScore + w_popularity * PopularityScore
     */
    fun rankPlaces(
        query: String,
        userLat: Double?,
        userLng: Double?,
        maxRadiusMeters: Double = 2000.0,
        filterCategory: String = "ALL",
        candidates: List<CandidatePlace> = PLACES_DATABASE
    ): List<RankedPlaceResult> {
        val hasGps = userLat != null && userLng != null
        val isQueryEmpty = query.isBlank()

        // Dynamic weights based on user intent
        val (wText, wDist, wPop) = when {
            isQueryEmpty && hasGps -> Triple(0.05, 0.75, 0.20) // GPS Discovery (nearby 2km is king)
            query.length <= 4 && hasGps -> Triple(0.45, 0.40, 0.15) // Short prefix matching (e.g. "star", "pho")
            hasGps -> Triple(0.60, 0.25, 0.15) // Explicit search with GPS context
            else -> Triple(0.75, 0.00, 0.25) // No GPS (text + popularity only)
        }

        val results = mutableListOf<RankedPlaceResult>()

        for (place in candidates) {
            // Category filter
            if (filterCategory != "ALL" && place.category != filterCategory) {
                continue
            }

            val distanceMeters = if (hasGps) {
                haversineDistanceMeters(userLat!!, userLng!!, place.latitude, place.longitude)
            } else null

            // If empty query and GPS active, strictly prioritize within max radius (e.g. 2km)
            if (isQueryEmpty && hasGps && maxRadiusMeters > 0) {
                if (distanceMeters != null && distanceMeters > maxRadiusMeters * 3.0) {
                    // Allow soft expansion if very few candidates, but downrank distant places
                }
            }

            val textScore = if (isQueryEmpty) 70.0 else calculateTextScore(query, place)
            // Filter out non-matching text if user explicitly typed a non-empty search query
            if (!isQueryEmpty && textScore < 30.0) {
                continue
            }

            val distScore = calculateDistanceScore(distanceMeters)
            val popScore = place.popularity.toDouble().coerceIn(0.0, 100.0)

            val totalScore = (wText * textScore) + (wDist * distScore) + (wPop * popScore)

            results.add(
                RankedPlaceResult(
                    place = place,
                    distanceMeters = distanceMeters,
                    distanceFormatted = formatDistance(distanceMeters),
                    textScore = textScore,
                    distanceScore = distScore,
                    popularityScore = popScore,
                    totalScore = totalScore
                )
            )
        }

        // Sort descending by total score
        return results.sortedByDescending { it.totalScore }
    }

    // Comprehensive initial database of iconic landmarks, cafes, heritage, and scenic spots across Vietnam with verified coordinates
    val PLACES_DATABASE: List<CandidatePlace> = listOf(
        // === ĐÀ LẠT ===
        CandidatePlace(
            id = "dl_1",
            name = "Quảng trường Lâm Viên",
            address = "Đường Trần Quốc Toản, Phường 10, TP. Đà Lạt",
            latitude = 11.9367,
            longitude = 108.4447,
            category = "LANDMARK",
            popularity = 98,
            rating = "4.8★",
            stampTitleSuggestion = "Nụ Hoa Atisô Đà Lạt",
            description = "Biểu tượng nụ hoa Atisô kính màu và bông hoa dã quỳ khổng lồ bên hồ Xuân Hương.",
            keywords = listOf("lam vien", "atiso", "quang truong", "da quy", "ho xuan huong")
        ),
        CandidatePlace(
            id = "dl_2",
            name = "Hồ Xuân Hương",
            address = "Trung tâm TP. Đà Lạt, Lâm Đồng",
            latitude = 11.9404,
            longitude = 108.4452,
            category = "NATURE",
            popularity = 99,
            rating = "4.9★",
            stampTitleSuggestion = "Sương Mù Hồ Xuân Hương",
            description = "Trái tim lãng mạn của thành phố ngàn hoa với làn sương mờ ảo buổi sớm.",
            keywords = listOf("ho xuan huong", "suong mu", "trung tam da lat", "dap ong dao")
        ),
        CandidatePlace(
            id = "dl_3",
            name = "Tiệm Cà Phê Túi Mơ To",
            address = "Hẻm 31 Sào Nam, Phường 11, TP. Đà Lạt",
            latitude = 11.9482,
            longitude = 108.4776,
            category = "CAFE",
            popularity = 96,
            rating = "4.7★",
            stampTitleSuggestion = "Cúc Họa Mi Mơ Màng",
            description = "Vườn cúc họa mi trắng tinh khôi ngắm trọn thung lũng ánh sáng về đêm.",
            keywords = listOf("tui mo to", "cafe", "cuc hoa mi", "sao nam", "thung lung den")
        ),
        CandidatePlace(
            id = "dl_4",
            name = "Chợ Đêm Đà Lạt (Chợ Âm Phủ)",
            address = "Đường Nguyễn Thị Minh Khai, Phường 1, TP. Đà Lạt",
            latitude = 11.9429,
            longitude = 108.4373,
            category = "STREET",
            popularity = 97,
            rating = "4.6★",
            stampTitleSuggestion = "Đêm Lạnh Phố Chợ",
            description = "Thiên đường ẩm thực đêm với bánh tráng nướng giòn tan và sữa đậu nành nóng.",
            keywords = listOf("cho dem", "cho am phu", "banh trang nuong", "sua dau nanh", "am thuc")
        ),
        CandidatePlace(
            id = "dl_5",
            name = "Ga Đà Lạt",
            address = "Số 1 Quang Trung, Phường 9, TP. Đà Lạt",
            latitude = 11.9416,
            longitude = 108.4552,
            category = "HERITAGE",
            popularity = 95,
            rating = "4.8★",
            stampTitleSuggestion = "Chuyến Tàu Cổ Đông Dương",
            description = "Nhà ga xe lửa cổ kính nhất Đông Dương với kiến trúc Art Deco mái chóp đặc trưng.",
            keywords = listOf("ga da lat", "nha ga", "tau hoa", "duong sat", "quang trung")
        ),
        CandidatePlace(
            id = "dl_6",
            name = "Dinh III Bảo Đại",
            address = "Số 1 Triệu Việt Vương, Phường 4, TP. Đà Lạt",
            latitude = 11.9304,
            longitude = 108.4298,
            category = "HERITAGE",
            popularity = 92,
            rating = "4.6★",
            stampTitleSuggestion = "Hoàng Triều Cương Thổ",
            description = "Biệt điện nghỉ dưỡng sang trọng của vị hoàng đế cuối cùng triều Nguyễn.",
            keywords = listOf("dinh bao dai", "dinh 3", "trieu viet vuong", "hoang de", "cung dien")
        ),
        CandidatePlace(
            id = "dl_7",
            name = "Quán Cà Phê Cheo Veooo",
            address = "Hẻm 116 Hùng Vương, Phường 11, TP. Đà Lạt",
            latitude = 11.9441,
            longitude = 108.4712,
            category = "CAFE",
            popularity = 93,
            rating = "4.7★",
            stampTitleSuggestion = "Hoàng Hôn Cheo Veooo",
            description = "Góc gỗ mộc mạc ngắm hoàng hôn buông xuống thung lũng thông reo.",
            keywords = listOf("cheo veo", "cheoveooo", "cafe san may", "hung vuong", "hoang hon")
        ),
        CandidatePlace(
            id = "dl_8",
            name = "Trường Cao Đẳng Sư Phạm Đà Lạt",
            address = "Số 29 Yersin, Phường 10, TP. Đà Lạt",
            latitude = 11.9388,
            longitude = 108.4549,
            category = "HERITAGE",
            popularity = 94,
            rating = "4.8★",
            stampTitleSuggestion = "Tháp Chuông Grand Lycée",
            description = "Kiến trúc vòm cong gạch trần độc đáo thế kỷ 20 được hội KTS thế giới công nhận.",
            keywords = listOf("su pham", "lycee yersin", "thap chuong", "gach do", "yersin")
        ),
        CandidatePlace(
            id = "dl_9",
            name = "Cà Phê Lưng Chừng",
            address = "Hẻm 31/8 Hoàng Hoa Thám, Phường 10, TP. Đà Lạt",
            latitude = 11.9325,
            longitude = 108.4623,
            category = "CAFE",
            popularity = 91,
            rating = "4.6★",
            stampTitleSuggestion = "Một Thoáng Lưng Chừng",
            description = "Căn nhà gỗ nhỏ nằm lọt thỏm giữa sườn đồi phủ đầy cây cỏ yên ả.",
            keywords = listOf("lung chung", "cafe lung chung", "hoang hoa tham", "rung thong")
        ),
        CandidatePlace(
            id = "dl_10",
            name = "Tiệm Bánh Cối Xay Gió",
            address = "Khu Hòa Bình, Phường 1, TP. Đà Lạt",
            latitude = 11.9421,
            longitude = 108.4379,
            category = "STREET",
            popularity = 95,
            rating = "4.6★",
            stampTitleSuggestion = "Bức Tường Vàng Kỷ Niệm",
            description = "Bức tường vàng cổ điển điểm check-in thanh xuân quen thuộc của giới trẻ.",
            keywords = listOf("coi xay gio", "tiem banh", "tuong vang", "hoa binh")
        ),

        // === SÀI GÒN / TP. HỒ CHÍ MINH ===
        CandidatePlace(
            id = "sg_1",
            name = "Bưu Điện Trung Tâm Sài Gòn",
            address = "Số 2 Công xã Paris, Phường Bến Nghé, Quận 1, TP.HCM",
            latitude = 10.7798,
            longitude = 106.6998,
            category = "HERITAGE",
            popularity = 99,
            rating = "4.8★",
            stampTitleSuggestion = "Bưu Chính Sài Gòn 1891",
            description = "Công trình bưu điện kiến trúc Gothic vòm cổ kính nổi tiếng bậc nhất Đông Nam Á.",
            keywords = listOf("buu dien", "cong xa paris", "quan 1", "buu chinh", "saigon post")
        ),
        CandidatePlace(
            id = "sg_2",
            name = "Nhà Thờ Đức Bà Sài Gòn",
            address = "Số 1 Công xã Paris, Phường Bến Nghé, Quận 1, TP.HCM",
            latitude = 10.7797,
            longitude = 106.6990,
            category = "HERITAGE",
            popularity = 99,
            rating = "4.9★",
            stampTitleSuggestion = "Vương Cung Thánh Đường",
            description = "Kiến trúc Roman pha lẫn Gothic tuyệt mỹ xây bằng gạch Marseille đỏ tươi.",
            keywords = listOf("nha tho duc ba", "cathedral", "cong xa paris", "gach do")
        ),
        CandidatePlace(
            id = "sg_3",
            name = "Chợ Bến Thành",
            address = "Đường Lê Lợi, Phường Bến Thành, Quận 1, TP.HCM",
            latitude = 10.7725,
            longitude = 106.6980,
            category = "HERITAGE",
            popularity = 98,
            rating = "4.7★",
            stampTitleSuggestion = "Tháp Đồng Hồ Bến Thành",
            description = "Tháp đồng hồ 4 mặt biểu tượng giao thương sầm uất hơn 100 năm qua.",
            keywords = listOf("cho ben thanh", "le loi", "thap dong ho", "cho", "market")
        ),
        CandidatePlace(
            id = "sg_4",
            name = "Phố Đi Bộ Nguyễn Huệ",
            address = "Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM",
            latitude = 10.7743,
            longitude = 106.7032,
            category = "STREET",
            popularity = 97,
            rating = "4.8★",
            stampTitleSuggestion = "Nhịp Đập Phố Hoa Nguyễn Huệ",
            description = "Đại lộ rực rỡ ánh đèn chạy thẳng ra bến Bạch Đằng lộng gió.",
            keywords = listOf("nguyen hue", "pho di bo", "walking street", "ubnd", "bach dang")
        ),
        CandidatePlace(
            id = "sg_5",
            name = "Cà Phê Chung Cư 42 Nguyễn Huệ",
            address = "42 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM",
            latitude = 10.7752,
            longitude = 106.7028,
            category = "CAFE",
            popularity = 96,
            rating = "4.7★",
            stampTitleSuggestion = "Khối Hộp Cà Phê Nghệ Thuật",
            description = "Chung cư cổ thập niên 60 hội tụ hàng chục quán cafe nghệ thuật độc đáo.",
            keywords = listOf("chung cu 42", "cafe 42", "nguyen hue apartment", "vintage cafe")
        ),
        CandidatePlace(
            id = "sg_6",
            name = "Dinh Độc Lập (Hội Trường Thống Nhất)",
            address = "135 Nam Kỳ Khởi Nghĩa, Phường Bến Thành, Quận 1, TP.HCM",
            latitude = 10.7770,
            longitude = 106.6953,
            category = "HERITAGE",
            popularity = 98,
            rating = "4.8★",
            stampTitleSuggestion = "Ký Ức Thống Nhất 1975",
            description = "Chứng tích lịch sử vĩ đại được thiết kế bởi kiến trúc sư Ngô Viết Thụ.",
            keywords = listOf("dinh doc lap", "hoi truong thong nhat", "nam ky khoi nghia", "lich su")
        ),
        CandidatePlace(
            id = "sg_7",
            name = "Starbucks Reserve Hàn Thuyên",
            address = "Số 11-13 Hàn Thuyên, Bến Nghé, Quận 1, TP.HCM",
            latitude = 10.7788,
            longitude = 106.6975,
            category = "CAFE",
            popularity = 94,
            rating = "4.6★",
            stampTitleSuggestion = "Góc Phố Cà Phê Hàn Thuyên",
            description = "Quán cà phê nhìn thẳng ra công viên 30/4 rợp bóng cây cổ thụ xanh ngắt.",
            keywords = listOf("starbucks", "starbuck", "han thuyen", "cafe", "reserve", "cong vien 30/4")
        ),
        CandidatePlace(
            id = "sg_8",
            name = "Phở Hòa Pasteur",
            address = "260C Pasteur, Phường 8, Quận 3, TP.HCM",
            latitude = 10.7876,
            longitude = 106.6894,
            category = "RESTAURANT",
            popularity = 96,
            rating = "4.7★",
            stampTitleSuggestion = "Phở Truyền Thống Pasteur",
            description = "Hương vị phở bò gia truyền trứ danh của đất Sài thành từ hơn nửa thế kỷ.",
            keywords = listOf("pho hoa", "pho pasteur", "pho", "am thuc", "quan 3")
        ),

        // === HÀ NỘI ===
        CandidatePlace(
            id = "hn_1",
            name = "Hồ Hoàn Kiếm (Hồ Gươm)",
            address = "Phường Hàng Trống, Quận Hoàn Kiếm, Hà Nội",
            latitude = 21.0285,
            longitude = 105.8542,
            category = "HERITAGE",
            popularity = 100,
            rating = "4.9★",
            stampTitleSuggestion = "Mùa Thu Hà Nội",
            description = "Tháp Rùa rêu phong giữa lòng hồ ngọc, trái tim thiêng liêng của thủ đô.",
            keywords = listOf("ho guom", "ho hoan kiem", "thap rua", "cau the huc", "hang trong")
        ),
        CandidatePlace(
            id = "hn_2",
            name = "Phở Thìn 13 Lò Đúc",
            address = "13 Lò Đúc, Phường Phạm Đình Hổ, Quận Hai Bà Trưng, Hà Nội",
            latitude = 21.0189,
            longitude = 105.8576,
            category = "RESTAURANT",
            popularity = 97,
            rating = "4.8★",
            stampTitleSuggestion = "Phở Bò Tái Lăn Lò Đúc",
            description = "Món phở bò xào tái lăn ngập tràn hành hoa thơm nức tiếng Hà Thành.",
            keywords = listOf("pho thin", "pho", "lo duc", "pho bo", "am thuc ha noi")
        ),
        CandidatePlace(
            id = "hn_3",
            name = "Nhà Thờ Lớn Hà Nội",
            address = "Số 40 Nhà Chung, Hàng Trống, Hoàn Kiếm, Hà Nội",
            latitude = 21.0288,
            longitude = 105.8495,
            category = "HERITAGE",
            popularity = 98,
            rating = "4.8★",
            stampTitleSuggestion = "Trà Chanh Nhà Thờ Cổ",
            description = "Nhà thờ thánh Joseph phong cách tân Gothic cổ kính với văn hóa trà chanh vỉa hè.",
            keywords = listOf("nha tho lon", "nha chung", "tra chanh", "saint joseph", "hang trong")
        ),
        CandidatePlace(
            id = "hn_4",
            name = "Cà Phê Giảng (Cà Phê Trứng)",
            address = "39 Nguyễn Hữu Huân, Hàng Bạc, Hoàn Kiếm, Hà Nội",
            latitude = 21.0345,
            longitude = 105.8538,
            category = "CAFE",
            popularity = 98,
            rating = "4.8★",
            stampTitleSuggestion = "Cà Phê Trứng Cụ Giảng 1946",
            description = "Nơi khai sinh ra món cà phê trứng béo ngậy làm say lòng thực khách thế giới.",
            keywords = listOf("cafe giang", "ca phe trung", "nguyen huu huan", "hang bac", "egg coffee")
        ),
        CandidatePlace(
            id = "hn_5",
            name = "Phố Cổ Hà Nội (36 Phố Phường)",
            address = "Quận Hoàn Kiếm, Hà Nội",
            latitude = 21.0348,
            longitude = 105.8502,
            category = "STREET",
            popularity = 99,
            rating = "4.8★",
            stampTitleSuggestion = "36 Phố Phường Ký Ức",
            description = "Những mái ngói rêu phong và con ngõ nhỏ mang đậm dấu ấn Thăng Long ngàn năm.",
            keywords = listOf("pho co", "36 pho phuong", "hang ngang", "hang dao", "ta hien")
        ),

        // === HỘI AN & ĐÀ NẴNG ===
        CandidatePlace(
            id = "ha_1",
            name = "Chùa Cầu Hội An",
            address = "Đường Nguyễn Thị Minh Khai, Phường Minh An, TP. Hội An",
            latitude = 15.8771,
            longitude = 108.3259,
            category = "HERITAGE",
            popularity = 99,
            rating = "4.9★",
            stampTitleSuggestion = "Lai Viễn Kiều Hội An",
            description = "Cây cầu mái ngói do thương nhân Nhật Bản xây dựng từ thế kỷ 17.",
            keywords = listOf("chua cau", "hoi an", "lai vien kieu", "pho co", "cau nhat ban")
        ),
        CandidatePlace(
            id = "ha_2",
            name = "Faifo Coffee Hội An",
            address = "130 Trần Phú, Phường Minh An, TP. Hội An",
            latitude = 15.8778,
            longitude = 108.3294,
            category = "CAFE",
            popularity = 97,
            rating = "4.7★",
            stampTitleSuggestion = "Mái Ngói Phố Cổ Faifo",
            description = "Tầng thượng ngắm trọn vẹn toàn cảnh mái ngói âm dương rực vàng của phố Hội.",
            keywords = listOf("faifo coffee", "tran phu", "rooftop", "mai ngoi", "cafe hoi an")
        ),
        CandidatePlace(
            id = "dn_1",
            name = "Cầu Vàng Bà Nà Hills",
            address = "Khu du lịch Sun World Ba Na Hills, Hòa Vang, Đà Nẵng",
            latitude = 15.9950,
            longitude = 107.9965,
            category = "LANDMARK",
            popularity = 99,
            rating = "4.8★",
            stampTitleSuggestion = "Dải Lụa Mây Ngàn",
            description = "Kiệt tác cầu đi bộ trên đôi bàn tay khổng lồ giữa biển mây bồng bềnh.",
            keywords = listOf("cau vang", "golden bridge", "ba na hills", "da nang", "ban tay")
        ),
        CandidatePlace(
            id = "dn_2",
            name = "Cầu Rồng Đà Nẵng",
            address = "Đường Nguyễn Văn Linh, Phước Ninh, Hải Châu, Đà Nẵng",
            latitude = 16.0611,
            longitude = 108.2272,
            category = "LANDMARK",
            popularity = 98,
            rating = "4.8★",
            stampTitleSuggestion = "Rồng Vàng Phun Lửa",
            description = "Biểu tượng vươn mình ra biển lớn phun lửa và phun nước mỗi cuối tuần.",
            keywords = listOf("cau rong", "dragon bridge", "song han", "hai chau", "nguyen van linh")
        )
    )
}
