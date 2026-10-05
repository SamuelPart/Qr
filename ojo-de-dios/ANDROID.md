# Ojo de Dios en Android

Guía para compilar la app y ejecutarla en tu celular. No hay que reescribir nada
en Kotlin: la interfaz ya es web, así que va dentro de un WebView con Capacitor.

**Lo bueno de este camino:** dentro de la app **desaparece el problema de CORS**.
El WebView habla con los servicios públicos directamente, sin proxy intermedio,
así que todas las capas funcionan en el celular sin necesitar el servidor Node.

---

## Resumen del flujo

```
public/  (tu app web)
   │  npx cap sync android
   ▼
android/  (proyecto nativo)
   │  Gradle
   ▼
APK  →  instalado en el celular
```

Cada vez que toques algo de `public/`, hay que volver a sincronizar. Es el único
paso que se olvida, y tiene una comprobación que te avisa.

---

## 1. Requisitos en tu PC

| Qué | Versión | Nota |
|---|---|---|
| **Android Studio** | Ladybug (2024.2.1) o superior | El plugin de Android es AGP 8.7.2; versiones anteriores de Studio no lo entienden |
| **JDK** | **21** | El proyecto compila con `VERSION_21`. Android Studio Ladybug ya trae su propio JDK 21 — no instales nada |
| **Node.js** | 18 o superior | Solo para sincronizar los archivos web |
| **Android SDK** | API 35 | El asistente de Android Studio lo instala solo |

> Si tu Android Studio es más antiguo y no quieres actualizarlo, se puede bajar
> `compileSdkVersion`/`targetSdkVersion` a 34 y el AGP a 8.5 en
> `android/variables.gradle` y `android/build.gradle`. Hazlo solo si sabes lo que
> tocas: es más fácil actualizar Studio.

### Instalarlo en Kali / Debian

```bash
# Node (si aún no lo tienes)
./scripts/instalar-kali.sh

# Android Studio: descarga el .tar.gz oficial y descomprímelo
#   https://developer.android.com/studio
tar -xzf android-studio-*.tar.gz -C ~/
~/android-studio/bin/studio.sh
```

El asistente de primer arranque instala el SDK y las herramientas de compilación.

---

## 2. Sincronizar y abrir el proyecto

Desde la carpeta `ojo-de-dios`:

```bash
npm install          # solo la primera vez
npm run android      # sincroniza y abre Android Studio
```

Ese comando hace dos cosas:

```bash
npx cap sync android        # copia public/ → android/app/src/main/assets/public/
npx cap open android        # abre Android Studio en el proyecto android/
```

Si solo quieres sincronizar sin abrir Studio: `npm run android:sync`.

> **Importante.** Capacitor no versiona los archivos web dentro de `android/`
> (los genera). Si abres el proyecto en Android Studio sin haber sincronizado,
> la compilación fallará con un mensaje que te dice exactamente qué ejecutar,
> en vez de darte una app en blanco sin explicación.

---

## 3. Ejecutar en el celular

### Opción A — por USB (la más cómoda para probar)

1. En el celular: **Ajustes → Acerca del teléfono → toca 7 veces "Número de compilación"**.
   Se activan las opciones de desarrollador.
2. **Ajustes → Sistema → Opciones de desarrollador → Depuración USB: activar**.
3. Conecta el cable. En el celular aparecerá *"¿Permitir depuración USB?"* → **Permitir**.
4. En Android Studio, arriba a la derecha, tu celular aparece en la lista de dispositivos.
5. Pulsa **Run ▶** (o `Shift+F10`).

La app se instala y se abre sola.

### Opción B — emulador

En Android Studio: **Device Manager → Create Device**. Cualquier Pixel reciente
sirve. Al arrancar el emulador, pulsa **Run ▶**.

Para probar el GPS en el emulador: los **⋯ (Extended Controls) → Location**
permiten fijar una posición simulada.

### Opción C — instalar el APK a mano

Genera el APK de depuración:

```bash
npm run android:apk
```

Queda en:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Pásalo al celular por cable, Telegram, Drive o `adb install`:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Si el celular te avisa de *"instalar apps de origen desconocido"*, es normal:
solo acepta la instalación de esa app.

---

## 4. Qué cambia dentro de la app

Tu código detecta dónde está corriendo y se adapta solo:

| | Navegador (con servidor) | App Android |
|---|---|---|
| `fetch` a servicios públicos | Choca con CORS → usa el proxy de Node | Va directo, sin intermediario |
| Indicador de red | Sondea `/api/estado` del servidor | Sondea una fuente real desde el celular |
| Instantáneas de respaldo | Servidas por Express | Empaquetadas dentro del APK |
| Botón **Mi ubicación** | Según el navegador | GPS nativo, con permiso del sistema |

El motor decide con esta detección:

```js
const ES_APP = Boolean(
  globalThis.Capacitor?.isNativePlatform?.() || location.protocol === 'capacitor:'
);
const HAY_SERVIDOR = !ES_APP;
```

Si no hay servidor, el intento de proxy se salta. Todo lo demás es idéntico.

