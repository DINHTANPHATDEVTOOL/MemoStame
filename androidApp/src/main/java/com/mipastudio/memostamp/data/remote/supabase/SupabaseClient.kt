package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.mipastudio.memostamp.data.repository.FriendRequest
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.domain.model.DirectMessage
import com.mipastudio.memostamp.domain.model.FeedPost
import com.mipastudio.memostamp.domain.model.FeedPostType
import com.mipastudio.memostamp.domain.model.AudienceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class SupabaseProfileRecord(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user_id") val rawUserId: String? = null,
    @SerializedName("username") val username: String = "",
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("email") val email: String? = "",
    @SerializedName("avatar_url") val avatarUrl: String? = "",
    @SerializedName("cover_url") val coverUrl: String? = "",
    @SerializedName("bio") val bio: String? = "",
    @SerializedName("city") val city: String? = "",
    @SerializedName("created_at") val createdAt: Any? = null
) {
    val userId: String
        get() = rawUserId?.takeIf { it.isNotBlank() } ?: id ?: ""
}

data class SupabaseFriendRequestRecord(
    @SerializedName("id") val id: String = "",
    @SerializedName("sender_id") val senderId: String = "",
    @SerializedName("sender_username") val senderUsername: String = "",
    @SerializedName("sender_display_name") val senderDisplayName: String = "",
    @SerializedName("sender_avatar") val senderAvatar: String? = null,
    @SerializedName("recipient_id") val recipientId: String = "",
    @SerializedName("recipient_username") val recipientUsername: String = "",
    @SerializedName("recipient_display_name") val recipientDisplayName: String = "",
    @SerializedName("recipient_avatar") val recipientAvatar: String? = null,
    @SerializedName("status") val status: String = "PENDING",
    @SerializedName("created_at") val createdAt: Any? = null
)

data class SupabaseFriendRelation(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user_id_1") val userId1: String = "",
    @SerializedName("user_id_2") val userId2: String = "",
    @SerializedName("created_at") val createdAt: Long? = System.currentTimeMillis()
)

data class SupabaseDirectMessageRecord(
    @SerializedName("id") val id: String = "",
    @SerializedName("sender_id") val senderId: String = "",
    @SerializedName("sender_name") val senderName: String = "",
    @SerializedName("sender_avatar") val senderAvatar: String? = null,
    @SerializedName("recipient_id") val recipientId: String = "",
    @SerializedName("recipient_name") val recipientName: String = "",
    @SerializedName("recipient_avatar") val recipientAvatar: String? = null,
    @SerializedName("text") val text: String = "",
    @SerializedName("stamp_id") val stampId: String? = null,
    @SerializedName("stamp_title") val stampTitle: String? = null,
    @SerializedName("stamp_image_url") val stampImageUrl: String? = null,
    @SerializedName("stamp_location") val stampLocation: String? = null,
    @SerializedName("created_at") val createdAt: Long? = System.currentTimeMillis(),
    @SerializedName("is_read") val isRead: Boolean = false
)

data class SupabaseFeedPostRecord(
    @SerializedName("id") val id: String? = null,
    @SerializedName("stamp_id") val stampId: String? = null,
    @SerializedName("stamp_url") val stampUrl: String? = null,
    @SerializedName("stamp_title") val stampTitle: String? = null,
    @SerializedName("shape") val shape: String? = "classic",
    @SerializedName("author_id") val authorId: String? = null,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("audience_type") val audienceType: String? = "EVERYONE",
    @SerializedName("circle_id") val circleId: String? = null,
    @SerializedName("circle_name") val circleName: String? = null,
    @SerializedName("created_at") val createdAt: Long? = null,
    @SerializedName("type") val type: String? = "MEMORY",
    @SerializedName("location") val location: String? = null
)

data class SupabaseFeedReactionRecord(
    @SerializedName("id") val id: String? = null,
    @SerializedName("post_id") val postId: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("emoji") val emoji: String? = "❤️",
    @SerializedName("created_at") val createdAt: Long? = null
)

data class SupabaseFeedCommentRecord(
    @SerializedName("id") val id: String? = null,
    @SerializedName("post_id") val postId: String? = null,
    @SerializedName("author_id") val authorId: String? = null,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("created_at") val createdAt: Long? = null
)

interface SupabaseHttpTransport {
    fun executeHttp(
        client: SupabaseClient,
        endpoint: String,
        method: String,
        jsonBody: String?,
        prefer: String?,
        requireUserAuth: Boolean
    ): Result<String>
}

class SupabaseClient internal constructor(private val context: Context? = null) {

    private val gson: Gson = createCustomGson()
    private val missingSchemaColumns = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var transport: SupabaseHttpTransport? = null

