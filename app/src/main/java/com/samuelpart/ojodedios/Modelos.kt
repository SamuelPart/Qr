package com.samuelpart.ojodedios

/**
 * Capas de observación. Cada una corresponde a una fuente pública abierta.
 */
enum class IdCapa(
    val etiqueta: String,
    val grupo: String,
    val descripcion: String,
    val porDefecto: Boolean = false,
) {
    CAMARAS(
        "Cámaras públicas", "Observación directa",
        "Tráfico oficial: Londres, Finlandia, Hong Kong y Singapur", porDefecto = true,
    ),
    SATELITES(
        "Satélites", "Órbita",
        "SGP4 en el teléfono · a su altitud real en el globo 3D", porDefecto = true,
    ),
    SISMOS(
        "Sismos", "Atmósfera y terreno",
        "Últimas 24 h, USGS", porDefecto = true,
    ),
    VUELOS(
        "Vuelos (ADS-B)", "Aire y mar",
        "Posiciones emitidas por las aeronaves",
    ),
    BARCOS(
        "Barcos (AIS)", "Aire y mar",
        "Tráfico marítimo del Báltico",
    ),
    RADAR(
        "Radar de lluvia", "Atmósfera y terreno",
        "Mosaico de radares, RainViewer",
    ),
    NASA(
        "Imagen satelital NASA", "Atmósfera y terreno",
        "Color verdadero, ~1 día de antigüedad",
    ),
}

/**
 * Color con el que cada capa se dibuja sobre el globo. Son los mismos de la
 * paleta del HUD, para que el punto de la leyenda y el del planeta coincidan:
 * si aquí se cambia uno, allí deja de mentir.
 */
val IdCapa.colorEnGlobo: FloatArray
    get() = when (this) {
        IdCapa.CAMARAS -> floatArrayOf(0.13f, 0.83f, 0.93f)   // cian
        IdCapa.SATELITES -> floatArrayOf(0.98f, 0.75f, 0.25f) // ámbar claro
        IdCapa.SISMOS -> floatArrayOf(0.96f, 0.62f, 0.04f)    // ámbar
        IdCapa.VUELOS -> floatArrayOf(0.20f, 0.83f, 0.60f)    // verde
        IdCapa.BARCOS -> floatArrayOf(0.65f, 0.55f, 0.98f)    // violeta
        // Radar e imagen de la NASA no son puntos: tiñen el planeta entero.
        IdCapa.RADAR -> floatArrayOf(0.44f, 0.72f, 0.94f)
        IdCapa.NASA -> floatArrayOf(0.44f, 0.72f, 0.94f)
    }

/** Rojo de alerta, para lo que la fuente marca como destacado. */
val COLOR_DESTACADO = floatArrayOf(0.94f, 0.27f, 0.27f)

/**
 * Un punto cualquiera del mapa: cámara, avión, barco, sismo o satélite.
 * Se unifica para que el mapa y la ficha de detalle traten todo igual.
 */
data class PuntoMapa(
    val id: String,
    val nombre: String,
    val lat: Double,
    val lon: Double,
    val capa: IdCapa,
    val fuente: String,
    val detalle: List<Pair<String, String>> = emptyList(),
    val imagenUrl: String? = null,
    val videoUrl: String? = null,
    val destacado: Boolean = false,
)

/**
 * Satélite con sus elementos orbitales. `línea1`/`línea2` son el TLE crudo que
 * publica CelesTrak; el cálculo de posición ocurre en el dispositivo.
 */
data class Satelite(
    val nombre: String,
    val norad: Int,
    val tipo: String,
    val linea1: String,
    val linea2: String,
)

/**
 * Estado de una capa: si está activa, cuántos objetos dibuja y por qué vía
 * obtuvo los datos.
 */
enum class OrigenDatos { VIVO, RESPALDO, ERROR }

data class EstadoCapa(
    val capa: IdCapa,
    val activa: Boolean = false,
    val cargando: Boolean = false,
    val objetos: Int = 0,
    val origen: OrigenDatos = OrigenDatos.VIVO,
    val nota: String = "",
)
