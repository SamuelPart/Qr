package com.samuelpart.ojodedios

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Acceso a las fuentes públicas abiertas.
 *
 * Cada función devuelve objetos ya normalizados a [PuntoMapa], para que la capa
 * de mapa no sepa nada de formatos: GeoJSON, arrays de TfL y JSON propio acaban
 * todos en la misma forma.
 *
 * Si una fuente falla, se lanza [ErrorRed] y la capa lo refleja en la interfaz.
 * Cuando existe instantánea en assets, se usa como respaldo.
 */
class Repositorio(private val contexto: Context) {

    // ─────────────────────────── Cámaras ───────────────────────────

    /**
     * Transport for London publica sus cámaras de tráfico como datos abiertos.
     * Cada cámara tiene un fotograma (JPG, se refresca cada ~5 min) y, en muchas,
     * un clip corto en MP4.
     */
    suspend fun camarasLondres(): List<PuntoMapa> {
        val respuesta = Red.arreglo("https://api.tfl.gov.uk/Place/Type/JamCam")
        val lista = mutableListOf<PuntoMapa>()

        for (i in 0 until respuesta.length()) {
            val lugar = respuesta.optJSONObject(i) ?: continue
            val id = lugar.optString("id").removePrefix("JamCams_")
            if (id.isBlank()) continue

            val propiedades = mutableMapOf<String, String>()
            lugar.optJSONArray("additionalProperties")?.let { props ->
                for (j in 0 until props.length()) {
                    val p = props.optJSONObject(j) ?: continue
                    propiedades[p.optString("key")] = p.optString("value")
                }
            }

            lista.add(
                PuntoMapa(
                    id = "tfl-$id",
                    nombre = lugar.optString("commonName", "Cámara TfL"),
                    lat = lugar.optDouble("lat"),
                    lon = lugar.optDouble("lon"),
                    capa = IdCapa.CAMARAS,
                    fuente = "TfL · Londres",
                    imagenUrl = propiedades["imageUrl"]
                        ?: "https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/$id.jpg",
                    videoUrl = propiedades["videoUrl"]
                        ?: "https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/$id.mp4",
                    detalle = listOf(
                        "Vista" to (propiedades["view"]?.takeIf { it.isNotBlank() } ?: "no declarada"),
                        "Estado" to if (propiedades["available"] != "false") "disponible" else "sin señal ahora",
                        "Identificador" to id,
                    ),
                )
            )
        }
        return lista
    }

