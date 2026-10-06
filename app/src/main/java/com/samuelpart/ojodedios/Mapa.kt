package com.samuelpart.ojodedios

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Envuelve el MapView de osmdroid y expone operaciones de alto nivel.
 *
 * Se mantienen referencias a los marcadores de satélite para poder moverlos cada
 * segundo sin destruirlos y volverlos a crear: reconstruir 26 marcadores por
 * segundo haría parpadear el mapa y castigaría la batería sin motivo.
 */
class ControladorMapa(contexto: Context) {

    val vista: MapView = MapView(contexto).apply {
        setMultiTouchControls(true)
        setTilesScaledToDpi(true)
        controller.setZoom(3.0)
        controller.setCenter(GeoPoint(35.0, 5.0))
        setMinZoomLevel(2.0)
        setMaxZoomLevel(19.0)
    }

    private var overlayRadar: TilesOverlay? = null
    private var overlayNasa: TilesOverlay? = null

    private val marcadoresPuntos = mutableListOf<Marker>()
    private val marcadoresSatelite = mutableMapOf<String, Marker>()
    private val trazas = mutableListOf<Polyline>()
    private var marcadorYo: Marker? = null

    /** Centro actual del mapa, en grados. */
    fun centro(): Pair<Double, Double> {
        val c = vista.mapCenter
        return c.latitude to c.longitude
    }

    fun moverA(lat: Double, lon: Double, zoom: Double? = null) {
        vista.controller.animateTo(GeoPoint(lat, lon))
        if (zoom != null) vista.controller.setZoom(zoom)
    }

    /**
     * El globo 3D tapa el mapa: mientras esté delante, osmdroid deja de pedir
     * mosaicos, porque no tiene sentido gastar datos en un mapa que nadie ve.
     * Ni el estado ni la vista se pierden: solo se pausa la descarga.
     */
    var tapado: Boolean = false
        private set

    fun tapar() {
        if (tapado) return
        tapado = true
        vista.onPause()
    }

    fun destapar() {
        if (!tapado) return
        tapado = false
        vista.onResume()
    }

    // ─────────────────────── Capa base ───────────────────────

    fun cambiarBase(fuente: FuentePlantilla) {
        vista.setTileSource(fuente)
        vista.invalidate()
    }

    // ─────────────────────── Capas de mosaicos ───────────────────────

    fun montarRadar(fuente: FuentePlantilla?) {
        overlayRadar?.let { vista.overlays.remove(it) }
        overlayRadar = null
        if (fuente != null) {
            overlayRadar = crearCapaMosaicos(fuente, alfa = 0.65f)
            vista.overlays.add(overlayRadar)
        }
        vista.invalidate()
    }

    fun montarNasa(fuente: FuentePlantilla?) {
        overlayNasa?.let { vista.overlays.remove(it) }
        overlayNasa = null
        if (fuente != null) {
            overlayNasa = crearCapaMosaicos(fuente, alfa = 0.95f)
            vista.overlays.add(overlayNasa)
        }
        vista.invalidate()
    }

    /**
     * Una capa de mosaicos superpuesta a la base. Se apoya en su propio
     * proveedor de mosaicos para no compartir caché con el fondo: cada fuente
     * tiene su identificador, y osmdroid cachea por nombre de fuente.
     */
    private fun crearCapaMosaicos(fuente: FuentePlantilla, alfa: Float): TilesOverlay {
        val proveedor = MapTileProviderBasic(vista.context, fuente)
        return TilesOverlay(proveedor, vista.context).apply {
            // osmdroid no expone setOpacity en las capas de mosaicos. La
            // transparencia se consigue escalando el canal alfa con un filtro
            // de color, que es el mecanismo que la propia librería recomienda.
            setColorFilter(
                ColorMatrixColorFilter(ColorMatrix().apply { setScale(1f, 1f, 1f, alfa) })
            )
        }
    }

    // ─────────────────────── Puntos genéricos ───────────────────────

