package com.mipastudio.memostamp.core.location

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mipastudio.memostamp.data.remote.supabase.AndroidAuthSessionStore
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
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

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Search places grounded with Google Maps data via server-side MemoStame Edge Function.
     * Android clients never contain, transmit, or configure Gemini provider secrets.
     */
    suspend fun searchPlacesWithMaps(
        context: Context? = null,
        query: String,
        currentCity: String? = null,
        userLat: Double? = null,
        userLng: Double? = null,
        categoryFilter: String = "ALL"
    ): List<GroundedPlace> = withContext(Dispatchers.IO) {
        // 1. Query native Google Places SDK if configured and context is available
        val placesSdkResults = if (context != null && GooglePlacesService.isPlacesSdkAvailable() && query.isNotBlank()) {
            GooglePlacesService.searchPredictions(context, query, userLat, userLng)
        } else {
            emptyList()
        }

        // 2. Fast local candidate ranking with Google Maps Algorithm
        val localCandidates = GoogleMapsRankingEngine.rankPlaces(
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
                isGroundedWithMaps = false
            )
        }.ifEmpty {
            getFallbackPlaces(query, currentCity).map { it.copy(isGroundedWithMaps = false) }
        }

        // 3. Fallback immediately if context or authenticated session is absent
        if (context == null) {
            return@withContext if (placesSdkResults.isNotEmpty()) placesSdkResults + localCandidates else localCandidates
        }

        val session = AndroidAuthSessionStore(context).load()
        val accessToken = session?.accessToken
        if (accessToken.isNullOrBlank()) {
            return@withContext if (placesSdkResults.isNotEmpty()) placesSdkResults + localCandidates else localCandidates
        }

        // 4. Invoke server-side maps-grounding Edge Function
        try {
            val baseUrl = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
            val anonKey = SupabaseConfig.getAnonKey(context).trim()
            val functionUrl = "$baseUrl/functions/v1/maps-grounding"

            val requestJson = JsonObject().apply {
                addProperty("action", "SEARCH_PLACES")
                addProperty("query", query.take(100))
                if (currentCity != null) addProperty("currentCity", currentCity.take(100))
                if (userLat != null && !userLat.isNaN() && userLat in -90.0..90.0) addProperty("latitude", userLat)
                if (userLng != null && !userLng.isNaN() && userLng in -180.0..180.0) addProperty("longitude", userLng)
                addProperty("categoryFilter", categoryFilter)
            }

            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(functionUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("apikey", anonKey)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string().orEmpty()
                val parsedPlaces = parseServerPlacesResponse(responseBody)
                if (parsedPlaces.isNotEmpty()) {
                    val merged = mutableListOf<GroundedPlace>()
                    val seenNames = mutableSetOf<String>()

                    for (p in parsedPlaces) {
                        val norm = GoogleMapsRankingEngine.normalize(p.name)
                        if (norm !in seenNames) {
                            seenNames.add(norm)
                            merged.add(p)
                        }
                    }
                    for (p in placesSdkResults) {
                        val norm = GoogleMapsRankingEngine.normalize(p.name)
                        if (norm !in seenNames) {
                            seenNames.add(norm)
                            merged.add(p)
                        }
                    }
                    for (p in localCandidates) {
                        val norm = GoogleMapsRankingEngine.normalize(p.name)
                        if (norm !in seenNames) {
                            seenNames.add(norm)
                            merged.add(p)
                        }
                    }
                    return@withContext merged
                }
            } else {
                Log.w(TAG, "Server maps-grounding returned HTTP ${response.code}; falling back to local results")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Maps grounding request failed; falling back to local results: ${e.message}")
        }

        if (placesSdkResults.isNotEmpty()) placesSdkResults + localCandidates else localCandidates
    }

    /**
     * Generate poetic stamp story & postmark details via server-side MemoStame Edge Function.
     */
    suspend fun generateGroundedPostmarkNote(
        placeName: String,
        locationAddress: String,
        context: Context? = null
    ): GroundedPostmarkStory = withContext(Dispatchers.IO) {
        val fallbackStory = GroundedPostmarkStory(
            poeticNote = "Những khoảnh khắc đẹp đẽ nhất luôn nằm lại nơi góc quán quen và con đường ngập nắng $placeName.",
            historicalFact = "$placeName là một trong những điểm dừng chân ghi dấu kỷ niệm khó quên tại $locationAddress.",
            suggestedPostmarkCode = "MEMO-${placeName.take(4).uppercase()}"
        )

        if (context == null) return@withContext fallbackStory

        val session = AndroidAuthSessionStore(context).load()
        val accessToken = session?.accessToken
        if (accessToken.isNullOrBlank()) return@withContext fallbackStory

        try {
            val baseUrl = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
            val anonKey = SupabaseConfig.getAnonKey(context).trim()
            val functionUrl = "$baseUrl/functions/v1/maps-grounding"

            val requestJson = JsonObject().apply {
                addProperty("action", "GENERATE_POSTMARK_STORY")
                addProperty("placeName", placeName.take(150))
                addProperty("locationAddress", locationAddress.take(250))
            }

            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(functionUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("apikey", anonKey)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string().orEmpty()
                val parsed = parseServerStoryResponse(responseBody, placeName, locationAddress)
                if (parsed != null) return@withContext parsed
            } else {
                Log.w(TAG, "Server postmark story returned HTTP ${response.code}; using fallback story")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Postmark story request failed; using fallback story: ${e.message}")
        }

        fallbackStory
    }

    private fun parseServerPlacesResponse(jsonString: String): List<GroundedPlace> {
        val places = mutableListOf<GroundedPlace>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val placesArray = root.getAsJsonArray("places") ?: return emptyList()

            for (element in placesArray) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val name = obj.get("name")?.asString.orEmpty()
                val address = obj.get("address")?.asString.orEmpty()
                val category = obj.get("category")?.asString ?: "LANDMARK"
                val desc = obj.get("description")?.asString.orEmpty()
                val stampTitle = obj.get("stampTitleSuggestion")?.asString.orEmpty()
                val rating = if (obj.has("rating") && !obj.get("rating").isJsonNull) obj.get("rating").asString else null
                val approxDist = if (obj.has("approximateDistanceMeters") && !obj.get("approximateDistanceMeters").isJsonNull) {
                    obj.get("approximateDistanceMeters").asDouble
                } else null

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
            Log.e(TAG, "Error parsing server places response: ${e.message}")
        }
        return places
    }

    private fun parseServerStoryResponse(jsonString: String, placeName: String, locationAddress: String): GroundedPostmarkStory? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val poeticNote = root.get("poeticNote")?.asString
                ?: "Lưu giữ một thoáng mộng mơ cùng ánh nắng dịu dàng tại $placeName."
            val historicalFact = root.get("historicalFact")?.asString
                ?: "Điểm ghi dấu hành trình bưu chính tại $locationAddress."
            val suggestedPostmarkCode = root.get("suggestedPostmarkCode")?.asString
                ?: "POST-${placeName.take(3).uppercase()}"

            GroundedPostmarkStory(
                poeticNote = poeticNote,
                historicalFact = historicalFact,
                suggestedPostmarkCode = suggestedPostmarkCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server story response: ${e.message}")
            null
        }
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
