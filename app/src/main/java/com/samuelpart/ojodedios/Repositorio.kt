package com.samuelpart.ojodedios

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Cuántas cámaras publica Hong Kong en total. La capa dibuja un tope y la
     * interfaz lo dice con este número, para que nadie crea que son todas.
     */
    var publicadasHongKong: Int = 0
        private set

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

    /**
     * El Departamento de Transporte de Hong Kong publica el índice de sus
     * cámaras y el fotograma en vivo de cada una. El índice es un fichero de
     * texto con un registro por cámara y estos campos, separados por
     * tabuladores:
     *
     *   clave · región · distrito · nombre · X · Y · latitud · longitud · imagen
     *
     * Las dos coordenadas X e Y de en medio son de la cuadrícula local y aquí
     * no sirven: se usan la latitud y la longitud que vienen después.
     *
     * El lector es tolerante a propósito. Se apoya en las direcciones de imagen
     * para separar un registro de otro y, dentro de cada registro, busca los
     * números empezando por el final: en Hong Kong la latitud cae entre 22 y 23
     * y la longitud entre 113 y 115. Si no aparecen las dos, esa cámara se
     * descarta. Aquí no se inventa ninguna coordenada.
     */
    suspend fun camarasHongKong(): List<PuntoMapa> {
        val contenido = Red.texto(URL_HONG_KONG, tiempoMaxMs = 30_000)
        val lista = mutableListOf<PuntoMapa>()

        var anterior = 0
        for (coincidencia in PATRON_IMAGEN_HK.findAll(contenido)) {
            val trozo = contenido.substring(anterior, coincidencia.range.first)
            anterior = coincidencia.range.last + 1

            val clave = coincidencia.groupValues[1]
            val coordenadas = coordenadasHongKong(trozo) ?: continue

            val nombre = nombreHongKong(trozo)
            val region = REGIONES_HK.firstOrNull { trozo.contains(it) }
            val distrito = distritoHongKong(trozo, region, nombre)

            lista.add(
                PuntoMapa(
                    id = "hk-$clave",
                    nombre = nombre.ifBlank { "Cámara de tráfico $clave" },
                    lat = coordenadas.first,
                    lon = coordenadas.second,
                    capa = IdCapa.CAMARAS,
                    fuente = "Departamento de Transporte · Hong Kong",
                    imagenUrl = coincidencia.value,
                    detalle = buildList {
                        add("Identificador" to clave)
                        if (!region.isNullOrBlank()) add("Región" to region)
                        if (distrito.isNotBlank()) add("Distrito" to distrito)
                        add("Fotograma" to "se actualiza cada 2 minutos")
                    },
                )
            )
        }

        publicadasHongKong = lista.size
        // Una sola ciudad no puede acaparar el mapa entero: se dibujan las
        // primeras y la interfaz dice cuántas hay en total, en vez de callarlo.
        return lista.take(TOPE_HONG_KONG)
    }

    /** Números del registro que encajan con una latitud y longitud de Hong Kong. */
    private fun coordenadasHongKong(trozo: String): Pair<Double, Double>? {
        val numeros = PATRON_NUMERO.findAll(trozo)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()

        for (i in numeros.indices.reversed()) {
            val longitud = numeros[i]
            if (longitud < 113.5 || longitud > 115.0) continue
            val latitud = numeros.getOrNull(i - 1) ?: continue
            if (latitud < 22.0 || latitud > 23.0) continue
            return latitud to longitud
        }

        // Si el fichero perdiera el tabulador entre latitud y longitud, las dos
        // quedarían pegadas en un solo número: 22.248255958114.160644465.
        PATRON_NUMEROS_PEGADOS.find(trozo)?.let { pegado ->
            val latitud = pegado.groupValues[1].toDoubleOrNull() ?: return null
            val longitud = pegado.groupValues[2].toDoubleOrNull() ?: return null
            return latitud to longitud
        }
        return null
    }

    /** El nombre del registro viene con la clave entre corchetes al final. */
    private fun nombreHongKong(trozo: String): String {
        val encontrado = PATRON_NOMBRE_HK.find(trozo) ?: return ""
        val texto = LIMPIEZA_HK.replace(encontrado.groupValues[1], " ").trim()

        // Si el fichero llegara sin separadores, el nombre se tragaría la
        // clave, la región y el distrito. Se nota en que aparece el nombre de
        // una región pegado a la letra anterior. En ese caso se deja en blanco:
        // la ficha ya usa la clave, y un amasijo de texto no ayuda a nadie.
        for (region in REGIONES_HK) {
            val posicion = texto.indexOf(region)
            if (posicion > 0 && !texto[posicion - 1].isWhitespace()) return ""
        }

        return if (texto.length > 120) "" else texto
    }

    private fun distritoHongKong(trozo: String, region: String?, nombre: String): String {
        if (region.isNullOrBlank() || nombre.isBlank()) return ""
        val desde = trozo.indexOf(region) + region.length
        val hasta = trozo.indexOf(nombre)
        if (hasta <= desde) return ""
        val texto = LIMPIEZA_HK.replace(trozo.substring(desde, hasta), " ").trim()
        return if (texto.length in 3..40) texto else ""
    }

    /**
     * La autoridad de transporte de Singapur publica los fotogramas de su red
     * de cámaras de tráfico, sin clave y con las coordenadas incluidas. Cada
     * registro trae su propia marca de tiempo, así que se muestra tal cual
     * viene: es el dato que publica la fuente, sin reinterpretarlo.
     */
    suspend fun camarasSingapur(): List<PuntoMapa> {
        val raiz = Red.objeto(URL_SINGAPUR)
        val items = raiz.optJSONArray("items") ?: JSONArray()
        val lista = mutableListOf<PuntoMapa>()

        for (i in 0 until items.length()) {
            val camaras = items.optJSONObject(i)?.optJSONArray("cameras") ?: continue
            for (j in 0 until camaras.length()) {
                val camara = camaras.optJSONObject(j) ?: continue
                val ubicacion = camara.optJSONObject("location") ?: continue

                val lat = ubicacion.optDouble("latitude", Double.NaN)
                val lon = ubicacion.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val id = camara.optString("camera_id")
                val metadatos = camara.optJSONObject("image_metadata")

                lista.add(
                    PuntoMapa(
                        id = "sg-$id",
                        nombre = "Cámara de tráfico $id",
                        lat = lat, lon = lon,
                        capa = IdCapa.CAMARAS,
                        fuente = "LTA · datos abiertos de Singapur",
                        imagenUrl = camara.optString("image").takeIf { it.isNotBlank() },
                        detalle = buildList {
                            add("Identificador" to id)
                            add("Marca de tiempo" to camara.optString("timestamp"))
                            if (metadatos != null) {
                                add(
                                    "Resolución" to
                                        "${metadatos.optInt("width")}×${metadatos.optInt("height")}"
                                )
                            }
                        },
                    )
                )
            }
        }
        return lista
    }

    // ─────────────────────── Textura del globo ───────────────────────

    /**
     * El mapamundi que se pega al globo 3D.
     *
     * Es la Blue Marble de la NASA, que es de dominio público: un mapamundi
     * equirectangular de 4096×2048 con relieve y batimetría del servicio de
     * imágenes GIBS. Tiene un día de antigüedad, así que la geografía está,
     * pero no las nubes de hoy: para el tiempo de ahora ya está la capa de
     * radar, y esto es el planeta, no el parte meteorológico.
     *
     * Son 4096 píxeles y no menos porque a esta escala cada píxel son unos
     * 10 km: al acercarse, la costa se mantiene en su sitio en vez de
     * deshacerse en manchas. Si el teléfono no admite una textura tan grande,
     * el globo la encoge antes de subirla.
     *
     * Se guarda en la caché de la app, así que a partir de la segunda vez el
     * globo tiene geografía aunque no haya red. Si no hay ni red ni caché,
     * devuelve null y el globo se queda con el océano y la rejilla.
     */
    suspend fun texturaTierra(): Bitmap? {
        // El nombre lleva el tamaño: si algún día cambia, la caché vieja no se
        // queda sirviendo la imagen de antes para siempre.
        val archivo = java.io.File(contexto.cacheDir, "tierra-4k.jpg")
        val bytes: ByteArray? = withContext(Dispatchers.IO) {
            val enCache = archivo.exists() &&
                archivo.length() > 50_000 &&
                System.currentTimeMillis() - archivo.lastModified() < 30L * 24 * 3600 * 1000

            if (enCache) {
                archivo.readBytes()
            } else {
                try {
                    val descargado = Red.bytes(URL_TEXTURA_TIERRA)
                    // Si el servicio contesta con un XML de error o una página
                    // web en vez de una imagen, no se guarda: envenenaría la
                    // caché y el globo se quedaría sin geografía para siempre.
                    if (descargado.size < 2 ||
                        descargado[0] != 0xFF.toByte() ||
                        descargado[1] != 0xD8.toByte()
                    ) {
                        throw ErrorRed("la respuesta de GIBS no es un JPEG")
                    }
                    archivo.writeBytes(descargado)
                    descargado
                } catch (e: Exception) {
                    // Sin red: si hay algo en caché, aunque sea viejo, mejor eso.
                    if (archivo.exists()) archivo.readBytes() else null
                }
            }
        }
        if (bytes == null) return null

        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: OutOfMemoryError) {
            // Un móvil justo de memoria: media resolución es mejor que nada.
            try {
                BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = 2 },
                )
            } catch (e: OutOfMemoryError) {
                null
            }
        }
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

        /** Índice de cámaras del Departamento de Transporte de Hong Kong. */
        private const val URL_HONG_KONG =
            "https://static.data.gov.hk/td/traffic-snapshot-images/code/" +
                "Traffic_Camera_Locations_En.xml"

        /** Fotogramas de las cámaras de tráfico de Singapur. */
        private const val URL_SINGAPUR =
            "https://api.data.gov.sg/v1/transport/traffic-images"

        /**
         * Mapamundi equirectangular de la NASA, de dominio público. Se pide con
         * WMS 1.1.1 y no 1.3.0 a propósito: en la 1.1.1 el orden de las cajas
         * para EPSG:4326 es longitud,latitud, sin la ambigüedad que trajo la
         * 1.3.0 con el orden de los ejes.
         */
        private const val URL_TEXTURA_TIERRA =
            "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi" +
                "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap" +
                "&LAYERS=BlueMarble_ShadedRelief_Bathymetry" +
                "&SRS=EPSG:4326&BBOX=-180,-90,180,90" +
                "&WIDTH=4096&HEIGHT=2048&FORMAT=image/jpeg"

        /** Tope de cámaras de Hong Kong dibujadas, para no ahogar el mapa. */
        private const val TOPE_HONG_KONG = 240

        private val REGIONES_HK =
            listOf("Hong Kong Island", "New Territories", "Kowloon")

        private val PATRON_IMAGEN_HK =
            Regex("""https://tdcctv\.data\.one\.gov\.hk/([A-Za-z0-9]+)\.JPG""")

        /**
         * El nombre, con la clave entre corchetes al final. La barra invertida
         * es opcional porque según cómo venga el fichero los corchetes pueden
         * aparecer escapados.
         */
        private val PATRON_NOMBRE_HK =
            Regex("""([^\t\n<>]{3,200}?)\\?\[[A-Za-z0-9]{2,10}\\?\]""")

        private val PATRON_NUMERO = Regex("""-?\d+\.\d+""")

        private val PATRON_NUMEROS_PEGADOS = Regex("""(2[23]\.\d{4,9})(1[0-9]{2}\.\d{4,9})""")

        private val LIMPIEZA_HK = Regex("""[<>\t\r\n]+""")

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
