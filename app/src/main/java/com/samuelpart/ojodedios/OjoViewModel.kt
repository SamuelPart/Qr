package com.samuelpart.ojodedios

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Un satélite listo para pintar: su órbita, la posición del instante actual y
 * la traza que dejará en las próximas horas.
 */
data class SateliteEnVuelo(
    val satelite: Satelite,
    val orbita: Orbita,
    val lat: Double,
    val lon: Double,
    val altitudKm: Double,
    val traza: List<List<Pair<Double, Double>>>,
    val destacado: Boolean,
)

/**
 * Estado de la aplicación. Todo el trabajo pesado (red, SGP4) ocurre aquí; la
 * interfaz solo dibuja lo que publican estos flujos.
 */
class OjoViewModel(app: Application) : AndroidViewModel(app) {

    private val repositorio = Repositorio(app)

    /** Estado de cada capa, con su origen de datos y número de objetos. */
    private val _capas = MutableStateFlow(
        IdCapa.values().associateWith { EstadoCapa(capa = it) }
    )
    val capas: StateFlow<Map<IdCapa, EstadoCapa>> = _capas.asStateFlow()

    /** Puntos de todas las capas activas, ya normalizados. */
    private val _puntos = MutableStateFlow<List<PuntoMapa>>(emptyList())
    val puntos: StateFlow<List<PuntoMapa>> = _puntos.asStateFlow()

    /** Satélites con posición viva. Se recalculan cada segundo en el teléfono. */
    private val _satelites = MutableStateFlow<List<SateliteEnVuelo>>(emptyList())
    val satelites: StateFlow<List<SateliteEnVuelo>> = _satelites.asStateFlow()

    /** Capa de radar lista para montar, si está activa. */
    private val _fuenteRadar = MutableStateFlow<FuentePlantilla?>(null)
    val fuenteRadar: StateFlow<FuentePlantilla?> = _fuenteRadar.asStateFlow()

    private val _fuenteNasa = MutableStateFlow<FuentePlantilla?>(null)
    val fuenteNasa: StateFlow<FuentePlantilla?> = _fuenteNasa.asStateFlow()

    private val _seleccion = MutableStateFlow<PuntoMapa?>(null)
    val seleccion: StateFlow<PuntoMapa?> = _seleccion.asStateFlow()

    private val _miUbicacion = MutableStateFlow<Pair<Double, Double>?>(null)
    val miUbicacion: StateFlow<Pair<Double, Double>?> = _miUbicacion.asStateFlow()

    /** Puntos por capa, para poder refrescar una sola sin tocar las demás. */
    private val acumulado = mutableMapOf<IdCapa, List<PuntoMapa>>()

    private var trabajoSatelites: Job? = null
    private var trabajoTraza: Job? = null

    // ─────────────────────── Activar y desactivar capas ───────────────────────

    fun alternar(capa: IdCapa, centro: Pair<Double, Double>? = null) {
        val actual = _capas.value[capa] ?: return
        if (actual.activa) desactivar(capa) else activar(capa, centro)
    }

    private fun activar(capa: IdCapa, centro: Pair<Double, Double>?) {
        marcar(capa) { it.copy(activa = true, cargando = true) }
        when (capa) {
            IdCapa.CAMARAS -> cargarCamaras()
            IdCapa.SISMOS -> cargar(capa) { repositorio.sismos() }
            IdCapa.VUELOS -> cargarVuelos(centro)
            IdCapa.BARCOS -> cargar(capa) { repositorio.barcos() }
            IdCapa.RADAR -> cargarRadar()
            IdCapa.NASA -> cargarNasa()
            IdCapa.SATELITES -> cargarSatelites()
        }
    }

    private fun desactivar(capa: IdCapa) {
        marcar(capa) { it.copy(activa = false, cargando = false, objetos = 0) }
        acumulado.remove(capa)
        _puntos.value = acumulado.values.flatten()

        when (capa) {
            IdCapa.SATELITES -> {
                trabajoSatelites?.cancel(); trabajoTraza?.cancel()
                _satelites.value = emptyList()
            }
            IdCapa.RADAR -> _fuenteRadar.value = null
            IdCapa.NASA -> _fuenteNasa.value = null
            else -> Unit
        }
    }

