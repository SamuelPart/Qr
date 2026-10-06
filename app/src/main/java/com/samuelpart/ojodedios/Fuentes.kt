package com.samuelpart.ojodedios

import android.content.Context
import android.content.res.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fuente de mosaicos definida con una plantilla de URL.
 *
 * osmdroid trae XYTileSource, pero asume el orden {z}/{x}/{y}. Varios de los
 * servicios que usamos no siguen ese orden — Esri y NASA usan {z}/{y}/{x} — y
 * RainViewer añade parámetros al final. En vez de pelear con subclases distintas,
 * una sola plantilla resuelve los cuatro casos.
 */
class FuentePlantilla(
    nombre: String,
    private val plantilla: String,
    zoomMin: Int,
    zoomMax: Int,
    tamanoMosaico: Int = 256,
    /** Atribución. CARTO y OpenStreetMap exigen que se vea en el mapa. */
    val atribucion: String = "",
) : OnlineTileSourceBase(
    nombre, zoomMin, zoomMax, tamanoMosaico, ".png", arrayOf(""), atribucion
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return plantilla
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }
}

/**
 * Catálogo de fondos y capas de mosaicos.
 *
 * Ninguno necesita clave de API. Los que exigen atribución la llevan escrita
 * en `copyright`: osmdroid la muestra en la esquina del mapa.
 */
object Fuentes {

    /** La línea de atribución que CARTO pide literalmente en sus términos. */
    private const val CARTO_ATRIB =
        "© OpenStreetMap contributors · © CARTO"

    /**
     * Clave de CARTO, inyectada al compilar desde `local.properties`
     * (ver app/build.gradle.kts). No está escrita aquí a propósito: el
     * repositorio es público.
     *
     * Si falta, los mosaicos siguen llegando —CARTO no bloquea la petición—
     * pero con la marca de agua «API KEY REQUIRED» encima.
     */
    private val CLAVE_CARTO: String = BuildConfig.CARTO_KEY

    /**
     * true si falta la clave. La interfaz lo avisa en el panel de capas: sin
     * este dato, una clave mal cableada y una clave inválida se ven igual, y
     * desde fuera parecen «la app está rota».
     */
    val cartoSinClave: Boolean get() = CLAVE_CARTO.isBlank()

    /**
     * URL de un estilo ráster de CARTO.
     *
     * Desde finales de 2026 el CDN marca con agua todo mosaico que no lleve la
     * clave. El parámetro se llama `key`, no `api_key`, y solo se añade si hay
     * clave: una petición sin él sigue siendo válida.
     */
    private fun urlCarto(estilo: String): String =
        "https://basemaps.cartocdn.com/rastertiles/$estilo/{z}/{x}/{y}.png" +
            if (CLAVE_CARTO.isBlank()) "" else "?key=$CLAVE_CARTO"

    /**
     * osmdroid guarda los mosaicos en caché por nombre de fuente. Si la clave
     * llega después de haber usado la app sin ella, hay que cambiar también el
     * nombre: si no, seguirían apareciendo los mosaicos ya guardados, con su
     * marca de agua, hasta que caducara la caché.
     */
    private fun nombreCarto(estilo: String): String =
        "carto-$estilo" + if (CLAVE_CARTO.isBlank()) "" else "-clave"

    val CARTO_OSCURO = FuentePlantilla(
        nombreCarto("oscuro"),
        urlCarto("dark_all"),
        0, 19, 256, CARTO_ATRIB,
    )

    val CARTO_CLARO = FuentePlantilla(
        nombreCarto("claro"),
        urlCarto("light_all"),
        0, 19, 256, CARTO_ATRIB,
    )

    val OSM = FuentePlantilla(
        "osm",
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        0, 19, 256, "© OpenStreetMap",
    )

    /**
     * Imagen aérea y satelital de archivo. Detalle de hasta ~30 cm por píxel,
     * pero es material comercial con meses o años de antigüedad: no es en vivo.
     * Ojo: este servicio ordena los mosaicos como {z}/{y}/{x}.
     */
    val ESRI_IMAGEN = FuentePlantilla(
        "esri-imagen",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        0, 19, 256,
        "Imágenes: Esri, Maxar, Earthstar Geographics, USDA FSA, USGS, Aerogrid, IGN, IGP",
    )

    /**
     * Color verdadero del satélite Suomi-NPP (NASA GIBS / VIIRS).
     * ~250-375 m por píxel y alrededor de un día de latencia: no es vídeo.
     * También en orden {z}/{y}/{x}.
     */
    fun nasaColorVerdadero(diasAtras: Int = 1): FuentePlantilla {
        val fecha = fechaUTC(diasAtras)
        return FuentePlantilla(
            "nasa-viirs-$fecha",
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
                "VIIRS_SNPP_CorrectedReflectance_TrueColor/default/$fecha/" +
                "GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg",
            0, 9, 256, "Imágenes: NASA EOSDIS GIBS / VIIRS Suomi-NPP",
        )
    }

    /** Focos de calor activos (NASA FIRMS). Se superpone a la imagen anterior. */
    fun nasaAnomaliasTermicas(diasAtras: Int = 1): FuentePlantilla {
        val fecha = fechaUTC(diasAtras)
        return FuentePlantilla(
            "nasa-fuegos-$fecha",
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
                "VIIRS_SNPP_Thermal_Anomalies_375m_All/default/$fecha/" +
                "GoogleMapsCompatible_Level9/{z}/{y}/{x}.png",
            0, 9, 256, "Anomalías térmicas: NASA FIRMS / VIIRS",
        )
    }

    /**
     * Radar de lluvia. RainViewer publica el fotograma más reciente en una URL
     * con marca de tiempo, que hay que pedir antes de montar la capa.
     */
    fun radar(tiempo: Long): FuentePlantilla = FuentePlantilla(
        "radar-$tiempo",
        "https://tilecache.rainviewer.com/v2/radar/$tiempo/256/{z}/{x}/{y}/4/1_1.png",
        0, 12, 256, "Radar: RainViewer",
    )

    /** Fecha en UTC con el formato que espera la API WMTS de NASA. */
    fun fechaUTC(diasAtras: Int): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(System.currentTimeMillis() - diasAtras * 86_400_000L))

    /**
     * osmdroid exige configurarse antes de crear cualquier MapView. Sin
     * userAgentValue, los servidores de mosaicos pueden bloquear las peticiones.
     */
    fun preparar(contexto: Context) {
        org.osmdroid.config.Configuration.getInstance().apply {
            // osmdroid guarda sus preferencias aquí. Se usa el SharedPreferences
            // del sistema en vez del de androidx.preference para no arrastrar
            // una dependencia entera solo por esto.
            load(
                contexto,
                contexto.getSharedPreferences("osmdroid", Context.MODE_PRIVATE),
            )
            userAgentValue = contexto.packageName
        }
    }

    /** true si el sistema está en modo oscuro, para elegir el fondo inicial. */
    fun sistemaEnOscuro(contexto: Context): Boolean =
        (contexto.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
