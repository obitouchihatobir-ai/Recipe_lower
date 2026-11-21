package app.recetario.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

data class Coordinates(val latitude: Double, val longitude: Double)

/** La ubicación aproximada del teléfono, si se puede saber ahora (las pruebas pasan una falsa). */
fun interface LocationProvider {
    fun lastKnown(): Coordinates?
}

/**
 * La última ubicación aproximada que conoce `LocationManager` (red, o la pasiva si no hay), sin Play Services. Null
 * si no hay permiso de ubicación aproximada o el teléfono aún no tiene ninguna. Nunca pide la ubicación precisa.
 */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val context = context.applicationContext

    override fun lastKnown(): Coordinates? {
        if (!hasCoarseLocationPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                ?.let { Coordinates(it.latitude, it.longitude) }
        } catch (_: SecurityException) {
            null
        }
    }
}

fun hasCoarseLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
