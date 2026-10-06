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
de agua **«API KEY REQUIRED»** si la petición no lleva clave. La app funciona
igual —CARTO no bloquea la petición— pero se ve fea. Para quitarla hace falta
una clave gratuita, que se pide en <https://carto.com/basemaps/apikey>.

**La clave no está escrita en este repositorio** (que es público) ni en el
código: se lee al compilar desde `local.properties`, un archivo que Android
Studio ya crea, que cada uno tiene solo en su máquina y que está ignorado por
git. Añade esta línea al final:

```properties
carto.apiKey=cb1_tu_clave_aqui
```

Después, **File → Sync Project with Gradle Files** y vuelve a compilar. Si el
mapa sigue con la marca de agua, borra los datos de la app o desinstálala: los
mosaicos ya descargados están en su caché.

Sin esa línea la app compila y funciona, solo que con la marca de agua.

**Sobre la clave:** es gratuita hasta 5 millones de peticiones al mes para uso
no comercial. En el [panel de
CARTO](https://dashboard.basemaps.carto.com/) puedes ver el consumo, revocarla o
ponerle restricciones. Ten en cuenta que la clave viaja dentro del APK y en cada
petición de mosaico: cualquiera que use la app puede verla. Por eso conviene
ponerle una restricción de dominio, y por eso no está en el repositorio.

La atribución **«© OpenStreetMap contributors · © CARTO»** se muestra bajo la
cabecera del mapa. No es decoración: sus términos exigen que esté visible.

## Qué hace

Siete capas, todas de fuentes que su operador publica de forma abierta:

| Capa | Fuente | Cómo se obtiene |
|---|---|---|
| **Cámaras públicas** | TfL (Londres) · Fintraffic (Finlandia) | Fotograma JPG, refresco cada 30 s |
| **Satélites** | CelesTrak | **Calculado en el teléfono con SGP4**, con traza orbital |
| **Sismos** | USGS | Últimas 24 h, con respaldo empaquetado |
| **Vuelos** | airplanes.live | ADS-B alrededor del centro del mapa |
| **Barcos** | Digitraffic | AIS del mar Báltico |
| **Radar de lluvia** | RainViewer | Mosaico del fotograma más reciente |
| **Imagen NASA** | GIBS / VIIRS | Color verdadero, ~1 día de antigüedad |

La capa de satélites es la más interesante: **no pide posiciones a nadie**. Lee
los elementos orbitales del paquete de la app y resuelve la órbita en el
dispositivo, así que funciona sin conexión y cualquiera puede reproducir el
cálculo.

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

Los once archivos Kotlin, y qué hace cada uno:

| Archivo | Responsabilidad |
|---|---|
| `Red.kt` | Cliente HTTP con `HttpURLConnection` y lectura de los assets |
| `Modelos.kt` | Tipos de datos y catálogo de las siete capas |
| `Fuentes.kt` | Fuentes de mosaicos y preparación de osmdroid |
| `Satelites.kt` | SGP4 sobre predict4java: posición y traza orbital |
| `Repositorio.kt` | Un método por feed, todos normalizados a `PuntoMapa` |
| `OjoViewModel.kt` | Estado, carga de capas y reloj de 1 Hz |
| `Mapa.kt` | `ControladorMapa` (osmdroid) y su composable |
| `Interfaz.kt` | HUD, panel de capas y ficha de detalle |
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
Hay dos causas y se distinguen en un minuto:

1. **Comprueba la clave en el navegador.** Pega esto cambiando `TU_CLAVE`:
   `https://basemaps.cartocdn.com/rastertiles/dark_all/7/63/42.png?key=TU_CLAVE`
   Si sale con marca de agua, la clave no vale (revisa el correo: puede venir
   cortada en dos líneas) y hay que pedir otra en carto.com/basemaps/apikey.
2. **Comprueba que llegó al proyecto.** Al compilar, la ventana **Build** dice:
   `CARTO: clave cargada — 35 caracteres (cb1_4bhs…d80e)` o
   `CARTO: SIN CLAVE`. Si dice SIN CLAVE, revisa que la línea esté en el
   `local.properties` de la **raíz** del proyecto.
   Dentro de la app, el panel **Capas** también avisa cuando falta.

Con la clave en su sitio, si aún se ven mosaicos marcados son los que el
teléfono guardó en caché: desinstala la app y vuelve a instalarla.

**Las cámaras de Londres no cargan**
La API de TfL limita peticiones por IP. Espera un minuto y refresca.

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
