package com.samuelpart.ojodedios

import com.github.amsacode.predict4java.Satellite
import com.github.amsacode.predict4java.SatelliteFactory
import com.github.amsacode.predict4java.TLE
import java.util.Date

/** Posición calculada de un satélite: punto subsatelital y altitud. */
data class PosicionSatelite(
    val lat: Double,
    val lon: Double,
    val altitudKm: Double,
)

/**
 * Cálculo orbital con SGP4/SDP4.
 *
 * Los elementos orbitales (TLE) se descargan de CelesTrak y la posición se
 * calcula **en el teléfono**, no se pide a ningún servidor. Eso significa que
 * la capa de satélites funciona sin conexión una vez tiene los TLE, y que
 * cualquiera puede reproducir el cálculo: es matemática pública, no un feed.
 *
 * La librería es predict4java (com.github.davidmoten, licencia MIT), el port a
 * Java del código de Vallado, el mismo modelo que usa la versión web a través
 * de satellite.js.
 */
class Orbita(val satelite: Satelite) {

    private val calculador: Satellite? = try {
        val tle = TLE(arrayOf(satelite.nombre, satelite.linea1, satelite.linea2))
        SatelliteFactory.createSatellite(tle)
    } catch (e: Exception) {
        null
    }

    val valido: Boolean get() = calculador != null

    /** Posición en un instante dado, o null si el TLE no se pudo propagar. */
    fun posicion(momento: Date = Date()): PosicionSatelite? {
        val sat = calculador ?: return null
        return try {
            sat.calculateSatelliteVectors(momento)
            val punto = sat.calculateSatelliteGroundTrack() ?: return null
            val lat = punto.latitude
            val lon = punto.longitude
            if (lat.isNaN() || lon.isNaN()) return null
            PosicionSatelite(lat, lon, punto.altitude)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Traza orbital de los próximos [minutos].
     *
     * Se corta cada vez que la traza cruza el antimeridiano (±180° de longitud):
     * si no, Leaflet/osmdroid dibujarían una línea recta que cruza el mapa entero.
     */
    fun traza(minutos: Int = 100, pasoMinutos: Int = 2): List<List<Pair<Double, Double>>> {
        val segmentos = mutableListOf<List<Pair<Double, Double>>>()
        var actual = mutableListOf<Pair<Double, Double>>()
        val ahora = System.currentTimeMillis()

        var minuto = 0
        while (minuto <= minutos) {
            val p = posicion(Date(ahora + minuto * 60_000L))
            if (p != null) {
                val anterior = actual.lastOrNull()
                if (anterior != null && Math.abs(p.lon - anterior.second) > 180) {
                    if (actual.isNotEmpty()) segmentos.add(actual.toList())
                    actual = mutableListOf()
                }
                actual.add(p.lat to p.lon)
            }
            minuto += pasoMinutos
        }
        if (actual.isNotEmpty()) segmentos.add(actual.toList())
        return segmentos
    }
}
