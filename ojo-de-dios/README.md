# Ojo de Dios

Una consola que reúne, sobre un solo mapa, **las fuentes de vigilancia que sus
operadores publican de forma abierta en Internet**: cámaras de tráfico oficiales,
posiciones de aeronaves y buques, satélites calculados en tiempo real, sismología,
radar meteorológico e imágenes satelitales de la NASA.

> **Qué no es.** No accede a cámaras privadas, no rompe contraseñas, no hay
> "satélites espía en vivo" dentro. Todo lo que ves lo publicó alguien a propósito.
> El panel **Límites legales** de la propia interfaz explica la frontera.

---

## Arrancar

### Kali Linux (o cualquier Debian/Ubuntu)

Hay un instalador que comprueba el entorno, instala lo que falte y valida que
el motor arranque:

```bash
git clone https://github.com/SamuelPart/Qr.git
cd Qr/ojo-de-dios
./scripts/instalar-kali.sh
```

Y después, a elegir:

```bash
./scripts/instalar-kali.sh --arrancar    # arranca en primer plano
./scripts/instalar-kali.sh --servicio    # servicio systemd, arranca al encender
./scripts/instalar-kali.sh --desinstalar # quita el servicio
```

El instalador detecta si lo ejecutas como root o con sudo, porque Kali suele
usarse como root y ahí `sudo` puede no existir. Ninguna dependencia se compila:
express, leaflet y satellite.js son JavaScript puro, así que no hace falta
`build-essential`.

### A mano, en cualquier sistema

```bash
cd ojo-de-dios
npm install
npm start          # → http://localhost:3000
npm test           # prueba de humo
```

No hace falta ninguna clave de API ni registro.

### Entrar desde otro dispositivo

El servidor escucha en `0.0.0.0`, así que desde el móvil o un portátil de la
misma red te vale la IP de la máquina con Kali:

```bash
ip -4 addr show scope global | grep inet    # averigua la IP
# luego, en el otro dispositivo:  http://192.168.x.x:3000
```

Si tienes `ufw` activo: `sudo ufw allow 3000/tcp`.
Si hay una VPN levantada, la IP que veas puede ser del túnel y no de tu red.

### Requisitos

- **Node.js 18 o superior** (se recomienda 20 o 22). El código usa `fetch`
  nativo y `AbortSignal.timeout`, que no existen en versiones anteriores.
  Con Node 18/20 verás un aviso de *fetch experimental*: es inofensivo.
- Puerto 3000 libre, o cambia con `PORT=8080 npm start`.

---

## Capas

| Capa | Fuente | Naturaleza del dato |
|---|---|---|
| **Cámaras públicas** | TfL (Londres), Fintraffic (Finlandia) | Fotograma cada pocos minutos. TfL también sirve clips MP4. |
| **Vuelos (ADS-B)** | airplanes.live | Posiciones emitidas por las aeronaves por radio a 1090 MHz. |
| **Barcos (AIS)** | Digitraffic Marine | Emisión obligatoria por convenio internacional. |
| **Satélites** | CelesTrak | Posición **calculada en tu navegador** con el modelo SGP4. |
| **Sismos** | USGS | Últimas 24 h, actualización cada 5 min. |
| **Radar de lluvia** | RainViewer | Mosaico de radares nacionales, animado. |
| **Imagen satelital NASA** | NASA GIBS (VIIRS, Suomi-NPP) | Color verdadero con ~1 día de latencia + anomalías térmicas. |

---

## Cómo resuelve el navegador los datos

Cada capa intenta tres vías, en este orden, y te dice por cuál lo consiguió
(la etiqueta de color a la derecha de cada capa):

1. **En vivo** — petición directa desde tu navegador al servicio original.
2. **Vía proxy** — el servidor de la app reenvía la petición. Sirve cuando el
   servicio no permite CORS o cuando la máquina que sirve la app sí tiene salida
   a Internet y el navegador no.
3. **Respaldo** — instantánea guardada en `data/`. Permite que la aplicación
   siga mostrando algo aunque no haya red.

