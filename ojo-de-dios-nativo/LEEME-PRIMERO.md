# Ojo de Dios · app nativa de Android

Kotlin + Jetpack Compose. **No es una WebView**: es una app Android de verdad,
con mapa osmdroid y cálculo orbital SGP4 dentro del propio teléfono.

**Esta carpeta ya es el proyecto completo.** Tiene todo lo que pide Android
Studio para compilar: el envoltorio de Gradle, la configuración de los módulos,
los iconos del lanzador y el código. No hay que crear ningún proyecto con
asistente ni copiar archivos a ningún sitio.

---

## 1. Abrirlo

1. Abre **Android Studio** (**Ladybug 2024.2.1** o superior; da igual si es más
   reciente).
2. **File → Open** y selecciona **esta carpeta** (`ojo-de-dios-nativo`), no la
   carpeta `ojo-de-dios` que está al lado.
3. Si pregunta **"Trust project"**, acepta. Si avisa de componentes del SDK que
   faltan, acepta también: se descarga el SDK 35 solo.
4. La primera sincronización tarda unos minutos —descarga Gradle 8.11.1 y las
   dependencias de Compose—. Cuando abajo ponga *Gradle sync finished*:
   **Run ▶**.

Con el móvil conectado por USB y la **Depuración USB** activada (Ajustes →
Acerca del teléfono → 7 toques en "Número de compilación" → Opciones de
desarrollador), se instala y arranca. También vale un emulador: **Device
Manager → Create Device**. Para probar el botón de ubicación en el emulador,
abre **⋯ → Location** y fija una posición.

> **Aviso honesto.** El código está escrito y revisado a mano, pero **nunca se
> ha compilado**: mi entorno de trabajo no tiene JDK ni Android SDK. Verifiqué
> cada llamada a osmdroid y a predict4java contra su código fuente real, y
> encontré errores que corregí (por ejemplo, que `TilesOverlay` no tiene
> `setOpacity`), pero la primera compilación la vas a hacer tú. Si Gradle se
> queja de algo, mándame el texto del error.

---

## 2. Requisitos

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
Android Studio tendrá que descargar la primera vez es el SDK de Android 35,
que es grande (unos cientos de MB).

---

## 3. Qué hay dentro

