package com.samuelpart.ojodedios

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * La atribución del globo 3D. En el mapa de mosaicos cada proveedor trae la
 * suya; aquí el planeta es la Blue Marble de la NASA y las órbitas las calcula
 * el propio teléfono a partir de los elementos que publica CelesTrak.
 */
private const val ATRIBUCION_GLOBO =
    "NASA Blue Marble (dominio público, GIBS) · CelesTrak + SGP4 en el dispositivo"

/**
 * A dónde se va al abrir la vista de calle: una posición y cómo llamarla.
 * No se reutiliza PuntoMapa porque aquí no hay ningún objeto observado, solo
 * un sitio al que mirar.
 */
private data class DestinoCalle(val lat: Double, val lon: Double, val titulo: String)

/**
 * Convierte un satélite en un punto como cualquier otro, para que su ficha sea
 * la misma que la de una cámara o un sismo. Se usa al tocarlo en el globo y en
 * la vista de calle.
 */
private fun sateliteComoPunto(s: SateliteEnVuelo): PuntoMapa = PuntoMapa(
    id = "sat-${s.satelite.norad}",
    nombre = s.satelite.nombre,
    lat = s.lat, lon = s.lon,
    capa = IdCapa.SATELITES,
    fuente = "CelesTrak · cálculo local con SGP4",
    detalle = listOf(
        "Tipo" to descripcionTipo(s.satelite.tipo),
        "Latitud" to "%.3f°".format(s.lat),
        "Longitud" to "%.3f°".format(s.lon),
        "Altitud" to "%.1f km".format(s.altitudKm),
        "NORAD" to s.satelite.norad.toString(),
    ),
)