El proxy tiene **lista blanca de hosts** (`HOSTS_PERMITIDOS` en `server.js`).
No es un proxy abierto: cualquier destino que no sea una fuente declarada se
rechaza con un 403.

### Actualizar las instantáneas

Desde cualquier máquina con Internet:

```bash
node scripts/refresh-data.mjs
```

Descarga los catálogos completos (TfL ~1000 cámaras, Fintraffic, Caltrans,
CelesTrak) y reescribe `data/*.json`.

---

## Estructura

```
ojo-de-dios/
├── server.js                    Express + proxy con lista blanca
├── scripts/
│   ├── instalar-kali.sh         Instalador para Kali/Debian (+ servicio systemd)
│   └── refresh-data.mjs         Actualiza las instantáneas de /data
├── test/smoke.mjs               Prueba de humo sin navegador
├── data/                        Instantáneas de respaldo (JSON)
│   ├── tle.json                 Elementos orbitales
│   ├── cams-london.json         Cámaras TfL
│   ├── cams-finland.json        Cámaras Fintraffic
│   ├── quakes.json              Sismos USGS
│   └── sources.json             Catálogo de fuentes con su licencia
└── public/
    ├── index.html               Interfaz y panel legal
    ├── style.css                Tema HUD
    ├── app.js                   Motor: capas, SGP4, proxy y respaldos
    └── vendor/                  Leaflet y satellite.js servidos en local
```

Se sirven Leaflet y satellite.js desde `public/vendor/`, sin CDN: la aplicación
no depende de terceros para arrancar.

---

## Resolución real de cada fuente

Conviene tenerlo claro antes de prometer nada:

- **Imagen satelital NASA (GIBS/VIIRS):** ~250–375 m por píxel, color verdadero,
  ~1 día de antigüedad. No es vídeo.
- **Imagen aérea/satelital de archivo (fondo Esri):** hasta ~30–50 cm por píxel,
  pero es archivo comercial con meses o años de antigüedad.
- **Cámaras de tráfico:** detalle de calle, pero fotograma cada minutos y solo
  donde el operador decide publicarlas.
- **Satélites:** una posición calculada, unos pocos km de precisión. Nunca imagen.
- **ADS-B / AIS:** posiciones de quien emite. Sin identidad de pasajeros.

**Lo que no existe en abierto:** vídeo continuo de cualquier punto del planeta
con detalle de calle. Un sistema así exigiría cientos de miles de satélites de
órbita baja con enlace en tiempo real; no está financiado ni por los presupuestos
militares combinados. Lo que sí existe es este mosaico de fuentes, cada una con
su latencia y su resolución — y ensamblarlas bien es justamente el trabajo
interesante del proyecto.

---

## Límites legales, en corto

- **Público y correcto:** consumir datos que un operador publica como datos
  abiertos, respetando su licencia y sus límites de peticiones.
- **Delito:** acceder a cámaras privadas o municipales sin autorización
  (España: arts. 197 y 264 CP; EE. UU.: CFAA; R. U.: Computer Misuse Act).
  Escanear puertos o probar credenciales por defecto también lo es.
- **Vigilar infraestructura pública está bien. Vigilar personas, no.**
  Ninguna de estas fuentes identifica a una persona concreta; no construyas
  esa capacidad combinándolas.

---

## Licencias de las fuentes

| Fuente | Licencia |
|---|---|
| TfL JamCams | Datos abiertos TfL (Open Government Licence) |
| Fintraffic / Digitraffic | CC BY 4.0 |
| Caltrans CCTV | Datos abiertos del Estado de California |
| USGS Earthquake Hazards | Dominio público |
| NASA GIBS / Worldview | Dominio público (NASA) |
| CelesTrak (TLE) | Dominio público (18.º Escuadrón de Defensa Espacial) |
| OpenStreetMap | ODbL — requiere atribución |
| CARTO | Uso libre con atribución |
| Esri World Imagery | Propiedad de Esri y sus proveedores |
| RainViewer | API gratuita para uso no comercial |
| airplanes.live | API pública, uso no comercial |