```
ojo-de-dios-nativo/
├── build.gradle.kts              Plugins del proyecto
├── settings.gradle.kts           Módulos y repositorios
├── gradle.properties             Memoria, AndroidX, caché
├── gradlew / gradlew.bat         Envoltorio de Gradle
├── gradle/wrapper/               El envoltorio (jar + versión 8.11.1)
├── LEEME-PRIMERO.md              Este archivo
└── app/
    ├── build.gradle.kts          Configuración del módulo
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/data/          TLE y sismos empaquetados (funciona sin red)
        ├── java/com/samuelpart/ojodedios/
        └── res/                  Icono, cadenas y tema
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

## 4. Qué hace la app

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

La capa de satélites es la más interesante: **no pide posiciones a nadie**.
Lee los elementos orbitales del paquete de la app y resuelve la órbita en el
dispositivo, así que funciona sin conexión y cualquiera puede reproducir el
cálculo.

El botón **Límites legales**, arriba a la derecha, abre la explicación de dónde
está la frontera entre consultar datos abiertos y cometer un delito. Es el
mismo texto que el panel de la versión web.

---

## 5. Decisiones que conviene conocer

- **Sin OkHttp, sin kotlinx.serialization, sin Coil.** Android ya trae
  `HttpURLConnection`, `org.json` y `BitmapFactory`. Cuantas menos
  dependencias, menos versiones que puedan romper la compilación.
- **Los marcadores de satélite no se recrean**, solo cambian de posición. Si se
  reconstruyeran 25 marcadores cada segundo, el mapa parpadearía y la batería
  lo notaría.
- **Las trazas orbitales se recalculan cada minuto**, no cada segundo: recorrer
  100 minutos de órbita por satélite cuesta más que una posición suelta.
- **La transparencia de las capas de mosaicos** se hace con un
  `ColorMatrixColorFilter`, porque osmdroid no expone `setOpacity`.
- **El escucha del mapa se instala una sola vez**, no en cada recomposición.

---

## 6. Generar un APK

**Build → Build Bundle(s) / APK(s) → Build APK(s)**. Queda en
`app/build/outputs/apk/debug/app-debug.apk`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para un APK de release firmado: **Build → Generate Signed Bundle / APK**. Necesita
un almacén de claves propio (**Create new…** la primera vez). El proyecto no
incluye ninguno a propósito: cada firma debe ser tuya.

---

## 7. Problemas frecuentes

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
archivo `local.properties` en esta carpeta con la ruta de tu SDK:
`sdk.dir=/home/tu-usuario/Android/Sdk` (en Windows, con las barras invertidas
duplicadas). Ese archivo no se versiona: es tuyo.

**`Missing permission` al pulsar "Mi ubicación"**
El permiso se pide en el momento. Si lo denegaste para siempre, ve a Ajustes →
Apps → Ojo de Dios → Permisos → Ubicación.

**No aparece ningún satélite**
Mira Logcat filtrando por `com.samuelpart.ojodedios`. Si un TLE tiene mal el
dígito de control, predict4java lanza excepción; el código la captura y descarta
ese satélite en vez de caerse. Los 25 que van dentro están verificados uno a uno.

**Las cámaras de Londres no cargan**
La API de TfL limita peticiones por IP. Espera un minuto y refresca.

---

## 8. Mantener los datos al día

Los TLE envejecen: la precisión de SGP4 se degrada unos kilómetros por semana.
La versión web del repositorio sabe actualizarlos:

```bash
cd ../ojo-de-dios
npm run refresh-data
cp public/data/tle.json ../ojo-de-dios-nativo/app/src/main/assets/data/
```

---

## Apéndice · Crear el proyecto a mano, si lo prefieres

Esto **no hace falta** —el proyecto ya está hecho—, pero queda aquí por si
quieres montarlo desde cero en otro sitio o entender qué genera cada pieza.

1. **File → New → New Project → Empty Activity** (la de Compose, no
   "Empty Views Activity").
2. Nombre `Ojo de Dios`, paquete `com.samuelpart.ojodedios`, lenguaje Kotlin,
   **Minimum SDK API 24**, lenguaje de configuración Kotlin DSL. **Finish**.
3. En `app/build.gradle.kts`, añade al final de `dependencies`:

```kotlin
implementation("org.osmdroid:osmdroid-android:6.1.20")
implementation("com.github.davidmoten:predict4java:1.3.1")
```

4. Copia los archivos de `app/src/main/` de esta carpeta encima de los del
   proyecto nuevo (los nombres de paquete coinciden) y borra el
   `MainActivity.kt` que haya generado el asistente.
5. **Run ▶**.

Fíjate en dos cosas que el asistente **no** hace y que aquí sí están: aplicar el
plugin `org.jetbrains.kotlin.plugin.compose` (obligatorio desde Kotlin 2.0) y
declarar `compileSdk = 35`. Si al crear el proyecto a mano Compose no compila,
es casi siempre por lo primero.

---

## La otra versión del proyecto

En `../ojo-de-dios/` está la versión **web**, que sigue siendo útil: sirve el
mapa a cualquier dispositivo de tu red desde un navegador y es la que actualiza
los TLE. Dentro de ella, `android/` es la versión antigua hecha con Capacitor
(un WebView): fue un paso intermedio y ya no es la vía para Android. Se puede
borrar esa carpeta sin afectar a nada más.

---

## Nota final

Esta app solo lee feeds públicos abiertos y, si se lo pides, tu propia
ubicación. No pide acceso a la cámara, ni a los contactos, ni al
almacenamiento, ni a la lista de aplicaciones: únicamente Internet, estado de
la red y ubicación, y las tres se usan.

Vigilar infraestructura pública está bien. Vigilar personas, no. Ninguna de
estas fuentes permite identificar a una persona concreta, y la app no intenta
construir esa capacidad.