/**
 * Pantalla principal: el mundo en 3D, con todas las capas encima y una capa de
 * controles flotantes, al estilo de un HUD de instrumentos.
 *
 * Ya no hay dos vistas que se turnan: el globo es la vista. La de calle —el
 * mapa de mosaicos, con calles y nombres— se abre desde la ficha de un objeto
 * y se cierra con «Volver», porque el globo no sabe distinguir una calle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaOjoDeDios(
    vm: OjoViewModel,
    controlador: ControladorMapa,
    fuenteBase: FuentePlantilla,
    alCambiarBase: (FuentePlantilla) -> Unit,
    alPedirUbicacion: () -> Unit,
) {
    val capas by vm.capas.collectAsState()
    val puntos by vm.puntos.collectAsState()
    val satelites by vm.satelites.collectAsState()
    val fuenteRadar by vm.fuenteRadar.collectAsState()
    val fuenteNasa by vm.fuenteNasa.collectAsState()
    val seleccion by vm.seleccion.collectAsState()
    val miUbicacion by vm.miUbicacion.collectAsState()

    var panelCapasAbierto by remember { mutableStateOf(false) }
    var detalleAbierto by remember { mutableStateOf(false) }
    var legalAbierto by remember { mutableStateOf(false) }

    /** Se enseña cuando alguien intenta acercarse en el globo más allá del tope. */
    var avisoDetalle by remember { mutableStateOf(false) }

    /**
     * Vista de calle: el mapa de mosaicos, que ya no es una vista principal
     * sino algo que se abre desde la ficha de un objeto cuando hay que ver la
     * calle. Guarda el punto al que se va.
     */
    var vistaCalle by remember { mutableStateOf<DestinoCalle?>(null) }

    /** Cambia de valor cada vez que se pulsa «Reiniciar»: la señal al globo. */
    var ordenReinicio by remember { mutableStateOf(0) }

    /** Lo mismo para «Mi ubicación»: pulsarlo otra vez vuelve a girar el globo. */
    var ordenUbicacion by remember { mutableStateOf(0) }

    /**
     * El punto del planeta que se está mirando en el globo. Es lo que se usa
     * para pedir los vuelos cercanos: antes se preguntaba al mapa de calle,
     * que puede estar a diez mil kilómetros de donde está la vista.
     */
    var centroVista by remember { mutableStateOf(35.0 to 5.0) }

    val abrirSatelite: (SateliteEnVuelo) -> Unit = { s ->
        vm.seleccionar(sateliteComoPunto(s))
        detalleAbierto = true
    }

    val estadoPanel = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val estadoDetalle = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val estadoLegal = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {

        // El globo es la vista. Todas las capas activas se dibujan sobre él:
        // cámaras, sismos, vuelos, barcos, y los satélites a su altitud real.
        GloboOjoDeDios(
            puntos = puntos,
            satelites = satelites,
            colorVerdadero = fuenteNasa != null,
            centrarEn = miUbicacion,
            pausado = vistaCalle != null,
            ordenReinicio = ordenReinicio,
            ordenUbicacion = ordenUbicacion,
            modifier = Modifier.fillMaxSize(),
            alPedirMasDetalle = { avisoDetalle = true },
            alTocarPunto = { vm.seleccionar(it); detalleAbierto = true },
            alTocarSatelite = abrirSatelite,
            alCambiarCentro = { centroVista = it },
        )

        // El mapa de mosaicos no está en pantalla, así que se queda pausado:
        // no tiene sentido descargar mosaicos de un mapa que nadie ve. La vista
        // de calle lo despierta mientras está abierta.
        LaunchedEffect(Unit) { controlador.tapar() }

        // ─────────── Cabecera HUD ───────────
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Colores.Panel)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Colores.Cian)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "OJO DE DIOS",
                        style = MaterialTheme.typography.titleLarge,
                        color = Colores.Texto,
                    )
                    Text(
                        "solo fuentes públicas abiertas",
                        style = MaterialTheme.typography.labelSmall,
                        color = Colores.TextoTenue,
                    )
                }
                BotonFondo("Límites legales", activo = legalAbierto) { legalAbierto = true }
                Spacer(Modifier.width(12.dp))
                ContadorObjetos(puntos.size + satelites.size)
            }

            // Atribución. No es decoración: la NASA no la exige, pero CelesTrak
            // sí pide que se cite el origen de los elementos orbitales, y en la
            // vista de calle los términos de CARTO y OpenStreetMap exigen que se
            // vea la suya, que es la que se pone allí.
            Text(
                ATRIBUCION_GLOBO,
                style = MaterialTheme.typography.labelSmall,
                color = Colores.TextoTenue,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
            )
        }

        // ─────────── Botonera inferior ───────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "arrastra para girar · pellizca para acercar · " +
                    "toca dos veces algo para ponerlo de frente · " +
                    "toca un punto para ver su ficha",
                style = MaterialTheme.typography.labelSmall,
                color = Colores.TextoTenue,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonHud("Capas") { panelCapasAbierto = true }
                BotonHud("Mi ubicación") {
                    alPedirUbicacion()
                    ordenUbicacion++
                }
                BotonHud("Reiniciar") { ordenReinicio++ }
            }
        }
    }

    // ─────────── Panel de capas ───────────
    if (panelCapasAbierto) {
        ModalBottomSheet(
            onDismissRequest = { panelCapasAbierto = false },
            sheetState = estadoPanel,
            containerColor = Colores.PanelSuave,
        ) {
            Text(
                "CAPAS DE OBSERVACIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = Colores.Cian,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
            Text(
                "Todas las capas se dibujan sobre el globo, cada una con su color. " +
                    "El radar de lluvia y la imagen de la NASA son para el mapa " +
                    "de calle: el radar se ve al abrir la vista de calle desde " +
                    "cualquier cámara, y la capa de la NASA cambia el planeta " +
                    "del globo por la imagen de hoy.",
                style = MaterialTheme.typography.bodySmall,
                color = Colores.Ambar,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                for (grupo in capas.values.map { it.capa.grupo }.distinct()) {
                    item {
                        Text(
                            grupo.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Colores.TextoTenue,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    val delGrupo = capas.values.filter { it.capa.grupo == grupo }
                    for (estado in delGrupo) {
                        item(key = estado.capa.name) {
                            FilaCapa(
                                estado = estado,
                                alAlternar = {
                                    vm.alternar(estado.capa, centroVista)
                                },
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider(color = Colores.Borde, modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        "FONDO DE MAPA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Colores.TextoTenue,
                        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                    )
                }
                item {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BotonFondo("Oscuro", fuenteBase == Fuentes.CARTO_OSCURO) {
                            alCambiarBase(Fuentes.CARTO_OSCURO)
                        }
                        BotonFondo("Satélite", fuenteBase == Fuentes.ESRI_IMAGEN) {
                            alCambiarBase(Fuentes.ESRI_IMAGEN)
                        }
                        BotonFondo("Claro", fuenteBase == Fuentes.CARTO_CLARO) {
                            alCambiarBase(Fuentes.CARTO_CLARO)
                        }
                    }
                }

                if (Fuentes.cartoSinClave) {
                    item {
                        Text(
                            "Los fondos oscuro y claro son de CARTO y saldrán con la marca " +
                                "de agua «API KEY REQUIRED». Pide una clave gratuita en " +
                                "carto.com/basemaps/apikey y añade a local.properties: " +
                                "carto.apiKey=tu_clave — después vuelve a compilar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Colores.Ambar,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    BotonAncho("Refrescar capas activas") {
                        vm.refrescarTodo(centroVista)
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }

    // ─────────── Hasta dónde llega el globo ───────────
    if (avisoDetalle) {
        AlertDialog(
            onDismissRequest = { avisoDetalle = false },
            containerColor = Colores.PanelSuave,
            title = {
                Text("Hasta aquí llega el globo", color = Colores.Texto)
            },
            text = {
                Text(
                    "La geografía del globo es el mapamundi de la NASA, de " +
                        "dominio público: unos 10 km por píxel. Tiene la " +
                        "antigüedad de un día, así que tampoco hay nubes de hoy. " +
                        "No existe ninguna fuente abierta con más detalle que " +
                        "esto: si pudieras seguir acercándote, solo verías una " +
                        "mancha borrosa.\n\n" +
                        "Para ver calles hay que bajar al mapa, que sí llega " +
                        "hasta el nivel de portal. Se abre con el botón de " +
                        "abajo, centrado en la zona que estabas mirando.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Colores.Texto,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        avisoDetalle = false
                        // Lo que se está mirando en el globo es lo que se abre
                        // en el mapa: así el botón lleva justo donde se estaba.
                        vistaCalle = DestinoCalle(
                            centroVista.first,
                            centroVista.second,
                            "La zona que estabas mirando",
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colores.CianTenue,
                        contentColor = Colores.Cian,
                    ),
                ) {
                    Text("Ver las calles de aquí")
                }
            },
            dismissButton = {
                Button(
                    onClick = { avisoDetalle = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colores.Panel,
                        contentColor = Colores.Texto,
                    ),
                ) {
                    Text("Seguir en el globo")
                }
            },
        )
    }

    // ─────────── Límites legales ───────────
    if (legalAbierto) {
        ModalBottomSheet(
            onDismissRequest = { legalAbierto = false },
            sheetState = estadoLegal,
            containerColor = Colores.PanelSuave,
        ) {
            PanelLegal()
        }
    }

    // ─────────── Ficha de detalle ───────────
    if (detalleAbierto && seleccion != null) {
        ModalBottomSheet(
            onDismissRequest = { detalleAbierto = false; vm.seleccionar(null) },
            sheetState = estadoDetalle,
            containerColor = Colores.PanelSuave,
        ) {
            FichaDetalle(
                punto = seleccion!!,
                alVerCalle = {
                    seleccion?.let {
                        vistaCalle = DestinoCalle(it.lat, it.lon, it.nombre)
                    }
                    detalleAbierto = false
                },
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    // ─────────── Vista de calle ───────────
    // El mapa de mosaicos, con calles y nombres, encima del globo y solo
    // mientras se mira: al cerrarla vuelve el planeta. Es lo único que el globo
    // no puede hacer, porque su geografía son 10 km por píxel.
    vistaCalle?.let { destino ->
        Box(Modifier.fillMaxSize().background(Colores.Fondo)) {
            MapaOjoDeDios(
                controlador = controlador,
                puntos = puntos,
                satelites = satelites,
                fuenteBase = fuenteBase,
                fuenteRadar = fuenteRadar,
                fuenteNasa = fuenteNasa,
                miUbicacion = miUbicacion,
                centroInicial = destino.lat to destino.lon,
                zoomInicial = 15.0,
                alTocarPunto = { vm.seleccionar(it); detalleAbierto = true },
                alTocarSatelite = abrirSatelite,
                alMoverMapa = { },
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Colores.Panel)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonHud("Volver al globo") { vistaCalle = null }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        destino.titulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Colores.Texto,
                    )
                    // Aquí la atribución que manda es la del fondo de mapa.
                    Text(
                        fuenteBase.atribucion,
                        style = MaterialTheme.typography.labelSmall,
                        color = Colores.TextoTenue,
                    )
                }
            }

            // El mapa solo descarga mientras se está mirando.
            DisposableEffect(destino) {
                controlador.destapar()
                onDispose { controlador.tapar() }
            }
        }
    }
}

@Composable
private fun ContadorObjetos(cantidad: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Text("OBJETOS", style = MaterialTheme.typography.labelSmall, color = Colores.TextoTenue)
        Text(
            cantidad.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = Colores.Cian,
        )
    }
}

@Composable
private fun BotonHud(etiqueta: String, alPulsar: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Colores.Panel)
            .border(1.dp, Colores.Borde, RoundedCornerShape(10.dp))
            .clickable { alPulsar() }
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Colores.Texto)
    }
}

@Composable
private fun BotonFondo(etiqueta: String, activo: Boolean, alPulsar: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (activo) Colores.CianTenue else Colores.Panel)
            .border(1.dp, if (activo) Colores.Cian else Colores.Borde, RoundedCornerShape(8.dp))
            .clickable { alPulsar() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = if (activo) Colores.Cian else Colores.Texto,
        )
    }
}

@Composable
private fun BotonAncho(etiqueta: String, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Colores.CianTenue,
            contentColor = Colores.Cian,
        ),
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilaCapa(estado: EstadoCapa, alAlternar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { alAlternar() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El punto de color es la leyenda: es el mismo color con el que esa
        // capa se dibuja sobre el planeta, así que mirando el globo se sabe de
        // qué capa es cada punto sin abrir nada.
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(colorDeCapa(estado.capa))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                estado.capa.etiqueta,
                style = MaterialTheme.typography.bodyMedium,
                color = Colores.Texto,
            )
            Text(
                if (estado.nota.isBlank()) estado.capa.descripcion else estado.nota,
                style = MaterialTheme.typography.bodySmall,
                color = Colores.TextoTenue,
            )
        }
        if (estado.activa) {
            Text(
                if (estado.cargando) "…" else "${estado.objetos}",
                style = MaterialTheme.typography.labelSmall,
                color = etiquetaColor(estado),
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Switch(
            checked = estado.activa,
            onCheckedChange = { alAlternar() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Colores.Cian,
                checkedTrackColor = Colores.CianTenue,
            ),
        )
    }
}

private fun etiquetaColor(estado: EstadoCapa) = when (estado.origen) {
    OrigenDatos.VIVO -> Colores.Verde
    OrigenDatos.RESPALDO -> Colores.Violeta
    OrigenDatos.ERROR -> Colores.Rojo
}

/** El color de la capa, en el formato de Compose. */
private fun colorDeCapa(capa: IdCapa): Color {
    val rgb = capa.colorEnGlobo
    return Color(rgb[0], rgb[1], rgb[2])
}

/**
 * Ficha de un objeto: nombre, vista previa si la fuente publica imagen, y los
 * datos que la fuente declare. Nada más: lo que no está en los datos no se
 * inventa.
 */
@Composable
private fun FichaDetalle(punto: PuntoMapa, alVerCalle: (() -> Unit)? = null) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(punto.nombre, style = MaterialTheme.typography.titleMedium, color = Colores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(punto.fuente, style = MaterialTheme.typography.bodySmall, color = Colores.Cian)
        Spacer(Modifier.height(14.dp))

        // El globo sirve para ver el planeta, pero no distingue una calle. Este
        // botón abre la vista de calle justo en este punto.
        if (alVerCalle != null) {
            BotonAncho("Ver la calle en el mapa") { alVerCalle() }
            Spacer(Modifier.height(10.dp))
        }

        if (punto.imagenUrl != null) {
            ImagenRemota(punto.imagenUrl)
            Spacer(Modifier.height(14.dp))
        } else if (punto.capa == IdCapa.SATELITES) {
            Aviso(
                "Este objeto no envía imagen. Su posición se calcula aquí mismo " +
                    "propagando los elementos orbitales que publica CelesTrak con el " +
                    "modelo SGP4."
            )
            Spacer(Modifier.height(14.dp))
        }

        for ((clave, valor) in punto.detalle) {
            if (valor.isBlank() || valor == "—") continue
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    clave,
                    style = MaterialTheme.typography.labelSmall,
                    color = Colores.TextoTenue,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    valor,
                    style = MaterialTheme.typography.bodySmall,
                    color = Colores.Texto,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Colores.Borde)
        Spacer(Modifier.height(10.dp))
        Text(
            "Dato tomado de una fuente que su operador publica de forma abierta.",
            style = MaterialTheme.typography.labelSmall,
            color = Colores.TextoTenue,
        )
    }
}

@Composable
private fun Aviso(texto: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Colores.CianTenue)
            .padding(14.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.bodySmall, color = Colores.Texto)
    }
}

