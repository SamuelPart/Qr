/**
 * Prueba de humo sin navegador.
 * ---------------------------------------------------------------------------
 * Monta un DOM y un Leaflet mínimos, carga el motor real (public/app.js) y
 * comprueba que arranca, activa capas y cae a las instantáneas locales cuando
 * no hay salida a Internet — exactamente el peor caso.
 *
 *     node test/smoke.mjs
 */

import { readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const PUBLICO = join(RAIZ, 'public');

/* ═══════════════════ DOM mínimo ═══════════════════ */

const oyentes = new Map();

function crearElemento(id = '', clases = []) {
  const el = {
    id,
    tagName: 'DIV',
    className: clases.join(' '),
    classList: {
      _s: new Set(clases),
      add(...c) { c.forEach((x) => this._s.add(x)); },
      remove(...c) { c.forEach((x) => this._s.delete(x)); },
      toggle(c, forzar) {
        const activo = forzar === undefined ? !this._s.has(c) : Boolean(forzar);
        activo ? this._s.add(c) : this._s.delete(c);
        return activo;
      },
      contains(c) { return this._s.has(c); },
    },
    style: {},
    dataset: {},
    children: [],
    _html: '',
    _texto: '',
    value: '',
    src: '',
    get innerHTML() { return this._html; },
    set innerHTML(v) { this._html = String(v); },
    get textContent() { return this._texto; },
    set textContent(v) { this._texto = String(v); },
    get outerHTML() { return this._html; },
    set outerHTML(v) { this._html = String(v); },
    addEventListener(tipo, fn) {
      const clave = `${id}:${tipo}`;
      if (!oyentes.has(clave)) oyentes.set(clave, []);
      oyentes.get(clave).push(fn);
    },
    removeEventListener() {},
    appendChild(c) { this.children.push(c); return c; },
    removeChild() {},
    setAttribute() {},
    getAttribute() { return null; },
    click() {},
    focus() {},
    querySelector(sel) { return buscarEnHtml(this._html, sel); },
    querySelectorAll(sel) { return buscarTodosEnHtml(this._html, sel); },
  };
  return el;
}

const elementos = new Map();

// Los IDs se leen del propio index.html: si la interfaz cambia, la prueba lo nota.
const htmlIndex = await readFile(join(PUBLICO, 'index.html'), 'utf8');
const IDS = [...new Set([...htmlIndex.matchAll(/id="([^"]+)"/g)].map((m) => m[1]))];
for (const id of IDS) elementos.set(id, crearElemento(id));

// Nodos que el motor consulta por id pero no viven en el documento.
for (const extra of ['img-camara', 'video-camara']) {
  if (!elementos.has(extra)) elementos.set(extra, crearElemento(extra));
}

elementos.get('sel-fecha-gibs').value = '1';
elementos.get('sel-base').value = 'carto-oscuro';
elementos.get('panel-detalle').classList.add('oculto');
elementos.get('modal-legal').classList.add('oculto');

function buscarEnHtml(html, sel) {
  // Doble mínimo: reconoce .clase y [atributo="valor"].
  const clase = /\.([\w-]+)/.exec(sel)?.[1];
  const attr = /\[([\w-]+)="([^"]+)"\]/.exec(sel);
  if (clase && !html.includes(clase)) return null;
  if (attr && !html.includes(`${attr[1]}=&quot;${attr[2]}&quot;`) && !html.includes(`${attr[1]}="${attr[2]}"`) && !html.includes(`${attr[1]}='${attr[2]}'`)) return null;
  return crearElemento('', clase ? [clase] : []);
}

function buscarTodosEnHtml(html, sel) {
  const uno = buscarEnHtml(html, sel);
  return uno ? [uno] : [];
}

function consultarTodos(sel) { return []; }

globalThis.document = {
  querySelector(sel) {
    const m = /^#([\w-]+)$/.exec(sel);
    if (m) return elementos.get(m[1]) ?? null;
    if (sel.startsWith('.')) return crearElemento('', sel.slice(1).split('.'));
    return null;
  },
  getElementById: (id) => elementos.get(id) ?? null,
  querySelectorAll: consultarTodos,
  addEventListener() {},
  createElement: (tag) => crearElemento('', [tag]),
  body: crearElemento('body'),
};

globalThis.window = globalThis;
globalThis.console.log = console.log;

/* ═══════════════════ Leaflet mínimo ═══════════════════ */

const registro = { capas: [], marcadores: [], posiciones: [], tiles: 0, trazados: 0 };

function crearCapaLeaflet(nombre) {
  const c = {
    _nombre: nombre,
    _hijos: new Set(),
    addTo(destino) { destino?._hijos?.add(this); registro.capas.push(this); return this; },
    removeLayer(x) { this._hijos.delete(x); },
    addLayer(x) { this._hijos.add(x); return this; },
    clearLayers() { this._hijos.clear(); },
    setOpacity() { return this; },
    bringToBack() { return this; },
    setLatLng(ll) { this._ll = ll; registro.posiciones.push(ll); return this; },
    setZIndex() { return this; },
    on() { return this; },
    bindPopup() { return this; },
    setStyle() { return this; },
  };
  return c;
}

globalThis.L = {
  map: () => ({
    getCenter: () => ({ lat: 51.5, lng: -0.12 }),
    getBounds: () => ({
      getNorth: () => 52, getSouth: () => 51,
      getEast: () => 0.5, getWest: () => -0.7,
    }),
    setView() { return this; },
    getZoom: () => 3,
    hasLayer: () => true,
    on() { return this; },
    removeLayer() { return this; },
  }),
  layerGroup: () => crearCapaLeaflet('layerGroup'),
  tileLayer: (url) => { registro.tiles++; return { ...crearCapaLeaflet('tileLayer'), url }; },
  marker: (ll, op) => {
    registro.marcadores.push({ ll, op });
    return { ...crearCapaLeaflet('marker'), _ll: ll, _op: op, on() { return this; } };
  },
  circleMarker: (ll, op) => {
    registro.marcadores.push({ ll, op });
    return { ...crearCapaLeaflet('circleMarker'), bindPopup() { return this; } };
  },
  polyline: (pts) => { registro.trazados++; return crearCapaLeaflet('polyline'); },
  divIcon: (op) => ({ _icono: true, ...op }),
};

/* ═══════════════════ fetch simulado: sin Internet, pero con /data ═══════ */

let peticionesDirectas = 0;
let peticionesProxy = 0;
let peticionesSnapshot = 0;

globalThis.fetch = async (url) => {
  const u = String(url);

  if (u.startsWith('./data/')) {
    peticionesSnapshot++;
    // El servidor monta /data desde la carpeta del proyecto, no desde /public.
    const ruta = join(RAIZ, u.replace('./', ''));
    try {
      const texto = await readFile(ruta, 'utf8');
      return { ok: true, status: 200, json: async () => JSON.parse(texto), text: async () => texto };
    } catch {
      throw new Error(`ENOENT ${u}`);
    }
  }

  if (u.startsWith('/api/proxy')) {
    peticionesProxy++;
    // Simula un servidor sin salida a Internet.
    return { ok: false, status: 502, json: async () => ({ error: 'sin salida' }) };
  }

  if (u.startsWith('/api/estado')) {
    return {
      ok: true, status: 200,
      json: async () => ({
        salidaInternet: false,
        sondas: [{ alcanzable: false }, { alcanzable: false }, { alcanzable: false }],
      }),
    };
  }

  // Cualquier URL absoluta: no hay ruta. Escenario "sin Internet".
  peticionesDirectas++;
  throw new TypeError('fetch failed');
};

/* ═══════════════════ Cargar el motor real ═══════════════════ */

const fuente = await readFile(join(PUBLICO, 'app.js'), 'utf8');
const reescrita = fuente
  .replace(/from '\.\/vendor\//g, `from '${pathToFileURL(join(PUBLICO, 'vendor')).href}/`)
  .replace(/from \.\.\/vendor/g, '');

const moduloTemp = join(RAIZ, 'test', '_app-bajo-prueba.mjs');
const { writeFile } = await import('node:fs/promises');
await writeFile(moduloTemp, reescrita, 'utf8');

let errorArranque = null;
try {
  await import(pathToFileURL(moduloTemp).href);
} catch (err) {
  errorArranque = err;
}

// Deja que las capas asíncronas terminen.
await new Promise((r) => setTimeout(r, 2500));

/* ═══════════════════ Comprobaciones ═══════════════════ */

const { estado } = await import(pathToFileURL(moduloTemp).href).then(
  () => ({ estado: null }),
  () => ({ estado: null })
);

let fallos = 0;
const comprobar = (titulo, condicion, extra = '') => {
  const marca = condicion ? '✔' : '✗';
  if (!condicion) fallos++;
  console.log(`  ${marca} ${titulo}${extra ? `  → ${extra}` : ''}`);
};

console.log('\n── PRUEBA DE HUMO · Ojo de Dios ──\n');

comprobar('el motor arranca sin excepciones', !errorArranque,
  errorArranque ? errorArranque.message : '');

comprobar('se pidieron instantáneas locales de respaldo', peticionesSnapshot > 0,
  `${peticionesSnapshot} lecturas de /data`);

comprobar('se intentaron rutas directa y proxy antes del respaldo',
  peticionesDirectas > 0 || peticionesProxy > 0,
  `${peticionesDirectas} directas · ${peticionesProxy} por proxy`);

comprobar('se dibujaron marcadores en el mapa', registro.marcadores.length > 0,
  `${registro.marcadores.length} marcadores`);

comprobar('se pintaron capas de mosaicos', registro.tiles > 0, `${registro.tiles} capas de tiles`);

comprobar('la cinta de eventos se rellenó', elementos.get('cinta-contenido').innerHTML.length > 0);

comprobar('el contador de objetos se actualizó',
  elementos.get('contador-objetos').textContent !== '0',
  elementos.get('contador-objetos').textContent);

const listaCapas = elementos.get('lista-capas').innerHTML;
comprobar('el panel de capas se generó', listaCapas.includes('data-capa="camaras"'));
for (const id of ['camaras', 'vuelos', 'barcos', 'satelites', 'sismos', 'radar', 'gibs']) {
  comprobar(`  fila de capa presente: ${id}`, listaCapas.includes(`data-capa="${id}"`));
}

const conPosicionReal = registro.posiciones.filter((p) => Math.abs(p[0]) > 0.1 || Math.abs(p[1]) > 0.1);
comprobar('los satélites tienen posición orbital calculada', conPosicionReal.length > 5,
  `${conPosicionReal.length} objetos con coordenadas distintas de 0,0`);

comprobar('la propagación SGP4 da latitudes válidas',
  registro.posiciones.every((p) => Math.abs(p[0]) <= 90 && Math.abs(p[1]) <= 180));

comprobar('el detalle quedó cerrado al inicio',
  elementos.get('panel-detalle').classList.contains('oculto'));

/* ═══════════════════ Resultado ═══════════════════ */

await import('node:fs/promises').then((fs) => fs.unlink(moduloTemp).catch(() => {}));

console.log(
  fallos === 0
    ? '\n  TODO CORRECTO — el motor arranca y degrada a respaldo sin red.\n'
    : `\n  ${fallos} COMPROBACIÓN(ES) FALLIDA(S)\n`
);
process.exit(fallos === 0 ? 0 : 1);
