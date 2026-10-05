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
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Pantalla principal: mapa a pantalla completa con una capa de controles
 * flotantes por encima, al estilo de un HUD de instrumentos.
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
    val estadoPanel = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val estadoDetalle = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(Colores.Fondo)) {

        MapaOjoDeDios(
            controlador = controlador,
            puntos = puntos,
            satelites = satelites,
            fuenteBase = fuenteBase,
            fuenteRadar = fuenteRadar,
            fuenteNasa = fuenteNasa,
            miUbicacion = miUbicacion,
            centroInicial = 35.0 to 5.0,
            alTocarPunto = { vm.seleccionar(it); detalleAbierto = true },
            alTocarSatelite = { s ->
                vm.seleccionar(
                    PuntoMapa(
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
                )
                detalleAbierto = true
            },
            alMoverMapa = { },
        )

        // ─────────── Cabecera HUD ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Colores.Panel)
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
            ContadorObjetos(puntos.size + satelites.size)
        }

        // ─────────── Botonera inferior ───────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BotonHud("Capas") { panelCapasAbierto = true }
            BotonHud("Mi ubicación") { alPedirUbicacion() }
            BotonHud("Global") { controlador.moverA(35.0, 5.0, 3.0) }
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
                                    vm.alternar(estado.capa, controlador.centro())
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
                item {
                    BotonAncho("Refrescar capas activas") {
                        vm.refrescarTodo(controlador.centro())
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }

    // ─────────── Ficha de detalle ───────────
    if (detalleAbierto && seleccion != null) {
        ModalBottomSheet(
            onDismissRequest = { detalleAbierto = false; vm.seleccionar(null) },
            sheetState = estadoDetalle,
            containerColor = Colores.PanelSuave,
        ) {
            FichaDetalle(seleccion!!)
            Spacer(Modifier.height(28.dp))
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

/**
 * Ficha de un objeto: nombre, vista previa si la fuente publica imagen, y los
 * datos que la fuente declare. Nada más: lo que no está en los datos no se
 * inventa.
 */
@Composable
private fun FichaDetalle(punto: PuntoMapa) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(punto.nombre, style = MaterialTheme.typography.titleMedium, color = Colores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(punto.fuente, style = MaterialTheme.typography.bodySmall, color = Colores.Cian)
        Spacer(Modifier.height(14.dp))

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
