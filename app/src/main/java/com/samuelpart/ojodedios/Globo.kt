package com.samuelpart.ojodedios

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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

/** Campo de visión de la cámara, en grados. Lo comparten dibujado y toques. */
private const val CAMPO_VISION = 45f

/**
 * Sensibilidad del arrastre: recorrer la pantalla de un borde al otro gira
 * media vuelta. Va en grados por píxel, calculados con el tamaño real de la
 * vista, así que no depende de la pantalla que tenga cada teléfono.
 */
private const val GRADOS_POR_PANTALLA = 180f

/** Tope de cada paso del centrado, en grados, para que no dé un salto loco. */
private const val TOPE_PASO_CENTRADO = 40f

/** Pasos del centrado. Con cuatro ya converge; ocho es margen de sobra. */
private const val PASOS_CENTRADO = 8

/** Cuánto se acerca el globo con un doble toque. */
private const val FACTOR_ACERCAMIENTO = 0.7f

/**
 * Altitud con la que se dibujan los objetos que están en el suelo: cámaras,
 * sismos, aviones y barcos. Sesenta kilómetros son un pelo sobre la superficie
 * (un 1 % del radio), lo justo para que el punto no compita con el planeta en
 * el búfer de profundidad y no parpadee.
 */
private const val ALTITUD_MARCADOR_KM = 60.0

/** Tamaño de cada punto en pantalla, en píxeles. */
private const val TAMANO_PUNTO = 7f
private const val TAMANO_PUNTO_DESTACADO = 13f

/** Radio de acierto al tocar: 30 píxeles alrededor del punto. */
private const val RADIO_TACTO_PX = 30f

/**
 * Holgura al decidir si un punto está tapado por el planeta. En el borde del
 * disco las dos formas de calcularlo difieren en menos de un píxel; con esta
 * holgura gana el lado que responde al dedo, que es lo que espera quien toca.
 */
private const val MARGEN_BORDE = 0.01f

/** Color de las trazas orbitales. */
private val COLOR_TRAZA = floatArrayOf(0.66f, 0.56f, 0.98f)

/**
 * Lo que hay detrás de un punto del globo, para cuando el dedo lo toca.
 * O es un objeto de una capa, o es un satélite: nunca las dos cosas.
 */
private class Objetivo(
    val punto: PuntoMapa? = null,
    val satelite: SateliteEnVuelo? = null,
)

/** Puntos del mismo color y tamaño, con sus objetivos en el mismo orden. */
private class GrupoPuntos(
    val color: FloatArray,
    val tamano: Float,
    val posiciones: FloatArray,
    val objetivos: List<Objetivo>,
)

/** La rejilla se dibuja un pelo por encima de la superficie para no competir
 *  con ella en el búfer de profundidad (lo que se ve como temblor de píxeles). */
private const val ALTITUD_REJILLA_KM = 18.0

/**
 * Distancias de cámara, en radios terrestres. Con 14 se ve el anillo
 * geoestacionario entero —los 35 786 km de altura— y el planeta entero de un
 * vistazo.
 *
 * El mínimo no es 1,2 porque la textura no lo aguantaría: a 1,7 radios la
 * cámara está a unos 4 500 km del suelo y el mapamundi de la NASA ya se está
 * estirando. Dejar acercarse más solo enseña una mancha borrosa, que es peor
 * que decir «hasta aquí llega».
 */
