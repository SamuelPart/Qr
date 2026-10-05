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
    copyright: String = "",
) : OnlineTileSourceBase(
    nombre, zoomMin, zoomMax, tamanoMosaico, ".png", arrayOf(""), copyright
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

    private const val CARTO_ATRIB =
        "© OpenStreetMap · © CARTO"

    val CARTO_OSCURO = FuentePlantilla(
        "carto-oscuro",
        "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        0, 19, 256, CARTO_ATRIB,
    )

    val CARTO_CLARO = FuentePlantilla(
        "carto-claro",
        "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
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
