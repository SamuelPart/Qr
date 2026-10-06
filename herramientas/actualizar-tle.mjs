#!/usr/bin/env node
/**
 * Actualiza los elementos orbitales (TLE) que lleva la app dentro.
 *
 *   node herramientas/actualizar-tle.mjs
 *
 * Descarga cada objeto del catálogo desde CelesTrak, comprueba el dígito de
 * control de las dos líneas y reescribe `app/src/main/assets/data/tle.json`.
 *
 * Por qué existe este archivo: un TLE envejece. La posición que calcula SGP4
 * se degrada unos kilómetros por semana, así que conviene refrescarlos cada
 * mes o dos. Sin dependencias: usa `fetch`, que ya viene con Node 18+.
 *
 * El catálogo está a mano a propósito. La app no necesita los 30 000 objetos
 * en órbita: necesita una selección que quepa en el APK y que se pueda
 * entender de un vistazo.
 */

import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const DESTINO = join(RAIZ, 'app/src/main/assets/data/tle.json');

/** NORAD → tipo. El tipo decide el color del marcador y si se dibuja su traza. */
const CATALOGO = {
  // Estaciones espaciales tripuladas. Se les dibuja la traza orbital.
  25544: ['estacion', 'ISS (ZARYA)'],
  48274: ['estacion', 'CSS (TIANHE)'],
  49044: ['estacion', 'ISS (NAUKA)'],
  36086: ['estacion', 'ISS (POISK)'],
  53239: ['estacion', 'CSS (WENTIAN)'],
  54216: ['estacion', 'CSS (MENGTIAN)'],

  // Naves tripuladas y de carga atracadas o en vuelo.
  67796: ['tripulada', 'CREW DRAGON 12'],
  69180: ['tripulada', 'SHENZHOU-23'],
  68837: ['carga', 'PROGRESS-MS 34'],
  68689: ['carga', 'CYGNUS NG-24'],
  69049: ['carga', 'TIANZHOU-10'],

  // Meteorología, muchos geoestacionarios: se ven fijos sobre el ecuador.
  41866: ['meteorologico', 'GOES 16 (EWS-G1)'],
  43226: ['meteorologico', 'GOES 17'],
  51850: ['meteorologico', 'GOES 18'],
  35491: ['meteorologico', 'GOES 14 (EWS-G3)'],
  36411: ['meteorologico', 'GOES 15 (EWS-G2)'],
  40732: ['meteorologico', 'METEOSAT-11'],
  54743: ['meteorologico', 'METEOSAT-12 (MTG-I1)'],
  41836: ['meteorologico', 'HIMAWARI-9'],

  // Observación terrestre en órbita baja.
  41335: ['observacion', 'SENTINEL-3A'],
  43437: ['observacion', 'SENTINEL-3B'],
  37849: ['observacion', 'SUOMI NPP'],
  43013: ['observacion', 'NOAA-20 (JPSS-1)'],
  54234: ['observacion', 'NOAA-21 (JPSS-2)'],
  43689: ['observacion', 'METOP-C'],
};

const CELESTRAK = 'https://celestrak.org/NORAD/elements/gp.php';
const CABECERA = {
  'User-Agent': 'OjoDeDios/1.0 (actualizador de TLE; uso personal)',
  Accept: 'text/plain',
};

/**
 * Dígito de control de una línea TLE: los dígitos suman su valor, el signo
 * menos suma 1 y lo demás suma 0. El resultado módulo 10 es el último dígito.
 *
 * Sin esta comprobación, un TLE mal formado llega a la app y predict4java
 * lanza una excepción al propagarlo. Ya pasó una vez.
 */
function digitoDeControl(linea) {
  let suma = 0;
  for (const c of linea.slice(0, 68)) {
    if (c >= '0' && c <= '9') suma += Number(c);
    else if (c === '-') suma += 1;
  }
  return suma % 10;
}

function lineaValida(linea) {
  if (typeof linea !== 'string' || linea.length < 69) return false;
  const esperado = Number(linea[68]);
  return Number.isInteger(esperado) && esperado === digitoDeControl(linea);
}

async function descargar(norad, intentos = 3) {
  const url = `${CELESTRAK}?CATNR=${norad}&FORMAT=tle`;

  for (let intento = 1; intento <= intentos; intento++) {
    try {
      const respuesta = await fetch(url, { headers: CABECERA });
      if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`);
      const texto = (await respuesta.text()).trim();
      if (!texto) throw new Error('respuesta vacía');

      const lineas = texto.split('\n').map((l) => l.trim()).filter(Boolean);
      // Formato: nombre, línea 1, línea 2.
      if (lineas.length < 3) throw new Error(`esperaba 3 líneas, llegaron ${lineas.length}`);
      return { nombre: lineas[0], l1: lineas[1], l2: lineas[2] };
    } catch (error) {
      if (intento === intentos) throw error;
      // CelesTrak corta si se piden muchas seguidas: se espera y se reintenta.
      await new Promise((r) => setTimeout(r, 1500 * intento));
    }
  }
}

async function main() {
  const norads = Object.keys(CATALOGO).map(Number);
  console.log(`Descargando ${norads.length} objetos de CelesTrak…\n`);

  const satelites = [];
  const descartados = [];

  for (const norad of norads) {
    const [tipo, nombreEsperado] = CATALOGO[norad];
    try {
      const { nombre, l1, l2 } = await descargar(norad);

      if (!lineaValida(l1) || !lineaValida(l2)) {
        descartados.push(`${nombre} — dígito de control inválido`);
        console.log(`  ✗ ${nombre.padEnd(22)} dígito de control inválido`);
        continue;
      }

      satelites.push({ nombre: nombre || nombreEsperado, norad, tipo, l1, l2 });
      console.log(`  ✔ ${nombre.padEnd(22)} ${tipo}`);
    } catch (error) {
      descartados.push(`${nombreEsperado} — ${error.message}`);
      console.log(`  ✗ ${nombreEsperado.padEnd(22)} ${error.message}`);
    }

    // Se respeta el servicio: es de una institución pública y gratuito.
    await new Promise((r) => setTimeout(r, 300));
  }

  if (satelites.length === 0) {
    console.error('\nNo se descargó ningún objeto. Se deja el archivo como estaba.');
    process.exit(1);
  }

  const hoy = new Date().toISOString().slice(0, 10);
  const salida = {
    _meta: {
      fuente: 'CelesTrak — https://celestrak.org/NORAD/elements/',
      capturado: hoy,
      actualizar_con: 'node herramientas/actualizar-tle.mjs',
      nota:
        'Elementos orbitales de dos líneas (TLE). Permiten calcular la posición ' +
        'de cada objeto con SGP4 en el propio dispositivo. Se comprueba el dígito ' +
        'de control de cada línea al descargarlos' +
        (descartados.length ? `; descartados: ${descartados.join(', ')}.` : '.'),
    },
    satelites,
  };

  await writeFile(DESTINO, `${JSON.stringify(salida, null, 2)}\n`, 'utf8');

  console.log(`\n${satelites.length} de ${norads.length} escritos en app/src/main/assets/data/tle.json`);
  if (descartados.length) {
    console.log('\nDescartados:');
    for (const d of descartados) console.log(`  · ${d}`);
  }
  console.log('\nVuelve a compilar en Android Studio para que el APK los lleve.');
}

main().catch((error) => {
  console.error(`\nError: ${error.message}`);
  process.exit(1);
});
