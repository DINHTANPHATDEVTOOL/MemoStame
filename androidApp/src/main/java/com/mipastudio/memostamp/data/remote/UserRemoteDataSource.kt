package com.mipastudio.memostamp.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.mipastudio.memostamp.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UsernameTakenException(username: String) : Exception("Username '$username' is already taken.")

class UserRemoteDataSource private constructor() {

    private var firestore: FirebaseFirestore? = null

    init {
        try {
            firestore = FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            firestore = null
        }
    }

    fun normalizeUsername(input: String): String {
        return input.trim().lowercase()
    }

    fun isValidUsername(username: String): Boolean {
        val normalized = normalizeUsername(username)
        val regex = "^[a-z0-9_.]{3,20}$".toRegex()
        return normalized.matches(regex)
    }

    suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext null
        try {
            val doc = db.collection("users").document(uid).get().await()
            if (!doc.exists()) return@withContext null

            UserProfile(
                uid = doc.getString("uid") ?: uid,
                username = doc.getString("username") ?: "",
                displayName = doc.getString("displayName") ?: "",
                avatarUrl = doc.getString("avatarUrl"),
                bio = doc.getString("bio") ?: "",
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext true
        val normalized = normalizeUsername(username)
        if (!isValidUsername(normalized)) return@withContext false

        try {
            val doc = db.collection("usernames").document(normalized).get().await()
            !doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createProfile(profile: UserProfile): Result<UserProfile> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.success(profile)
        val normalized = normalizeUsername(profile.username)
        if (!isValidUsername(normalized)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid username format. Must be 3-20 characters (a-z, 0-9, _, .)"))
        }

        try {
            db.runTransaction { transaction ->
                val usernameRef = db.collection("usernames").document(normalized)
                val userRef = db.collection("users").document(profile.uid)

                val usernameDoc = transaction.get(usernameRef)
                if (usernameDoc.exists()) {
                    throw UsernameTakenException(normalized)
                }

                val finalProfile = profile.copy(
                    username = normalized,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(usernameRef, mapOf("uid" to profile.uid))
                transaction.set(
                    userRef,
                    mapOf(
                        "uid" to finalProfile.uid,
                        "username" to finalProfile.username,
                        "displayName" to finalProfile.displayName,
                        "avatarUrl" to finalProfile.avatarUrl,
                        "bio" to finalProfile.bio,
                        "createdAt" to finalProfile.createdAt,
                        "updatedAt" to finalProfile.updatedAt
                    )
                )
            }.await()

            Result.success(profile.copy(username = normalized))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserRemoteDataSource? = null

        fun getInstance(): UserRemoteDataSource {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRemoteDataSource().also { INSTANCE = it }
            }
        }
    }
}
