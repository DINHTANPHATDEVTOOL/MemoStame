package com.mipastudio.memostamp.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    fun isConnectedToWifiOrNetwork(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (e: Exception) {
            true
        }
    }

    fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    fun isLocationAndNetworkReady(context: Context): Boolean {
        return hasLocationPermission(context) && isConnectedToWifiOrNetwork(context)
    }

    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun fetchCurrentLocation(context: Context, onLocationRetrieved: (String) -> Unit) {
        if (!hasLocationPermission(context)) {
            onLocationRetrieved("Da Lat, Vietnam")
            return
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                onLocationRetrieved("Da Lat, Vietnam")
                return
            }

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            var lastKnownLocation: Location? = null
            if (isGpsEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastKnownLocation == null && isNetworkEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (lastKnownLocation != null) {
                processLocation(context, lastKnownLocation, onLocationRetrieved)
            } else {
                // Request live fix from GPS or Network provider
                val provider = when {
                    isGpsEnabled -> LocationManager.GPS_PROVIDER
                    isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    val mainHandler = Handler(Looper.getMainLooper())
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            try {
                                locationManager.removeUpdates(this)
                            } catch (e: Exception) { e.printStackTrace() }
                            processLocation(context, location, onLocationRetrieved)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    try {
                        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        // Timeout fallback after 4 seconds
                        mainHandler.postDelayed({
                            try {
                                locationManager.removeUpdates(listener)
                            } catch (e: Exception) { e.printStackTrace() }
                        }, 4000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onLocationRetrieved("Ho Chi Minh City, Vietnam")
                    }
                } else {
                    onLocationRetrieved("Ho Chi Minh City, Vietnam")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onLocationRetrieved("Da Lat, Vietnam")
        }
    }

    @Suppress("DEPRECATION")
    private fun processLocation(context: Context, location: Location, onResult: (String) -> Unit) {
        val lat = location.latitude
        val lng = location.longitude

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val address = addresses.firstOrNull()
                        val locality = address?.subLocality ?: address?.locality ?: address?.subAdminArea ?: address?.adminArea
                        val country = address?.countryName ?: "Vietnam"
                        val result = if (locality != null) "$locality, $country" else country
                        onResult(result)
                    }
                    return
                } else {
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val address = addresses?.firstOrNull()
                    val locality = address?.subLocality ?: address?.locality ?: address?.subAdminArea ?: address?.adminArea
                    val country = address?.countryName ?: "Vietnam"
                    val result = if (locality != null) "$locality, $country" else country
                    onResult(result)
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onResult("Da Lat, Vietnam")
    }

    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun fetchCurrentLocationCoordinates(context: Context, onLocationRetrieved: (Location?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onLocationRetrieved(null)
            return
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                onLocationRetrieved(null)
                return
            }

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            var lastKnownLocation: Location? = null
            if (isGpsEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastKnownLocation == null && isNetworkEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (lastKnownLocation != null) {
                onLocationRetrieved(lastKnownLocation)
            } else {
                val provider = when {
                    isGpsEnabled -> LocationManager.GPS_PROVIDER
                    isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    val mainHandler = Handler(Looper.getMainLooper())
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            try {
                                locationManager.removeUpdates(this)
                            } catch (e: Exception) { e.printStackTrace() }
                            onLocationRetrieved(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    try {
                        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        mainHandler.postDelayed({
                            try {
                                locationManager.removeUpdates(listener)
                            } catch (e: Exception) { e.printStackTrace() }
                        }, 3500)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onLocationRetrieved(null)
                    }
                } else {
                    onLocationRetrieved(null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onLocationRetrieved(null)
        }
    }

    @Suppress("DEPRECATION")
    fun fetchNearbyPlaces(context: Context, onPlacesRetrieved: (List<String>) -> Unit) {
        if (!hasLocationPermission(context)) {
            onPlacesRetrieved(
                listOf(
                    "Đà Lạt, Vietnam",
                    "Quảng trường Lâm Viên, Đà Lạt",
                    "Hồ Xuân Hương, Đà Lạt",
                    "Chợ Đà Lạt",
                    "TP. Hồ Chí Minh, Vietnam"
                )
            )
            return
        }

        fetchCurrentLocationCoordinates(context) { location ->
            if (location == null) {
                onPlacesRetrieved(
                    listOf(
                        "Đà Lạt, Vietnam",
                        "Quảng trường Lâm Viên, Đà Lạt",
                        "Hồ Xuân Hương, Đà Lạt",
                        "Chợ Đà Lạt",
                        "TP. Hồ Chí Minh, Vietnam"
                    )
                )
                return@fetchCurrentLocationCoordinates
            }

            val lat = location.latitude
            val lng = location.longitude

            Thread {
                val places = mutableListOf<String>()

                try {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            var fetched: List<android.location.Address>? = null
                            val lock = java.lang.Object()
                            geocoder.getFromLocation(lat, lng, 8) { list ->
                                synchronized(lock) {
                                    fetched = list
                                    lock.notifyAll()
                                }
                            }
                            synchronized(lock) {
                                if (fetched == null) lock.wait(2000)
                            }
                            fetched ?: emptyList()
                        } else {
                            geocoder.getFromLocation(lat, lng, 8) ?: emptyList()
                        }

                        for (addr in addresses) {
                            val feature = addr.featureName
                            val street = addr.thoroughfare
                            val subLoc = addr.subLocality ?: addr.locality
                            val admin = addr.adminArea ?: addr.subAdminArea

                            if (!feature.isNullOrBlank() && feature != street && !feature.matches(Regex("^[0-9-]+$"))) {
                                val candidate = if (subLoc != null) "$feature, $subLoc" else "$feature, $admin"
                                if (!places.contains(candidate)) places.add(candidate)
                            }
                            if (!street.isNullOrBlank()) {
                                val candidate = if (subLoc != null) "$street, $subLoc" else "$street, $admin"
                                if (!places.contains(candidate)) places.add(candidate)
                            }
                            if (!subLoc.isNullOrBlank() && !admin.isNullOrBlank()) {
                                val candidate = "$subLoc, $admin"
                                if (!places.contains(candidate)) places.add(candidate)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (places.size < 3) {
                    try {
                        val urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1"
                        val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", "MemoStampAndroid/1.0")
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val json = conn.inputStream.bufferedReader().use { it.readText() }
                            val jsonObj = org.json.JSONObject(json)
                            val addressObj = jsonObj.optJSONObject("address")
                            if (addressObj != null) {
                                val amenity = addressObj.optString("amenity", addressObj.optString("building", ""))
                                val road = addressObj.optString("road", addressObj.optString("pedestrian", ""))
                                val suburb = addressObj.optString("suburb", addressObj.optString("quarter", addressObj.optString("city_district", "")))
                                val city = addressObj.optString("city", addressObj.optString("town", addressObj.optString("province", "")))

                                if (amenity.isNotBlank()) places.add("$amenity, $city")
                                if (road.isNotBlank() && suburb.isNotBlank()) places.add("$road, $suburb")
                                if (suburb.isNotBlank() && city.isNotBlank()) places.add("$suburb, $city")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val curatedPopular = listOf(
                    "Quảng trường Lâm Viên, Đà Lạt",
                    "Hồ Xuân Hương, Đà Lạt",
                    "Chợ Đêm Đà Lạt",
                    "Túi Mơ To Cafe, Đà Lạt",
                    "Chợ Bến Thành, Quận 1, TP.HCM",
                    "Phố đi bộ Nguyễn Huệ, TP.HCM",
                    "Hồ Hoàn Kiếm, Hà Nội"
                )
                for (p in curatedPopular) {
                    if (!places.contains(p)) places.add(p)
                }

                val finalResults = places.distinct().take(8)
                Handler(Looper.getMainLooper()).post {
                    onPlacesRetrieved(finalResults)
                }
            }.start()
        }
    }

    private val samplePois = listOf(
        // Tourist spots
        "Quảng trường Lâm Viên, Đà Lạt",
        "Hồ Xuân Hương, Đà Lạt",
        "Thung Lũng Tình Yêu, Đà Lạt",
        "Chợ Đêm Đà Lạt",
        "Dinh 3 Bảo Đại, Đà Lạt",
        "Thác Datanla, Đà Lạt",
        "Thiền Viện Trúc Lâm, Đà Lạt",
        "Chợ Bến Thành, Quận 1, TP.HCM",
        "Phố đi bộ Nguyễn Huệ, TP.HCM",
        "Nhà thờ Đức Bà, TP.HCM",
        "Landmark 81, TP.HCM",
        "Hồ Hoàn Kiếm, Hà Nội",
        "Phố cổ Hội An, Quảng Nam",
        "Bà Nà Hills, Đà Nẵng",
        "Cầu Vàng, Đà Nẵng",
        "Bãi Sao, Phú Quốc",
        "Đỉnh Fansipan, Sapa",
        // Cafes & Restaurants
        "Túi Mơ To Cafe, Đà Lạt",
        "Quán Cà Phê Mê Linh, Đà Lạt",
        "Tiệm Cà Phê Tùng, Đà Lạt",
        "Lẩu Gà Lá É Tao Ngộ, Đà Lạt",
        "Quán Bánh Căn Tăng Bạt Hổ, Đà Lạt",
        "Cà Phê Chung Cư 42 Nguyễn Huệ, TP.HCM",
        "Cà Phê Giảng, Hà Nội",
        "Phở Thìn Bờ Hồ, Hà Nội",
        "Bánh Mỳ Phượng, Hội An",
        "Cà Phê Muối, Huế"
    )

    @Suppress("DEPRECATION")
    fun searchPlaces(
        context: Context,
        query: String,
        categoryFilter: String = "ALL",
        onPlacesRetrieved: (List<String>) -> Unit
    ) {
        val trimmed = query.trim()

        Thread {
            val results = mutableListOf<String>()

            // 1. Filter local curated POIs
            val matchedLocal = samplePois.filter { poi ->
                if (trimmed.isEmpty()) {
                    when (categoryFilter) {
                        "ATTRACTIONS" -> poi.contains("Lâm Viên") || poi.contains("Tháp") || poi.contains("Thung Lũng") || poi.contains("Hồ") || poi.contains("Phố") || poi.contains("Chợ") || poi.contains("Bà Nà") || poi.contains("Fansipan") || poi.contains("Nhà thờ")
                        "CAFES" -> poi.contains("Cafe") || poi.contains("Cà Phê") || poi.contains("Lẩu") || poi.contains("Bánh") || poi.contains("Phở") || poi.contains("Tiệm") || poi.contains("Quán")
                        else -> true
                    }
                } else {
                    poi.lowercase().contains(trimmed.lowercase())
                }
            }
            results.addAll(matchedLocal)

            // 2. Query Geocoder search if query is non-empty
            if (trimmed.length >= 2 && Geocoder.isPresent()) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        var fetched: List<android.location.Address>? = null
                        val lock = java.lang.Object()
                        geocoder.getFromLocationName(trimmed, 5) { list ->
                            synchronized(lock) {
                                fetched = list
                                lock.notifyAll()
                            }
                        }
                        synchronized(lock) {
                            if (fetched == null) lock.wait(1500)
                        }
                        fetched ?: emptyList()
                    } else {
                        geocoder.getFromLocationName(trimmed, 5) ?: emptyList()
                    }

                    for (addr in addresses) {
                        val feature = addr.featureName
                        val subLoc = addr.subLocality ?: addr.locality
                        val admin = addr.adminArea ?: addr.countryName ?: "Vietnam"
                        val candidate = if (!feature.isNullOrBlank()) {
                            if (subLoc != null) "$feature, $subLoc" else "$feature, $admin"
                        } else {
                            if (subLoc != null) "$subLoc, $admin" else admin
                        }
                        if (!results.contains(candidate)) {
                            results.add(candidate)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (results.isEmpty() && trimmed.isNotBlank()) {
                results.add(trimmed)
            }

            val finalOutput = results.distinct().take(8)
            Handler(Looper.getMainLooper()).post {
                onPlacesRetrieved(finalOutput)
            }
        }.start()
    }
}