    /** Reconstruye los marcadores de cámaras, vuelos, barcos y sismos. */
    fun pintarPuntos(puntos: List<PuntoMapa>, alTocar: (PuntoMapa) -> Unit) {
        vista.overlays.removeAll(marcadoresPuntos)
        marcadoresPuntos.clear()

        for (p in puntos) {
            val marcador = Marker(vista).apply {
                position = GeoPoint(p.lat, p.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = p.nombre
                icon = iconoPara(vista.context, p)
                setOnMarkerClickListener { _, _ ->
                    alTocar(p)
                    true // consume el clic: la ficha la muestra Compose
                }
            }
            marcadoresPuntos.add(marcador)
            vista.overlays.add(marcador)
        }
        vista.invalidate()
    }

    // ─────────────────────── Satélites ───────────────────────

    /**
     * Mueve los marcadores ya existentes y añade los que falten. No se destruyen
     * ni se recrean: solo cambian de posición.
     */
    fun pintarSatelites(satelites: List<SateliteEnVuelo>, alTocar: (SateliteEnVuelo) -> Unit) {
        val contexto = vista.context
        var huboCambios = false

        for (s in satelites) {
            val existente = marcadoresSatelite[s.satelite.nombre]
            if (existente == null) {
                val nuevo = Marker(vista).apply {
                    position = GeoPoint(s.lat, s.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = s.satelite.nombre
                    icon = iconoSatelite(contexto, s.destacado)
                    setOnMarkerClickListener { _, _ ->
                        alTocar(s)
                        true
                    }
                }
                marcadoresSatelite[s.satelite.nombre] = nuevo
                vista.overlays.add(nuevo)
                huboCambios = true
            } else {
                existente.position = GeoPoint(s.lat, s.lon)
                huboCambios = true
            }
        }

        // Satélites que ya no están (capa desactivada)
        val nombres = satelites.map { it.satelite.nombre }.toSet()
        val sobrantes = marcadoresSatelite.keys - nombres
        for (n in sobrantes) {
            marcadoresSatelite.remove(n)?.let { vista.overlays.remove(it) }
            huboCambios = true
        }

        if (huboCambios) vista.invalidate()
    }

    /** Redibuja las trazas orbitales de las estaciones tripuladas. */
    fun pintarTrazas(satelites: List<SateliteEnVuelo>) {
        vista.overlays.removeAll(trazas)
        trazas.clear()

        for (s in satelites) {
            for (segmento in s.traza) {
                if (segmento.size < 2) continue
                val linea = Polyline(vista).apply {
                    setPoints(segmento.map { GeoPoint(it.first, it.second) })
                    outlinePaint.color = Color.parseColor("#8A78BFA") // violeta translúcido
                    outlinePaint.strokeWidth = 2.5f
                    setGeodesic(true)
                }
                trazas.add(linea)
                vista.overlays.add(linea)
            }
        }
        vista.invalidate()
    }

    // ─────────────────────── Ubicación propia ───────────────────────

    fun pintarMiUbicacion(ubicacion: Pair<Double, Double>?) {
        marcadorYo?.let { vista.overlays.remove(it) }
        marcadorYo = null
        if (ubicacion != null) {
            marcadorYo = Marker(vista).apply {
                position = GeoPoint(ubicacion.first, ubicacion.second)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Tu posición"
                icon = circulo(vista.context, Color.parseColor("#22D3EE"), 14, "#0A0E17")
            }
            vista.overlays.add(marcadorYo)
        }
        vista.invalidate()
    }

    // ─────────────────────── Iconos ───────────────────────

    private fun iconoPara(contexto: Context, p: PuntoMapa): GradientDrawable = when (p.capa) {
        IdCapa.CAMARAS -> circulo(contexto, Color.parseColor("#22D3EE"), 11, "#0A0E17")
        IdCapa.VUELOS -> circulo(contexto, Color.parseColor("#F59E0B"), 9, "#0A0E17")
        IdCapa.BARCOS -> circulo(contexto, Color.parseColor("#34D399"), 8, "#0A0E17")
        IdCapa.SISMOS -> circulo(
            contexto,
            Color.parseColor(colorMagnitud(p)),
            if (p.destacado) 11 else 7,
            "#0A0E17",
        )
        else -> circulo(contexto, Color.parseColor("#94A3B8"), 8, "#0A0E17")
    }

    private fun iconoSatelite(contexto: Context, destacado: Boolean): GradientDrawable {
        val color = if (destacado) "#F59E0B" else "#A78BFA"
        return circulo(contexto, Color.parseColor(color), if (destacado) 12 else 9, "#0A0E17")
    }

    private fun colorMagnitud(p: PuntoMapa): String {
        val magnitud = p.detalle.firstOrNull { it.first == "Magnitud" }?.second?.toDoubleOrNull() ?: 0.0
        return when {
            magnitud >= 6.0 -> "#EF4444"
            magnitud >= 5.0 -> "#F97316"
            magnitud >= 4.0 -> "#F59E0B"
            magnitud >= 3.0 -> "#EAB308"
            magnitud >= 2.0 -> "#84CC16"
            else -> "#22D3EE"
        }
    }

    /**
     * osmdroid dibuja Drawables, no iconos vectoriales como Compose. Un círculo
     * con borde se construye en tres líneas y se ve bien a cualquier tamaño.
     */
    private fun circulo(contexto: Context, relleno: Int, radioDp: Int, borde: String): GradientDrawable {
        val densidad = contexto.resources.displayMetrics.density
        val lado = (radioDp * 2 * densidad).toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(relleno)
            setStroke((2 * densidad).toInt(), Color.parseColor(borde))
            setSize(lado, lado)
            setBounds(0, 0, lado, lado)
        }
    }
}

/**
 * El mapa como componente de Compose.
 *
 * osmdroid es una vista clásica de Android, así que se inserta con AndroidView.
 * La sincronización de datos se hace con efectos: cada flujo de estado provoca
 * la operación correspondiente sobre el controlador.
 */
@Composable
fun MapaOjoDeDios(
    controlador: ControladorMapa,
    puntos: List<PuntoMapa>,
    satelites: List<SateliteEnVuelo>,
    fuenteBase: FuentePlantilla,
    fuenteRadar: FuentePlantilla?,
    fuenteNasa: FuentePlantilla?,
    miUbicacion: Pair<Double, Double>?,
    centroInicial: Pair<Double, Double>,
    alTocarPunto: (PuntoMapa) -> Unit,
    alTocarSatelite: (SateliteEnVuelo) -> Unit,
    alMoverMapa: (Pair<Double, Double>) -> Unit,
    modifier: Modifier = Modifier,
    zoomInicial: Double = 3.0,
) {
    val contexto = LocalContext.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { controlador.vista },
    )

