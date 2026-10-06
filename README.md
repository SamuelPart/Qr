# Ojo de Dios · app nativa de Android

Una consola de vigilancia sobre un solo mapa que reúne **las fuentes que sus
operadores publican de forma abierta en Internet**: cámaras de tráfico
oficiales, posiciones de aeronaves (ADS-B) y buques (AIS), satélites calculados
en el propio teléfono con SGP4, sismología del USGS, radar meteorológico e
imagen satelital de la NASA.

Escrita en **Kotlin + Jetpack Compose**. No es una WebView ni envuelve ninguna
página web: es una aplicación Android nativa.

**Este repositorio ya es el proyecto.** No hay que crear nada con el asistente
ni copiar archivos a ningún sitio: se abre con Android Studio y se ejecuta.

---

## Cómo abrirlo

1. Abre **Android Studio** (**Ladybug 2024.2.1** o superior).
2. **File → Open** y selecciona **esta carpeta** (la raíz del repositorio).
3. Si pregunta **"Trust project"**, acepta. Si avisa de componentes del SDK que
   faltan, acepta también: se descarga el SDK 35 solo.
4. La primera sincronización tarda unos minutos —descarga Gradle 8.11.1 y las
   dependencias de Compose—. Cuando abajo ponga *Gradle sync finished*:
   **Run ▶**.

Con el móvil conectado por USB y la **Depuración USB** activada (Ajustes →
Acerca del teléfono → 7 toques en "Número de compilación" → Opciones de
desarrollador), se instala y arranca. También vale un emulador: **Device
Manager → Create Device**; para probar el botón de ubicación, abre
**⋯ → Location** y fija una posición.

> **Aviso honesto.** El código está escrito y revisado a mano, pero **nunca se
> ha compilado**: el entorno donde se escribió no tiene JDK ni Android SDK.
> Cada llamada a osmdroid y a predict4java se verificó contra su código fuente
> real, y así se corrigieron errores concretos (por ejemplo, que `TilesOverlay`
> no tiene `setOpacity`, o un TLE con el dígito de control mal). Aun así, la
> primera compilación la vas a hacer tú.

### Requisitos

| Qué | Versión | ¿Lo pone el proyecto? |
|---|---|---|
| Android Studio | Ladybug (2024.2.1) o superior | — |
| JDK | 17 | Sí: el que trae Android Studio |
| Gradle | 8.11.1 | Sí: lo descarga el envoltorio |
| Plugin de Android (AGP) | 8.7.2 | Sí |
| Kotlin | 2.0.21 | Sí |
| compileSdk / targetSdk | 35 | Sí |
| minSdk | 24 (Android 7.0) | Sí |

No hace falta instalar Java aparte ni configurar Gradle a mano. Lo único que
Android Studio tendrá que descargar la primera vez es el SDK de Android 35.

---

---

## La clave de CARTO (mapas base)

Desde finales de 2026 el CDN de CARTO devuelve sus mosaicos ráster con la marca
de agua **«API KEY REQUIRED»** encima si la petición no lleva clave. La app
funciona igual —CARTO responde 200, no bloquea— pero el mapa se ve marcado.

**La clave ya viene puesta por defecto en `app/build.gradle.kts`**, así que al
compilar sale sin marca de agua. No hay que configurar nada.

Si quieres usar otra clave sin tocar el código, añade a `local.properties`:

```properties
carto.apiKey=cb1_tu_clave_aqui
```

Esa tiene prioridad. Y si la dejas **vacía** (`carto.apiKey=`), la app compila
con la marca de agua a propósito.

Al compilar, la ventana **Build** dice de dónde salió la clave:

```
CARTO: clave cargada desde la clave por defecto del proyecto — 35 caracteres (cb1_4bhs…d80e)
CARTO: clave cargada desde local.properties — 35 caracteres (cb1_4bhs…d80e)
CARTO: se compila SIN CLAVE (indicado en local.properties). Los fondos de mapa saldrán con la marca de agua…
```

### Aviso importante: la clave es pública

El repositorio es público, así que **cualquiera puede ver esta clave** en el
código o en el APK. Eso es inherente a una clave de mapas de navegador: viaja
en cada petición de mosaico, y el APK se puede descomprimir.

