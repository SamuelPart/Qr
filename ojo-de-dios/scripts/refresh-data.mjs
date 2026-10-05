#!/usr/bin/env node
/**
 * refresh-data.mjs — actualiza las instantáneas de respaldo en /data
 * ---------------------------------------------------------------------------
 * Ejecútalo desde una máquina con salida a Internet:
 *
 *     node scripts/refresh-data.mjs
 *
 * Descarga los catálogos completos de las fuentes públicas y los guarda como
 * instantáneas. La aplicación funcionará aunque después se quede sin red.
 */

import { writeFile, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const CARPETA_DATOS = join(RAIZ, 'public', 'data');

const CABECERAS = {
  'User-Agent': 'Mozilla/5.0 (compatible; OjoDeDios/1.0; +https://github.com/SamuelPart/Qr)',
  Accept: 'application/json, text/plain, */*',
};

async function traer(url, ms = 30000) {
  const r = await fetch(url, { headers: CABECERAS, signal: AbortSignal.timeout(ms) });
  if (!r.ok) throw new Error(`HTTP ${r.status} en ${url}`);
  return r.json();
}

async function guardar(nombre, objeto) {
  await mkdir(CARPETA_DATOS, { recursive: true });
  const ruta = join(CARPETA_DATOS, `${nombre}.json`);
  await writeFile(ruta, JSON.stringify(objeto, null, 2) + '\n', 'utf8');
  console.log(`  ✓ ${nombre}.json`);
}

function hoy() {
  return new Date().toISOString().slice(0, 10);
}

/* ── 1. Elementos orbitales (TLE) ──────────────────────────────────────── */
async function tle() {
  const grupos = ['stations', 'weather', 'science'];
  const vistos = new Set();
  const salida = [];

  for (const grupo of grupos) {
    const url = `https://celestrak.org/NORAD/elements/gp.php?GROUP=${grupo}&FORMAT=json`;
    let lista;
    try {
      lista = await traer(url);
    } catch (err) {
      console.warn(`  ! grupo ${grupo}: ${err.message}`);
      continue;
    }
    for (const o of lista) {
      if (vistos.has(o.NORAD_CAT_ID)) continue;
      vistos.add(o.NORAD_CAT_ID);
      salida.push({
        nombre: o.OBJECT_NAME,
        norad: o.NORAD_CAT_ID,
        tipo: grupo === 'stations' ? 'estacion' : grupo === 'weather' ? 'meteorologico' : 'observacion',
        l1: o.TLE_LINE1,
        l2: o.TLE_LINE2,
      });
    }
  }

  if (!salida.length) throw new Error('CelesTrak no devolvió elementos orbitales');

  await guardar('tle', {
    _meta: {
      fuente: 'CelesTrak — https://celestrak.org/NORAD/elements/',
      capturado: hoy(),
      actualizar_con: 'node scripts/refresh-data.mjs',
      nota: 'Elementos orbitales de dos líneas (TLE). Permiten calcular la posición de cada objeto en tiempo real con SGP4.',
    },
    satelites: salida,
  });
}

/* ── 2. Cámaras de Londres (TfL) ───────────────────────────────────────── */
async function camarasLondres() {
  const lugares = await traer('https://api.tfl.gov.uk/Place/Type/JamCam');

  const camaras = lugares.map((p) => {
    const props = Object.fromEntries((p.additionalProperties || []).map((a) => [a.key, a.value]));
    return {
      id: String(p.id).replace('JamCams_', ''),
      nombre: p.commonName,
      lat: p.lat,
      lon: p.lon,
      vista: props.view || '',
      disponible: props.available !== 'false',
    };
  });

  if (!camaras.length) throw new Error('TfL no devolvió cámaras');

  await guardar('cams-london', {
    _meta: {
      fuente: 'Transport for London — TfL JamCams (API pública abierta)',
      endpoint: 'https://api.tfl.gov.uk/Place/Type/JamCam',
      capturado: hoy(),
      licencia: 'Datos abiertos TfL. Las imágenes son propiedad de TfL.',
      patron_imagen: 'https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/{id}.jpg',
      patron_video: 'https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/{id}.mp4',
    },
    camaras,
  });
}

/* ── 3. Cámaras de carretera de Finlandia (Fintraffic) ─────────────────── */
async function camarasFinlandia() {
  const gj = await traer('https://tie.digitraffic.fi/api/weathercam/v1/stations');
  const camaras = (gj.features || []).map((f) => {
    const [lon, lat] = f.geometry.coordinates;
    return {
      id: f.properties.id,
      nombre: f.properties.name,
      lat,
      lon,
      presets: (f.properties.presets || []).filter((p) => p.inCollection !== false).map((p) => p.id),
    };
  });

  if (!camaras.length) throw new Error('Digitraffic no devolvió estaciones');

  await guardar('cams-finland', {
    _meta: {
      fuente: 'Fintraffic / Digitraffic — cámaras meteorológicas de carretera',
      endpoint: 'https://tie.digitraffic.fi/api/weathercam/v1/stations',
      capturado: hoy(),
      licencia: 'CC BY 4.0 (Digitraffic, Fintraffic)',
      patron_imagen: 'https://weathercam.digitraffic.fi/{presetId}.jpg',
    },
    camaras,
  });
}

/* ── 4. Cámaras de California (Caltrans) ───────────────────────────────── */
async function camarasCalifornia() {
  const distritos = ['d3', 'd4', 'd7', 'd11', 'd12'];
  const camaras = [];

  for (const d of distritos) {
    const url = `https://cwwp2.dot.ca.gov/data/${d}/cctv/cctv${d.toUpperCase()}.json`;
    let datos;
    try {
      datos = await traer(url);
    } catch (err) {
      console.warn(`  ! Caltrans ${d}: ${err.message} (su servidor suele rechazar clientes que no son navegador)`);
      continue;
    }
    for (const item of datos?.data ?? []) {
      const c = item.cctv;
      if (!c?.location?.latitude) continue;
      camaras.push({
        id: c.index,
        nombre: c.location.locationName,
        lat: Number(c.location.latitude),
        lon: Number(c.location.longitude),
        ruta: c.location.route,
        condado: c.location.county,
        imagen: c.imageData?.static?.currentImageURL || null,
        video: c.imageData?.streaming?.currentVideoURL || null,
        direccion: c.location.direction,
      });
    }
  }

  if (!camaras.length) {
    console.warn('  ! Caltrans no devolvió ninguna cámara; se omite la instantánea');
    return;
  }

  await guardar('cams-california', {
    _meta: {
      fuente: 'Caltrans — CCTV de carretera (datos abiertos del Estado de California)',
      capturado: hoy(),
      licencia: 'Datos abiertos del Estado de California',
    },
    camaras,
  });
}

/* ── 5. Sismos (USGS) ──────────────────────────────────────────────────── */
async function sismos() {
  const gj = await traer('https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson');
  if (!gj.features?.length) throw new Error('USGS no devolvió sismos');

  await guardar('quakes', {
    _meta: {
      fuente: 'USGS Earthquake Hazards Program',
      endpoint: 'https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson',
      capturado: hoy(),
      licencia: 'Dominio público (obra del gobierno de los EE. UU.)',
      nota: 'Instantánea de respaldo del feed M4.5+ de las últimas 24 h. La app pide el feed completo y solo cae aquí si no hay red.',
      formato: 'GeoJSON FeatureCollection, idéntico al que sirve USGS',
    },
    type: gj.type,
    metadata: gj.metadata,
    features: gj.features,
  });
}

/* ── Ejecución ─────────────────────────────────────────────────────────── */
const tareas = [
  ['Elementos orbitales (CelesTrak)', tle],
  ['Cámaras de Londres (TfL)', camarasLondres],
  ['Cámaras de Finlandia (Fintraffic)', camarasFinlandia],
  ['Cámaras de California (Caltrans)', camarasCalifornia],
  ['Sismos (USGS)', sismos],
];

console.log('\nActualizando instantáneas de datos públicos…\n');

let fallos = 0;
for (const [titulo, tarea] of tareas) {
  console.log(`▸ ${titulo}`);
  try {
    await tarea();
  } catch (err) {
    fallos++;
    console.error(`  ✗ ${err.message}`);
  }
}

console.log(fallos ? `\nTerminado con ${fallos} fallo(s).\n` : '\nTodo actualizado.\n');
process.exit(fallos ? 1 : 0);