private const val DISTANCIA_MINIMA = 1.7f
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

    /** Cada animación lleva su número; un toque nuevo invalida la anterior. */
    private var generacion = 0

    /** Recorrido del dedo desde que tocó, para saber si está arrastrando. */
    private var recorrido = 0f
    private var arrastrando = false

    /** Para no repetir el aviso cada vez que se llega al tope de acercamiento. */
    private var limiteAvisado = false

    /**
     * La interfaz se suscribe a esto para explicar por qué el globo no se
     * acerca más. Sin el aviso, el tope parece un fallo de la app.
     */
    var alPedirMasDetalle: (() -> Unit)? = null

    private val detector = GestureDetector(
        contexto,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(evento: MotionEvent): Boolean {
                centrarYAcercar(evento.x, evento.y)
                return true
            }

            /**
             * Un toque suelto sobre un punto abre su ficha.
             *
             * Se usa `onSingleTapConfirmed` y no `onSingleTapUp` a propósito:
             * el segundo salta antes de saber si el usuario va a repetir el
             * toque, así que un doble toque abriría una ficha y además
             * centraría el globo.
             */
            override fun onSingleTapConfirmed(evento: MotionEvent): Boolean {
                val objetivo = objetivoEnPantalla(evento.x, evento.y) ?: return true
                objetivo.punto?.let { alTocarPunto?.invoke(it) }
                objetivo.satelite?.let { alTocarSatelite?.invoke(it) }
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderizador)
        // Solo se redibuja cuando hace falta. Un globo girando solo, a 60
        // fotogramas por segundo, se come la batería sin que nadie lo mire.
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Grupos de puntos dibujados ahora mismo. Se guardan para el toque. */
    private val gruposDibujados = mutableListOf<GrupoPuntos>()

    /** Se llama al tocar un objeto de cualquier capa. */
    var alTocarPunto: ((PuntoMapa) -> Unit)? = null

    /** Se llama al tocar un satélite, que lleva su propia ficha. */
    var alTocarSatelite: ((SateliteEnVuelo) -> Unit)? = null

    /**
     * Se llama cuando el globo deja de moverse, con las coordenadas que han
     * quedado de frente. Sirve para pedir los vuelos de lo que se está
     * mirando: sin esto se pedirían los del último sitio donde estuviera el
     * mapa de calle, que puede estar al otro lado del mundo.
     */
    var alCambiarCentro: ((Pair<Double, Double>) -> Unit)? = null

    /**
     * Además de los satélites, dibuja todas las capas activas: cámaras,
     * sismos, vuelos y barcos. Un grupo por capa para que cada una conserve
     * su color, y los marcados como destacados van en rojo y más grandes.
     */
    fun actualizar(puntos: List<PuntoMapa>, satelites: List<SateliteEnVuelo>) {
        val vector = FloatArray(3)
        val grupos = mutableListOf<GrupoPuntos>()

        for (capa in IdCapa.values()) {
            for (destacado in listOf(false, true)) {
                val seleccion = puntos.filter { it.capa == capa && it.destacado == destacado }
                if (seleccion.isEmpty()) continue

                val posiciones = FloatArray(seleccion.size * 3)
                val objetivos = ArrayList<Objetivo>(seleccion.size)
                seleccion.forEachIndexed { i, punto ->
                    aVector(punto.lat, punto.lon, ALTITUD_MARCADOR_KM, vector)
                    posiciones[i * 3] = vector[0]
                    posiciones[i * 3 + 1] = vector[1]
                    posiciones[i * 3 + 2] = vector[2]
                    objetivos.add(Objetivo(punto = punto))
                }

                grupos.add(
                    GrupoPuntos(
                        color = if (destacado) COLOR_DESTACADO else capa.colorEnGlobo,
                        tamano = if (destacado) TAMANO_PUNTO_DESTACADO else TAMANO_PUNTO,
                        posiciones = posiciones,
                        objetivos = objetivos,
                    )
                )
            }
        }

        if (satelites.isNotEmpty()) {
            val posiciones = FloatArray(satelites.size * 3)
            val objetivos = ArrayList<Objetivo>(satelites.size)
            satelites.forEachIndexed { i, enVuelo ->
                // Los satélites van a su altitud de verdad: es la razón de ser
                // del globo, porque en un mapa plano eso no se puede ver.
                aVector(enVuelo.lat, enVuelo.lon, enVuelo.altitudKm, vector)
                posiciones[i * 3] = vector[0]
                posiciones[i * 3 + 1] = vector[1]
                posiciones[i * 3 + 2] = vector[2]
                objetivos.add(Objetivo(satelite = enVuelo))
            }
            grupos.add(
                GrupoPuntos(
                    color = IdCapa.SATELITES.colorEnGlobo,
                    tamano = TAMANO_PUNTO_DESTACADO,
                    posiciones = posiciones,
                    objetivos = objetivos,
                )
            )
        }

        val trazas = mutableListOf<FloatArray>()
        for (enVuelo in satelites) {
            for (segmento in enVuelo.traza) {
                if (segmento.size < 2) continue
                val linea = FloatArray(segmento.size * 3)
                segmento.forEachIndexed { i, punto ->
                    // La traza viene en latitud y longitud, sin altitud. Se le
                    // pone la del satélite ahora mismo: en las órbitas bajas,
                    // que son las que llevan traza, la altura varía pocos
                    // kilómetros en 100 minutos y en el globo no se nota.
                    aVector(punto.first, punto.second, enVuelo.altitudKm, vector)
                    linea[i * 3] = vector[0]
                    linea[i * 3 + 1] = vector[1]
                    linea[i * 3 + 2] = vector[2]
                }
                trazas.add(linea)
            }
        }

        gruposDibujados.clear()
        gruposDibujados.addAll(grupos)

        queueEvent {
            renderizador.fijarPuntos(grupos)
            renderizador.fijarTrazas(trazas)
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
     * Girar, acercar y centrar.
     *
     * Tres decisiones que se notan al usarlo:
     *
     * - **La sensibilidad sale del tamaño de la vista**: arrastrar de un borde
     *   al otro gira media vuelta. Antes era un valor fijo por píxel, y en una
     *   pantalla grande eso hacía que el globo se fuera de las manos.
     * - **Al levantar un dedo no hay salto**: el arrastre continúa desde donde
     *   está el dedo que queda, no desde la referencia vieja.
     * - **Doble toque**: pone de frente lo que se ha tocado y acerca. Es la
     *   forma de llegar a un sitio concreto sin pelearse con el pellizco.
     *
     * El signo de cada eje sigue a la superficie, como si se agarrara el globo
     * con la mano.
     */
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        // El detector solo mira si hubo doble toque; no consume el evento.
        detector.onTouchEvent(evento)

        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelarAnimacion()
                xPrevio = evento.x
                yPrevio = evento.y
                dosDedos = false
                recorrido = 0f
                arrastrando = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (evento.pointerCount >= 2) {
                    dosDedos = true
                    distanciaPrevia = separacion(evento)
                    cancelarAnimacion()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Queda un dedo: el arrastre sigue desde donde está ese dedo.
                // Sin esto el globo pegaba un salto, porque la referencia
                // seguía siendo el dedo que se acababa de levantar.
                if (evento.pointerCount == 2) {
                    val indice = if (evento.actionIndex == 0) 1 else 0
                    xPrevio = evento.getX(indice)
                    yPrevio = evento.getY(indice)
                    dosDedos = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dosDedos && evento.pointerCount >= 2) {
                    val actual = separacion(evento)
                    if (distanciaPrevia > 0f && actual > 0f) {
                        distancia *= distanciaPrevia / actual
                        distancia = distancia.coerceIn(DISTANCIA_MINIMA, DISTANCIA_MAXIMA)
                        if (distancia <= DISTANCIA_MINIMA) avisarDelLimite()
                        if (distancia > DISTANCIA_MINIMA * 1.02f) limiteAvisado = false
                    }
                    distanciaPrevia = actual
                } else if (evento.pointerCount == 1) {
                    // Un temblor de dos píxeles no es un arrastre: si se tomara
                    // por tal, cualquier doble toque se cancelaría a sí mismo
                    // antes de empezar a animar.
                    recorrido += kotlin.math.abs(evento.x - xPrevio) +
                        kotlin.math.abs(evento.y - yPrevio)
                    if (!arrastrando && recorrido > 12f) {
                        arrastrando = true
                        cancelarAnimacion()
                    }

                    giro += (evento.x - xPrevio) *
                        (GRADOS_POR_PANTALLA / width.coerceAtLeast(1))
                    inclinacion = (inclinacion + (evento.y - yPrevio) *
                        (GRADOS_POR_PANTALLA / height.coerceAtLeast(1)))
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
                if (arrastrando) alCambiarCentro?.invoke(centroVisible())
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

    private fun avisarDelLimite() {
        if (limiteAvisado) return
        limiteAvisado = true
        alPedirMasDetalle?.invoke()
    }

    // ─────────────────────── Ir a un sitio ───────────────────────

    /**
     * Gira el globo hasta poner de frente unas coordenadas y, si se pide,
     * acerca. Se usa para «mi ubicación».
     *
     * Los dos ángulos no son la latitud y la longitud, aunque lo parezca: como
     * las rotaciones van encadenadas —primero sobre el eje horizontal de la
     * pantalla y luego sobre el vertical—, la inclinación que hace falta
     * depende también de la longitud. Esta es la solución exacta, y la que se
     * comprobó contra una réplica de las matrices de OpenGL.
     *
     * Hay dos orientaciones que dejan el mismo punto de frente, separadas 180°
     * en la inclinación. Se elige la que no pasa de 90°, porque el arrastre
     * solo llega hasta ±89°: si no, el globo aparecería boca abajo y el primer
     * arrastre lo devolvería de un salto.
     */
    fun irA(lat: Double, lon: Double, distanciaNueva: Float? = null) {
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon)
        val senoLat = kotlin.math.sin(phi)
        val cosLat = kotlin.math.cos(phi)
        val cosLon = kotlin.math.cos(lambda)
        val senoLon = kotlin.math.sin(lambda)

        var inclinacionObjetivo = Math.toDegrees(
            kotlin.math.atan2(senoLat, cosLat * cosLon)
        ).toFloat()
        if (inclinacionObjetivo > 90f) {
            inclinacionObjetivo -= 180f
        } else if (inclinacionObjetivo < -90f) {
            inclinacionObjetivo += 180f
        }

        // Con la inclinación ya decidida, el giro queda determinado: es el que
        // deja el punto en el plano vertical que pasa por la cámara.
        val radianes = Math.toRadians(inclinacionObjetivo.toDouble())
        val componente = kotlin.math.sin(radianes) * senoLat +
            kotlin.math.cos(radianes) * cosLat * cosLon
        val giroObjetivo = Math.toDegrees(
            kotlin.math.atan2(-cosLat * senoLon, componente)
        ).toFloat()

        animar(
            giro + caminoCorto(giro, giroObjetivo),
            inclinacionObjetivo.coerceIn(-89f, 89f),
            distanciaNueva ?: minOf(distancia, 2.4f),
        )
    }

    /** Vuelve a la vista de partida: el planeta entero, sin girar de más. */
    fun reiniciar() {
        animar(20f, 18f, DISTANCIA_INICIAL)
    }

    /**
     * Qué punto del planeta queda de frente ahora mismo.
     *
     * La cámara mira al centro de la esfera desde (0, 0, distancia), así que lo
     * que se ve en el centro de la pantalla es el (0, 0, 1) del mundo pasado al
     * espacio del globo: la rotación inversa de la cámara.
     */
    fun centroVisible(): Pair<Double, Double> {
        val inversa = FloatArray(16)
        Matrix.setIdentityM(inversa, 0)
        Matrix.rotateM(inversa, 0, -inclinacion, 1f, 0f, 0f)
        Matrix.rotateM(inversa, 0, -giro, 0f, 1f, 0f)

        // El (0, 0, 1) del mundo por la traspuesta son estos tres elementos.
        // Las matrices van por columnas: la columna 2 de la traspuesta es
        // (inversa[8], inversa[9], inversa[10]).
        val x = inversa[8]
        val y = inversa[9]
        val z = inversa[10]

        val latitud = Math.toDegrees(kotlin.math.asin(y.coerceIn(-1f, 1f).toDouble()))
        val longitud = Math.toDegrees(kotlin.math.atan2(x.toDouble(), z.toDouble()))
        return latitud to longitud
    }

    // ─────────────────────── Qué ha tocado el dedo ───────────────────────

    /**
     * El punto más cercano al dedo, si hay alguno a menos de treinta píxeles.
     *
     * Se proyecta cada objeto a la pantalla con la misma matriz que usa el
     * dibujado y se compara la distancia. Si hay varios candidatos, gana el más
     * cercano: al tocar una zona con muchos aviones, conviene que responda el
     * que está justo debajo del dedo y no el primero de la lista.
     */
    private fun objetivoEnPantalla(x: Float, y: Float): Objetivo? {
        if (gruposDibujados.isEmpty()) return null

        val ancho = width.coerceAtLeast(1)
        val alto = height.coerceAtLeast(1)
        val matriz = FloatArray(16)
        renderizador.copiarMatrizProyeccion(matriz)

        // La rotación se construye una vez, no una por objeto: con tres mil
        // puntos en pantalla, montar la matriz en cada vuelta se nota.
        val rotacion = FloatArray(16)
        construirRotacion(giro, inclinacion, rotacion)

        var mejor: Objetivo? = null
        var mejorDistancia = RADIO_TACTO_PX

        for (grupo in gruposDibujados) {
            val posiciones = grupo.posiciones
            for (i in grupo.objetivos.indices) {
                val px = posiciones[i * 3]
                val py = posiciones[i * 3 + 1]
                val pz = posiciones[i * 3 + 2]

                // Un punto de la cara oculta también cae dentro del círculo del
                // planeta al proyectarlo, así que sin esta comprobación se
                // podría seleccionar una cámara que está al otro lado del
                // mundo. Se lanza el rayo que va de la cámara al punto y se
                // mira si ha atravesado la esfera antes de llegar: si lo ha
                // hecho, el planeta lo tapa.
                val mx = rotacion[0] * px + rotacion[4] * py + rotacion[8] * pz
                val my = rotacion[1] * px + rotacion[5] * py + rotacion[9] * pz
                val mz = rotacion[2] * px + rotacion[6] * py + rotacion[10] * pz
                val rayoX = mx
                val rayoY = my
                val rayoZ = mz - distancia
                val largo = kotlin.math.sqrt(rayoX * rayoX + rayoY * rayoY + rayoZ * rayoZ)
                if (largo > 0.0001f) {
                    // (cámara · dirección) del rayo, con la cámara en (0,0,distancia)
                    val producto = distancia * rayoZ / largo
                    val discriminante = producto * producto - (distancia * distancia - 1f)
                    if (discriminante > 0f) {
                        val primerCorte = -producto - kotlin.math.sqrt(discriminante)
                        if (primerCorte < largo - MARGEN_BORDE) continue
                    }
                }

                val clipX = matriz[0] * px + matriz[4] * py + matriz[8] * pz + matriz[12]
                val clipY = matriz[1] * px + matriz[5] * py + matriz[9] * pz + matriz[13]
                val clipW = matriz[3] * px + matriz[7] * py + matriz[11] * pz + matriz[15]
                if (clipW <= 0f) continue

                val pantallaX = (clipX / clipW + 1f) * ancho / 2f
                val pantallaY = (1f - clipY / clipW) * alto / 2f

                val separacion = kotlin.math.hypot(pantallaX - x, pantallaY - y)
                if (separacion < mejorDistancia) {
                    mejorDistancia = separacion
                    mejor = grupo.objetivos[i]
                }
            }
        }
        return mejor
    }

    // ─────────────────────── Centrado por doble toque ───────────────────────

    /**
     * Doble toque: gira el globo hasta poner de frente el punto tocado y se
     * acerca un paso.
     *
     * Aquí está el arreglo del problema de «quiero acercarme a algo y se me
     * va a otro lado». La cámara mira siempre al centro del planeta, así que
     * acercarse sin más empuja hacia el borde todo lo que no esté justo en el
     * centro. Lo que hay que hacer es girar primero y acercarse después.
     *
     * El giro se resuelve con un método de Newton sobre los dos ángulos del
     * globo: se busca la orientación que deja ese punto de frente y cada paso
     * mira dónde ha quedado para corregir. Con cuatro pasos converge; se dan
     * ocho porque no cuestan nada.
     */
    private fun centrarYAcercar(x: Float, y: Float) {
        val distanciaNueva = (distancia * FACTOR_ACERCAMIENTO).coerceAtLeast(DISTANCIA_MINIMA)

        val objetivo = puntoEnLaEsfera(x, y)
        if (objetivo == null) {
            // Toque al vacío: solo se acerca.
            animar(giro, inclinacion, distanciaNueva)
            return
        }

        var giroNuevo = giro
        var inclinacionNueva = inclinacion
        val vector = FloatArray(3)

        repeat(PASOS_CENTRADO) {
            // Dónde ha quedado ese punto del planeta, en coordenadas del mundo.
            aplicarRotacion(giroNuevo, inclinacionNueva, objetivo, vector)
            // Si el punto está pegado al borde, la corrección no es estable: su
            // proyección se dispara. Se deja donde está, que ya se ve.
            if (vector[2] < 0.1f) return@repeat

            val seno = kotlin.math.sin(Math.toRadians(giroNuevo.toDouble())).toFloat()
            val coseno = kotlin.math.cos(Math.toRadians(giroNuevo.toDouble())).toFloat()
            val denominador = seno * vector[0] + coseno * vector[2]
            if (kotlin.math.abs(denominador) < 1e-6f) return@repeat

            val pasoInclinacion = Math.toDegrees(
                (vector[1] / denominador).toDouble()
            ).toFloat().coerceIn(-TOPE_PASO_CENTRADO, TOPE_PASO_CENTRADO)

            val numerador = -vector[0] - seno * vector[1] *
                Math.toRadians(pasoInclinacion.toDouble()).toFloat()
            val pasoGiro = Math.toDegrees(
                (numerador / vector[2]).toDouble()
            ).toFloat().coerceIn(-TOPE_PASO_CENTRADO, TOPE_PASO_CENTRADO)

            giroNuevo += pasoGiro
            inclinacionNueva += pasoInclinacion
        }

        animar(
            giro + caminoCorto(giro, giroNuevo),
            // El arrastre admite este margen y nada más. Si la solución se sale
            // de ahí se recorta: pasa en uno de cada cincuenta toques, y aun
            // así el punto queda dentro de la pantalla.
            inclinacionNueva.coerceIn(-89f, 89f),
            distanciaNueva,
        )
    }

    /**
     * Dónde ha caído el dedo sobre el planeta, en coordenadas del globo.
     *
     * Devuelve null si el toque cayó al vacío, es decir, fuera del planeta.
     */
    private fun puntoEnLaEsfera(x: Float, y: Float): FloatArray? {
        val ancho = width.coerceAtLeast(1)
        val alto = height.coerceAtLeast(1)

        // De píxeles a coordenadas normalizadas, y de ahí a un rayo.
        val nx = 2f * x / ancho - 1f
        val ny = 1f - 2f * y / alto
        val medio = kotlin.math.tan(Math.toRadians(CAMPO_VISION / 2.0)).toFloat()
        val aspecto = ancho.toFloat() / alto

        val rotacion = FloatArray(16)
        construirRotacion(giro, inclinacion, rotacion)

        // La cámara está en (0, 0, distancia) mirando al origen: en su propio
        // sistema está en el origen y mira hacia -Z. El rayo se pasa al espacio
        // del globo con la traspuesta, que en una rotación es la inversa. La
        // vista solo traslada, así que las direcciones no cambian al pasar.
        val direccion = floatArrayOf(nx * medio * aspecto, ny * medio, -1f)
        normalizar(direccion)
        val direccionModelo = FloatArray(3)
        aplicarTraspuesta(rotacion, direccion, direccionModelo)

        val origen = floatArrayOf(0f, 0f, distancia)
        val origenModelo = FloatArray(3)
        aplicarTraspuesta(rotacion, origen, origenModelo)

        // Corte del rayo con la esfera de radio 1: |origen + t·dirección| = 1.
        val b = 2f * (origenModelo[0] * direccionModelo[0] +
            origenModelo[1] * direccionModelo[1] +
            origenModelo[2] * direccionModelo[2])
        val c = origenModelo[0] * origenModelo[0] +
            origenModelo[1] * origenModelo[1] +
            origenModelo[2] * origenModelo[2] - 1f
        val discriminante = b * b - 4f * c
        if (discriminante < 0f) return null

        val raiz = kotlin.math.sqrt(discriminante)
        // El primer corte es la cara que se ve; si queda detrás, el otro.
        var t = (-b - raiz) / 2f
        if (t < 0f) t = (-b + raiz) / 2f
        if (t < 0f) return null

        return floatArrayOf(
            origenModelo[0] + t * direccionModelo[0],
            origenModelo[1] + t * direccionModelo[1],
            origenModelo[2] + t * direccionModelo[2],
        )
    }

    // ─────────────────────── Animación ───────────────────────

    /**
     * Lleva la cámara al destino en una fracción de segundo.
     *
     * Sin esto, el doble toque daría un salto seco y no se entendería qué ha
     * pasado. El suavizado hace que arranque y frene.
     */
    private fun animar(giroNuevo: Float, inclinacionNueva: Float, distanciaNueva: Float) {
        val giroInicial = giro
        val inclinacionInicial = inclinacion
        val distanciaInicial = distancia
        val pasos = 18
        val miGeneracion = ++generacion
        var paso = 0

        fun siguiente() {
            if (miGeneracion != generacion) return
            paso++
            val avance = paso.toFloat() / pasos
            val suave = avance * avance * (3f - 2f * avance)
            giro = giroInicial + (giroNuevo - giroInicial) * suave
            inclinacion = inclinacionInicial + (inclinacionNueva - inclinacionInicial) * suave
            distancia = distanciaInicial + (distanciaNueva - distanciaInicial) * suave
            actualizarCamara()
            requestRender()
            if (paso < pasos) {
                postOnAnimation { siguiente() }
            } else {
                alCambiarCentro?.invoke(centroVisible())
            }
        }
        siguiente()
    }

    private fun cancelarAnimacion() {
        generacion++
    }

    // ─────────────────────── Cuentas de rotación ───────────────────────

    /**
     * La rotación del globo: primero la inclinación sobre X, después el giro
     * sobre Y. Tiene que ser exactamente la misma composición que la del
     * renderizador, o los toques dejarían de coincidir con lo que se ve.
     */
    private fun construirRotacion(giro: Float, inclinacion: Float, salida: FloatArray) {
        Matrix.setIdentityM(salida, 0)
        Matrix.rotateM(salida, 0, giro, 0f, 1f, 0f)
        Matrix.rotateM(salida, 0, inclinacion, 1f, 0f, 0f)
    }

    /** Multiplica un punto por la matriz de rotación. */
    private fun aplicarRotacion(
        giro: Float,
        inclinacion: Float,
        punto: FloatArray,
        salida: FloatArray,
    ) {
        val matriz = FloatArray(16)
        construirRotacion(giro, inclinacion, matriz)
        salida[0] = matriz[0] * punto[0] + matriz[4] * punto[1] + matriz[8] * punto[2]
        salida[1] = matriz[1] * punto[0] + matriz[5] * punto[1] + matriz[9] * punto[2]
        salida[2] = matriz[2] * punto[0] + matriz[6] * punto[1] + matriz[10] * punto[2]
    }

    /**
     * Multiplica un vector por la traspuesta de la matriz. Las matrices de
     * OpenGL van por columnas, de ahí que los índices no sigan el orden de las
     * filas: el elemento (fila, columna) está en `columna * 4 + fila`.
     */
    private fun aplicarTraspuesta(matriz: FloatArray, vector: FloatArray, salida: FloatArray) {
        salida[0] = matriz[0] * vector[0] + matriz[1] * vector[1] + matriz[2] * vector[2]
        salida[1] = matriz[4] * vector[0] + matriz[5] * vector[1] + matriz[6] * vector[2]
        salida[2] = matriz[8] * vector[0] + matriz[9] * vector[1] + matriz[10] * vector[2]
    }

    private fun normalizar(vector: FloatArray) {
        val longitud = kotlin.math.sqrt(
            vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]
        )
        if (longitud <= 0f) return
        vector[0] /= longitud
        vector[1] /= longitud
        vector[2] /= longitud
    }

    /** Diferencia entre dos ángulos por el camino más corto, en grados. */
    private fun caminoCorto(desde: Float, hasta: Float): Float {
        var diferencia = (hasta - desde) % 360f
        if (diferencia > 180f) diferencia -= 360f
        if (diferencia < -180f) diferencia += 360f
        return diferencia
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

        private var gruposPuntos: List<GrupoRender> = emptyList()
        private var trazas: List<FloatBuffer> = emptyList()
        private var tamanosTrazas = IntArray(0)

        /** Copia de la última matriz de proyección, para acertar al tocar. */
        @Volatile private var instantanea = FloatArray(16)

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

        /** Grupo de puntos listo para dibujar. */
        private class GrupoRender(
            val posiciones: FloatBuffer,
            val cantidad: Int,
            val color: FloatArray,
            val tamano: Float,
        )

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

        fun fijarPuntos(grupos: List<GrupoPuntos>) {
            gruposPuntos = grupos.map {
                GrupoRender(
                    posiciones = aBuffer(it.posiciones),
                    cantidad = it.posiciones.size / 3,
                    color = it.color,
                    tamano = it.tamano,
                )
            }
        }

        fun fijarTrazas(lineas: List<FloatArray>) {
            trazas = lineas.map { aBuffer(it) }
            tamanosTrazas = IntArray(lineas.size) { lineas[it].size / 3 }
        }

        /**
         * Copia la matriz de proyección para que la vista pueda saber qué ha
         * tocado el dedo.
         *
         * El arreglo lo escribe solo el hilo de dibujado y quien lo lee se
         * lleva su propia copia. En el peor caso, un toque acierta con la
         * posición de hace un fotograma: a 60 por segundo, un píxel.
         */
        fun copiarMatrizProyeccion(destino: FloatArray) {
            System.arraycopy(instantanea, 0, destino, 0, 16)
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
            // El campo de visión es una constante compartida: los toques
            // necesitan el mismo valor para desproyectar bien el rayo.
            Matrix.perspectiveM(proyeccion, 0, CAMPO_VISION, aspecto, 0.1f, 100f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            texturaPendiente?.let { mapaBits ->
                val ajustada = ajustarAlLimite(mapaBits)
                mapaTierra?.recycle()
                mapaTierra = ajustada
                subirTextura(ajustada)
                texturaPendiente = null
                texturaLista = true
            }

            calcularMatrices()
            dibujarEsfera()
            dibujarRejilla()
            dibujarPuntos()
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

            // Queda publicada para que la vista pueda saber qué ha tocado el
            // dedo sin repetir estas cuentas.
            System.arraycopy(mvp, 0, instantanea, 0, 16)
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

        /**
         * Los puntos de todas las capas: qué hay dónde, sobre el planeta.
         *
         * Primero las trazas orbitales, translúcidas para que se lean las
         * inclinaciones sin tapar el mundo, y después los objetos, cada capa
         * con su color.
         */
        private fun dibujarPuntos() {
            if (gruposPuntos.isEmpty() && trazas.isEmpty()) return

            GLES20.glUseProgram(programaPuntos)
            GLES20.glUniformMatrix4fv(uMvpPuntos, 1, false, mvp, 0)
            GLES20.glEnableVertexAttribArray(aPosPuntos)

            dibujarSegmentos(
                trazas, tamanosTrazas,
                COLOR_TRAZA[0], COLOR_TRAZA[1], COLOR_TRAZA[2], 0.5f, 1f,
            )

            for (grupo in gruposPuntos) {
                grupo.posiciones.position(0)
                GLES20.glVertexAttribPointer(
                    aPosPuntos, 3, GLES20.GL_FLOAT, false, 12, grupo.posiciones
                )
                GLES20.glUniform4f(
                    uColorPuntos, grupo.color[0], grupo.color[1], grupo.color[2], 1f
                )
                GLES20.glUniform1f(uTamanoPuntos, grupo.tamano)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, grupo.cantidad)
            }

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
            // Con mipmaps: al alejarse, el planeta entero cabe en pocos píxeles
            // y sin ellos el texturizado hierve. El nivel pequeño también quita
            // trabajo a la tarjeta gráfica.
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR,
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

        /**
         * El lado mayor de textura que admite este teléfono.
         *
         * El mapamundi se pide a 4096×2048, que es lo que aguanta cualquier
         * móvil de los últimos años, pero OpenGL solo garantiza 2048: si el
         * aparato dice menos, se encoge la imagen antes de subirla en vez de
         * dejar una textura en blanco.
         */
        private fun maximoLadoTextura(): Int {
            val valores = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, valores, 0)
            return if (valores[0] > 0) valores[0] else 2048
        }

        private fun ajustarAlLimite(mapaBits: Bitmap): Bitmap {
            val maximo = maximoLadoTextura()
            if (mapaBits.width <= maximo && mapaBits.height <= maximo) return mapaBits

            val factor = maximo.toFloat() / mapaBits.width
            val ajustada = Bitmap.createScaledBitmap(
                mapaBits,
                maximo,
                (mapaBits.height * factor).toInt().coerceAtLeast(1),
                true,
            )
            if (ajustada !== mapaBits) mapaBits.recycle()
            return ajustada
        }

        private fun subirTextura(mapaBits: Bitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mapaBits, 0)
            // Los mipmaps se generan aquí, una sola vez por textura.
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
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
 *
 * [alPedirMasDetalle] se avisa cuando alguien intenta acercarse más allá del
 * tope: sin esa explicación, el tope parece que la app se ha quedado colgada.
 */
@Composable
fun GloboOjoDeDios(
    puntos: List<PuntoMapa>,
    satelites: List<SateliteEnVuelo>,
    colorVerdadero: Boolean,
    centrarEn: Pair<Double, Double>?,
    pausado: Boolean,
    modifier: Modifier = Modifier,
    ordenReinicio: Int = 0,
    ordenUbicacion: Int = 0,
    alPedirMasDetalle: () -> Unit = {},
    alTocarPunto: (PuntoMapa) -> Unit = {},
    alTocarSatelite: (SateliteEnVuelo) -> Unit = {},
    alCambiarCentro: (Pair<Double, Double>) -> Unit = {},
) {
    val contexto = LocalContext.current
    val repositorio = remember(contexto) { Repositorio(contexto) }
    val globo = remember { GloboView(contexto) }
    val avisoActual = rememberUpdatedState(alPedirMasDetalle)
    val toquePunto = rememberUpdatedState(alTocarPunto)
    val toqueSatelite = rememberUpdatedState(alTocarSatelite)
    val centroActual = rememberUpdatedState(alCambiarCentro)

    var enPrimerPlano by remember { mutableStateOf(true) }

    AndroidView(modifier = modifier, factory = { globo })

    // GLSurfaceView exige que el ciclo de vida de la pantalla llegue a su hilo
    // de dibujado: si no, el sistema puede llevarse la superficie de OpenGL
    // mientras la app está en segundo plano.
    val propietario = LocalLifecycleOwner.current
    DisposableEffect(propietario, globo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> enPrimerPlano = true
                Lifecycle.Event.ON_PAUSE -> enPrimerPlano = false
                else -> Unit
            }
        }
        propietario.lifecycle.addObserver(observador)
        globo.alPedirMasDetalle = { avisoActual.value() }
        globo.alTocarPunto = { toquePunto.value(it) }
        globo.alTocarSatelite = { toqueSatelite.value(it) }
        globo.alCambiarCentro = { centroActual.value(it) }
        globo.prepararEscena()

        onDispose {
            propietario.lifecycle.removeObserver(observador)
            globo.alPedirMasDetalle = null
            globo.alTocarPunto = null
            globo.alTocarSatelite = null
            globo.alCambiarCentro = null
            globo.onPause()
        }
    }

    // Se dibuja solo si la pantalla está delante y nada lo tapa. Mientras la
    // vista de calle está abierta encima, el globo se para: no hay razón para
    // gastar batería pintando lo que nadie ve.
    LaunchedEffect(enPrimerPlano, pausado, globo) {
        if (enPrimerPlano && !pausado) globo.onResume() else globo.onPause()
    }

    // La geografía del planeta. Cambia entre el relieve y el color verdadero
    // del día según la capa de la NASA, y se cachea, así que solo se descarga
    // una vez por variante.
    LaunchedEffect(globo, colorVerdadero) {
        val mapaBits = withContext(Dispatchers.IO) {
            repositorio.texturaTierra(colorVerdadero)
        }
        if (mapaBits != null) globo.fijarTextura(mapaBits)
    }

    LaunchedEffect(globo, puntos, satelites) { globo.actualizar(puntos, satelites) }

    // Al llegar una posición nueva (el botón «Mi ubicación»), el globo gira
    // hasta ponerla de frente. El contador está ahí para que pulsar el botón
    // vuelva a girar aunque las coordenadas sean las mismas: sin él, el efecto
    // no se reiniciaría y el botón parecería muerto tras mover el globo a mano.
    LaunchedEffect(globo, centrarEn, ordenUbicacion) {
        val destino = centrarEn ?: return@LaunchedEffect
        globo.irA(destino.first, destino.second)
    }

    // El botón «Reiniciar» no manda una posición sino una señal: este número
    // cambia de valor y el globo vuelve a su vista de partida.
    LaunchedEffect(globo, ordenReinicio) {
        if (ordenReinicio > 0) globo.reiniciar()
    }

    // El terminador se mueve unos 15° por hora: refrescarlo cada minuto sobra.
    LaunchedEffect(globo) {
        while (true) {
            globo.actualizarSoles()
            delay(60_000)
        }
    }
}