    /** Actualiza todas las capas activas a la vez. */
    fun refrescarTodo(centro: Pair<Double, Double>?) {
        for ((capa, estado) in _capas.value) {
            if (!estado.activa) continue
            marcar(capa) { it.copy(cargando = true) }
            when (capa) {
                IdCapa.CAMARAS -> cargarCamaras()
                IdCapa.SISMOS -> cargar(capa) { repositorio.sismos() }
                IdCapa.VUELOS -> cargarVuelos(centro)
                IdCapa.BARCOS -> cargar(capa) { repositorio.barcos() }
                IdCapa.RADAR -> cargarRadar()
                IdCapa.NASA -> cargarNasa()
                IdCapa.SATELITES -> recargarTraza()
            }
        }
    }

    // ─────────────────────── Cargadores ───────────────────────

    /**
     * Cámaras: cuatro redes oficiales independientes. Si una falla, las otras
     * tres siguen; la nota de la capa dice cuántas aportó cada una.
     */
    private fun cargarCamaras() {
        viewModelScope.launch {
            val fuentes = listOf<Pair<String, suspend () -> List<PuntoMapa>>>(
                "Londres" to { repositorio.camarasLondres() },
                "Finlandia" to { repositorio.camarasFinlandia() },
                "Hong Kong" to { repositorio.camarasHongKong() },
                "Singapur" to { repositorio.camarasSingapur() },
            )

            val notas = mutableListOf<String>()
            val encontradas = mutableListOf<PuntoMapa>()

            for ((nombre, fuente) in fuentes) {
                try {
                    val datos = withContext(Dispatchers.IO) { fuente() }
                    encontradas.addAll(datos)
                    notas.add(
                        // Hong Kong publica muchas más cámaras de las que se
                        // dibujan, así que se dice el total en vez de callarlo.
                        if (nombre == "Hong Kong" &&
                            repositorio.publicadasHongKong > datos.size
                        ) {
                            "Hong Kong ${datos.size} de ${repositorio.publicadasHongKong}"
                        } else if (datos.isEmpty()) {
                            "$nombre sin datos"
                        } else {
                            "$nombre ${datos.size}"
                        }
                    )
                } catch (e: Exception) {
                    notas.add("$nombre sin datos")
                }
            }

            acumulado[IdCapa.CAMARAS] = encontradas
            _puntos.value = acumulado.values.flatten()
            marcar(IdCapa.CAMARAS) {
                it.copy(
                    cargando = false,
                    objetos = encontradas.size,
                    origen = if (encontradas.isEmpty()) OrigenDatos.ERROR else OrigenDatos.VIVO,
                    nota = notas.joinToString(" · "),
                )
            }
        }
    }

    private fun cargar(capa: IdCapa, fuente: suspend () -> List<PuntoMapa>) {
        viewModelScope.launch {
            try {
                val datos = withContext(Dispatchers.IO) { fuente() }
                acumulado[capa] = datos
                _puntos.value = acumulado.values.flatten()
                marcar(capa) {
                    it.copy(cargando = false, objetos = datos.size, origen = OrigenDatos.VIVO, nota = "")
                }
            } catch (e: Exception) {
                marcar(capa) {
                    it.copy(
                        cargando = false, objetos = 0,
                        origen = OrigenDatos.ERROR,
                        nota = e.message ?: "sin datos",
                    )
                }
            }
        }
    }

    /**
     * Vuelos alrededor del centro del mapa. El radio se deduce de la vista para
     * no pedir medio planeta: solo interesa lo que el usuario está mirando.
     */
    fun cargarVuelos(centro: Pair<Double, Double>?) {
        val (lat, lon) = centro ?: return
        viewModelScope.launch {
            try {
                val datos = withContext(Dispatchers.IO) { repositorio.vuelos(lat, lon, 120) }
                acumulado[IdCapa.VUELOS] = datos
                _puntos.value = acumulado.values.flatten()
                marcar(IdCapa.VUELOS) {
                    it.copy(cargando = false, objetos = datos.size, origen = OrigenDatos.VIVO, nota = "")
                }
            } catch (e: Exception) {
                marcar(IdCapa.VUELOS) {
                    it.copy(cargando = false, objetos = 0, origen = OrigenDatos.ERROR, nota = e.message ?: "")
                }
            }
        }
    }

