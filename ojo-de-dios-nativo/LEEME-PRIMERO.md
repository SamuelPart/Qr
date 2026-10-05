# Ojo de Dios · app nativa de Android

Kotlin + Jetpack Compose, sin WebView. El mapa es **osmdroid** y el cálculo
orbital es **SGP4 en el propio teléfono**. Ninguna de las dos dependencias pide
clave de API ni cuenta de Google.

Los archivos de `app/src/main/` de esta carpeta están listos para copiarse en un
proyecto recién creado con el asistente de Android Studio.

> **Antes de nada, lo honesto:** escribí este código sin poder compilarlo, porque
> mi entorno no tiene JDK ni Android SDK. Verifiqué a mano todas las firmas de
> osmdroid y de predict4java contra el código fuente real de sus repositorios
> —encontré y corregí, por ejemplo, que `TilesOverlay` no tiene `setOpacity`—,
> pero la primera compilación la vas a hacer tú. Si Gradle se queja de algo,
> mándame el error y lo arreglamos.

---

## 1. Requisitos

| Qué | Versión |
|---|---|
| Android Studio | Ladybug (2024.2.1) o superior |
| JDK | **17**, el que trae Android Studio (esta es la diferencia con la versión Capacitor, que exigía JDK 21) |
| Android SDK | API 35 |
| Conexión a Internet | Para descargar dependencias y para los feeds |

---

## 2. Crear el proyecto en Android Studio

**File → New → New Project** y elige la plantilla **Empty Activity**
(en la lista se llama *Empty Activity*, con icono de Compose; **no** elijas
"Empty Views Activity", que es la de vistas XML).

En la pantalla siguiente:

| Campo | Valor |
|---|---|
| **Name** | `Ojo de Dios` |
| **Package name** | `com.samuelpart.ojodedios` |
| **Save location** | donde quieras |
| **Language** | Kotlin |
| **Minimum SDK** | **API 24 ("Nougat"; Android 7.0)** |
| **Build configuration language** | Kotlin DSL (build.gradle.kts) |

Pulsa **Finish** y espera a que Gradle termine la primera sincronización. Tarda
un rato: está descargando el SDK y las dependencias de Compose.

**Por qué API 24:** osmdroid necesita 23 o superior y Compose también. 24 deja
fuera solo dispositivos de 2016 y anteriores, y a cambio evita las molestias de
los permisos antiguos.

---

## 3. Añadir las dos dependencias

Abre `app/build.gradle.kts` y añade al final del bloque `dependencies`:

```kotlin
dependencies {
    // … lo que ya genera el asistente …

    // Mapa de mosaicos, sin clave de API.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // SGP4/SDP4: propagación orbital. Port a Java del código de Vallado,
    // el mismo modelo que usan las apps de seguimiento de satélites.
    implementation("com.github.davidmoten:predict4java:1.3.1")
}
```

Las dos están publicadas en Maven Central, así que **no hace falta añadir
repositorios extra**: ni JitPack ni el repositorio de Google más allá del que ya
pone la plantilla.

Pulsa **Sync Now** (el aviso azul que aparece arriba).

> **Ojo con la versión de predict4java.** En Maven Central existe
> `uk.me.g4dpz:predict4java`, pero su última publicación es **1.1.3, de 2015**.
> La que hay que usar es `com.github.davidmoten:predict4java:1.3.1`, el fork
> mantenido, que además tiene la API moderna (`calculateSatelliteGroundTrack()`).

---

## 4. Copiar los archivos

Desde esta carpeta, a tu proyecto recién creado:

```
app/src/main/AndroidManifest.xml          →  app/src/main/AndroidManifest.xml   (reemplaza)
app/src/main/assets/data/tle.json         →  app/src/main/assets/data/          (carpeta nueva)
app/src/main/assets/data/quakes.json      →  app/src/main/assets/data/
app/src/main/java/com/samuelpart/ojodedios/*.kt   →  app/src/main/java/com/samuelpart/ojodedios/
app/src/main/res/values/strings.xml       →  app/src/main/res/values/strings.xml  (reemplaza)
app/src/main/res/values/themes.xml        →  app/src/main/res/values/themes.xml
```

Los nombres de paquete coinciden, así que los `.kt` encajan sin tocar nada.
Borra el `MainActivity.kt` que generó el asistente: el mío lo sustituye.

Si en el paso 2 elegiste otro *package name*, cambia la primera línea de cada
archivo `.kt` (`package com.samuelpart.ojodedios`) y también
`android:name=".MainActivity"` si moviste el paquete.

### El icono (opcional)

El proyecto trae un icono propio en `ojo-de-dios/build/icono-fuente.png`. Para
llevarlo a la app nativa, la vía más rápida es Android Studio:
**botón derecho sobre `res` → New → Image Asset**, y ahí eliges ese PNG.

---

## 5. Ejecutar

Conecta el celular por USB (Ajustes → Acerca del teléfono → 7 toques en
"Número de compilación" → Opciones de desarrollador → **Depuración USB**), y
pulsa **Run ▶**.

O usa un emulador: **Device Manager → Create Device**. Para probar el botón de
ubicación, en el emulador abre **⋯ → Location** y fija una posición.

### Generar un APK instalable

