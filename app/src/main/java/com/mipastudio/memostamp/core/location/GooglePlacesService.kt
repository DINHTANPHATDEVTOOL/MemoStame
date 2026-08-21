package com.mipastudio.memostamp.core.location

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.mipastudio.memostamp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object GooglePlacesService {
    private const val TAG = "GooglePlacesService"
    private var placesClient: PlacesClient? = null
    private var sessionToken: AutocompleteSessionToken? = null

    fun isPlacesSdkAvailable(): Boolean {
        val apiKey = try {
            BuildConfig::class.java.getField("GOOGLE_MAPS_API_KEY").get(null) as? String
        } catch (e: Exception) {
            ""
        }
        return !apiKey.isNullOrBlank() && !apiKey.contains("Placeholder")
    }

    private fun getOrInitClient(context: Context): PlacesClient? {
        if (placesClient != null) return placesClient
        val apiKey = try {
            BuildConfig::class.java.getField("GOOGLE_MAPS_API_KEY").get(null) as? String
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNullOrBlank() || apiKey.contains("Placeholder")) {
            return null
        }

        try {
            if (!Places.isInitialized()) {
                Places.initialize(context.applicationContext, apiKey)
            }
            placesClient = Places.createClient(context.applicationContext)
            return placesClient
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Google Places SDK: ${e.message}")
            return null
        }
    }

    fun getSessionToken(): AutocompleteSessionToken {
        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance()
        }
        return sessionToken!!
    }

    fun resetSessionToken() {
        sessionToken = AutocompleteSessionToken.newInstance()
    }

    /**
     * Autocomplete search with real Google Places SDK
     */
    suspend fun searchPredictions(
        context: Context,
        query: String,
        userLat: Double? = null,
        userLng: Double? = null
    ): List<GroundedPlace> = withContext(Dispatchers.IO) {
        val client = getOrInitClient(context) ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext emptyList()

        suspendCancellableCoroutine<List<GroundedPlace>> { continuation ->
            val requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setSessionToken(getSessionToken())
                .setCountries(listOf("VN"))

            if (userLat != null && userLng != null) {
                val delta = 0.05 // ~5km radius bounds
                val bounds = RectangularBounds.newInstance(
                    LatLng(userLat - delta, userLng - delta),
                    LatLng(userLat + delta, userLng + delta)
                )
                requestBuilder.setLocationBias(bounds)
            }

            client.findAutocompletePredictions(requestBuilder.build())
                .addOnSuccessListener { response ->
                    val places = response.autocompletePredictions.map { pred ->
                        val primary = pred.getPrimaryText(null).toString()
                        val secondary = pred.getSecondaryText(null).toString()
                        val full = pred.getFullText(null).toString()

                        GroundedPlace(
                            name = primary,
                            address = secondary.ifBlank { full },
                            category = if (primary.contains("Cafe", ignoreCase = true) || primary.contains("Cà phê", ignoreCase = true)) "CAFE" else "LANDMARK",
                            description = "Địa điểm được định vị chính xác qua Google Places",
                            stampTitleSuggestion = primary,
                            rating = null,
                            distanceMeters = null,
                            distanceFormatted = null,
                            isGroundedWithMaps = true
                        )
                    }
                    continuation.resume(places)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Places Autocomplete error: ${e.message}")
                    continuation.resume(emptyList())
                }
        }
    }
}
