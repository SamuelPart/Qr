# Qr

Genera QR con múltiples diseños.

---

## Ojo de Dios

En `ojo-de-dios/` vive un proyecto aparte: una consola de vigilancia que reúne
sobre un solo mapa **las fuentes que sus operadores publican de forma abierta
en Internet** — cámaras de tráfico oficiales, posiciones de aeronaves (ADS-B) y
buques (AIS), satélites calculados en tiempo real con SGP4, sismología del USGS,
radar meteorológico e imagen satelital de la NASA.

```bash
cd ojo-de-dios
npm install
npm start          # → http://localhost:3000
```

No necesita claves de API ni registro.

**Lo que no es:** no accede a cámaras privadas, no rompe credenciales y no
existe ningún "satélite espía en vivo" dentro. Todo lo que muestra lo publicó
alguien a propósito. La propia interfaz incluye un panel **Límites legales**
que explica dónde está la frontera entre consultar datos abiertos y cometer un
delito.

Documentación completa en [`ojo-de-dios/README.md`](ojo-de-dios/README.md).

---

## Ojo de Dios · app nativa de Android

En `ojo-de-dios-nativo/` está la versión **nativa** para Android: Kotlin +
Jetpack Compose, mapa osmdroid y cálculo orbital SGP4 **dentro del teléfono**.
No es un WebView ni envuelve la versión web: es una app Android de verdad.

**Es un proyecto completo, no una carpeta de fuentes sueltas:** incluye el
envoltorio de Gradle, la configuración de los módulos, los iconos del lanzador,
el manifiesto y once archivos Kotlin. Se abre directamente con **Android Studio
→ File → Open** sobre esa carpeta y se pulsa *Run*; no hay que crear nada con
el asistente ni copiar archivos.

La guía paso a paso está en
[`ojo-de-dios-nativo/LEEME-PRIMERO.md`](ojo-de-dios-nativo/LEEME-PRIMERO.md).

No se ha compilado aquí —el entorno de trabajo no tiene JDK ni Android SDK—,
así que la primera compilación la hará quien lo abra.

Solo dos dependencias, ninguna con clave de API: `osmdroid-android` y
`predict4java`.

> La carpeta `ojo-de-dios/android/` es la versión anterior hecha con Capacitor
> (es decir, un WebView). Fue un paso intermedio: la vía para Android es la
> carpeta nativa. El servidor web sigue siendo independiente y válido.
