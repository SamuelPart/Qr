package com.samuelpart.ojodedios

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Globo terráqueo en 3D, dibujado con OpenGL ES 2.0 del propio Android.
 *
 * Por qué no se usa una librería de mapas 3D: la única que lo tiene en Android
 * nativo hoy es la de Google, que exige cuenta de facturación con tarjeta.
 * MapLibre Native —la alternativa libre— tiene el globo en su hoja de ruta,
 * pero todavía no lo ha implementado. Así que se dibuja aquí: es una esfera,
 * una textura y unas líneas, sin ninguna dependencia nueva y sin clave.
 *
 * Qué aporta frente al mapa plano: los satélites se colocan **a su altitud
 * real**, así que se ve lo que un plano no puede mostrar — que las órbitas
 * bajas van pegadas al suelo, que el anillo geoestacionario está 35 786 km
 * más arriba, y cómo el terminador de día y noche barre el planeta.
 */

private const val RADIO_TIERRA_KM = 6371.0

/** Resolución de la esfera: 64 franjas de latitud por 96 de longitud. */
private const val SEGMENTOS_LAT = 64
private const val SEGMENTOS_LON = 96

/** La rejilla se dibuja un pelo por encima de la superficie para no competir
 *  con ella en el búfer de profundidad (lo que se ve como temblor de píxeles). */
private const val ALTITUD_REJILLA_KM = 18.0

/** Distancias de cámara, en radios terrestres. Con 14 se ve el anillo GEO
 *  entero; con 1.35 se está prácticamente sobre la superficie. */
private const val DISTANCIA_MINIMA = 1.35f
private const val DISTANCIA_MAXIMA = 14f
private const val DISTANCIA_INICIAL = 3.4f

/**
 * Convierte latitud, longitud y altitud a coordenadas de la esfera.
 *
 * El convenio de ejes es el corriente: +X hacia el este, +Y hacia el norte,
 * +Z hacia la cámara cuando el globo está sin girar. De ahí sale que el punto
 * (0, 0) —latitud y longitud cero— quede mirando al frente.
 */
private fun aVector(lat: Double, lon: Double, altitudKm: Double, salida: FloatArray) {
    val radio = 1.0 + altitudKm / RADIO_TIERRA_KM
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    salida[0] = (radio * cos(latRad) * sin(lonRad)).toFloat()
    salida[1] = (radio * sin(latRad)).toFloat()
    salida[2] = (radio * cos(latRad) * cos(lonRad)).toFloat()
}

/**
 * Dirección del Sol en el espacio del globo, a partir del punto subsolar.
 *
 * Es una aproximación: se calcula la declinación solar con la serie de Spencer
 * y la longitud subsolar con la hora UTC, sin la ecuación del tiempo. El error
 * es de pocos grados, que sobre un terminador de 12 000 km de ancho es medio
 * minuto de nada. Para ver de un vistazo qué mitad del planeta está a oscuras,
 * sobra; para astronomía de precisión, no.
 */
private fun direccionDelSol(momento: java.util.Date, salida: FloatArray) {
    val calendario = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
    calendario.time = momento

    val diaDelAno = calendario.get(java.util.Calendar.DAY_OF_YEAR)
    val hora = calendario.get(java.util.Calendar.HOUR_OF_DAY) +
        calendario.get(java.util.Calendar.MINUTE) / 60.0

    val g = 2.0 * Math.PI / 365.0 * (diaDelAno - 1 + (hora - 12.0) / 24.0)
    val declinacion = 0.006918 -
        0.399912 * cos(g) + 0.070257 * sin(g) -
        0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
        0.002697 * cos(3 * g) + 0.001480 * sin(3 * g)

    // El Sol está sobre el meridiano donde es mediodía.
    val longitudSubsolar = -15.0 * (hora - 12.0)

    aVector(Math.toDegrees(declinacion), longitudSubsolar, 0.0, salida)
}

