package com.samuelpart.ojodedios

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.osmdroid.views.MapView

/**
 * Actividad única. Se encarga de tres cosas que Compose no puede hacer sola:
 * preparar osmdroid antes de crear la vista, atar el ciclo de vida del MapView
 * al de la pantalla, y pedir el permiso de ubicación.
 */
class MainActivity : ComponentActivity() {

    private val modelo: OjoViewModel by viewModels()
    private var mapa: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid exige configurarse antes de construir cualquier MapView.
        Fuentes.preparar(this)

        setContent {
            TemaOjoDeDios {
                val controlador = remember { ControladorMapa(this) }
                var fuenteBase by remember {
                    mutableStateOf(
                        if (Fuentes.sistemaEnOscuro(this)) Fuentes.CARTO_OSCURO
                        else Fuentes.CARTO_CLARO
                    )
                }

                mapa = controlador.vista

                PantallaOjoDeDios(
                    vm = modelo,
                    controlador = controlador,
                    fuenteBase = fuenteBase,
                    alCambiarBase = { fuenteBase = it },
                    alPedirUbicacion = { pedirUbicacion(controlador) },
                )
            }
        }
    }

    // ─────────────────────── Ciclo de vida del mapa ───────────────────────
    // osmdroid necesita saber cuándo la pantalla deja de estar visible para
    // dejar de descargar mosaicos. Sin esto, sigue trabajando en segundo plano.

    override fun onResume() {
        super.onResume()
        mapa?.onResume()
    }

    override fun onPause() {
        mapa?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapa?.onDetach()
        mapa = null
        super.onDestroy()
    }

    // ─────────────────────── Ubicación ───────────────────────

    private val pedirPermisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { concedidos ->
        if (concedidos.values.any { it }) {
            mapa?.let { obtenerUbicacion() }
        }
    }

    private fun pedirUbicacion(controlador: ControladorMapa) {
        mapa = controlador.vista
        val fina = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fina) obtenerUbicacion() else pedirPermisoUbicacion.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    /**
     * Una sola lectura de posición: primero la última conocida (instantánea) y,
     * si no hay, una petición puntual. No se deja el GPS encendido escuchando
     * indefinidamente — esta app no necesita seguir al usuario.
     */
    private fun obtenerUbicacion() {
        val gestor = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        try {
            val previa = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            ).mapNotNull { proveedor ->
                if (gestor.isProviderEnabled(proveedor)) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        .let { if (it == PackageManager.PERMISSION_GRANTED) gestor.getLastKnownLocation(proveedor) else null }
                } else null
            }.maxByOrNull { it.time }

            if (previa != null) {
                usarUbicacion(previa)
            }

            val escucha = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    usarUbicacion(loc)
                    gestor.removeUpdates(this)
                }

                @Deprecated("Requerido por la interfaz en API anteriores a 30")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val proveedor = when {
                gestor.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return
            }
            gestor.requestLocationUpdates(proveedor, 0L, 0f, escucha, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // El permiso se revocó entre la comprobación y la llamada.
        }
    }

    private fun usarUbicacion(loc: Location) {
        modelo.fijarMiUbicacion(loc.latitude, loc.longitude)
        mapa?.controller?.animateTo(org.osmdroid.util.GeoPoint(loc.latitude, loc.longitude))
        mapa?.controller?.setZoom(14.0)
    }
}