    companion object {
        private const val TAG = "SupabaseClient"

        @Volatile
        private var instance: SupabaseClient? = null

        fun getInstance(context: Context): SupabaseClient {
            return instance ?: synchronized(this) {
                instance ?: SupabaseClient(context.applicationContext).also { instance = it }
            }
        }

        init {
            allowPatchMethodInHttpUrlConnection()
        }

        private fun allowPatchMethodInHttpUrlConnection() {
            val patchMethods = arrayOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE", "PATCH")
            try {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
                theUnsafeField.isAccessible = true
                val unsafe = theUnsafeField.get(null)

                fun patchClassMethods(clazzName: String) {
                    try {
                        val clazz = Class.forName(clazzName)
                        val field = clazz.getDeclaredField("methods")
                        val staticFieldBaseMethod = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
                        val staticFieldOffsetMethod = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
                        val putObjectMethod = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

                        val base = staticFieldBaseMethod.invoke(unsafe, field)
                        val offset = staticFieldOffsetMethod.invoke(unsafe, field)
                        putObjectMethod.invoke(unsafe, base, offset, patchMethods)
                    } catch (_: Throwable) {}
                }

                patchClassMethods("java.net.HttpURLConnection")
                patchClassMethods("sun.net.www.protocol.http.HttpURLConnection")
            } catch (_: Throwable) {}
        }

        private fun createCustomGson(): Gson {
            val dateDeserializer = JsonDeserializer { json, _, _ ->
                if (json == null || json.isJsonNull) return@JsonDeserializer System.currentTimeMillis()
                try {
                    if (json.isJsonPrimitive) {
                        val prim = json.asJsonPrimitive
                        if (prim.isNumber) {
                            val num = prim.asLong
                            return@JsonDeserializer if (num < 10000000000L) num * 1000 else num
                        } else if (prim.isString) {
                            val str = prim.asString
                            val asLong = str.toLongOrNull()
                            if (asLong != null) {
                                return@JsonDeserializer if (asLong < 10000000000L) asLong * 1000 else asLong
                            }
                            return@JsonDeserializer parseIsoStringToMillis(str)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                System.currentTimeMillis()
            }

            return GsonBuilder()
                .registerTypeAdapter(Long::class.java, dateDeserializer)
                .registerTypeAdapter(java.lang.Long::class.java, dateDeserializer)
                .create()
        }

        private fun parseIsoStringToMillis(str: String): Long {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val cleanStr = if (str.contains(".")) str.substringBefore(".") else str.substringBefore("+").substringBefore("Z")
                sdf.parse(cleanStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun getBaseUrl(): String = if (context != null) SupabaseConfig.getSupabaseUrl(context).trimEnd('/') else "https://fake.supabase.co"
    private fun getApiKey(): String = if (context != null) SupabaseConfig.getAnonKey(context).trim() else "fake_anon_key"

    suspend fun testConnection(overrideUrl: String? = null, overrideKey: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val baseUrl = (overrideUrl ?: getBaseUrl()).trimEnd('/')
        val apiKey = (overrideKey ?: getApiKey()).trim()

        if (apiKey.isBlank()) {
            return@withContext Pair(false, "Chưa cấu hình Supabase Anon Key. Vui lòng nhập Anon Key dạng 'eyJ...'.")
        }

        val endpoint = "$baseUrl/rest/v1/profiles?select=count&limit=1"
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            when (code) {
                in 200..299 -> Pair(true, "Kết nối Supabase thành công (HTTP $code)! Đã liên kết với bảng 'profiles'.")
                401, 403 -> Pair(false, "Lỗi xác thực ($code): Anon Key không hợp lệ. Vui lòng vào Supabase Dashboard > Project Settings > API lấy 'anon public key' dạng eyJhbGciOi... ($responseText)")
                404 -> Pair(false, "Lỗi ($code): Bảng 'profiles' chưa tồn tại trên Supabase. Vui lòng chạy đoạn mã SQL tạo bảng trong SQL Editor ($responseText)")
                else -> Pair(false, "Supabase phản hồi lỗi [$code]: $responseText")
            }
        } catch (e: Exception) {
            Pair(false, "Lỗi kết nối mạng: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun setPatchMethod(conn: HttpURLConnection) {
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)
            val objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
            val putObjectMethod = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

            fun patchObject(obj: Any) {
                var current: Class<*>? = obj.javaClass
                while (current != null && current != Any::class.java) {
                    try {
                        val methodField = current.getDeclaredField("method")
                        val offset = objectFieldOffsetMethod.invoke(unsafe, methodField)
                        putObjectMethod.invoke(unsafe, obj, offset, "PATCH")
                    } catch (_: Throwable) {}
                    current = current.superclass
                }
            }

            patchObject(conn)

            var current: Class<*>? = conn.javaClass
            while (current != null && current != Any::class.java) {
                try {
                    val delegateField = current.getDeclaredField("delegate")
                    delegateField.isAccessible = true
                    val delegateObj = delegateField.get(conn)
                    if (delegateObj != null) {
                        patchObject(delegateObj)
                    }
                } catch (_: Throwable) {}
                current = current.superclass
            }
        } catch (_: Throwable) {
            try {
                val methodField = HttpURLConnection::class.java.getDeclaredField("method")
                methodField.isAccessible = true
                methodField.set(conn, "PATCH")
            } catch (_: Throwable) {}
        }
    }

    @Volatile
    var userAccessToken: String? = null

    private fun executeHttp(
        endpoint: String,
        method: String = "GET",
        jsonBody: String? = null,
        prefer: String? = null,
        requireUserAuth: Boolean = false
    ): Result<String> {
        val token = userAccessToken.takeIf { !it.isNullOrBlank() }
        if (requireUserAuth && token == null) {
            return Result.failure(IllegalStateException("User authentication token required for RLS mutation"))
        }
        val t = transport
        if (t != null) {
            return t.executeHttp(this, endpoint, method, jsonBody, prefer, requireUserAuth)
        }
        return try {
            val apiKey = getApiKey()
            val authHeaderToken = token ?: apiKey
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (method == "PATCH") {
                    try {
                        requestMethod = "PATCH"
                    } catch (_: Exception) {
                        setPatchMethod(this)
                    }
                    setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    requestMethod = method
                }
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $authHeaderToken")
                setRequestProperty("Content-Type", "application/json")
                if (!prefer.isNullOrBlank()) {
                    setRequestProperty("Prefer", prefer)
                }
                connectTimeout = 12000
                readTimeout = 12000
                if (!jsonBody.isNullOrBlank()) {
                    doOutput = true
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonBody)
                        writer.flush()
                    }
                }
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) {
                if (code == 204) null else conn.inputStream
            } else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (isSuccess) {
                Result.success(responseText)
            } else {
                Log.w(TAG, "HTTP $method $endpoint failed [$code]: $responseText")
                Result.failure(Exception("Supabase Error [$code]: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network request error on $endpoint", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // PROFILES & AUTH
    // ==========================================

    suspend fun upsertProfile(profile: UserProfile): Result<Boolean> = withContext(Dispatchers.IO) {
        val jsonMap = mutableMapOf<String, Any?>(
            "id" to profile.userId,
            "user_id" to profile.userId,
            "username" to profile.username,
            "display_name" to profile.displayName,
            "email" to profile.email,
            "avatar_url" to profile.avatarUrl,
            "cover_url" to profile.coverUrl,
            "bio" to profile.bio,
            "city" to profile.city,
            "created_at" to System.currentTimeMillis()
        )
        val endpoint = "${getBaseUrl()}/rest/v1/profiles?on_conflict=user_id"
        val res = executeUpsertWithSchemaFallback(endpoint, jsonMap, prefer = "resolution=merge-duplicates")
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
    }

    suspend fun getProfileByUsername(username: String): SupabaseProfileRecord? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(username.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/profiles?username=eq.$encoded&select=*"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            val listType = object : TypeToken<List<SupabaseProfileRecord>>() {}.type
            val list: List<SupabaseProfileRecord>? = gson.fromJson(json, listType)
            list?.firstOrNull()
        }
    }

    suspend fun getProfileById(userId: String): SupabaseProfileRecord? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(userId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/profiles?user_id=eq.$encoded&select=*"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            val listType = object : TypeToken<List<SupabaseProfileRecord>>() {}.type
            val list: List<SupabaseProfileRecord>? = gson.fromJson(json, listType)
            list?.firstOrNull()
        }
    }

    suspend fun getAllProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        var endpoint = "${getBaseUrl()}/rest/v1/profiles?select=*&order=created_at.desc&limit=5000"
        var res = executeHttp(endpoint, method = "GET")
        if (!res.isSuccess) {
            endpoint = "${getBaseUrl()}/rest/v1/profiles?select=*&limit=5000"
            res = executeHttp(endpoint, method = "GET")
        }
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
                val list: List<Map<String, Any?>> = gson.fromJson(json, listType) ?: emptyList()
                list.map { map ->
                    val uid = (map["user_id"] ?: map["id"] ?: "").toString()
                    val uname = (map["username"] ?: "").toString()
                    val dname = (map["display_name"] ?: map["name"] ?: "").toString()
                    val email = (map["email"] ?: "").toString()
                    val avatar = (map["avatar_url"] ?: map["avatar"] ?: "").toString()
                    val cover = (map["cover_url"] ?: map["cover"] ?: "").toString()
                    val bio = (map["bio"] ?: "").toString()
                    val city = (map["city"] ?: "").toString()
                    UserProfile(
                        userId = uid,
                        username = uname,
                        displayName = dname,
                        email = email,
                        avatarUrl = avatar,
                        coverUrl = cover,
                        bio = bio,
                        city = city,
                        isCloudSynced = true
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun searchProfiles(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val clean = query.trim().lowercase().removePrefix("@")
        val all = getAllProfiles()
        if (clean.isBlank()) return@withContext all

        val encoded = URLEncoder.encode("*$clean*", "UTF-8")
        val cleanEncoded = URLEncoder.encode(clean, "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/profiles?or=(username.ilike.$encoded,display_name.ilike.$encoded,username.eq.$cleanEncoded)&select=*"
        val res = executeHttp(endpoint, method = "GET")
        val cloudList: List<UserProfile> = res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<SupabaseProfileRecord>>() {}.type
                val list: List<SupabaseProfileRecord> = gson.fromJson(json, listType) ?: emptyList()
                list.map { record ->
                    UserProfile(
                        userId = record.userId,
                        username = record.username,
                        displayName = record.displayName,
                        email = record.email ?: "",
                        avatarUrl = if (!record.avatarUrl.isNullOrBlank()) record.avatarUrl else "https://i.pravatar.cc/150?u=${record.userId}",
                        coverUrl = if (!record.coverUrl.isNullOrBlank()) record.coverUrl else "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
                        bio = record.bio ?: "",
                        city = record.city ?: "",
                        isCloudSynced = true
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        val localFiltered = all.filter {
            it.username.lowercase().contains(clean) ||
            it.displayName.lowercase().contains(clean) ||
            it.userId.lowercase().contains(clean)
        }
        (cloudList + localFiltered).distinctBy { it.userId }
    }

    // ==========================================
    // FRIEND REQUESTS & FRIENDS
    // ==========================================

    suspend fun sendFriendRequest(request: FriendRequest): Result<Boolean> = withContext(Dispatchers.IO) {
        val record = SupabaseFriendRequestRecord(
            id = request.id,
            senderId = request.senderId,
            senderUsername = request.senderUsername,
            senderDisplayName = request.senderDisplayName,
            senderAvatar = request.senderAvatar,
            recipientId = request.recipientId,
            recipientUsername = request.recipientUsername,
            recipientDisplayName = request.recipientDisplayName,
            recipientAvatar = request.recipientAvatar,
            status = request.status,
            createdAt = request.createdAt
        )
        val json = gson.toJson(record)
        val endpoint = "${getBaseUrl()}/rest/v1/friend_requests?on_conflict=id"
        val res = executeHttp(endpoint, method = "POST", jsonBody = json, prefer = "resolution=merge-duplicates", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
    }

    suspend fun getFriendRequestsForUser(userId: String): List<FriendRequest> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(userId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/friend_requests?or=(recipient_id.eq.$encoded,sender_id.eq.$encoded)&select=*&order=created_at.desc"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<SupabaseFriendRequestRecord>>() {}.type
                val list: List<SupabaseFriendRequestRecord> = gson.fromJson(json, listType) ?: emptyList()
                list.map {
                    FriendRequest(
                        id = it.id,
                        senderId = it.senderId,
                        senderUsername = it.senderUsername,
                        senderDisplayName = it.senderDisplayName,
                        senderAvatar = it.senderAvatar ?: "https://i.pravatar.cc/150?u=${it.senderId}",
                        recipientId = it.recipientId,
                        recipientUsername = it.recipientUsername,
                        recipientDisplayName = it.recipientDisplayName,
                        recipientAvatar = it.recipientAvatar ?: "https://i.pravatar.cc/150?u=${it.recipientId}",
                        status = it.status,
                        createdAt = (it.createdAt as? Double)?.toLong() ?: (it.createdAt as? Long) ?: System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun updateFriendRequestStatus(requestId: String, status: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(requestId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/friend_requests?id=eq.$encoded"
        var res = executeHttp(endpoint, method = "PATCH", jsonBody = gson.toJson(mapOf("status" to status)), requireUserAuth = true)
        if (!res.isSuccess) {
            val queryEndpoint = "${getBaseUrl()}/rest/v1/friend_requests?id=eq.$encoded&select=*"
            val getRes = executeHttp(queryEndpoint, method = "GET")
            getRes.getOrNull()?.let { responseJson ->
                try {
                    val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val list: List<Map<String, Any>> = gson.fromJson(responseJson, listType) ?: emptyList()
                    val reqMap = list.firstOrNull()?.toMutableMap()
                    if (reqMap != null) {
                        reqMap["status"] = status
                        val upsertEndpoint = "${getBaseUrl()}/rest/v1/friend_requests?on_conflict=id"
                        res = executeUpsertWithSchemaFallback(upsertEndpoint, reqMap, prefer = "resolution=merge-duplicates")
                    }
                } catch (_: Exception) {}
            }
        }
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
    }

    suspend fun addFriendship(userId1: String, userId2: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val id1 = "${userId1}_${userId2}"
        val id2 = "${userId2}_${userId1}"
        val now = System.currentTimeMillis()
        val map1 = mutableMapOf<String, Any?>(
            "id" to id1,
            "user_id" to userId1,
            "friend_id" to userId2,
            "friend_username" to userId2,
            "friend_name" to userId2,
            "created_at" to now
        )
        val map2 = mutableMapOf<String, Any?>(
            "id" to id2,
            "user_id" to userId2,
            "friend_id" to userId1,
            "friend_username" to userId1,
            "friend_name" to userId1,
            "created_at" to now
        )
        val endpointPlain = "${getBaseUrl()}/rest/v1/friends"

        val res1 = executeHttp(endpointPlain, method = "POST", jsonBody = gson.toJson(map1), requireUserAuth = true)
        if (!res1.isSuccess) {
            return@withContext Result.failure(res1.exceptionOrNull() ?: Exception("Failed to create friendship pair 1"))
        }

        val res2 = executeHttp(endpointPlain, method = "POST", jsonBody = gson.toJson(map2), requireUserAuth = true)
        if (!res2.isSuccess) {
            return@withContext Result.failure(res2.exceptionOrNull() ?: Exception("Failed to create friendship pair 2"))
        }

        Result.success(true)
    }

    suspend fun removeFriendship(userId1: String, userId2: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val id1 = URLEncoder.encode("${userId1}_${userId2}", "UTF-8")
        val id2 = URLEncoder.encode("${userId2}_${userId1}", "UTF-8")
        val u1 = URLEncoder.encode(userId1.trim(), "UTF-8")
        val u2 = URLEncoder.encode(userId2.trim(), "UTF-8")

        // 1. Delete friends record by id
        val res1 = executeHttp("${getBaseUrl()}/rest/v1/friends?or=(id.eq.$id1,id.eq.$id2)", method = "DELETE", requireUserAuth = true)
        if (!res1.isSuccess) {
            return@withContext Result.failure(res1.exceptionOrNull() ?: Exception("Failed to remove friend records"))
        }
        // 2. Delete friends record by user pairs
        val res2 = executeHttp("${getBaseUrl()}/rest/v1/friends?or=(and(user_id_1.eq.$u1,user_id_2.eq.$u2),and(user_id_1.eq.$u2,user_id_2.eq.$u1))", method = "DELETE", requireUserAuth = true)
        if (!res2.isSuccess) {
            return@withContext Result.failure(res2.exceptionOrNull() ?: Exception("Failed to remove friend pair records"))
        }
        // 3. Delete any friend requests between these two users so they are not resurrected on next sync
        val res3 = executeHttp("${getBaseUrl()}/rest/v1/friend_requests?or=(and(sender_id.eq.$u1,recipient_id.eq.$u2),and(sender_id.eq.$u2,recipient_id.eq.$u1))", method = "DELETE", requireUserAuth = true)
        if (!res3.isSuccess) {
            return@withContext Result.failure(res3.exceptionOrNull() ?: Exception("Failed to cleanup friend request records"))
        }

        Result.success(true)
    }

    suspend fun getFriendsForUser(userId: String): Set<String> = withContext(Dispatchers.IO) {
        val cleanId = userId.trim()
        val res = executeHttp("${getBaseUrl()}/rest/v1/friends?select=*", method = "GET")
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
                val list: List<Map<String, Any?>> = gson.fromJson(json, listType) ?: emptyList()
                val friends = mutableSetOf<String>()
                list.forEach { map ->
                    val u1 = (map["user_id_1"] ?: map["user_id"] ?: map["sender_id"] ?: "").toString()
                    val u2 = (map["user_id_2"] ?: map["friend_id"] ?: map["recipient_id"] ?: "").toString()
                    if (u1.isNotBlank() && u1 == cleanId && u2.isNotBlank() && u2 != cleanId) friends.add(u2)
                    if (u2.isNotBlank() && u2 == cleanId && u1.isNotBlank() && u1 != cleanId) friends.add(u1)
                }
                friends
            } catch (e: Exception) {
                emptySet()
            }
        } ?: emptySet()
    }

    // ==========================================
    // DIRECT MESSAGES (CHAT)
    // ==========================================

    suspend fun sendDirectMessage(msg: DirectMessage): Result<Boolean> = withContext(Dispatchers.IO) {
        val record = SupabaseDirectMessageRecord(
            id = msg.id,
            senderId = msg.senderId,
            senderName = msg.senderName,
            senderAvatar = msg.senderAvatar,
            recipientId = msg.recipientId,
            recipientName = msg.recipientName,
            recipientAvatar = msg.recipientAvatar,
            text = msg.text,
            stampId = msg.stampId,
            stampTitle = msg.stampTitle,
            stampImageUrl = msg.stampImageUrl,
            stampLocation = msg.stampLocation,
            createdAt = msg.createdAt,
            isRead = msg.isRead
        )
        val json = gson.toJson(record)
        val endpoint = "${getBaseUrl()}/rest/v1/direct_messages?on_conflict=id"
        val res = executeHttp(endpoint, method = "POST", jsonBody = json, prefer = "resolution=merge-duplicates", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
    }

    suspend fun getMessagesForUser(userId: String): List<DirectMessage> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(userId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/direct_messages?or=(sender_id.eq.$encoded,recipient_id.eq.$encoded)&select=*&order=created_at.asc"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<SupabaseDirectMessageRecord>>() {}.type
                val list: List<SupabaseDirectMessageRecord> = gson.fromJson(json, listType) ?: emptyList()
                list.map {
                    DirectMessage(
                        id = it.id,
                        senderId = it.senderId,
                        senderName = it.senderName,
                        senderAvatar = it.senderAvatar ?: "https://i.pravatar.cc/150?u=${it.senderId}",
                        recipientId = it.recipientId,
                        recipientName = it.recipientName,
                        recipientAvatar = it.recipientAvatar ?: "https://i.pravatar.cc/150?u=${it.recipientId}",
                        text = it.text,
                        stampId = it.stampId,
                        stampTitle = it.stampTitle,
                        stampImageUrl = it.stampImageUrl,
                        stampLocation = it.stampLocation,
                        createdAt = it.createdAt ?: System.currentTimeMillis(),
                        isRead = it.isRead
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun markMessagesAsRead(senderId: String, recipientId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val json = gson.toJson(mapOf("is_read" to true))
        val sEncoded = URLEncoder.encode(senderId.trim(), "UTF-8")
        val rEncoded = URLEncoder.encode(recipientId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/direct_messages?sender_id=eq.$sEncoded&recipient_id=eq.$rEncoded&is_read=eq.false"
        val res = executeHttp(endpoint, method = "PATCH", jsonBody = json, requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Mark messages read failed"))
    }

    suspend fun deleteDirectMessage(messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(messageId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/direct_messages?id=eq.$encoded"
        val res = executeHttp(endpoint, method = "DELETE", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Delete direct message failed"))
    }

    private fun extractMissingColumn(errMsg: String): String? {
        val p1 = Regex("Could not find the ['\"]([^'\"]+)['\"] column", RegexOption.IGNORE_CASE).find(errMsg)
        if (p1 != null) return p1.groupValues[1]

        val p2 = Regex("column ['\"]([a_zA_Z0-9_]+)['\"] of relation", RegexOption.IGNORE_CASE).find(errMsg)
        if (p2 != null) return p2.groupValues[1]

        val p3 = Regex("column ['\"]([a_zA_Z0-9_]+)['\"] does not exist", RegexOption.IGNORE_CASE).find(errMsg)
        if (p3 != null) return p3.groupValues[1]

        val p4 = Regex("['\"]([a_zA_Z0-9_]+)['\"] column", RegexOption.IGNORE_CASE).find(errMsg)
        if (p4 != null) return p4.groupValues[1]

        return null
    }

    private fun executeUpsertWithSchemaFallback(
        endpoint: String,
        initialJsonMap: Map<String, Any?>,
        prefer: String = "resolution=merge-duplicates,return=minimal"
    ): Result<String> {
        val jsonMap = initialJsonMap
            .filterKeys { !missingSchemaColumns.contains(it) }
            .toMutableMap()
        var attempts = 0
        val maxAttempts = 12

        while (attempts < maxAttempts) {
            attempts++
            val res = executeHttp(endpoint, method = "POST", jsonBody = gson.toJson(jsonMap), prefer = prefer, requireUserAuth = true)
            if (res.isSuccess) {
                return res
            }
            val errMsg = res.exceptionOrNull()?.message ?: ""

            val isNotNullError = errMsg.contains("23502") || errMsg.contains("not-null constraint", ignoreCase = true)
            if (isNotNullError) {
                val notNullCol = extractMissingColumnFromNotNull(errMsg)
                if (notNullCol != null && !jsonMap.containsKey(notNullCol)) {
                    val defaultVal = getDefaultValueForColumn(notNullCol, jsonMap["user_id"]?.toString() ?: jsonMap["user_id_1"]?.toString())
                    jsonMap[notNullCol] = defaultVal
                    Log.w(TAG, "Supabase NOT NULL constraint on '$notNullCol', injected default value '$defaultVal' and retrying...")
                    continue
                }
            }

            val isSchemaMissingColumn = errMsg.contains("PGRST204") || errMsg.contains("schema cache", ignoreCase = true) || errMsg.contains("Could not find", ignoreCase = true)
            val extractedCol = extractMissingColumn(errMsg)

            val keyToRemove = when {
                isSchemaMissingColumn && extractedCol != null && jsonMap.containsKey(extractedCol) -> extractedCol
                isSchemaMissingColumn -> jsonMap.keys.find { k -> errMsg.contains("'$k'", ignoreCase = true) || errMsg.contains("\"$k\"", ignoreCase = true) }
                else -> null
            }

            if (keyToRemove != null) {
                missingSchemaColumns.add(keyToRemove)
                Log.w(TAG, "Supabase schema missing key '$keyToRemove', added to missing list and retrying... Error: $errMsg")
                jsonMap.remove(keyToRemove)
                continue
            }

            // If error is schema error (PGRST204 or missing column) but extractedCol is not in jsonMap,
            // fallback by pruning any optional non-id column remaining in jsonMap
            if (errMsg.contains("PGRST204") || errMsg.contains("schema cache", ignoreCase = true) || errMsg.contains("Could not find", ignoreCase = true)) {
                val primaryKeys = setOf("id", "user_id", "user_id_1", "user_id_2", "friend_id", "sender_id", "recipient_id", "author_id", "post_id")
                val optionalKey = jsonMap.keys.firstOrNull { it !in primaryKeys }
                if (optionalKey != null) {
                    missingSchemaColumns.add(optionalKey)
                    Log.w(TAG, "Supabase schema mismatch, pruning optional key '$optionalKey' and retrying... Error: $errMsg")
                    jsonMap.remove(optionalKey)
                    continue
                }
            }

            // Check if error is timestamp formatting issue on created_at
            if ((errMsg.contains("created_at", ignoreCase = true) || errMsg.contains("timestamp", ignoreCase = true) || errMsg.contains("syntax", ignoreCase = true)) && jsonMap.containsKey("created_at")) {
                val currentVal = jsonMap["created_at"]
                if (currentVal is Long) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        jsonMap["created_at"] = sdf.format(Date(currentVal))
                        continue
                    } catch (_: Exception) {}
                }
                // If conversion fails or is already string, remove created_at so DB DEFAULT applies
                missingSchemaColumns.add("created_at")
                jsonMap.remove("created_at")
                continue
            }

            return res
        }
        return Result.failure(Exception("Max attempts reached for Supabase upsert"))
    }

    private fun extractMissingColumnFromNotNull(errMsg: String): String? {
        val regex = """null value in column "([^"]+)" of relation""".toRegex()
        val match = regex.find(errMsg)
        return match?.groupValues?.get(1)
    }

    private fun getDefaultValueForColumn(colName: String, fallbackUserId: String? = null): Any {
        val lower = colName.lowercase()
        return when {
            lower.contains("username") || lower.contains("name") -> fallbackUserId ?: "user_default"
            lower.contains("avatar") || lower.contains("image") || lower.contains("url") -> ""
            lower.contains("status") -> "ACTIVE"
            lower.contains("created") || lower.contains("updated") || lower.contains("time") -> System.currentTimeMillis()
            else -> fallbackUserId ?: "default"
        }
    }

    // ==========================================
    // FEED POSTS (COMMUNITY & FRIENDS)
    // ==========================================

    suspend fun createFeedPost(post: FeedPost): Result<Boolean> = withContext(Dispatchers.IO) {
        val jsonMap = mutableMapOf<String, Any?>()
        if (post.id.isNotBlank()) jsonMap["id"] = post.id
        if (post.stampId.isNotBlank()) jsonMap["stamp_id"] = post.stampId
        if (post.stampUrl.isNotBlank()) jsonMap["stamp_url"] = post.stampUrl
        if (post.stampTitle.isNotBlank()) jsonMap["stamp_title"] = post.stampTitle
        if (!post.shape.isNullOrBlank()) jsonMap["shape"] = post.shape
        if (post.authorId.isNotBlank()) jsonMap["author_id"] = post.authorId
        if (post.authorName.isNotBlank()) jsonMap["author_name"] = post.authorName
        if (!post.authorAvatar.isNullOrBlank()) jsonMap["author_avatar"] = post.authorAvatar
        if (!post.caption.isNullOrBlank()) jsonMap["caption"] = post.caption
        jsonMap["audience_type"] = post.audienceType.name
        if (!post.circleId.isNullOrBlank()) jsonMap["circle_id"] = post.circleId
        if (!post.circleName.isNullOrBlank()) jsonMap["circle_name"] = post.circleName
        jsonMap["created_at"] = post.createdAt
        jsonMap["type"] = post.type.name
        if (!post.location.isNullOrBlank()) jsonMap["location"] = post.location

        val endpoint = "${getBaseUrl()}/rest/v1/feed_posts?on_conflict=id"
        val res = executeUpsertWithSchemaFallback(endpoint, jsonMap, prefer = "resolution=merge-duplicates,return=minimal")

        if (res.isSuccess) {
            Log.d(TAG, "Successfully created/updated feed post ${post.id} on Supabase")
            Result.success(true)
        } else {
            Log.w(TAG, "Failed to create feed post ${post.id}: ${res.exceptionOrNull()?.message}")
            Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun getFeedPosts(): List<SupabaseFeedPostRecord> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/rest/v1/feed_posts?select=*&order=created_at.desc&limit=100"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<SupabaseFeedPostRecord>>() {}.type
                val items: List<SupabaseFeedPostRecord>? = gson.fromJson(json, listType)
                items ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing getFeedPosts JSON", e)
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun addFeedReaction(reaction: SupabaseFeedReactionRecord): Result<Boolean> = withContext(Dispatchers.IO) {
        val jsonMap = mutableMapOf<String, Any?>(
            "id" to reaction.id,
            "post_id" to reaction.postId,
            "user_id" to reaction.userId,
            "user_name" to reaction.userName,
            "emoji" to reaction.emoji,
            "created_at" to reaction.createdAt
        )
        val endpoint = "${getBaseUrl()}/rest/v1/feed_reactions?on_conflict=id"
        val res = executeUpsertWithSchemaFallback(endpoint, jsonMap, prefer = "resolution=merge-duplicates")
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Error adding reaction"))
    }

    suspend fun deleteFeedReaction(postId: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val encPost = URLEncoder.encode(postId.trim(), "UTF-8")
        val encUser = URLEncoder.encode(userId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/feed_reactions?post_id=eq.$encPost&user_id=eq.$encUser"
        val res = executeHttp(endpoint, method = "DELETE", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Delete reaction failed"))
    }

    suspend fun getFeedReactions(): List<SupabaseFeedReactionRecord> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/rest/v1/feed_reactions?select=*&limit=500"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<SupabaseFeedReactionRecord>>() {}.type
                gson.fromJson(json, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun addFeedComment(comment: SupabaseFeedCommentRecord): Result<Boolean> = withContext(Dispatchers.IO) {
        val jsonMap = mutableMapOf<String, Any?>(
            "id" to comment.id,
            "post_id" to comment.postId,
            "author_id" to comment.authorId,
            "author_name" to comment.authorName,
            "author_avatar" to comment.authorAvatar,
            "content" to comment.content,
            "created_at" to comment.createdAt
        )
        val endpoint = "${getBaseUrl()}/rest/v1/feed_comments?on_conflict=id"
        val res = executeUpsertWithSchemaFallback(endpoint, jsonMap, prefer = "resolution=merge-duplicates")
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Error adding comment"))
    }

    suspend fun getFeedComments(): List<SupabaseFeedCommentRecord> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/rest/v1/feed_comments?select=*&order=created_at.asc&limit=500"
        val res = executeHttp(endpoint, method = "GET")
        res.getOrNull()?.let { json ->
            try {
                val listType = object : TypeToken<List<SupabaseFeedCommentRecord>>() {}.type
                gson.fromJson(json, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun deleteFeedPost(postId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(postId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/feed_posts?id=eq.$enc"
        val res = executeHttp(endpoint, method = "DELETE", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: IllegalStateException("Delete feed post failed"))
    }

    suspend fun deleteFeedComment(commentId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(commentId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/feed_comments?id=eq.$enc"
        val res = executeHttp(endpoint, method = "DELETE", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: IllegalStateException("Delete feed comment failed"))
    }

    suspend fun deleteFeedReply(replyId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(replyId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/feed_replies?id=eq.$enc"
        val res = executeHttp(endpoint, method = "DELETE", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: IllegalStateException("Delete feed reply failed"))
    }

    suspend fun deleteStamp(stampId: String, ownerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val encStamp = URLEncoder.encode(stampId.trim(), "UTF-8")
        val encOwner = URLEncoder.encode(ownerId.trim(), "UTF-8")
        val endpoint = "${getBaseUrl()}/rest/v1/stamps?id=eq.$encStamp&user_id=eq.$encOwner"
        val res = executeHttp(endpoint, method = "DELETE", requireUserAuth = true)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: IllegalStateException("Delete stamp failed"))
    }
}