/**
 * Imagen remota con reintento periódico.
 *
 * Las cámaras de tráfico publican un fotograma cada pocos minutos, así que se
 * vuelve a pedir cada 30 s. Se descarga a mano en vez de usar una librería de
 * imágenes para no añadir otra dependencia al proyecto.
 */
@Composable
private fun ImagenRemota(url: String, cadaMs: Long = 30_000) {
    var mapaBits by remember(url) { mutableStateOf<Bitmap?>(null) }
    var fallo by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val separador = if (url.contains("?")) "&" else "?"
        while (true) {
            val marca = System.currentTimeMillis()
            val descargada = withContext(Dispatchers.IO) { descargarBitmap("$url${separador}t=$marca") }
            if (descargada != null) {
                mapaBits = descargada
                fallo = false
            } else {
                fallo = true
            }
            delay(cadaMs)
        }
    }

    val actual = mapaBits
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Colores.Fondo)
            .border(1.dp, Colores.Borde, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (actual != null) {
            Image(
                bitmap = actual.asImageBitmap(),
                contentDescription = "Vista de la cámara",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                if (fallo) "Sin señal de esta cámara" else "Cargando…",
                style = MaterialTheme.typography.bodySmall,
                color = Colores.TextoTenue,
            )
        }
    }
}

private fun descargarBitmap(url: String): Bitmap? = try {
    val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 12_000
        setRequestProperty("User-Agent", "OjoDeDios/1.0 (Android)")
    }
    try {
        conexion.inputStream.use { BitmapFactory.decodeStream(it) }
    } finally {
        conexion.disconnect()
    }
} catch (e: Exception) {
    null
}

private fun descripcionTipo(tipo: String) = when (tipo) {
    "estacion" -> "Estación espacial tripulada"
    "tripulada" -> "Nave tripulada"
    "carga" -> "Nave de carga"
    "meteorologico" -> "Satélite meteorológico (geoestacionario)"
    "observacion" -> "Satélite de observación terrestre"
    else -> tipo
}
