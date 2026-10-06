package com.samuelpart.ojodedios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * El mismo contenido que el panel "Límites legales" de la versión web.
 *
 * Está aquí y no en un documento aparte por un motivo: la app muestra datos
 * que mucha gente confunde con vigilancia, y la explicación de por qué no lo
 * es debe estar donde se usan, no en un archivo que nadie abre.
 */
private data class Bloque(
    val titulo: String,
    val parrafos: List<String> = emptyList(),
    val vinetas: List<String> = emptyList(),
)

private val contenido = listOf(
    Bloque(
        titulo = "Lo que hace esta app",
        parrafos = listOf(
            "Solo consume feeds que su operador publica de forma abierta: " +
                "cámaras de tráfico oficiales, datos orbitales públicos, " +
                "sismología, radar meteorológico e imágenes de la NASA. " +
                "No hay accesos, credenciales ni puertas traseras.",
        ),
    ),
    Bloque(
        titulo = "Lo que sí es público",
        vinetas = listOf(
            "Cámaras de tráfico oficiales. TfL (Londres), Fintraffic (Finlandia), el " +
                "Departamento de Transporte de Hong Kong y la autoridad de transporte " +
                "de Singapur publican sus cámaras como datos abiertos. Las ves porque " +
                "el organismo decidió publicarlas.",
            "Vuelos. Las aeronaves emiten su posición por radio en abierto (ADS-B). " +
                "Cualquiera con un receptor barato la capta: no es una filtración, es " +
                "una emisión.",
            "Barcos. Igual, por AIS. Obligatorio por convenio internacional en buques " +
                "grandes.",
            "Satélites. Las efemérides orbitales las publica el 18.º Escuadrón de " +
                "Defensa Espacial de EE. UU. Son dominio público.",
            "Imágenes satelitales. NASA, ESA y USGS publican observación terrestre de " +
                "forma gratuita y deliberada.",
        ),
    ),
    Bloque(
        titulo = "Lo que no es accesible, y por qué",
        vinetas = listOf(
            "Cámaras privadas o municipales cerradas. No están en Internet; acceder a " +
                "ellas sin autorización es delito en casi cualquier jurisdicción.",
            "Satélites espía en vivo. No existe un feed público. Los sistemas militares " +
                "son clasificados; lo comercial llega con horas o días de retraso.",
            "Zoom infinito sobre cualquier tejado en tiempo real. Ninguna constelación " +
                "civil ofrece vídeo continuo con ese detalle. Lo mejor abierto son unos " +
                "250 m por píxel con un día de antigüedad.",
            "Cámaras ajenas por escaneo de puertos o contraseñas por defecto. Eso no es " +
                "«acceder a lo expuesto», es intrusión, y la mayoría de esas cámaras son " +
                "víctimas, no servicios públicos.",
        ),
    ),
    Bloque(
        titulo = "Por qué el «Ojo de Dios» literal no existe",
        parrafos = listOf(
            "Un sistema capaz de ver cualquier punto del planeta en vivo y con detalle " +
                "exigiría cientos de miles de satélites en órbita baja con enlace en " +
                "tiempo real. No lo financian ni los presupuestos militares combinados. " +
                "Lo que existe de verdad es esto: un mosaico de fuentes abiertas, cada " +
                "una con su latencia, su cobertura y su resolución.",
        ),
    ),
    Bloque(
        titulo = "Uso responsable",
        parrafos = listOf(
            "Vigilar infraestructura pública está bien. Vigilar personas está mal. " +
                "Ninguna de estas fuentes permite identificar a una persona concreta, y " +
                "no deberías intentar reconstruir esa capacidad combinándolas. Respeta " +
                "además los límites de peticiones de cada API: son servicios gratuitos " +
                "de instituciones públicas.",
        ),
    ),
)

@Composable
fun PanelLegal(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "QUÉ PUEDES VER Y QUÉ NO",
            style = MaterialTheme.typography.labelSmall,
            color = Colores.Cian,
        )
        Spacer(Modifier.height(14.dp))

        for (bloque in contenido) {
            Text(
                bloque.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = Colores.Texto,
            )
            Spacer(Modifier.height(6.dp))

            for (parrafo in bloque.parrafos) {
                Text(
                    parrafo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Colores.TextoTenue,
                )
                Spacer(Modifier.height(8.dp))
            }

            for (vineta in bloque.vinetas) {
                Text(
                    "•  $vineta",
                    style = MaterialTheme.typography.bodySmall,
                    color = Colores.TextoTenue,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Colores.Borde)
            Spacer(Modifier.height(14.dp))
        }

        Text(
            "Fuentes y créditos completos en el README del proyecto.",
            style = MaterialTheme.typography.labelSmall,
            color = Colores.TextoTenue,
        )
        Spacer(Modifier.height(28.dp))
    }
}
