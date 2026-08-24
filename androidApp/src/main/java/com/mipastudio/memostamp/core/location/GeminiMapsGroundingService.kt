package com.mipastudio.memostamp.core.location

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mipastudio.memostamp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class GroundedPlace(
    val name: String,
    val address: String,
    val category: String = "LANDMARK", // LANDMARK, CAFE, HERITAGE, NATURE, STREET, RESTAURANT
    val description: String = "",
    val stampTitleSuggestion: String = "",
    val rating: String? = null,
    val distanceMeters: Double? = null,
    val distanceFormatted: String? = null,
    val isGroundedWithMaps: Boolean = true
)

data class GroundedPostmarkStory(
    val poeticNote: String,
    val historicalFact: String,
    val suggestedPostmarkCode: String
)

object GeminiMapsGroundingService {
    private const val TAG = "GeminiMapsGrounding"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.contains("Placeholder")) "" else key
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Search places grounded with Google Maps data via gemini-3.5-flash and ranked with Google Maps Algorithm
     */
    suspend fun searchPlacesWithMaps(
        context: Context? = null,
        query: String,
        currentCity: String? = null,
        userLat: Double? = null,
        userLng: Double? = null,
        categoryFilter: String = "ALL"
    ): List<GroundedPlace> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        
        // 1. If Google Places SDK is configured and context is provided, query Places Autocomplete
        val placesSdkResults = if (context != null && GooglePlacesService.isPlacesSdkAvailable() && query.isNotBlank()) {
            GooglePlacesService.searchPredictions(context, query, userLat, userLng)
        } else {
            emptyList()
        }

        // 2. Fast local candidate ranking with Google Maps Algorithm (Prefix + Token + Haversine 2km radius + Rating)
        val rankedLocal = GoogleMapsRankingEngine.rankPlaces(
            query = query,
            userLat = userLat,
            userLng = userLng,
            maxRadiusMeters = 2000.0,
            filterCategory = categoryFilter
        ).map { ranked ->
            GroundedPlace(
                name = ranked.place.name,
                address = ranked.place.address,
                category = ranked.place.category,
                description = ranked.place.description,
                stampTitleSuggestion = ranked.place.stampTitleSuggestion,
                rating = ranked.place.rating,
                distanceMeters = ranked.distanceMeters,
                distanceFormatted = ranked.distanceFormatted,
                isGroundedWithMaps = true
            )
        }

        if (apiKey.isBlank()) {
            if (placesSdkResults.isNotEmpty()) {
                val merged = mutableListOf<GroundedPlace>()
                merged.addAll(placesSdkResults)
                merged.addAll(rankedLocal)
                return@withContext merged
            }
            return@withContext rankedLocal
        }

        try {
            val isSpecificSearch = query.isNotBlank() && query != currentCity
            val prompt = if (!isSpecificSearch && userLat != null && userLng != null) {
                """
                You are Google Maps Search Engine for the MemoStamp app.
                User is at coordinates: (Latitude $userLat, Longitude $userLng), City: ${currentCity ?: "Vietnam"}.
                Return 6 to 8 real Google Maps places, cafes, landmarks, tourist attractions strictly within a 2km radius of (lat: $userLat, lng: $userLng).
                
                For each place, output a JSON array of objects with:
                - "name": Exact verified name on Google Maps
                - "address": Real address
                - "category": One of "LANDMARK", "CAFE", "HERITAGE", "NATURE", "STREET", "RESTAURANT"
                - "description": 1-sentence poetic highlight in Vietnamese
                - "stampTitleSuggestion": 2-4 word vintage stamp title
                - "rating": Google Maps rating (e.g. "4.8★")
                - "approxDistanceMeters": Approximate distance in meters from (lat $userLat, lng $userLng)

                Return ONLY raw JSON array.
                """.trimIndent()
            } else {
                val locContext = if (userLat != null && userLng != null) {
                    "near coordinates (lat: $userLat, lng: $userLng, City: ${currentCity ?: "Vietnam"})"
                } else {
                    "in ${currentCity ?: "Vietnam"}"
                }
                """
                You are Google Maps Search Engine for the MemoStamp app.
                Search Google Maps for query: "$query" $locContext.
                Use prefix, token, and location bias matching to return 6 to 8 verified places matching this search query like real Google Maps.

                For each place, output a JSON array of objects with:
                - "name": Exact place name
                - "address": Full verified address
                - "category": One of "LANDMARK", "CAFE", "HERITAGE", "NATURE", "STREET", "RESTAURANT"
                - "description": 1-sentence poetic highlight in Vietnamese
                - "stampTitleSuggestion": 2-4 word evocative stamp title
                - "rating": Verified rating (e.g. "4.7★")
                - "approxDistanceMeters": Distance in meters if nearby or null

                Return ONLY raw JSON array.
                """.trimIndent()
            }

            // Request body with googleMaps tool grounding
            val requestJson = JsonObject().apply {
                val contentsArray = JsonArray().apply {
                    val contentObj = JsonObject().apply {
                        val partsArray = JsonArray().apply {
                            val partObj = JsonObject().apply {
                                addProperty("text", prompt)
                            }
                            add(partObj)
                        }
                        add("parts", partsArray)
                    }
                    add(contentObj)
                }
                add("contents", contentsArray)

                // Add Google Maps Grounding Tool
                val toolsArray = JsonArray().apply {
                    val mapsTool = JsonObject().apply {
                        add("googleMaps", JsonObject())
                    }
                    add(mapsTool)
                }
                add("tools", toolsArray)
            }

            val url = "$BASE_URL?key=$apiKey"
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string().orEmpty()
                val parsedList = parseGeminiPlacesResponse(responseBody, userLat, userLng)
                if (parsedList.isNotEmpty()) {
                    // Merge AI Grounding results with ranked local candidates, deduplicate by normalized name
                    val merged = mutableListOf<GroundedPlace>()
                    val seenNames = mutableSetOf<String>()

                    for (p in parsedList) {
                        val norm = GoogleMapsRankingEngine.normalize(p.name)
                        if (norm !in seenNames) {
                            seenNames.add(norm)
                            merged.add(p)
                        }
                    }
                    for (p in rankedLocal) {
                        val norm = GoogleMapsRankingEngine.normalize(p.name)
                        if (norm !in seenNames) {
                            seenNames.add(norm)
                            merged.add(p)
                        }
                    }
                    return@withContext merged
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini Maps Grounding: ${e.message}", e)
        }

        rankedLocal
    }

    /**
     * Generate poetic stamp story & postmark details for the back of the stamp
     */
    suspend fun generateGroundedPostmarkNote(
        placeName: String,
        locationAddress: String
    ): GroundedPostmarkStory = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext GroundedPostmarkStory(
                poeticNote = "Những khoảnh khắc đẹp đẽ nhất luôn nằm lại nơi góc quán quen và con đường ngập nắng $placeName.",
                historicalFact = "$placeName là một trong những điểm dừng chân ghi dấu kỷ niệm khó quên tại $locationAddress.",
                suggestedPostmarkCode = "MEMO-${placeName.take(4).uppercase()}"
            )
        }

        try {
            val prompt = """
                Use Google Maps data and cultural knowledge about place: "$placeName" at "$locationAddress".
                Provide a JSON object with:
                - "poeticNote": A warm, poetic 2-sentence postcard note (Vietnamese) capturing the vibe and soul of this place.
                - "historicalFact": A 1-sentence verified interesting fact or highlight about this spot.
                - "suggestedPostmarkCode": A 6-character postmark code like "VN-DLT26" or "HCM-BT26".

                Return ONLY raw JSON object.
            """.trimIndent()

            val requestJson = JsonObject().apply {
                val contentsArray = JsonArray().apply {
                    val contentObj = JsonObject().apply {
                        val partsArray = JsonArray().apply {
                            val partObj = JsonObject().apply {
                                addProperty("text", prompt)
                            }
                            add(partObj)
                        }
                        add("parts", partsArray)
                    }
                    add(contentObj)
                }
                add("contents", contentsArray)
                val toolsArray = JsonArray().apply {
                    val mapsTool = JsonObject().apply {
                        add("googleMaps", JsonObject())
                    }
                    add(mapsTool)
                }
                add("tools", toolsArray)
            }

            val url = "$BASE_URL?key=$apiKey"
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string().orEmpty()
                val parsed = parseGeminiStoryResponse(responseBody, placeName, locationAddress)
                if (parsed != null) return@withContext parsed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed generating story: ${e.message}")
        }

        GroundedPostmarkStory(
            poeticNote = "Lưu giữ một thoáng mộng mơ cùng ánh nắng dịu dàng tại $placeName.",
            historicalFact = "Điểm ghi dấu hành trình bưu chính tại $locationAddress.",
            suggestedPostmarkCode = "POST-${placeName.take(3).uppercase()}"
        )
    }

    private fun parseGeminiPlacesResponse(jsonString: String, userLat: Double? = null, userLng: Double? = null): List<GroundedPlace> {
        val places = mutableListOf<GroundedPlace>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val candidates = root.getAsJsonArray("candidates") ?: return emptyList()
            if (candidates.size() == 0) return emptyList()

            val firstCandidate = candidates[0].asJsonObject
            val content = firstCandidate.getAsJsonObject("content") ?: return emptyList()
            val parts = content.getAsJsonArray("parts") ?: return emptyList()

            var textContent = ""
            for (p in parts) {
                val partObj = p.asJsonObject
                if (partObj.has("text")) {
                    textContent += partObj.get("text").asString
                }
            }

            // Extract JSON array from textContent
            val jsonArrayStr = extractJsonArray(textContent) ?: return emptyList()
            val jsonArray = JsonParser.parseString(jsonArrayStr).asJsonArray

            for (element in jsonArray) {
                val obj = element.asJsonObject
                val name = obj.get("name")?.asString.orEmpty()
                val address = obj.get("address")?.asString.orEmpty()
                val category = obj.get("category")?.asString ?: "LANDMARK"
                val desc = obj.get("description")?.asString.orEmpty()
                val stampTitle = obj.get("stampTitleSuggestion")?.asString.orEmpty()
                val rating = obj.get("rating")?.asString
                val approxDist = obj.get("approxDistanceMeters")?.asDouble

                if (name.isNotBlank()) {
                    places.add(
                        GroundedPlace(
                            name = name,
                            address = address,
                            category = category,
                            description = desc,
                            stampTitleSuggestion = stampTitle.ifBlank { name },
                            rating = rating,
                            distanceMeters = approxDist,
                            distanceFormatted = GoogleMapsRankingEngine.formatDistance(approxDist),
                            isGroundedWithMaps = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing places JSON: ${e.message}", e)
        }
        return places
    }

    private fun parseGeminiStoryResponse(jsonString: String, placeName: String, locationAddress: String): GroundedPostmarkStory? {
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val candidates = root.getAsJsonArray("candidates") ?: return null
            val firstCandidate = candidates[0].asJsonObject
            val content = firstCandidate.getAsJsonObject("content") ?: return null
            val parts = content.getAsJsonArray("parts") ?: return null

            var textContent = ""
            for (p in parts) {
                if (p.asJsonObject.has("text")) {
                    textContent += p.asJsonObject.get("text").asString
                }
            }

            val jsonObjectStr = extractJsonObject(textContent) ?: return null
            val obj = JsonParser.parseString(jsonObjectStr).asJsonObject

            return GroundedPostmarkStory(
                poeticNote = obj.get("poeticNote")?.asString ?: "Gửi trọn một thoáng thương nhớ từ $placeName.",
                historicalFact = obj.get("historicalFact")?.asString ?: "$placeName tọa lạc tại $locationAddress.",
                suggestedPostmarkCode = obj.get("suggestedPostmarkCode")?.asString ?: "POST-${placeName.take(3).uppercase()}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing story JSON: ${e.message}")
            return null
        }
    }

    private fun extractJsonArray(text: String): String? {
        val trimmed = text.trim()
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }

    private fun extractJsonObject(text: String): String? {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }

    private fun getFallbackPlaces(query: String, currentCity: String?): List<GroundedPlace> {
        val all = listOf(
            GroundedPlace(
                name = "Quảng trường Lâm Viên",
                address = "Đường Trần Quốc Toản, Phường 10, TP. Đà Lạt",
                category = "LANDMARK",
                description = "Biểu tượng nụ hoa Atisô và hoa dã quỳ khổng lồ bên hồ.",
                stampTitleSuggestion = "Nụ Hoa Atisô Đà Lạt",
                rating = "4.7★"
            ),
            GroundedPlace(
                name = "Hồ Xuân Hương",
                address = "Trung tâm TP. Đà Lạt, Lâm Đồng",
                category = "NATURE",
                description = "Trái tim lãng mạn của thành phố sương mù, mặt nước phẳng lặng soi bóng thông reo.",
                stampTitleSuggestion = "Sương Mù Hồ Xuân Hương",
                rating = "4.8★"
            ),
            GroundedPlace(
                name = "Chợ Đêm Đà Lạt (Chợ Âm Phủ)",
                address = "Đường Nguyễn Thị Minh Khai, Phường 1, TP. Đà Lạt",
                category = "STREET",
                description = "Không gian ẩm thực phố núi ấm nồng với sữa đậu nành, bánh tráng nướng.",
                stampTitleSuggestion = "Đêm Lạnh Phố Chợ",
                rating = "4.5★"
            ),
            GroundedPlace(
                name = "Tiệm Cà Phê Túi Mơ To",
                address = "Hẻm 31 Sào Nam, Phường 11, TP. Đà Lạt",
                category = "CAFE",
                description = "Vườn cúc hoạ mi trắng tinh khôi và view ngắm thung lũng lồng kính rực rỡ về đêm.",
                stampTitleSuggestion = "Cúc Họa Mi Mơ Màng",
                rating = "4.6★"
            ),
            GroundedPlace(
                name = "Bưu Điện Trung Tâm Sài Gòn",
                address = "Số 2 Công xã Paris, Bến Nghé, Quận 1, TP.HCM",
                category = "HERITAGE",
                description = "Kiến trúc Pháp cổ kính biểu tượng bưu chính lâu đời nhất Việt Nam.",
                stampTitleSuggestion = "Bưu Chính Sài Gòn 1891",
                rating = "4.8★"
            ),
            GroundedPlace(
                name = "Chợ Bến Thành",
                address = "Đường Lê Lợi, Phường Bến Thành, Quận 1, TP.HCM",
                category = "HERITAGE",
                description = "Biểu tượng văn hóa giao thương trăm năm của Sài Gòn.",
                stampTitleSuggestion = "Tháp Đồng Hồ Bến Thành",
                rating = "4.6★"
            ),
            GroundedPlace(
                name = "Hồ Hoàn Kiếm (Hồ Gươm)",
                address = "Quận Hoàn Kiếm, Hà Nội",
                category = "HERITAGE",
                description = "Tháp Rùa trầm mặc giữa lòng thủ đô ngàn năm văn hiến.",
                stampTitleSuggestion = "Mùa Thu Hà Nội",
                rating = "4.9★"
            ),
            GroundedPlace(
                name = "Phố Cổ Hội An",
                address = "Thành phố Hội An, Tỉnh Quảng Nam",
                category = "HERITAGE",
                description = "Dãy nhà cổ tường vàng hoa giấy và lung linh ánh đèn lồng bên sông Hoài.",
                stampTitleSuggestion = "Đèn Lồng Phố Cổ",
                rating = "4.9★"
            ),
            GroundedPlace(
                name = "Cầu Vàng Bà Nà Hills",
                address = "Hòa Vang, Đà Nẵng",
                category = "LANDMARK",
                description = "Bàn tay khổng lồ nâng dải lụa vàng giữa biển mây bồng bềnh.",
                stampTitleSuggestion = "Dải Lụa Mây Ngàn",
                rating = "4.7★"
            )
        )

        if (query.isBlank()) return all

        val filtered = all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.address.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
        return if (filtered.isNotEmpty()) filtered else listOf(
            GroundedPlace(
                name = query,
                address = currentCity ?: "Việt Nam",
                category = "LANDMARK",
                description = "Địa điểm ghi dấu hành trình kỷ niệm của bạn.",
                stampTitleSuggestion = query
            )
        )
    }
}
