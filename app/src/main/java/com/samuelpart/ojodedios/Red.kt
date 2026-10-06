package com.samuelpart.ojodedios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP mínimo.
 *
 * Android ya trae en el sistema `HttpURLConnection` y el parser `org.json`, así
 * que no hacen falta OkHttp ni kotlinx.serialization. El proyecto se queda con
 * dos únicas dependencias externas: osmdroid (mapa) y predict4java (SGP4).
 *
 * Menos dependencias = menos versiones que puedan romper el build.
 */
object Red {

    private const val AGENTE =
        "OjoDeDios/1.0 (Android) +https://github.com/SamuelPart/Qr"

    suspend fun texto(url: String, tiempoMaxMs: Int = 15_000): String =
        withContext(Dispatchers.IO) {
            val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = tiempoMaxMs
                readTimeout = tiempoMaxMs
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", AGENTE)
                setRequestProperty("Accept", "application/json, */*")
            }
            try {
                val codigo = conexion.responseCode
                if (codigo !in 200..299) throw ErrorRed("HTTP $codigo")
                conexion.inputStream.bufferedReader().use { it.readText() }
            } catch (e: ErrorRed) {
                throw e
            } catch (e: Exception) {
                throw ErrorRed(e.message ?: "fallo de red")
            } finally {
                conexion.disconnect()
            }
        }

    /**
     * Descarga binaria, para lo que tiene que llegar entero: la textura del
     * globo, por ejemplo. Un JPEG no sobrevive a la conversión a cadena.
     */
    suspend fun bytes(url: String, tiempoMaxMs: Int = 30_000): ByteArray =
        withContext(Dispatchers.IO) {
            val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = tiempoMaxMs
                readTimeout = tiempoMaxMs
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", AGENTE)
                setRequestProperty("Accept", "image/jpeg, image/png, */*")
            }
            try {
                val codigo = conexion.responseCode
                if (codigo !in 200..299) throw ErrorRed("HTTP $codigo")
                conexion.inputStream.use { it.readBytes() }
            } catch (e: ErrorRed) {
                throw e
            } catch (e: Exception) {
                throw ErrorRed(e.message ?: "fallo de red")
            } finally {
                conexion.disconnect()
            }
        }

    suspend fun objeto(url: String): JSONObject = JSONObject(texto(url))

    suspend fun arreglo(url: String): JSONArray = JSONArray(texto(url))

    /**
     * Lee un fichero JSON empaquetado en assets/. Se usa como respaldo cuando no
     * hay red, igual que hacen las instantáneas de la versión web.
     */
    suspend fun activo(contexto: android.content.Context, nombre: String): JSONObject =
        withContext(Dispatchers.IO) {
            contexto.assets.open("data/$nombre.json").bufferedReader().use {
                JSONObject(it.readText())
            }
        }
}

class ErrorRed(mensaje: String) : Exception(mensaje)
