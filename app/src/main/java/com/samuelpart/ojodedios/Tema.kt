package com.samuelpart.ojodedios

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Paleta del HUD: fondo profundo, cian de instrumento, ámbar de alerta.
 * Es la misma que usa la versión web, para que ambas se reconozcan.
 */
object Colores {
    val Fondo = Color(0xFF05070C)
    val Panel = Color(0xF20A0E16)
    val PanelSuave = Color(0xFF10151F)
    val Cian = Color(0xFF22D3EE)
    val CianTenue = Color(0x2222D3EE)
    val Ambar = Color(0xFFF59E0B)
    val Violeta = Color(0xFFA78BFA)
    val Verde = Color(0xFF34D399)
    val Rojo = Color(0xFFEF4444)
    val Texto = Color(0xFFDBE6F0)
    val TextoTenue = Color(0xFF7D8FA3)
    val Borde = Color(0x2E78BEDC)
}

private val esquema = darkColorScheme(
    primary = Colores.Cian,
    onPrimary = Colores.Fondo,
    secondary = Colores.Violeta,
    background = Colores.Fondo,
    onBackground = Colores.Texto,
    surface = Colores.PanelSuave,
    onSurface = Colores.Texto,
    surfaceVariant = Colores.Panel,
    onSurfaceVariant = Colores.TextoTenue,
    error = Colores.Rojo,
)

private val tipografia = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun TemaOjoDeDios(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = esquema,
        typography = tipografia,
        content = contenido,
    )
}