La protección real **no es el secreto, sino la restricción**. Entra en tu
[panel de CARTO](https://dashboard.basemaps.carto.com/) y ponle una restricción
por dominio, o revócala y pide otra si alguien abusa. Es gratuita hasta 5
millones de peticiones al mes para uso no comercial.

La atribución **«© OpenStreetMap contributors · © CARTO»** se muestra bajo la
cabecera del mapa. No es decoración: sus términos exigen que esté visible.

---

## Qué hace

Siete capas, todas de fuentes que su operador publica de forma abierta:

| Capa | Fuente | Cómo se obtiene |
|---|---|---|
| **Cámaras públicas** | TfL (Londres) · Fintraffic (Finlandia) · TD (Hong Kong) · LTA (Singapur) | Fotograma JPG, refresco cada 30 s |
| **Satélites** | CelesTrak | **Calculado en el teléfono con SGP4**, con traza orbital |
| **Sismos** | USGS | Últimas 24 h, con respaldo empaquetado |
| **Vuelos** | airplanes.live | ADS-B alrededor de lo que estás mirando |
| **Barcos** | Digitraffic | AIS del mar Báltico |
| **Radar de lluvia** | RainViewer | Mosaico del fotograma más reciente |
| **Imagen NASA** | GIBS / VIIRS | Color verdadero de hoy, **como planeta del globo** |

La vista es una sola: **el mundo en 3D**, con todas esas capas encima. No hay
que elegir entre globo y mapa; para bajar a la calle se abre la vista de calle
desde la ficha de cualquier objeto.

La capa de satélites es la más interesante: **no pide posiciones a nadie**. Lee
los elementos orbitales del paquete de la app y resuelve la órbita en el
dispositivo, así que funciona sin conexión y cualquiera puede reproducir el
cálculo. Y como el globo tiene altura de verdad, ahí se ve **a qué altura va
cada uno**.

### El mundo en 3D

La vista es **el planeta entero**, dibujado con **OpenGL ES 2.0 del propio
Android**: ni una dependencia más, ni una clave, ni una cuenta. La geografía es
la *Blue Marble* de la NASA (dominio público, servida por GIBS) y el terminador
de día y noche se calcula en el teléfono con la hora UTC.

Sobre el planeta va **todo**:

| Capa | Cómo se ve en el globo |
|---|---|
| Cámaras | Puntos cian donde hay una cámara oficial |
| Sismos | Ámbar; los de magnitud 5 o más, rojos y más grandes |
| Vuelos | Puntos verdes, alrededor de la zona que estás mirando |
| Barcos | Puntos violeta |
| Satélites | **A su altitud real**, con la traza de su órbita |

Que los satélites vayan a su altitud de verdad es lo que el 3D aporta y un mapa
plano no puede: se ve de un vistazo que la Estación Espacial va pegada al suelo
y que el anillo geoestacionario está 35 786 km más arriba. Las trazas orbitales
también van en 3D, así que las inclinaciones se leen sin esfuerzo.

La capa de la NASA cambia el planeta entero: en vez del relieve, pone la imagen
en **color verdadero de hoy**, la misma que sirve GIBS. El radar de lluvia, que
es un mosaico de mosaicos, se queda en la vista de calle.

Se maneja con el dedo:

- **Arrastrar** gira el globo. La sensibilidad se calcula con el tamaño de la
  pantalla —de un borde al otro es media vuelta—, así que no se descontrola en
  un móvil grande.
- **Pellizcar** acerca y aleja.
- **Doble toque** pone de frente el punto tocado y acerca un paso. Es la forma
  de llegar a un sitio concreto: acercarse sin más empujaba hacia el borde todo
  lo que no estuviera justo en el centro, que es el efecto de «quiero acercarme
  a algo y se me va a otro lado». El centrado se resuelve con un método de
  Newton sobre los dos ángulos del globo, y converge siempre.

### La vista de calle

El acercamiento tiene un tope, y no es una limitación que se pueda quitar
compilando otra cosa: la geografía del globo es el mapamundi de la NASA, unos
**10 km por píxel** y con un día de antigüedad. Acercarse más solo enseña una
mancha borrosa. Para que se note lo menos posible, la textura es de 4096×2048,
lleva *mipmaps* y el globo la encoge solo si el teléfono no admite ese tamaño.

Para ver calles está la **vista de calle**: el mapa de mosaicos de siempre
—con CARTO de fondo, radar de lluvia y nombres de calle— pero ya no como una
vista que compite, sino como algo que se abre desde la ficha de un objeto o
desde el aviso del tope, centrada en lo que estabas mirando, y se cierra con
«Volver al globo». Mientras está abierta, el globo deja de dibujar; mientras
está cerrada, el mapa no descarga ni un mosaico.

Si no hay red la primera vez, el globo se queda en océano azul con meridianos y
paralelos, y los puntos se ven igual: la textura es lo único que necesita
conexión, y a partir de la segunda apertura queda en la caché de la app.

El botón **Límites legales**, arriba a la derecha, abre la explicación de dónde
está la frontera entre consultar datos abiertos y cometer un delito.

---

## Estructura

```
.
├── app/
│   ├── build.gradle.kts             Configuración del módulo
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/data/             TLE y sismos empaquetados (funciona sin red)
│       ├── java/com/samuelpart/ojodedios/
│       └── res/                     Icono, cadenas y tema
├── herramientas/actualizar-tle.mjs  Actualiza los elementos orbitales
├── build.gradle.kts                 Plugins del proyecto
├── settings.gradle.kts              Módulos y repositorios
├── gradle.properties
├── gradlew / gradlew.bat            Envoltorio de Gradle
└── gradle/wrapper/                  Gradle 8.11.1
```

Los doce archivos Kotlin, y qué hace cada uno:

| Archivo | Responsabilidad |
|---|---|
| `Red.kt` | Cliente HTTP con `HttpURLConnection` y lectura de los assets |
| `Modelos.kt` | Tipos de datos y catálogo de las siete capas |
| `Fuentes.kt` | Fuentes de mosaicos y preparación de osmdroid |
| `Satelites.kt` | SGP4 sobre predict4java: posición y traza orbital |
| `Repositorio.kt` | Un método por feed, todos normalizados a `PuntoMapa` |
| `OjoViewModel.kt` | Estado, carga de capas y reloj de 1 Hz |
| `Mapa.kt` | `ControladorMapa` (osmdroid), que ahora solo sirve la vista de calle |
| `Globo.kt` | El mundo en 3D: esfera, texturas, rejilla, terminador, todas las capas y el tacto |
| `Interfaz.kt` | HUD, panel de capas, ficha de detalle y vista de calle |
| `Legal.kt` | El panel "Límites legales" |
| `Tema.kt` | Paleta y tipografía |
| `MainActivity.kt` | Ciclo de vida del mapa y permiso de ubicación |

**Dependencias: dos.** `org.osmdroid:osmdroid-android:6.1.20` para el mapa y
`com.github.davidmoten:predict4java:1.3.1` para la órbita. Ninguna pide clave de
API, cuenta de Google ni registro. Todo lo demás es AndroidX y Compose.

---

## Decisiones que conviene conocer

- **Sin OkHttp, sin kotlinx.serialization, sin Coil.** Android ya trae
  `HttpURLConnection`, `org.json` y `BitmapFactory`. Cuantas menos
  dependencias, menos versiones que puedan romper la compilación.
- **Los marcadores de satélite no se recrean**, solo cambian de posición. Si se
  reconstruyeran 25 marcadores cada segundo, el mapa parpadearía y la batería
  lo notaría.
- **Las trazas orbitales se recalculan cada minuto**, no cada segundo.
- **La transparencia de las capas de mosaicos** se hace con un
  `ColorMatrixColorFilter`, porque osmdroid no expone `setOpacity`.
- **El escucha del mapa se instala una sola vez**, no en cada recomposición.
- **El globo es propio, no de una librería de mapas.** MapLibre Native todavía
  no tiene globo (solo su versión web) y el de Google exige cuenta de
  facturación con tarjeta. Se dibuja con `GLSurfaceView` y GLES 2.0: una
  esfera, una textura y unas líneas. Cero dependencias nuevas.
- **El globo solo dibuja cuando hace falta** (`RENDERMODE_WHEN_DIRTY`) y, como
  es la única vista, el mapa de mosaicos permanece pausado salvo mientras la
  vista de calle está abierta: no se descarga ni un mosaico que nadie vaya a
  ver.
- **Todos los puntos del globo se agrupan por capa** y se dibujan como puntos
  de OpenGL, no como objetos individuales: tres mil cámaras, sismos, aviones y
  barcos son tres mil vértices, que para una tarjeta gráfica es nada.
- **Tocar un punto no inventa nada**: se proyecta cada objeto a la pantalla con
  la misma matriz del dibujado y gana el más cercano al dedo dentro de treinta
  píxeles. Es la misma cuenta que decide qué se ve, así que el punto que
  responde es siempre el que está debajo del dedo.
- **Las cámaras de Hong Kong se recortan a 240** de las ~800 que publica su
  Departamento de Transporte. Dibujar ochocientas más en un mapa que ya lleva
  mil cuatrocientas no aporta nada; la interfaz dice el total en la nota de la
  capa, para que el recorte no parezca un error.

---

## Generar un APK

**Build → Build Bundle(s) / APK(s) → Build APK(s)**. Queda en
`app/build/outputs/apk/debug/app-debug.apk`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para un APK de release firmado: **Build → Generate Signed Bundle / APK**.
Necesita un almacén de claves propio (**Create new…** la primera vez). El
proyecto no incluye ninguno a propósito: cada firma debe ser tuya.

---

## Mantener los datos al día

Los TLE envejecen: la precisión de SGP4 se degrada unos kilómetros por semana.
El actualizador los descarga de CelesTrak, comprueba el dígito de control de
cada línea y reescribe el asset de la app:

```bash
node herramientas/actualizar-tle.mjs
```

No necesita dependencias: usa `fetch`, que ya viene con Node 18 o superior.
Después, vuelve a compilar en Android Studio para que el APK lleve los datos
nuevos.

---

## Problemas frecuentes

**`Unresolved reference: amsacode`**
La dependencia está mal escrita. Tiene que ser
`com.github.davidmoten:predict4java:1.3.1`, y el paquete del código es
`com.github.amsacode.predict4java`. Que no coincidan es correcto: ese fork
publica bajo el grupo de su mantenedor.

**El mapa sale gris o en blanco**
osmdroid necesita configurarse **antes** de construir el MapView. Eso ocurre en
`Fuentes.preparar(this)`, primera línea de `MainActivity.onCreate`. Si mueves
código, no lo quites. Revisa también que la app tenga permiso de Internet.

**`SDK location not found`**
Android Studio lo resuelve solo al abrir el proyecto. Si insiste, crea un
archivo `local.properties` en la raíz con la ruta de tu SDK:
`sdk.dir=/home/tu-usuario/Android/Sdk` (en Windows, con las barras invertidas
duplicadas). Ese archivo no se versiona: es tuyo.

**`Missing permission` al pulsar "Mi ubicación"**
El permiso se pide en el momento. Si lo denegaste para siempre, ve a Ajustes →
Apps → Ojo de Dios → Permisos → Ubicación.

**No aparece ningún satélite**
Mira Logcat filtrando por `com.samuelpart.ojodedios`. Si un TLE tiene mal el
dígito de control, predict4java lanza excepción; el código la captura y descarta
ese satélite en vez de caerse. Los 25 que van dentro están verificados uno a uno.

**Sale la marca de agua «API KEY REQUIRED» en el mapa**
La clave va puesta por defecto, así que esto solo puede pasar por una de estas
tres razones:

1. **No has traído los últimos cambios.** `git pull` y vuelve a compilar.
2. **Hay mosaicos viejos en el teléfono.** Están guardados en caché con la
   marca de agua. Desinstala la app y vuelve a instalarla.
3. **La clave dejó de valer.** Compruébalo en el navegador:
   `https://basemaps.cartocdn.com/rastertiles/dark_all/7/63/42.png?key=TU_CLAVE`
   Si sale con marca de agua, pide otra en carto.com/basemaps/apikey.

**Las cámaras de Londres no cargan**
La API de TfL limita peticiones por IP. Espera un minuto y refresca.

**El tráfico se ve borroso al acercarse en el globo**
No es un fallo: es hasta donde llega el mapamundi. La textura es la Blue Marble
de la NASA, unos 10 km por píxel y con un día de antigüedad, así que a poca
distancia la costa se deshace. La app avisa cuando se llega al tope y ofrece la
vista de calle. Lo que **no** existe es una fuente abierta con más detalle: ni
la NASA, ni el USGS, ni ningún catálogo público ofrecen imagen de menos de
250 m por píxel, y todas llegan con horas o días de retraso. El globo sirve
para ver el planeta y las órbitas, no para mirar calles.

**No encuentro la forma de mirar una calle concreta**
Toca el punto que te interese —una cámara, por ejemplo— y en su ficha pulsa
«Ver la calle en el mapa». El botón abre el mapa de mosaicos centrado ahí
mismo. Para volver, «Volver al globo», arriba a la izquierda.

**El globo no gira hasta donde le pido**
Puede ser que las coordenadas que has pedido estén a menos de un grado del
polo: ahí la inclinación necesaria se sale del margen que permite el arrastre
(±89°) y el punto se queda a un grado del centro. En cualquier otro sitio del
planeta el punto acaba exactamente en el centro.

**En el globo, el fondo sale de un color plano**
Es lo esperado cuando no hay red: el globo dibuja su océano, la rejilla de
meridianos y paralelos, y todos los puntos. La textura se guarda en caché, así
que basta con haber abierto la app una vez con conexión.

**Pulso la capa de la NASA y no cambia nada**
Esa capa tarda unos segundos: descarga la imagen de hoy del planeta entero
(4096×2048, unos 2 MB) y la sube al globo. Si el servicio no la tiene
publicada, se queda el relieve y la nota de la capa lo dice.

---

## Nota final

Esta app solo lee feeds públicos abiertos y, si se lo pides, tu propia
ubicación. No pide acceso a la cámara, ni a los contactos, ni al
almacenamiento, ni a la lista de aplicaciones: únicamente Internet, estado de
la red y ubicación, y las tres se usan.

Vigilar infraestructura pública está bien. Vigilar personas, no. Ninguna de
estas fuentes permite identificar a una persona concreta, y la app no intenta
construir esa capacidad.

**Lo que no es:** no accede a cámaras privadas, no rompe credenciales y no
existe ningún "satélite espía en vivo" dentro. Todo lo que muestra lo publicó
alguien a propósito.