---

## 5. Compilar una versión instalable de verdad (release)

El APK de depuración sirve para probar. Para repartirlo o publicarlo, firmado:

1. En Android Studio: **Build → Generate Signed App Bundle / APK**.
2. **Android App Bundle** si es para Google Play; **APK** si lo vas a repartir tú.
3. **Create new…** para crear el keystore. Se te pedirá una contraseña y tus datos.

   **Guarda el keystore y la contraseña.** Si lo pierdes, no podrás actualizar la
   app en Google Play nunca más. Es el error más caro y más habitual.

4. Marca *Remember passwords* y elige `release`.
5. Marca **V1 y V2** en los tipos de firma.

El archivo queda en `android/app/release/`. Ese keystore **no se sube a git**
(el `.gitignore` ya lo excluye).

---

## 6. Problemas frecuentes

**"SDK location not found"**
Crea `android/local.properties` con la ruta de tu SDK:
```properties
sdk.dir=/home/TU_USUARIO/Android/Sdk
```
Android Studio normalmente lo crea solo al abrir el proyecto.

**"Unsupported class file major version" o errores de Java**
Estás compilando con un JDK antiguo. El proyecto necesita **21**. En Android
Studio: **File → Settings → Build, Execution, Deployment → Build Tools →
Gradle → Gradle JDK → `jbr-21`**.

**"Faltan los archivos web de la aplicación"**
Es la comprobación que añadí. Ejecuta `npm run android:sync` y vuelve a compilar.

**Cambié el código y la app sigue igual**
Falta el `sync`. Ejecuta `npm run android:sync` (o `npm run android`) y recompila.
Es el paso que más se olvida.

**No cargan los mosaicos del mapa**
Necesitas conexión. Si estás en una red con portal cautivo, ábrela antes en el
navegador del celular.

**"Mi ubicación" no hace nada**
El permiso se deniega a la primera. Ve a **Ajustes → Apps → Ojo de Dios →
Permisos → Ubicación** y concédelo. Si lo denegaste para siempre, hay que
desinstalar y reinstalar la app.

**La app abre en blanco**
Repasa el registro en Android Studio (**Logcat**, filtra por `Capacitor`).
Casi siempre es assets sin sincronizar.

---

## 7. Detalles técnicos de la configuración

`capacitor.config.json`:

```json
{
  "appId": "com.samuelpart.ojodedios",
  "appName": "Ojo de Dios",
  "webDir": "public",
  "server": { "androidScheme": "https" },
  "plugins": { "CapacitorHttp": { "enabled": true } }
}
```

- **`webDir: "public"`** — Capacitor empaqueta solo esa carpeta. Por eso las
  instantáneas de datos viven en `public/data/` y no en `data/`: si estuvieran
  fuera, no viajarían dentro del APK.
- **`androidScheme: "https"`** — la app se sirve desde `https://localhost`. Con
  `http` el WebView bloquearía las peticiones a servicios HTTPS.
- **`CapacitorHttp`** — redirige `fetch` por la pila de red nativa de Android.
  Es lo que esquiva CORS. No afecta a las imágenes: las baldosas del mapa y los
  fotogramas de las cámaras cargan con `<img>`, que nunca ha tenido problema de CORS.

Permisos declarados en `android/app/src/main/AndroidManifest.xml`:

- `INTERNET` — imprescindible.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — para el botón de ubicación.
  El WebView pide el permiso nativo solo, sin plugins adicionales.
- `ACCESS_NETWORK_STATE` — distinguir "sin cobertura" de "servidor caído".

---

## 8. Actualizar la app cuando cambies el código

```bash
# 1. Editas public/app.js o public/style.css
# 2. Sincronizas
npm run android:sync
# 3. Run ▶ en Android Studio, o generas APK de nuevo
npm run android:apk
```

Si además actualizaste los datos (`npm run refresh-data`), el `sync` los
empaqueta junto con el resto.

---

## 9. Personalizar

| Quiero… | Dónde |
|---|---|
| Cambiar el nombre de la app | `android/app/src/main/res/values/strings.xml` (`app_name`) y `capacitor.config.json` |
| Cambiar el identificador (paquete) | `appId` en `capacitor.config.json`, luego `npx cap sync` |
| Cambiar el icono | Reemplaza los PNG en `android/app/src/main/res/mipmap-*/` y el color en `values/ic_launcher_background.xml` |
| Cambiar la pantalla de carga | Los `splash.png` en `android/app/src/main/res/drawable-*/` |
| Forzar orientación | `android:screenOrientation="portrait"` en el `<activity>` del manifiesto |
| Que no se apague la pantalla | `android:keepScreenOn="true"` en el `<activity>` |

---

## Nota final

La app accede a feeds públicos abiertos y a tu propia ubicación, nada más. No
pide permisos que no use: no hay cámara, ni contactos, ni almacenamiento, ni
acceso a la lista de apps. Si en algún momento añades un plugin que pida algo
de eso, el sistema se lo preguntará al usuario y su política de privacidad en
Google Play tendrá que declararlo.