/**
 * La vista del globo: tacto, cámara y puente con el hilo de dibujado.
 *
 * GLSurfaceView dibuja en su propio hilo, así que todo lo que llega de fuera
 * (satélites, textura, cámara) se entrega con `queueEvent`. La cámara no se
 * mueve nunca: lo que gira es el planeta, que es como se comporta un globo de
 * verdad y evita el lío de ejes de las cámaras orbitales.
 */
class GloboView(contexto: Context) : GLSurfaceView(contexto) {

    private val renderizador = RenderizadorGlobo()

    private var giro = 20f
    private var inclinacion = 18f
    private var distancia = DISTANCIA_INICIAL

    private var xPrevio = 0f
    private var yPrevio = 0f
    private var distanciaPrevia = 0f
    private var dosDedos = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderizador)
        // Solo se redibuja cuando hace falta. Un globo girando solo, a 60
        // fotogramas por segundo, se come la batería sin que nadie lo mire.
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Envía los satélites y sus trazas al hilo de dibujado. */
    fun actualizar(satelites: List<SateliteEnVuelo>) {
        val puntos = FloatArray(satelites.size * 3)
        val vector = FloatArray(3)
        satelites.forEachIndexed { i, s ->
            aVector(s.lat, s.lon, s.altitudKm, vector)
            puntos[i * 3] = vector[0]
            puntos[i * 3 + 1] = vector[1]
            puntos[i * 3 + 2] = vector[2]
        }

        val trazas = mutableListOf<FloatArray>()
        for (s in satelites) {
            for (segmento in s.traza) {
                if (segmento.size < 2) continue
                val linea = FloatArray(segmento.size * 3)
                segmento.forEachIndexed { i, punto ->
                    // La traza viene en latitud y longitud, sin altitud. Se le
                    // pone la del satélite ahora mismo: en las órbitas bajas,
                    // que son las que llevan traza, la altura varía pocos
                    // kilómetros en 100 minutos y en el globo no se nota.
                    aVector(punto.first, punto.second, s.altitudKm, vector)
                    linea[i * 3] = vector[0]
                    linea[i * 3 + 1] = vector[1]
                    linea[i * 3 + 2] = vector[2]
                }
                trazas.add(linea)
            }
        }

        queueEvent {
            renderizador.fijarSatelites(puntos, trazas)
            requestRender()
        }
    }

    /** Entrega la textura de la Tierra cuando termina de descargarse. */
    fun fijarTextura(mapaBits: Bitmap) {
        queueEvent {
            renderizador.fijarTextura(mapaBits)
            requestRender()
        }
    }

    /** Coloca la cámara y el Sol en su sitio antes del primer fotograma. */
    fun prepararEscena() {
        actualizarCamara()
        actualizarSoles()
    }

    /** Recalcula el terminador de día y noche. */
    fun actualizarSoles() {
        val direccion = FloatArray(3)
        direccionDelSol(java.util.Date(), direccion)
        queueEvent {
            renderizador.fijarSol(direccion)
            requestRender()
        }
    }

    // ─────────────────────────── Tacto ───────────────────────────

    /**
     * Girar y acercar. El signo de cada eje está puesto para que la superficie
     * siga al dedo: si arrastras a la derecha, el trozo de planeta que tenías
     * debajo se va a la derecha, como si lo agarraras.
     */
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                xPrevio = evento.x
                yPrevio = evento.y
                dosDedos = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (evento.pointerCount >= 2) {
                    dosDedos = true
                    distanciaPrevia = separacion(evento)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dosDedos && evento.pointerCount >= 2) {
                    val actual = separacion(evento)
                    if (distanciaPrevia > 0f && actual > 0f) {
                        distancia *= distanciaPrevia / actual
                        distancia = distancia.coerceIn(DISTANCIA_MINIMA, DISTANCIA_MAXIMA)
                    }
                    distanciaPrevia = actual
                } else {
                    giro += (evento.x - xPrevio) * 0.35f
                    inclinacion = (inclinacion + (evento.y - yPrevio) * 0.35f)
                        .coerceIn(-89f, 89f)
                    xPrevio = evento.x
                    yPrevio = evento.y
                }
                actualizarCamara()
                requestRender()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dosDedos = false
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun separacion(evento: MotionEvent): Float {
        val dx = evento.getX(0) - evento.getX(1)
        val dy = evento.getY(0) - evento.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun actualizarCamara() {
        queueEvent {
            renderizador.fijarCamara(giro, inclinacion, distancia)
            requestRender()
        }
    }

    // ─────────────────────── Renderizador ───────────────────────

    private class RenderizadorGlobo : Renderer {

        private var programaEsfera = 0
        private var programaPuntos = 0

        // Las localizaciones de atributos y uniformes se buscan al enlazar el
        // programa, no en cada fotograma: glGetAttribLocation compara cadenas y
        // hacerlo 60 veces por segundo es trabajo regalado.
        private var aPosEsfera = -1
        private var aUvEsfera = -1
        private var uMvpEsfera = -1
        private var uSolEsfera = -1
        private var uTexturaEsfera = -1
        private var uTexturaListaEsfera = -1

        private var aPosPuntos = -1
        private var uMvpPuntos = -1
        private var uColorPuntos = -1
        private var uTamanoPuntos = -1

        private var verticesEsfera: FloatBuffer? = null
        private var indicesEsfera: ShortBuffer? = null
        private var numeroIndices = 0

        private var rejilla: List<FloatBuffer> = emptyList()
        private var tamanosRejilla = IntArray(0)

        private var textura = 0
        private var texturaPendiente: Bitmap? = null
        /** Se guarda el mapa original para poder volver a subirlo si el sistema
         *  se lleva por delante el contexto de OpenGL (pasa al apagar la
         *  pantalla). Sin esto, el globo se quedaría sin geografía para siempre. */
        private var mapaTierra: Bitmap? = null
        private var texturaLista = false

        private var satelites: FloatBuffer? = null
        private var numeroSatelites = 0
        private var trazas: List<FloatBuffer> = emptyList()
        private var tamanosTrazas = IntArray(0)

        private val sol = floatArrayOf(1f, 0f, 0f)
        private val solMundo = FloatArray(4)
        private val solModelo = FloatArray(4)

        @Volatile private var giro = 20f
        @Volatile private var inclinacion = 18f
        @Volatile private var distancia = DISTANCIA_INICIAL

        private val proyeccion = FloatArray(16)
        private val vista = FloatArray(16)
        private val modelo = FloatArray(16)
        private val vistaModelo = FloatArray(16)
        private val mvp = FloatArray(16)
        private val inversa = FloatArray(16)

        fun fijarCamara(giroNuevo: Float, inclinacionNueva: Float, distanciaNueva: Float) {
            giro = giroNuevo
            inclinacion = inclinacionNueva
            distancia = distanciaNueva
        }

        fun fijarSol(direccion: FloatArray) {
            System.arraycopy(direccion, 0, sol, 0, 3)
        }

        fun fijarTextura(mapaBits: Bitmap) {
            texturaPendiente = mapaBits
        }

        fun fijarSatelites(puntos: FloatArray, lineas: List<FloatArray>) {
            satelites = aBuffer(puntos)
            numeroSatelites = puntos.size / 3
            trazas = lineas.map { aBuffer(it) }
            tamanosTrazas = IntArray(lineas.size) { lineas[it].size / 3 }
        }

        // ── Ciclo de OpenGL ──

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.015f, 0.02f, 0.035f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            programaEsfera = crearPrograma(VERTICE_ESFERA, FRAGMENTO_ESFERA)
            aPosEsfera = GLES20.glGetAttribLocation(programaEsfera, "aPos")
            aUvEsfera = GLES20.glGetAttribLocation(programaEsfera, "aUV")
            uMvpEsfera = GLES20.glGetUniformLocation(programaEsfera, "uMVP")
            uSolEsfera = GLES20.glGetUniformLocation(programaEsfera, "uSol")
            uTexturaEsfera = GLES20.glGetUniformLocation(programaEsfera, "uTextura")
            uTexturaListaEsfera = GLES20.glGetUniformLocation(programaEsfera, "uTexturaLista")

            programaPuntos = crearPrograma(VERTICE_PUNTO, FRAGMENTO_PUNTO)
            aPosPuntos = GLES20.glGetAttribLocation(programaPuntos, "aPos")
            uMvpPuntos = GLES20.glGetUniformLocation(programaPuntos, "uMVP")
            uColorPuntos = GLES20.glGetUniformLocation(programaPuntos, "uColor")
            uTamanoPuntos = GLES20.glGetUniformLocation(programaPuntos, "uTamano")

            construirEsfera()
            construirRejilla()

            textura = crearTextura()
            mapaTierra?.let {
                subirTextura(it)
                texturaLista = true
            }
        }

        override fun onSurfaceChanged(gl: GL10?, ancho: Int, alto: Int) {
            GLES20.glViewport(0, 0, ancho, alto)
            val aspecto = ancho.toFloat() / alto.coerceAtLeast(1)
            // 45° de campo de visión: con la cámara a 3,4 radios, el planeta
            // ocupa algo más de dos tercios de la pantalla.
            Matrix.perspectiveM(proyeccion, 0, 45f, aspecto, 0.1f, 100f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            texturaPendiente?.let { mapaBits ->
                mapaTierra?.recycle()
                mapaTierra = mapaBits
                subirTextura(mapaBits)
                texturaPendiente = null
                texturaLista = true
            }

            calcularMatrices()
            dibujarEsfera()
            dibujarRejilla()
            dibujarSatelites()
        }

        private fun calcularMatrices() {
            // La rotación inversa sirve para pasar el Sol al espacio del globo.
            // Así, al girar el planeta con el dedo, el terminador se queda
            // donde tiene que estar y no se va con la vista.
            Matrix.setIdentityM(inversa, 0)
            Matrix.rotateM(inversa, 0, -inclinacion, 1f, 0f, 0f)
            Matrix.rotateM(inversa, 0, -giro, 0f, 1f, 0f)
            solMundo[0] = sol[0]
            solMundo[1] = sol[1]
            solMundo[2] = sol[2]
            solMundo[3] = 0f
            Matrix.multiplyMV(solModelo, 0, inversa, 0, solMundo, 0)

            // El globo: primero se inclina sobre su eje X y luego se gira sobre
            // el eje Y del mundo. Es el comportamiento de un globo de mesa.
            Matrix.setIdentityM(modelo, 0)
            Matrix.rotateM(modelo, 0, giro, 0f, 1f, 0f)
            Matrix.rotateM(modelo, 0, inclinacion, 1f, 0f, 0f)

            Matrix.setLookAtM(
                vista, 0,
                0f, 0f, distancia,
                0f, 0f, 0f,
                0f, 1f, 0f,
            )

            Matrix.multiplyMM(vistaModelo, 0, vista, 0, modelo, 0)
            Matrix.multiplyMM(mvp, 0, proyeccion, 0, vistaModelo, 0)
        }

        // ── Geometría ──

        private fun construirEsfera() {
            val vertices = FloatArray((SEGMENTOS_LAT + 1) * (SEGMENTOS_LON + 1) * 5)
            var v = 0
            for (i in 0..SEGMENTOS_LAT) {
                val lat = 90.0 - 180.0 * i / SEGMENTOS_LAT
                for (j in 0..SEGMENTOS_LON) {
                    val lon = -180.0 + 360.0 * j / SEGMENTOS_LON
                    val posicion = FloatArray(3)
                    aVector(lat, lon, 0.0, posicion)

                    vertices[v++] = posicion[0]
                    vertices[v++] = posicion[1]
                    vertices[v++] = posicion[2]
                    // La textura es equirectangular: la longitud va al ancho y la
                    // latitud al alto. Y aquí está el detalle que se suele
                    // equivocar: OpenGL lee la fila 0 del mapa de bits como
                    // v = 0, y la fila 0 de una imagen equirectangular es el
                    // polo norte. Como la malla también pone v = 0 en el polo
                    // norte, las dos convenciones se cancelan y no hace falta
                    // darle la vuelta a nada.
                    vertices[v++] = j.toFloat() / SEGMENTOS_LON
                    vertices[v++] = i.toFloat() / SEGMENTOS_LAT
                }
            }

            val indices = ShortArray(SEGMENTOS_LAT * SEGMENTOS_LON * 6)
            var k = 0
            for (i in 0 until SEGMENTOS_LAT) {
                for (j in 0 until SEGMENTOS_LON) {
                    val a = (i * (SEGMENTOS_LON + 1) + j).toShort()
                    val b = (a + 1).toShort()
                    val c = (a + SEGMENTOS_LON + 1).toShort()
                    val d = (c + 1).toShort()
                    indices[k++] = a; indices[k++] = c; indices[k++] = b
                    indices[k++] = b; indices[k++] = c; indices[k++] = d
                }
            }
            numeroIndices = indices.size

            verticesEsfera = aBuffer(vertices)
            indicesEsfera = aBufferCorto(indices)
        }

        /**
         * Meridianos y paralelos cada 30°. No es adorno: sirve para leer en qué
         * latitud va un satélite y, sobre todo, para que el globo se entienda
         * como globo incluso cuando no hay red y no hay textura que mirar.
         */
        private fun construirRejilla() {
            val lineas = mutableListOf<FloatArray>()
            val punto = FloatArray(3)

            for (lon in -180 until 180 step 30) {
                val meridianos = FloatArray(3 * 49)
                for (i in 0..48) {
                    val lat = -90.0 + 180.0 * i / 48.0
                    aVector(lat, lon.toDouble(), ALTITUD_REJILLA_KM, punto)
                    meridianos[i * 3] = punto[0]
                    meridianos[i * 3 + 1] = punto[1]
                    meridianos[i * 3 + 2] = punto[2]
                }
                lineas.add(meridianos)
            }

            for (lat in -60 until 90 step 30) {
                val paralelos = FloatArray(3 * 97)
                for (i in 0..96) {
                    val lon = -180.0 + 360.0 * i / 96.0
                    aVector(lat.toDouble(), lon, ALTITUD_REJILLA_KM, punto)
                    paralelos[i * 3] = punto[0]
                    paralelos[i * 3 + 1] = punto[1]
                    paralelos[i * 3 + 2] = punto[2]
                }
                lineas.add(paralelos)
            }

            rejilla = lineas.map { aBuffer(it) }
            tamanosRejilla = IntArray(lineas.size) { lineas[it].size / 3 }
        }

        // ── Dibujado ──

        private fun dibujarEsfera() {
            val buffer = verticesEsfera ?: return
            val indices = indicesEsfera ?: return

            GLES20.glUseProgram(programaEsfera)

            val paso = 5 * 4
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(aPosEsfera)
            GLES20.glVertexAttribPointer(aPosEsfera, 3, GLES20.GL_FLOAT, false, paso, buffer)

            buffer.position(3)
            GLES20.glEnableVertexAttribArray(aUvEsfera)
            GLES20.glVertexAttribPointer(aUvEsfera, 2, GLES20.GL_FLOAT, false, paso, buffer)

            GLES20.glUniformMatrix4fv(uMvpEsfera, 1, false, mvp, 0)
            GLES20.glUniform3f(uSolEsfera, solModelo[0], solModelo[1], solModelo[2])
            GLES20.glUniform1f(uTexturaListaEsfera, if (texturaLista) 1f else 0f)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
            GLES20.glUniform1i(uTexturaEsfera, 0)

            indices.position(0)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, numeroIndices, GLES20.GL_UNSIGNED_SHORT, indices
            )

            GLES20.glDisableVertexAttribArray(aPosEsfera)
            GLES20.glDisableVertexAttribArray(aUvEsfera)
        }

        private fun dibujarRejilla() {
            dibujarSegmentos(rejilla, tamanosRejilla, 0.34f, 0.44f, 0.56f, 0.30f, 1f)
        }

        private fun dibujarSatelites() {
            val bufferSatelites = satelites ?: return
            if (numeroSatelites == 0) return

            GLES20.glUseProgram(programaPuntos)
            GLES20.glUniformMatrix4fv(uMvpPuntos, 1, false, mvp, 0)
            GLES20.glEnableVertexAttribArray(aPosPuntos)

            // Trazas orbitales: violeta translúcido, para que se lean las
            // inclinaciones sin tapar el planeta.
            dibujarSegmentos(trazas, tamanosTrazas, 0.66f, 0.56f, 0.98f, 0.55f, 1f)

            // Los satélites, como puntos. Diez píxeles de ancho: a esa escala se
            // ven sin convertir la pantalla en un prado de cuadros.
            bufferSatelites.position(0)
            GLES20.glVertexAttribPointer(aPosPuntos, 3, GLES20.GL_FLOAT, false, 12, bufferSatelites)
            GLES20.glUniform4f(uColorPuntos, 0.98f, 0.75f, 0.25f, 1f)
            GLES20.glUniform1f(uTamanoPuntos, 10f)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numeroSatelites)

            GLES20.glDisableVertexAttribArray(aPosPuntos)
        }

        /** Dibuja una lista de tiras de líneas con el programa de puntos. */
        private fun dibujarSegmentos(
            buffers: List<FloatBuffer>,
            tamanos: IntArray,
            rojo: Float, verde: Float, azul: Float, alfa: Float, grosor: Float,
        ) {
            if (buffers.isEmpty()) return
            GLES20.glUseProgram(programaPuntos)
            GLES20.glUniformMatrix4fv(uMvpPuntos, 1, false, mvp, 0)
            GLES20.glUniform4f(uColorPuntos, rojo, verde, azul, alfa)
            GLES20.glUniform1f(uTamanoPuntos, grosor)
            GLES20.glEnableVertexAttribArray(aPosPuntos)

            buffers.forEachIndexed { i, buffer ->
                buffer.position(0)
                GLES20.glVertexAttribPointer(aPosPuntos, 3, GLES20.GL_FLOAT, false, 12, buffer)
                GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, tamanos[i])
            }

            GLES20.glDisableVertexAttribArray(aPosPuntos)
        }

        // ── Recursos de OpenGL ──

        private fun crearPrograma(fuenteVertice: String, fuenteFragmento: String): Int {
            val vertice = compilar(GLES20.GL_VERTEX_SHADER, fuenteVertice)
            val fragmento = compilar(GLES20.GL_FRAGMENT_SHADER, fuenteFragmento)
            val programa = GLES20.glCreateProgram()
            GLES20.glAttachShader(programa, vertice)
            GLES20.glAttachShader(programa, fragmento)
            GLES20.glLinkProgram(programa)
            GLES20.glDeleteShader(vertice)
            GLES20.glDeleteShader(fragmento)
            return programa
        }

        private fun compilar(tipo: Int, fuente: String): Int {
            val sombreador = GLES20.glCreateShader(tipo)
            GLES20.glShaderSource(sombreador, fuente)
            GLES20.glCompileShader(sombreador)
            return sombreador
        }

        private fun crearTextura(): Int {
            val identificadores = IntArray(1)
            GLES20.glGenTextures(1, identificadores, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, identificadores[0])
            // Sin mipmaps: el globo se ve casi siempre a la misma escala y
            // generarlos cuesta memoria y tiempo de carga.
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
            )
            // En longitud se repite (el borde izquierdo y el derecho son el
            // mismo meridiano); en latitud no, porque los polos son un punto.
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
            )
            return identificadores[0]
        }

        private fun subirTextura(mapaBits: Bitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mapaBits, 0)
        }

        companion object {
            fun aBuffer(datos: FloatArray): FloatBuffer =
                ByteBuffer.allocateDirect(datos.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply { put(datos); position(0) }

            fun aBufferCorto(datos: ShortArray): ShortBuffer =
                ByteBuffer.allocateDirect(datos.size * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .apply { put(datos); position(0) }
        }
    }

    private companion object {

        /**
         * La Tierra. El Sol llega ya pasado al espacio del globo, y
         * `uTexturaLista` permite dibujar un océano azul mientras la textura no
         * ha llegado: el globo nunca aparece vacío ni en negro.
         */
        const val VERTICE_ESFERA = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec2 aUV;
            varying vec2 vUV;
            varying vec3 vNormal;
            void main() {
                vUV = aUV;
                vNormal = aPos;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENTO_ESFERA = """
            precision mediump float;
            uniform sampler2D uTextura;
            uniform vec3 uSol;
            uniform float uTexturaLista;
            varying vec2 vUV;
            varying vec3 vNormal;
            void main() {
                vec3 base = uTexturaLista > 0.5
                    ? texture2D(uTextura, vUV).rgb
                    : vec3(0.05, 0.16, 0.34);
                float luz = dot(normalize(vNormal), normalize(uSol));
                float dia = smoothstep(-0.12, 0.15, luz);
                gl_FragColor = vec4(base * (0.16 + 0.95 * dia), 1.0);
            }
        """

        const val VERTICE_PUNTO = """
            uniform mat4 uMVP;
            uniform float uTamano;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                gl_PointSize = uTamano;
            }
        """

        const val FRAGMENTO_PUNTO = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """
    }
}

/**
 * El globo como componente de Compose.
 *
 * La textura se pide aparte, así que el globo aparece al instante con el océano
 * y la geografía llega unos segundos después. Si no hay red ni caché, se queda
 * con el océano y la rejilla: los satélites y las órbitas, que son lo
 * interesante, se dibujan igual.
 */
@Composable
fun GloboOjoDeDios(
    satelites: List<SateliteEnVuelo>,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val repositorio = remember(contexto) { Repositorio(contexto) }
    val globo = remember { GloboView(contexto) }

    AndroidView(modifier = modifier, factory = { globo })

    // GLSurfaceView exige que el ciclo de vida de la pantalla llegue a su hilo
    // de dibujado: si no, el sistema puede llevarse la superficie de OpenGL
    // mientras la app está en segundo plano.
    val propietario = LocalLifecycleOwner.current
    DisposableEffect(propietario, globo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> globo.onResume()
                Lifecycle.Event.ON_PAUSE -> globo.onPause()
                else -> Unit
            }
        }
        propietario.lifecycle.addObserver(observador)
        globo.prepararEscena()

        onDispose {
            propietario.lifecycle.removeObserver(observador)
            globo.onPause()
        }
    }

    // La geografía del planeta, una sola vez.
    LaunchedEffect(globo) {
        val mapaBits = withContext(Dispatchers.IO) { repositorio.texturaTierra() }
        if (mapaBits != null) globo.fijarTextura(mapaBits)
    }

    LaunchedEffect(satelites) { globo.actualizar(satelites) }

    // El terminador se mueve unos 15° por hora: refrescarlo cada minuto sobra.
    LaunchedEffect(globo) {
        while (true) {
            globo.actualizarSoles()
            delay(60_000)
        }
    }
}
