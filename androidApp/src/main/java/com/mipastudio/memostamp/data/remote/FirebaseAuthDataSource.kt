package com.mipastudio.memostamp.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthDataSource private constructor() {

    private var firebaseAuth: FirebaseAuth? = null

    init {
        try {
            firebaseAuth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            // Firebase not initialized yet in offline/local mock test environment
            firebaseAuth = null
        }
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth?.currentUser

    suspend fun signInWithGoogle(context: Context, webClientId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleIdTokenCredential.idToken

            val auth = firebaseAuth ?: return@withContext Result.failure(IllegalStateException("Firebase Auth not initialized"))
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()

            val uid = authResult.user?.uid ?: return@withContext Result.failure(IllegalStateException("User UID is null"))
            Result.success(uid)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth?.signOut()
    }

    companion object {
        @Volatile
        private var INSTANCE: FirebaseAuthDataSource? = null

        fun getInstance(): FirebaseAuthDataSource {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseAuthDataSource().also { INSTANCE = it }
            }
        }
    }
}