    private fun cargarRadar() {
        viewModelScope.launch {
            try {
                val tiempo = withContext(Dispatchers.IO) { repositorio.ultimoFotogramaRadar() }
                _fuenteRadar.value = Fuentes.radar(tiempo)
                marcar(IdCapa.RADAR) {
                    it.copy(cargando = false, objetos = 1, origen = OrigenDatos.VIVO, nota = "fotograma más reciente")
                }
            } catch (e: Exception) {
                marcar(IdCapa.RADAR) {
                    it.copy(cargando = false, origen = OrigenDatos.ERROR, nota = e.message ?: "")
                }
            }
        }
    }

    private fun cargarNasa() {
        _fuenteNasa.value = Fuentes.nasaColorVerdadero(1)
        marcar(IdCapa.NASA) {
            it.copy(
                cargando = false, objetos = 1, origen = OrigenDatos.VIVO,
                nota = Fuentes.fechaUTC(1),
            )
        }
    }

    /**
     * Satélites. Carga los TLE empaquetados, crea las órbitas y arranca el reloj
     * que recalcula la posición cada segundo.
     */
    private fun cargarSatelites() {
        viewModelScope.launch {
            val lista = withContext(Dispatchers.IO) {
                repositorio.satelites().mapNotNull { s ->
                    val orbita = Orbita(s)
                    if (!orbita.valido) return@mapNotNull null
                    orbita to s
                }
            }

            if (lista.isEmpty()) {
                marcar(IdCapa.SATELITES) {
                    it.copy(cargando = false, origen = OrigenDatos.ERROR, nota = "ningún TLE válido")
                }
                return@launch
            }

            val enVuelo = lista.mapNotNull { (orbita, satelite) ->
                val p = orbita.posicion() ?: return@mapNotNull null
                SateliteEnVuelo(
                    satelite = satelite,
                    orbita = orbita,
                    lat = p.lat, lon = p.lon, altitudKm = p.altitudKm,
                    traza = emptyList(),
                    destacado = satelite.tipo == "estacion" || satelite.tipo == "tripulada",
                )
            }
            _satelites.value = enVuelo
            marcar(IdCapa.SATELITES) {
                it.copy(
                    cargando = false, objetos = enVuelo.size,
                    origen = OrigenDatos.VIVO, nota = "SGP4 en el dispositivo",
                )
            }

            arrancarRelojSatelites()
            recargarTraza()
        }
    }

    /** Recalcula latitud y longitud cada segundo. 26 satélites es trabajo trivial. */
    private fun arrancarRelojSatelites() {
        trabajoSatelites?.cancel()
        trabajoSatelites = viewModelScope.launch {
            while (true) {
                delay(1000)
                val actuales = _satelites.value
                if (actuales.isEmpty()) continue
                val ahora = Date()
                _satelites.value = actuales.map { s ->
                    val p = s.orbita.posicion(ahora)
                    if (p == null) s else s.copy(lat = p.lat, lon = p.lon, altitudKm = p.altitudKm)
                }
            }
        }
    }

    /**
     * Las trazas se recalculan cada minuto: recorrer 100 minutos de órbita por
     * satélite es más caro que una sola posición, y a esta escala no hace falta
     * refrescarlas cada segundo.
     */
    private fun recargarTraza() {
        trabajoTraza?.cancel()
        trabajoTraza = viewModelScope.launch {
            while (true) {
                val actuales = _satelites.value
                if (actuales.isNotEmpty()) {
                    val conTraza = withContext(Dispatchers.Default) {
                        actuales.map { s ->
                            if (s.destacado) s.copy(traza = s.orbita.traza(100, 3)) else s
                        }
                    }
                    _satelites.value = conTraza
                }
                delay(60_000)
            }
        }
    }

    // ─────────────────────── Interacción ───────────────────────

    fun seleccionar(punto: PuntoMapa?) { _seleccion.value = punto }

    fun fijarMiUbicacion(lat: Double, lon: Double) { _miUbicacion.value = lat to lon }

    private fun marcar(capa: IdCapa, transformar: (EstadoCapa) -> EstadoCapa) {
        _capas.value = _capas.value.toMutableMap().apply {
            this[capa] = transformar(this[capa] ?: EstadoCapa(capa))
        }
    }

    override fun onCleared() {
        super.onCleared()
        trabajoSatelites?.cancel()
        trabajoTraza?.cancel()
    }
}
