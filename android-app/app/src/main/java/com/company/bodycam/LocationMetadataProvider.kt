package com.company.bodycam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class LocationMetadataProvider(
    private val context: Context
) {

    fun getLatestLocationMetadata(): LocationSnapshot? {
        if (!hasLocationPermission()) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val candidates = buildList {
            addIfLocation(locationManager, LocationManager.GPS_PROVIDER)
            addIfLocation(locationManager, LocationManager.NETWORK_PROVIDER)
            addIfLocation(locationManager, LocationManager.PASSIVE_PROVIDER)
        }

        val bestLocation = candidates.maxByOrNull { it.time } ?: return null
        return LocationSnapshot(
            latitude = bestLocation.latitude,
            longitude = bestLocation.longitude,
            altitudeMeters = bestLocation.altitude.takeIf { bestLocation.hasAltitude() },
            locationAccuracyMeters = bestLocation.accuracy.toDouble().takeIf { bestLocation.hasAccuracy() }
        )
    }

    private fun MutableList<Location>.addIfLocation(locationManager: LocationManager, provider: String) {
        runCatching { locationManager.getLastKnownLocation(provider) }
            .getOrNull()
            ?.let(::add)
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val locationAccuracyMeters: Double?
)