    /**
     * Fintraffic publica las cámaras meteorológicas de las carreteras finlandesas.
     * Cada estación puede tener varias cámaras (presets); se usa la primera.
     */
    suspend fun camarasFinlandia(): List<PuntoMapa> {
        val raiz = Red.objeto("https://tie.digitraffic.fi/api/weathercam/v1/stations")
        val features = raiz.optJSONArray("features") ?: JSONArray()
        val lista = mutableListOf<PuntoMapa>()

        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val props = f.optJSONObject("properties") ?: JSONObject()
            val presets = props.optJSONArray("presets")
            val primerPreset = if (presets != null && presets.length() > 0) {
                presets.optJSONObject(0)?.optString("id")
            } else null

            lista.add(
                PuntoMapa(
                    id = "fi-${props.optString("id")}",
                    nombre = props.optString("name", "Cámara Fintraffic"),
                    lat = coords.optDouble(1),
                    lon = coords.optDouble(0),
                    capa = IdCapa.CAMARAS,
                    fuente = "Fintraffic · Finlandia",
                    imagenUrl = primerPreset?.let { "https://weathercam.digitraffic.fi/$it.jpg" },
                    detalle = listOf(
                        "Estación" to props.optString("id"),
                        "Cámaras" to (presets?.length() ?: 0).toString(),
                    ),
                )
            )
        }
        return lista
    }

    // ─────────────────────────── Sismos ───────────────────────────

    /** Sismología del USGS, últimas 24 h, actualizada cada 5 minutos. */
    suspend fun sismos(): List<PuntoMapa> {
        val raiz = try {
            Red.objeto("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson")
        } catch (e: Exception) {
            Red.activo(contexto, "quakes")
        }
        val features = raiz.optJSONArray("features") ?: JSONArray()
        val lista = mutableListOf<PuntoMapa>()

        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val p = f.optJSONObject("properties") ?: JSONObject()
            val magnitud = p.optDouble("mag", 0.0)
            val hora = p.optLong("time")

            lista.add(
                PuntoMapa(
                    id = "usgs-${f.optString("id")}",
                    nombre = "M${"%.1f".format(magnitud)} · ${p.optString("place", "sin localizar")}",
                    lat = coords.optDouble(1),
                    lon = coords.optDouble(0),
                    capa = IdCapa.SISMOS,
                    fuente = "USGS",
                    destacado = magnitud >= 5.0,
                    detalle = listOf(
                        "Magnitud" to "%.1f".format(magnitud),
                        "Profundidad" to "${coords.optDouble(2, 0.0).toInt()} km",
                        "Hora (UTC)" to horaUTC(hora),
                        "Ficha" to p.optString("url"),
                    ),
                )
            )
        }
        return lista
    }

    // ─────────────────────────── Vuelos ───────────────────────────

    /**
     * Posiciones ADS-B alrededor de un punto.
     *
     * Las aeronaves emiten su posición por radio a 1090 MHz y cualquiera con un
     * receptor de 30 € la capta. No es una filtración: es una emisión que existe
     * por seguridad aérea.
     */
    suspend fun vuelos(lat: Double, lon: Double, radioMn: Int): List<PuntoMapa> {
        val radio = radioMn.coerceIn(20, 250)
        val raiz = Red.objeto(String.format(java.util.Locale.US,
            "https://api.airplanes.live/v2/point/%.4f/%.4f/%d", lat, lon, radio))
        val aviones = raiz.optJSONArray("ac") ?: JSONArray()
        val lista = mutableListOf<PuntoMapa>()

        for (i in 0 until aviones.length()) {
            val a = aviones.optJSONObject(i) ?: continue
            val la = a.optDouble("lat", Double.NaN)
            val lo = a.optDouble("lon", Double.NaN)
            if (la.isNaN() || lo.isNaN()) continue

            val indicativo = a.optString("flight").trim().ifBlank { a.optString("hex") }
            val altitud = if (a.opt("alt_baro") is String) "en tierra"
            else "${a.optInt("alt_baro")} ft"

            lista.add(
                PuntoMapa(
                    id = "adsb-${a.optString("hex")}",
                    nombre = indicativo.ifBlank { "Aeronave" },
                    lat = la, lon = lo,
                    capa = IdCapa.VUELOS,
                    fuente = "ADS-B · airplanes.live",
                    detalle = listOf(
                        "Matrícula" to a.optString("r", "—"),
                        "Modelo" to a.optString("t", "—"),
                        "Altitud" to altitud,
                        "Velocidad" to "${a.optInt("gs")} nudos",
                        "Rumbo" to "${a.optInt("track")}°",
                    ),
                )
            )
        }
        return lista
    }

    // ─────────────────────────── Barcos ───────────────────────────

    /**
     * Tráfico marítimo por AIS. Los buques grandes están obligados por convenio
     * internacional a emitir su posición: es tráfico público, igual que el ADS-B.
     * Esta fuente cubre el mar Báltico.
     */
    suspend fun barcos(): List<PuntoMapa> {
        val raiz = Red.objeto("https://meri.digitraffic.fi/api/ais/v1/locations")
        val features = raiz.optJSONArray("features") ?: JSONArray()
        val lista = mutableListOf<PuntoMapa>()

        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val p = f.optJSONObject("properties") ?: JSONObject()
            val mmsi = p.optString("mmsi")

            lista.add(
                PuntoMapa(
                    id = "ais-$mmsi",
                    nombre = p.optString("name").ifBlank { "MMSI $mmsi" },
                    lat = coords.optDouble(1),
                    lon = coords.optDouble(0),
                    capa = IdCapa.BARCOS,
                    fuente = "Digitraffic · mar Báltico",
                    detalle = listOf(
                        "MMSI" to mmsi,
                        "Estado" to (ESTADOS_NAVEGACION[p.optInt("navStat", -1)] ?: "—"),
                        "Velocidad" to "${p.optDouble("sog", 0.0)} nudos",
                        "Rumbo" to "${p.optDouble("cog", 0.0)}°",
                        "Destino" to p.optString("destination", "—"),
                    ),
                )
            )
        }
        return lista
    }

    // ─────────────────────────── Radar ───────────────────────────

    /**
     * RainViewer publica la lista de fotogramas de radar disponibles. Devuelve el
     * más reciente; el mapa lo monta como capa de mosaicos.
     */
    suspend fun ultimoFotogramaRadar(): Long {
        val raiz = Red.objeto("https://api.rainviewer.com/public/weather-maps.json")
        val pasados = raiz.optJSONObject("radar")?.optJSONArray("past")
            ?: throw ErrorRed("RainViewer no devolvió fotogramas")
        if (pasados.length() == 0) throw ErrorRed("RainViewer no devolvió fotogramas")
        return pasados.optJSONObject(pasados.length() - 1)?.optLong("time")
            ?: throw ErrorRed("fotograma sin marca de tiempo")
    }

    // ─────────────────────────── Satélites ───────────────────────────

    /**
     * Elementos orbitales de CelesTrak. Se leen del paquete de la app: cambian
     * despacio y así la capa funciona aunque no haya red.
     */
    suspend fun satelites(): List<Satelite> {
        val raiz = Red.activo(contexto, "tle")
        val lista = raiz.optJSONArray("satelites") ?: JSONArray()
        val salida = mutableListOf<Satelite>()

        for (i in 0 until lista.length()) {
            val s = lista.optJSONObject(i) ?: continue
            salida.add(
                Satelite(
                    nombre = s.optString("nombre"),
                    norad = s.optInt("norad"),
                    tipo = s.optString("tipo"),
                    linea1 = s.optString("l1"),
                    linea2 = s.optString("l2"),
                )
            )
        }
        return salida
    }

    // ─────────────────────────── Utilidades ───────────────────────────

    private fun horaUTC(millis: Long): String {
        if (millis <= 0) return "—"
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(millis))
    }

    companion object {
        private val ESTADOS_NAVEGACION = mapOf(
            0 to "en navegación a motor",
            1 to "fondeado",
            2 to "sin gobierno",
            3 to "maniobra restringida",
            4 to "restringido por calado",
            5 to "amarrado",
            6 to "varado",
            7 to "pescando",
            8 to "a vela",
            15 to "indefinido",
        )
    }
}