    // El escucha se instala una sola vez, no en cada recomposición: la pantalla
    // se recompone una vez por segundo mientras el reloj de satélites corre.
    LaunchedEffect(controlador) {
        controlador.vista.setMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                alMoverMapa(controlador.centro())
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                alMoverMapa(controlador.centro())
                return true
            }
        })
    }

    // Sincronización de datos: cada flujo de estado provoca su operación.
    LaunchedEffect(fuenteBase) { controlador.cambiarBase(fuenteBase) }
    LaunchedEffect(fuenteRadar) { controlador.montarRadar(fuenteRadar) }
    LaunchedEffect(fuenteNasa) { controlador.montarNasa(fuenteNasa) }
    LaunchedEffect(puntos) { controlador.pintarPuntos(puntos, alTocarPunto) }
    LaunchedEffect(satelites) { controlador.pintarSatelites(satelites, alTocarSatelite) }

    // Clave derivada: mover un satélite crea un objeto nuevo pero conserva su
    // traza, así que este efecto solo se reinicia cuando la traza cambia de
    // verdad (cada minuto). Si no, se reconstruirían las polilíneas cada segundo.
    LaunchedEffect(satelites.map { it.traza }) { controlador.pintarTrazas(satelites) }
    LaunchedEffect(miUbicacion) { controlador.pintarMiUbicacion(miUbicacion) }
    // El centro y el zoom solo se aplican al abrir. Si dependieran de las
    // recomposiciones, el mapa daría un salto cada vez que se mueve un satélite.
    LaunchedEffect(controlador) {
        controlador.moverA(centroInicial.first, centroInicial.second, zoomInicial)
    }
}
