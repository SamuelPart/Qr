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
        "Tráfico oficial: Londres y Finlandia", porDefecto = true,
    ),
    SATELITES(
        "Satélites", "Órbita",
        "Posición calculada en el teléfono con SGP4", porDefecto = true,
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