**Build → Build Bundle(s) / APK(s) → Build APK(s)**. Queda en
`app/build/outputs/apk/debug/app-debug.apk` y se instala con:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. Qué hace la app

Siete capas, todas de fuentes que su operador publica de forma abierta:

| Capa | Fuente | Cómo se obtiene |
|---|---|---|
| **Cámaras públicas** | TfL (Londres) · Fintraffic (Finlandia) | Fotograma JPG que se refresca cada 30 s |
| **Satélites** | CelesTrak | **Calculado en el teléfono con SGP4**, traza orbital incluida |
| **Sismos** | USGS | 24 h, con respaldo empaquetado |
| **Vuelos** | airplanes.live | ADS-B alrededor del centro del mapa |
| **Barcos** | Digitraffic | AIS del mar Báltico |
| **Radar de lluvia** | RainViewer | Mosaico del fotograma más reciente |
| **Imagen NASA** | GIBS / VIIRS | Color verdadero, ~1 día de antigüedad |

La capa de satélites es la joya: **no pide posiciones a nadie**. Descarga los
elementos orbitales una vez y resuelve la órbita en el dispositivo, así que
funciona sin conexión y cualquiera puede reproducir el cálculo.

---

## 7. Cómo está organizado

```
Red.kt            Cliente HTTP sobre HttpURLConnection + lectura de assets
Modelos.kt        Tipos de datos y catálogo de capas
Fuentes.kt        Fuentes de mosaicos y preparación de osmdroid
Satelites.kt      Envoltorio de SGP4 sobre predict4java
Repositorio.kt    Un método por feed, todos normalizados a PuntoMapa
OjoViewModel.kt   Estado, carga de capas y reloj de 1 Hz para los satélites
Mapa.kt           ControladorMapa (osmdroid) y su composable
Interfaz.kt       HUD, panel de capas y ficha de detalle
Tema.kt           Paleta y tipografía
MainActivity.kt   Ciclo de vida del mapa y permiso de ubicación
```

Decisiones que conviene conocer:

- **Sin OkHttp, sin kotlinx.serialization, sin Coil.** Android ya trae
  `HttpURLConnection`, `org.json` y `BitmapFactory`. Cuantas menos dependencias,
  menos versiones que puedan romper el build. El proyecto tiene exactamente dos.
- **Los marcadores de satélite no se recrean**, solo cambian de posición. Si se
  reconstruyeran 25 marcadores cada segundo, el mapa parpadearía y la batería
  sufriría sin motivo.
- **Las trazas orbitales se recalculan cada minuto**, no cada segundo: recorrer
  100 minutos de órbita por satélite cuesta más que una posición suelta.
- **La transparencia de las capas de mosaicos** se logra con un
  `ColorMatrixColorFilter`: osmdroid no expone `setOpacity` en `TilesOverlay`.

---

## 8. Problemas frecuentes

**`Unresolved reference: amsacode`**
Copiaste mal la dependencia. Tiene que ser
`com.github.davidmoten:predict4java:1.3.1` y el paquete del código es
`com.github.amsacode.predict4java`. Ambas cosas son correctas aunque no
coincidan: ese fork publica bajo el grupo de su mantenedor.

**El mapa sale gris o en blanco**
osmdroid necesita que `Fuentes.preparar(this)` se ejecute **antes** de construir
el MapView. Ya está en `MainActivity.onCreate`; si mueves código, no lo quites.
También revisa que la app tenga permiso de Internet.

**`Missing permission` al pulsar "Mi ubicación"**
El permiso se pide en el momento. Si lo denegaste para siempre, hay que ir a
Ajustes → Apps → Ojo de Dios → Permisos → Ubicación.

**No aparece ningún satélite**
Mira `Logcat` filtrando por tu paquete. Si un TLE tiene el dígito de control
mal, predict4java lanza excepción; el código la captura y descarta ese satélite
en vez de caerse, pero conviene saberlo. Los 25 del paquete están verificados.

**Las cámaras de Londres no cargan**
La API de TfL limita peticiones por IP. Espera un minuto y refresca.

---

## 9. Sobre la otra versión del proyecto

En `ojo-de-dios/` del repositorio sigue estando la versión web, que además
sirve para dos cosas que esta no hace:

- **`npm run refresh-data`** — actualiza los TLE y las instantáneas. Cuando lo
  ejecutes, copia el `tle.json` nuevo a `app/src/main/assets/data/` para que la
  app nativa lleve datos frescos. Los TLE envejecen: la precisión de SGP4 se
  degrada unos kilómetros por semana.
- **Servir a otros dispositivos** de tu red desde el navegador.

Y en `ojo-de-dios/android/` está la versión con Capacitor (WebView). Si ya no la
quieres, se puede borrar esa carpeta entera sin afectar a nada más — el
servidor web y esta app nativa son independientes.

---

## Nota final

Esta app solo lee feeds públicos abiertos y, si se lo pides, tu propia
ubicación. No hay cámara, ni contactos, ni almacenamiento, ni acceso a la lista
de aplicaciones. No pide ningún permiso que no use.

Vigilar infraestructura pública está bien. Vigilar personas, no. Ninguna de
estas fuentes permite identificar a una persona concreta, y la app no intenta
construir esa capacidad.
