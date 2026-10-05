/**
 * OJO DE DIOS — motor de la aplicación
 * ---------------------------------------------------------------------------
 * Ensambla feeds públicos abiertos sobre un mapa. Nada aquí es intrusión:
 * cada capa consume un servicio que su operador publica deliberadamente.
 *
 * Estrategia de datos por capa, en orden:
 *   1. petición directa desde el navegador  → estado VIVO
 *   2. proxy del servidor (/api/proxy)      → estado PROXY (cuando hay CORS o sin salida a Internet)
 *   3. instantánea local en /data/*.json    → estado RESPALDO
 *
 * Dentro de la app de Android no hay servidor Node: el navegador embebido pasa
 * por CapacitorHttp, que va directo a la red y esquiva CORS, así que la vía 1
 * funciona sola y las vías 2 y 3 quedan como red de seguridad.
 */

import { twoline2satrec } from './vendor/satellite/io.js';
import { propagate, gstime } from './vendor/satellite/propagation.js';
import { eciToGeodetic, degreesLat, degreesLong } from './vendor/satellite/transforms.js';

/* ═══════════════════════════════════════════════════════════════════════════
   1. UTILIDADES
   ═══════════════════════════════════════════════════════════════════════════ */

/* ═══════════════════════════════════════════════════════════════════════════
   0. PLATAFORMA
   ═══════════════════════════════════════════════════════════════════════════ */

/** true cuando corremos empaquetados como app nativa (Android/iOS vía Capacitor). */
const ES_APP = Boolean(
  globalThis.Capacitor?.isNativePlatform?.() ||
  globalThis.location?.protocol === 'capacitor:' ||
  (typeof globalThis.Capacitor?.getPlatform === 'function' && globalThis.location?.hostname === 'localhost')
);

/** true cuando hay un servidor Node detrás sirviendo /api/*. */
const HAY_SERVIDOR = !ES_APP;

// Android WebView anterior a Chrome 103 no implementa AbortSignal.timeout.
// Sin esto, toda petición fallaría y la app se quedaría siempre en modo respaldo.
if (typeof AbortSignal.timeout !== 'function') {
  AbortSignal.timeout = (ms) => {
    const controlador = new AbortController();
    setTimeout(() => controlador.abort(), ms);
    return controlador.signal;
  };
}

const $ = (sel) => document.querySelector(sel);

const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
  );

const pad = (n) => String(n).padStart(2, '0');

const horaUTC = (d = new Date()) =>
  `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;

function hace(ms) {
  const s = Math.round((Date.now() - ms) / 1000);
  if (s < 60) return `hace ${s} s`;
  if (s < 3600) return `hace ${Math.round(s / 60)} min`;
  if (s < 86400) return `hace ${Math.round(s / 3600)} h`;
  return `hace ${Math.round(s / 86400)} d`;
}

const fechaISO = (desplazamientoDias = 0) => {
  const d = new Date(Date.now() - desplazamientoDias * 86400_000);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
};

/** Petición con tiempo límite y sin romper la app si falla. */
async function pedir(url, ms = 9000, tipo = 'json') {
  const r = await fetch(url, { signal: AbortSignal.timeout(ms) });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return tipo === 'json' ? r.json() : r.text();
}

/**
 * Obtiene datos intentando las tres vías.
 * @returns {Promise<{datos:any, origen:'vivo'|'proxy'|'respaldo'}>}
 */
async function obtener(url, { snapshot = null, ms = 9000 } = {}) {
  let ultimoError;

  // Vía 1: directa
  try {
    return { datos: await pedir(url, ms), origen: 'vivo' };
  } catch (err) {
    ultimoError = err;
  }

  // Vía 2: proxy del servidor (no existe dentro de la app nativa)
  if (HAY_SERVIDOR) {
    try {
      const datos = await pedir(`/api/proxy?url=${encodeURIComponent(url)}`, ms + 4000);
      return { datos, origen: 'proxy' };
    } catch (err) {
      ultimoError = err;
    }
  }

  // Vía 3: instantánea local
  if (snapshot) {
    try {
      const datos = await pedir(`./data/${snapshot}.json`, 6000);
      return { datos, origen: 'respaldo' };
    } catch (err) {
      ultimoError = err;
    }
  }

  throw ultimoError ?? new Error('sin datos');
}

/* ═══════════════════════════════════════════════════════════════════════════
   2. ESTADO GLOBAL
   ═══════════════════════════════════════════════════════════════════════════ */

const estado = {
  capas: {},          // id → controlador de capa
  reloj: null,
  eventos: [],        // para la cinta inferior
  contador: 0,
};

/* ═══════════════════════════════════════════════════════════════════════════
   3. MAPA Y FONDOS
   ═══════════════════════════════════════════════════════════════════════════ */

const mapa = L.map('mapa', {
  center: [35, 5],
  zoom: 3,
  zoomControl: true,
  worldCopyJump: true,
  minZoom: 2,
  maxZoom: 19,
  preferCanvas: true,
});

const FONDOS = {
  'carto-oscuro': {
    url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
    atribucion:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · &copy; <a href="https://carto.com/attributions">CARTO</a>',
    subdominios: 'abcd',
  },
  'carto-claro': {
    url: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
    atribucion: '&copy; OpenStreetMap · &copy; CARTO',
    subdominios: 'abcd',
  },
  'esri-satelite': {
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    atribucion:
      'Imágenes: Esri, Maxar, Earthstar Geographics, USDA FSA, USGS, Aerogrid, IGN, IGP y la comunidad GIS',
    maxZoom: 19,
  },
  osm: {
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    atribucion: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 19,
  },
};

let capaFondo = null;

/**
 * Densidad según el zoom.
 * A escala mundial, 36 cámaras sobre Londres son un borrón ilegible: se ocultan
 * hasta que el usuario se acerca. Igual con las etiquetas de los aviones.
 */
function ajustarDensidad() {
  const contenedor = document.getElementById('mapa');
  if (!contenedor) return;
  const z = mapa.getZoom();
  contenedor.classList.toggle('zoom-lejos', z < 5);
  contenedor.classList.toggle('zoom-medio', z < 7);
}

mapa.on('zoomend', ajustarDensidad);

function ponerFondo(clave) {
  const cfg = FONDOS[clave] ?? FONDOS['carto-oscuro'];
  if (capaFondo) mapa.removeLayer(capaFondo);
  capaFondo = L.tileLayer(cfg.url, {
    attribution: cfg.atribucion,
    subdomains: cfg.subdominios ?? 'abc',
    maxZoom: cfg.maxZoom ?? 19,
  }).addTo(mapa);
  capaFondo.bringToBack();
}

ponerFondo('carto-oscuro');

/* ═══════════════════════════════════════════════════════════════════════════
   4. INFRAESTRUCTURA DE CAPAS
   ═══════════════════════════════════════════════════════════════════════════ */

const GRUPOS = {
  ojo:       'Observación directa',
  vuelo:     'Aire y mar',
  orbita:    'Órbita',
  atmosfera: 'Atmósfera y terreno',
};

/**
 * Registra una capa con su comportamiento.
 */
function crearCapa({ id, nombre, glifo, grupo, descripcion, activa = false, alActivar, alDesactivar, alRefrescar }) {
  const grupoLeaflet = L.layerGroup();
  const capa = {
    id, nombre, glifo, grupo, descripcion,
    activa: false,
    origen: 'cargando',
    detalle: '',
    grupoLeaflet,
    async activar() {
      this.activa = true;
      this.grupoLeaflet.addTo(mapa);
      this.pintarEstado('cargando', 'cargando');
      try {
        if (alActivar) await alActivar(this);
      } catch (err) {
        this.pintarEstado('fallo', 'sin datos');
        this.detalle = err.message;
        console.warn(`[capa ${id}]`, err.message);
      }
    },
    desactivar() {
      this.activa = false;
      mapa.removeLayer(this.grupoLeaflet);
      this.grupoLeaflet.clearLayers();
      if (alDesactivar) alDesactivar(this);
    },
    async refrescar() {
      if (!this.activa) return;
      this.pintarEstado('cargando', 'cargando');
      try {
        if (alRefrescar) await alRefrescar(this);
        else if (alActivar) await alActivar(this);
      } catch (err) {
        this.pintarEstado('fallo', 'sin datos');
        this.detalle = err.message;
      }
    },
    pintarEstado(estadoClase, texto) {
      // El panel puede no estar pintado todavía: no es motivo para romper la capa.
      const fila = document.querySelector(`.capa[data-capa="${id}"]`);
      const el = fila?.querySelector('.capa-estado');
      if (el) {
        el.className = `capa-estado ${estadoClase}`;
        el.textContent = texto ?? estadoClase.toUpperCase();
      }
      if (estadoClase === 'vivo' || estadoClase === 'proxy' || estadoClase === 'respaldo') {
        this.origen = estadoClase;
      }
    },
    // Contabiliza los objetos que la capa acaba de dibujar.
    contar(n) {
      this._n = n;
      actualizarContador();
    },
  };
  estado.capas[id] = capa;
  return capa;
}

function actualizarContador() {
  let total = 0;
  for (const c of Object.values(estado.capas)) {
    if (c.activa && typeof c._n === 'number') total += c._n;
  }
  $('#contador-objetos').textContent = total.toLocaleString('es-ES');
}

function etiquetaOrigen(origen) {
  return { vivo: 'EN VIVO', proxy: 'VÍA PROXY', respaldo: 'RESPALDO', cargando: 'cargando' }[origen] ?? origen;
}

function claseOrigen(origen) {
  return { cargando: 'cargando', vivo: 'vivo', proxy: 'proxy', respaldo: 'respaldo' }[origen] ?? 'fallo';
}

/* ═══════════════════════════════════════════════════════════════════════════
   5. CAPA · CÁMARAS PÚBLICAS
   ═══════════════════════════════════════════════════════════════════════════ */

const iconoCamara = (disponible = true) =>
  L.divIcon({
    className: '',
    html: `<div class="marcador-camara ${disponible ? '' : 'inactiva'}" title="Cámara pública">◉</div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });

const TFL_IMG = (id) => `https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/${id}.jpg`;
const TFL_VID = (id) => `https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/${id}.mp4`;
const FI_IMG = (preset) => `https://weathercam.digitraffic.fi/${preset}.jpg`;

function normalizarTfL(places) {
  if (!Array.isArray(places)) return [];
  return places.map((p) => {
    const props = {};
    for (const a of p.additionalProperties || []) props[a.key] = a.value;
    const id = String(p.id).replace('JamCams_', '');
    return {
      id,
      nombre: p.commonName,
      lat: p.lat,
      lon: p.lon,
      vista: props.view || '',
      imagen: props.imageUrl || TFL_IMG(id),
      video: props.videoUrl || TFL_VID(id),
      disponible: props.available !== 'false',
      fuente: 'TfL · Londres',
      fuenteUrl: 'https://api.tfl.gov.uk/Place/Type/JamCam',
    };
  });
}

function normalizarTfLRespaldo(snap) {
  return (snap.camaras || []).map((c) => ({
    ...c,
    imagen: TFL_IMG(c.id),
    video: TFL_VID(c.id),
    fuente: 'TfL · Londres',
    fuenteUrl: 'https://api.tfl.gov.uk/Place/Type/JamCam',
  }));
}

function normalizarFinlandia(geojson) {
  const features = geojson?.features ?? [];
  return features.map((f) => {
    const [lon, lat] = f.geometry.coordinates;
    const presets = (f.properties.presets || []).filter((p) => p.inCollection !== false).map((p) => p.id);
    return {
      id: f.properties.id,
      nombre: f.properties.name,
      lat, lon,
      vista: '',
      presets,
      presetActual: presets[0] ?? null,
      imagen: presets[0] ? FI_IMG(presets[0]) : null,
      video: null,
      disponible: true,
      fuente: 'Fintraffic · Finlandia',
      fuenteUrl: 'https://tie.digitraffic.fi/api/weathercam/v1/stations',
    };
  });
}

function normalizarFinlandiaRespaldo(snap) {
  return (snap.camaras || []).map((c) => {
    const presets = c.presets || [];
    return {
      ...c,
      presets,
      presetActual: presets[0] ?? null,
      imagen: presets[0] ? FI_IMG(presets[0]) : null,
      video: null,
      disponible: true,
      fuente: 'Fintraffic · Finlandia',
      fuenteUrl: 'https://tie.digitraffic.fi/api/weathercam/v1/stations',
    };
  });
}

const capaCamaras = crearCapa({
  id: 'camaras',
  nombre: 'Cámaras públicas',
  glifo: '◉',
  grupo: 'ojo',
  descripcion: 'Tráfico oficial: Londres y Finlandia',
  async alActivar(capa) {
    capa.grupoLeaflet.clearLayers();
    const camaras = [];
    const detalles = [];

    // ── TfL Londres ──────────────────────────────────────────────
    try {
      const r = await obtener('https://api.tfl.gov.uk/Place/Type/JamCam', { snapshot: 'cams-london' });
      const lista = r.origen === 'respaldo' ? normalizarTfLRespaldo(r.datos) : normalizarTfL(r.datos);
      camaras.push(...lista);
      detalles.push(`Londres: ${lista.length} (${etiquetaOrigen(r.origen)})`);
    } catch (err) {
      detalles.push(`Londres: sin datos (${err.message})`);
    }

    // ── Fintraffic Finlandia ─────────────────────────────────────
    try {
      const r = await obtener('https://tie.digitraffic.fi/api/weathercam/v1/stations', { snapshot: 'cams-finland' });
      const lista = r.origen === 'respaldo' ? normalizarFinlandiaRespaldo(r.datos) : normalizarFinlandia(r.datos);
      camaras.push(...lista);
      detalles.push(`Finlandia: ${lista.length} (${etiquetaOrigen(r.origen)})`);
    } catch (err) {
      detalles.push(`Finlandia: sin datos (${err.message})`);
    }

    if (!camaras.length) throw new Error('ninguna fuente de cámaras respondió');

    capa.camaras = camaras;
    capa.detalle = detalles.join(' · ');

    for (const c of camaras) {
      const marcador = L.marker([c.lat, c.lon], {
        icon: iconoCamara(c.disponible),
        title: c.nombre,
      });
      marcador.on('click', () => mostrarDetalleCamara(c, capa));
      marcador.addTo(capa.grupoLeaflet);
    }

    capa.contar(camaras.length);

    const mejor = detalles.some((d) => d.includes('EN VIVO'))
      ? 'vivo'
      : detalles.some((d) => d.includes('PROXY'))
        ? 'proxy'
        : 'respaldo';
    capa.pintarEstado(mejor, `${camaras.length} cams`);
  },
});

/* ── Ficha de detalle de una cámara ─────────────────────────────── */
let temporizadorCamara = null;

function mostrarDetalleCamara(camara, capa) {
  clearInterval(temporizadorCamara);
  abrirDetalle();

  const pintar = () => {
    const marca = camara.disponible ? '' : ' · <span style="color:var(--rojo)">no disponible ahora</span>';
    const esVideo = camara.video && camara._modo === 'video';

    const media = esVideo
      ? `<video class="tarjeta-media" id="video-camara" src="${esc(camara.video)}" autoplay muted loop playsinline></video>`
      : camara.imagen
        ? `<img class="tarjeta-media" id="img-camara" src="${esc(camara.imagen)}" alt="Vista de la cámara ${esc(camara.nombre)}">`
        : `<div class="aviso-media">Esta cámara no publica imagen.</div>`;

    const controles = [];
    if (camara.video) {
      controles.push(`<button data-modo="imagen" class="${esVideo ? '' : 'activo'}">Fotograma</button>`);
      controles.push(`<button data-modo="video" class="${esVideo ? 'activo' : ''}">Vídeo</button>`);
    }
    if (camara.presets && camara.presets.length > 1) {
      for (const p of camara.presets) {
        controles.push(
          `<button data-preset="${esc(p)}" class="${p === camara.presetActual ? 'activo' : ''}">${esc(p.slice(-2))}</button>`
        );
      }
    }

    $('#detalle-titulo').textContent = camara.nombre;
    $('#detalle-cuerpo').innerHTML = `
      <div class="tarjeta">
        <span class="etiqueta">${esc(camara.fuente)}${marca}</span>
        ${media}
        ${controles.length ? `<div class="miniaturas">${controles.join('')}</div>` : ''}
      </div>
      <dl class="datos">
        <dt>Vista</dt><dd>${esc(camara.vista || 'no declarada')}</dd>
        <dt>Coordenadas</dt><dd>${camara.lat.toFixed(5)}, ${camara.lon.toFixed(5)}</dd>
        <dt>Identificador</dt><dd>${esc(camara.id)}</dd>
        <dt>Fuente</dt><dd>${esc(camara.fuente)}</dd>
      </dl>
      <a class="enlace-fuente" href="${esc(camara.fuenteUrl)}" target="_blank" rel="noopener">Ver el feed original ↗</a>
      <p style="font-size:11px;color:var(--texto-tenue);margin-top:12px;line-height:1.55">
        El fotograma se actualiza solo cada pocos minutos: no es un vídeo continuo.
        Esta cámara la publica su operador para informar del tráfico.
      </p>
    `;

    $('#detalle-cuerpo').querySelectorAll('[data-modo]').forEach((b) =>
      b.addEventListener('click', () => {
        camara._modo = b.dataset.modo;
        pintar();
      })
    );
    $('#detalle-cuerpo').querySelectorAll('[data-preset]').forEach((b) =>
      b.addEventListener('click', () => {
        camara.presetActual = b.dataset.preset;
        camara.imagen = FI_IMG(camara.presetActual);
        pintar();
      })
    );

    // Si el clip no carga, se avisa en lugar de dejar un marco negro mudo.
    const video = document.getElementById('video-camara');
    if (video) {
      video.addEventListener('error', () => {
        video.outerHTML =
          '<div class="aviso-media">El vídeo no cargó. TfL solo publica clips cortos y rota los archivos; prueba con el fotograma.</div>';
      });
    }

    if (!esVideo && camara.imagen) {
      clearInterval(temporizadorCamara);
      temporizadorCamara = setInterval(() => {
        const img = document.getElementById('img-camara');
        if (!img) return clearInterval(temporizadorCamara);
        img.src = `${camara.imagen}?t=${Date.now()}`;
      }, 30_000);
    }
  };

  camara._modo ??= 'imagen';
  pintar();
}

/* ═══════════════════════════════════════════════════════════════════════════
   6. CAPA · SATÉLITES (cálculo orbital local con SGP4)
   ═══════════════════════════════════════════════════════════════════════════ */

let satelites = [];
let temporizadorSatelites = null;

const iconoSatelite = (destacado) =>
  L.divIcon({
    className: '',
    html: `<div class="marcador-satelite ${destacado ? 'destacado' : ''}">▮</div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });

function posicionDe(satrec, fecha) {
  const pv = propagate(satrec, fecha);
  if (!pv || !pv.position || typeof pv.position === 'boolean') return null;
  const g = eciToGeodetic(pv.position, gstime(fecha));
  const lat = degreesLat(g.latitude);
  const lon = degreesLong(g.longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  return { lat, lon, alt: g.height, v: pv.velocity };
}

function velocidadKmS(v) {
  if (!v) return null;
  return Math.hypot(v.x, v.y, v.z);
}

/** Traza orbital de las próximas ~1,7 h, partiendo en el antimeridiano. */
function trazaOrbital(satrec, minutos = 100) {
  const segmentos = [];
  let actual = [];
  const t0 = Date.now();
  for (let m = 0; m <= minutos; m += 1.5) {
    const p = posicionDe(satrec, new Date(t0 + m * 60_000));
    if (!p) continue;
    if (actual.length && Math.abs(p.lon - actual[actual.length - 1][1]) > 180) {
      segmentos.push(actual);
      actual = [];
    }
    actual.push([p.lat, p.lon]);
  }
  if (actual.length) segmentos.push(actual);
  return segmentos;
}

const capaSatelites = crearCapa({
  id: 'satelites',
  nombre: 'Satélites',
  glifo: '▮',
  grupo: 'orbita',
  descripcion: 'Posición calculada en tu navegador con SGP4',
  async alActivar(capa) {
    if (!satelites.length) {
      const r = await obtener('./data/tle.json', { ms: 5000 });
      const lista = (r.datos.satelites || []).map((s) => {
        try {
          return { ...s, satrec: twoline2satrec(s.l1, s.l2), marcador: null };
        } catch {
          return null;
        }
      }).filter(Boolean);
      satelites = lista;
      capa.detalle = `${lista.length} objetos con elementos orbitales CelesTrak`;
    }

    capa.grupoLeaflet.clearLayers();

    for (const s of satelites) {
      const destacado = s.tipo === 'estacion' || s.tipo === 'tripulada';
      s.destacado = destacado;
      const m = L.marker([0, 0], { icon: iconoSatelite(destacado), title: s.nombre, zIndexOffset: 500 });
      m.on('click', () => mostrarDetalleSatelite(s));
      m.addTo(capa.grupoLeaflet);
      s.marcador = m;

      if (destacado && capa.mostrarTrazas !== false) {
        s.trazas = trazaOrbital(s.satrec).map((seg) =>
          L.polyline(seg, {
            color: '#a78bfa', weight: 1, opacity: 0.35, dashArray: '4 5', interactive: false,
          }).addTo(capa.grupoLeaflet)
        );
      }
    }

    const latido = () => {
      if (!capa.activa) return;
      const ahora = new Date();
      for (const s of satelites) {
        const p = posicionDe(s.satrec, ahora);
        if (!p || !s.marcador) continue;
        s.lat = p.lat; s.lon = p.lon; s.alt = p.alt; s.vel = velocidadKmS(p.v);
        s.marcador.setLatLng([p.lat, p.lon]);
      }
      if (detalleAbiertoSatelite) refrescarDetalleSatelite();
    };

    latido();
    clearInterval(temporizadorSatelites);
    temporizadorSatelites = setInterval(latido, 1000);

    capa.contar(satelites.length);
    capa.pintarEstado('vivo', `${satelites.length} sats`);
  },
  desactivar(capa) {
    clearInterval(temporizadorSatelites);
    temporizadorSatelites = null;
    satelites.forEach((s) => { s.marcador = null; s.trazas = null; });
  },
});

let detalleAbiertoSatelite = null;

function mostrarDetalleSatelite(sat) {
  detalleAbiertoSatelite = sat;
  abrirDetalle();
  refrescarDetalleSatelite(true);
}

function refrescarDetalleSatelite(completo = false) {
  const s = detalleAbiertoSatelite;
  if (!s || !s.lat) return;

  if (!completo && document.getElementById('sat-pos')) {
    document.getElementById('sat-pos').textContent = `${s.lat.toFixed(3)}°, ${s.lon.toFixed(3)}°`;
    document.getElementById('sat-alt').textContent = `${s.alt.toFixed(1)} km`;
    if (s.vel) document.getElementById('sat-vel').textContent = `${s.vel.toFixed(2)} km/s`;
    return;
  }

  const tipo = {
    estacion: 'Estación espacial tripulada',
    tripulada: 'Nave tripulada',
    carga: 'Nave de carga',
    meteorologico: 'Satélite meteorológico (geoestacionario)',
    observacion: 'Satélite de observación terrestre',
  }[s.tipo] ?? s.tipo;

  $('#detalle-titulo').textContent = s.nombre;
  $('#detalle-cuerpo').innerHTML = `
    <div class="tarjeta">
      <span class="etiqueta">${esc(tipo)}</span>
      <div class="aviso-media" style="padding:16px 12px;text-align:left">
        Este objeto <strong>no</strong> envía vídeo. Su posición se calcula aquí mismo
        propagando los elementos orbitales que publica CelesTrak con el modelo SGP4.
      </div>
    </div>
    <dl class="datos">
      <dt>Posición</dt><dd id="sat-pos">${s.lat.toFixed(3)}°, ${s.lon.toFixed(3)}°</dd>
      <dt>Altitud</dt><dd id="sat-alt">${s.alt.toFixed(1)} km</dd>
      <dt>Velocidad</dt><dd id="sat-vel">${s.vel ? s.vel.toFixed(2) + ' km/s' : '—'}</dd>
      <dt>NORAD</dt><dd>${esc(s.norad ?? '—')}</dd>
    </dl>
    <a class="enlace-fuente" href="https://celestrak.org/NORAD/elements/" target="_blank" rel="noopener">
      Elementos orbitales en CelesTrak ↗</a>
    <p style="font-size:11px;color:var(--texto-tenue);margin-top:12px;line-height:1.55">
      Datos orbitales públicos del 18.º Escuadrón de Defensa Espacial (EE. UU.).
      La precisión típica de SGP4 es de pocos kilómetros; empeora según envejece el TLE.
    </p>
  `;
}

/* ═══════════════════════════════════════════════════════════════════════════
   7. CAPA · VUELOS (ADS-B)
   ═══════════════════════════════════════════════════════════════════════════ */

const capaVuelos = crearCapa({
  id: 'vuelos',
  nombre: 'Vuelos (ADS-B)',
  glifo: '✈',
  grupo: 'vuelo',
  descripcion: 'Posiciones de aeronaves alrededor de la vista',
  async alActivar(capa) {
    await cargarVuelos(capa);
  },
});

async function cargarVuelos(capa) {
  const c = mapa.getCenter();
  const b = mapa.getBounds();
  const radioGrados = Math.max(b.getNorth() - b.getSouth(), b.getEast() - b.getWest()) / 2;
  const radioMn = Math.min(250, Math.max(40, Math.round(radioGrados * 60 * 0.55)));

  const centros = [[c.lat, c.lng]];

  capa.grupoLeaflet.clearLayers();
  let total = 0;
  let origenFinal = 'vivo';

  for (const [lat, lon] of centros) {
    try {
      const r = await obtener(
        `https://api.airplanes.live/v2/point/${lat.toFixed(4)}/${lon.toFixed(4)}/${radioMn}`
      );
      origenFinal = origenFinal === 'vivo' ? r.origen : origenFinal;
      const aviones = (r.datos.ac || []).filter((a) => typeof a.lat === 'number' && typeof a.lon === 'number');

      for (const a of aviones) {
        const rumbo = a.track ?? a.true_heading ?? 0;
        const icono = L.divIcon({
          className: '',
          html: `<div class="marcador-avion" style="transform:rotate(${rumbo}deg)">✈</div>
                 <div class="etiqueta-mapa punto-avion">${esc((a.flight || a.hex || '').trim())}</div>`,
          iconSize: [20, 20],
          iconAnchor: [10, 10],
        });
        L.marker([a.lat, a.lon], { icon: icono })
          .on('click', () => mostrarDetalleAvion(a))
          .addTo(capa.grupoLeaflet);
        total++;
      }
    } catch (err) {
      if (total === 0) throw err;
    }
  }

  capa.contar(total);
  capa.detalle = `${total} aeronaves en un radio de ${radioMn} mn alrededor del centro`;
  capa.pintarEstado(origenFinal, `${total} aero`);
}

function mostrarDetalleAvion(a) {
  abrirDetalle();
  const altitud = a.alt_baro === 'ground' ? 'en tierra' : `${a.alt_baro ?? a.alt_geom ?? '—'} ft`;
  $('#detalle-titulo').textContent = (a.flight || a.hex || 'Aeronave').trim();
  $('#detalle-cuerpo').innerHTML = `
    <div class="tarjeta">
      <span class="etiqueta">ADS-B · emisión en abierto</span>
      <div class="aviso-media" style="padding:16px 12px;text-align:left">
        Las aeronaves transmiten su posición por radio a 1090 MHz. Cualquiera con un
        receptor barato la recibe. No es una filtración: es una emisión deliberada
        por seguridad aérea.
      </div>
    </div>
    <dl class="datos">
      <dt>Matrícula</dt><dd>${esc(a.r || '—')}</dd>
      <dt>Modelo</dt><dd>${esc(a.t || '—')}</dd>
      <dt>Altitud</dt><dd>${esc(altitud)}</dd>
      <dt>Velocidad</dt><dd>${a.gs ? a.gs + ' nudos' : '—'}</dd>
      <dt>Rumbo</dt><dd>${a.track != null ? a.track + '°' : '—'}</dd>
      <dt>Identificador</dt><dd>${esc(a.hex || '—')}</dd>
      <dt>Posición</dt><dd>${a.lat.toFixed(4)}, ${a.lon.toFixed(4)}</dd>
    </dl>
    <p style="font-size:11px;color:var(--texto-tenue);margin-top:12px;line-height:1.55">
      Si eres propietario y no quieres aparecer, existen programas de filtrado de
      matrícula (PIA/LADD) que puedes solicitar a la autoridad aeronáutica.
    </p>
  `;
}

/* ═══════════════════════════════════════════════════════════════════════════
   8. CAPA · BARCOS (AIS)
   ═══════════════════════════════════════════════════════════════════════════ */

const capaBarcos = crearCapa({
  id: 'barcos',
  nombre: 'Barcos (AIS)',
  glifo: '⚓',
  grupo: 'vuelo',
  descripcion: 'Tráfico marítimo del mar Báltico',
  async alActivar(capa) {
    await cargarBarcos(capa);
  },
});

async function cargarBarcos(capa) {
  const r = await obtener('https://meri.digitraffic.fi/api/ais/v1/locations');
  const features = r.datos?.features ?? [];
  capa.grupoLeaflet.clearLayers();

  let total = 0;
  for (const f of features) {
    const [lon, lat] = f.geometry?.coordinates ?? [];
    if (typeof lat !== 'number' || typeof lon !== 'number') continue;
    const p = f.properties || {};
    const icono = L.divIcon({
      className: '',
      html: `<div class="marcador-barco" style="transform:rotate(${p.cog ?? 0}deg)">▲</div>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });
    L.marker([lat, lon], { icon: icono })
      .on('click', () => mostrarDetalleBarco(p, lat, lon))
      .addTo(capa.grupoLeaflet);
    total++;
  }

  capa.contar(total);
  capa.detalle = `${total} buques con AIS en el Báltico`;
  capa.pintarEstado(r.origen, `${total} barcos`);
}

const NAV = {
  0: 'en navegación a motor', 1: 'fondeado', 2: 'sin gobierno', 3: 'con maniobra restringida',
  4: 'restringido por calado', 5: 'amarrado', 6: 'varado', 7: 'pescando', 8: 'a vela',
  9: 'reservado', 15: 'indefinido',
};

function mostrarDetalleBarco(p, lat, lon) {
  abrirDetalle();
  $('#detalle-titulo').textContent = p.name || `MMSI ${p.mmsi}`;
  $('#detalle-cuerpo').innerHTML = `
    <div class="tarjeta">
      <span class="etiqueta">AIS · emisión obligatoria</span>
      <div class="aviso-media" style="padding:16px 12px;text-align:left">
        Los buques grandes están obligados por convenio internacional a emitir su
        posición por radio. Es tráfico público, igual que el ADS-B de los aviones.
      </div>
    </div>
    <dl class="datos">
      <dt>MMSI</dt><dd>${esc(p.mmsi ?? '—')}</dd>
      <dt>Estado</dt><dd>${esc(NAV[p.navStat] ?? p.navStat ?? '—')}</dd>
      <dt>Velocidad</dt><dd>${p.sog != null ? p.sog + ' nudos' : '—'}</dd>
      <dt>Rumbo</dt><dd>${p.cog != null ? p.cog + '°' : '—'}</dd>
      <dt>Destino</dt><dd>${esc(p.destination || '—')}</dd>
      <dt>Tipo</dt><dd>${esc(p.shipType ?? '—')}</dd>
      <dt>Posición</dt><dd>${lat.toFixed(4)}, ${lon.toFixed(4)}</dd>
    </dl>
    <a class="enlace-fuente" href="https://www.digitraffic.fi/en/marine-traffic/" target="_blank" rel="noopener">
      Digitraffic Marine ↗</a>
  `;
}

/* ═══════════════════════════════════════════════════════════════════════════
   9. CAPA · SISMOS
   ═══════════════════════════════════════════════════════════════════════════ */

function colorMagnitud(m) {
  if (m >= 6) return '#ef4444';
  if (m >= 5) return '#f97316';
  if (m >= 4) return '#f59e0b';
  if (m >= 3) return '#eab308';
  if (m >= 2) return '#84cc16';
  return '#22d3ee';
}

const capaSismos = crearCapa({
  id: 'sismos',
  nombre: 'Sismos',
  glifo: '◆',
  grupo: 'atmosfera',
  descripcion: 'Últimas 24 h, USGS en tiempo casi real',
  async alActivar(capa) {
    const r = await obtener('https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson', {
      snapshot: 'quakes',
    });
    const sismos = r.datos?.features ?? [];
    capa.grupoLeaflet.clearLayers();

    let max = 0;
    for (const s of sismos) {
      const [lon, lat, prof] = s.geometry?.coordinates ?? [];
      const m = s.properties?.mag ?? 0;
      max = Math.max(max, m);
      const radio = Math.max(4, (m ?? 0) * 2.6);
      L.circleMarker([lat, lon], {
        radius: radio,
        color: colorMagnitud(m),
        weight: 1.4,
        fillColor: colorMagnitud(m),
        fillOpacity: 0.22,
      })
        .bindPopup(
          `<strong>${esc(s.properties.place)}</strong><br>
           Magnitud ${m} · profundidad ${prof} km<br>
           ${new Date(s.properties.time).toISOString().replace('T', ' ').slice(0, 16)} UTC<br>
           <a href="${esc(s.properties.url)}" target="_blank" rel="noopener">Ficha USGS ↗</a>`
        )
        .addTo(capa.grupoLeaflet);

      if (m >= 4.5) {
        estado.eventos.push({ t: s.properties.time, texto: `Sismo M${m} — ${s.properties.place}` });
      }
    }

    capa.contar(sismos.length);
    capa.detalle = `${sismos.length} sismos en 24 h · el mayor, M${max.toFixed(1)}`;
    capa.pintarEstado(r.origen, `${sismos.length} sismos`);
    pintarCinta();
  },
});

/* ═══════════════════════════════════════════════════════════════════════════
   10. CAPA · RADAR METEOROLÓGICO (RainViewer)
   ═══════════════════════════════════════════════════════════════════════════ */

let animadorRadar = null;

const capaRadar = crearCapa({
  id: 'radar',
  nombre: 'Radar de lluvia',
  glifo: '☂',
  grupo: 'atmosfera',
  descripcion: 'Mosaico de radares, animado 2 h atrás',
  async alActivar(capa) {
    const r = await obtener('https://api.rainviewer.com/public/weather-maps.json');
    const { host, radar } = r.datos;
    const cuadros = [...(radar?.past ?? []).slice(-8), ...(radar?.nowcast ?? []).slice(0, 2)];
    if (!cuadros.length) throw new Error('RainViewer no devolvió fotogramas');

    capa.grupoLeaflet.clearLayers();
    const capas = cuadros.map((f, i) =>
      L.tileLayer(`${host}${f.path}/256/{z}/{x}/{y}/4/1_1.png`, {
        opacity: 0, zIndex: 300 + i, attribution: 'Radar: RainViewer',
      }).addTo(capa.grupoLeaflet)
    );

    let i = 0;
    const paso = () => {
      capas.forEach((c, j) => c.setOpacity(j === i ? 0.62 : 0));
      i = (i + 1) % capas.length;
    };
    paso();
    clearInterval(animadorRadar);
    animadorRadar = setInterval(() => { if (capa.activa) paso(); }, 700);

    capa.contar(1);
    capa.detalle = `${cuadros.length} fotogramas en bucle`;
    capa.pintarEstado(r.origen, 'animado');
  },
  desactivar() {
    clearInterval(animadorRadar);
    animadorRadar = null;
  },
});

/* ═══════════════════════════════════════════════════════════════════════════
   11. CAPA · IMAGEN SATELITAL NASA (GIBS)
   ═══════════════════════════════════════════════════════════════════════════ */

const capaGibs = crearCapa({
  id: 'gibs',
  nombre: 'Imagen satelital NASA',
  glifo: '🛰',
  grupo: 'atmosfera',
  descripcion: 'Color verdadero, ~1 día de antigüedad',
  async alActivar(capa) {
    pintarGibs(capa);
    capa.pintarEstado('vivo', 't+1 día');
  },
});

let capaGibsTile = null;

function pintarGibs(capa) {
  const dias = Number($('#sel-fecha-gibs').value) || 1;
  const fecha = fechaISO(dias);
  if (capaGibsTile) capa.grupoLeaflet.removeLayer(capaGibsTile);
  capaGibsTile = L.tileLayer(
    `https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_SNPP_CorrectedReflectance_TrueColor/default/${fecha}/GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg`,
    {
      opacity: 0.85,
      maxNativeZoom: 9,
      maxZoom: 19,
      attribution: 'Imágenes: NASA EOSDIS GIBS / VIIRS Suomi-NPP',
      className: 'gibs-tile',
    }
  ).addTo(capa.grupoLeaflet);

  // Anomalías térmicas: fuegos activos en la fecha elegida.
  if (capa.capaFuego) capa.grupoLeaflet.removeLayer(capa.capaFuego);
  capa.capaFuego = L.tileLayer(
    `https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_SNPP_Thermal_Anomalies_375m_All/default/${fecha}/GoogleMapsCompatible_Level9/{z}/{y}/{x}.png`,
    {
      opacity: 0.9,
      maxNativeZoom: 9,
      maxZoom: 19,
      attribution: 'Anomalías térmicas: NASA FIRMS / VIIRS',
    }
  ).addTo(capa.grupoLeaflet);

  capa.contar(1);
  capa.detalle = `Fecha de la imagen: ${fecha} (ayer no es hoy: hay latencia de procesado)`;
  capa.pintarEstado('vivo', fecha.slice(5));
}

/* ═══════════════════════════════════════════════════════════════════════════
   12. REGISTRO Y PANEL DE CAPAS
   ═══════════════════════════════════════════════════════════════════════════ */

const ORDEN = ['camaras', 'vuelos', 'barcos', 'satelites', 'sismos', 'radar', 'gibs'];
const TODAS = [capaCamaras, capaVuelos, capaBarcos, capaSatelites, capaSismos, capaRadar, capaGibs];

function pintarPanelCapas() {
  const contenedor = $('#lista-capas');
  const porGrupo = {};
  for (const id of ORDEN) {
    const c = estado.capas[id];
    (porGrupo[c.grupo] ??= []).push(c);
  }

  contenedor.innerHTML = Object.entries(porGrupo)
    .map(
      ([grupo, capas]) => `
      <div class="grupo">
        <p class="grupo-titulo">${esc(GRUPOS[grupo] ?? grupo)}</p>
        ${capas
          .map(
            (c) => `
          <div class="capa ${c.activa ? 'activa' : ''}" data-capa="${c.id}">
            <div class="interruptor"></div>
            <div class="capa-texto">
              <div class="capa-nombre"><span class="glifo">${c.glifo}</span>${esc(c.nombre)}</div>
              <div class="capa-desc">${esc(c.descripcion)}</div>
            </div>
            <span class="capa-estado ${claseOrigen(c.origen)}">${etiquetaOrigen(c.origen)}</span>
          </div>`
          )
          .join('')}
      </div>`
    )
    .join('');

  contenedor.querySelectorAll('.capa').forEach((fila) =>
    fila.addEventListener('click', () => alternarCapa(fila.dataset.capa))
  );
}

async function alternarCapa(id) {
  const c = estado.capas[id];
  if (!c) return;
  const fila = document.querySelector(`.capa[data-capa="${id}"]`);
  if (c.activa) {
    c.desactivar();
    fila?.classList.remove('activa');
    actualizarContador();
  } else {
    fila?.classList.add('activa');
    await c.activar();
  }
  pintarResumen();
}

function pintarResumen() {
  const activas = Object.values(estado.capas).filter((c) => c.activa);
  if (!activas.length) {
    $('#resumen-fuentes').textContent = 'Ninguna capa activa. Activa alguna para empezar a observar.';
    return;
  }
  const conDatos = activas.filter((c) => c._n > 0).length;
  $('#resumen-fuentes').innerHTML =
    `<strong>${conDatos}/${activas.length}</strong> capas con datos.<br>` +
    activas.map((c) => `· ${esc(c.nombre)}: ${esc(c.detalle || '—')}`).join('<br>');
}

/* ═══════════════════════════════════════════════════════════════════════════
   12-bis. UBICACIÓN DEL DISPOSITIVO
   El navegador embebido pide el permiso nativo al sistema; basta con declarar
   ACCESS_FINE_LOCATION en AndroidManifest.xml. Sin plugins adicionales.
   ═══════════════════════════════════════════════════════════════════════════ */

let marcadorYo = null;

function irAMiUbicacion() {
  const boton = $('#btn-ubicacion');

  if (!navigator.geolocation) {
    alert('Este dispositivo no expone la ubicación al navegador.');
    return;
  }

  boton.disabled = true;
  boton.textContent = 'Localizando…';

  navigator.geolocation.getCurrentPosition(
    (pos) => {
      const { latitude: lat, longitude: lon, accuracy } = pos.coords;

      if (marcadorYo) mapa.removeLayer(marcadorYo);
      marcadorYo = L.circleMarker([lat, lon], {
        radius: 7,
        color: '#22d3ee',
        weight: 2,
        fillColor: '#22d3ee',
        fillOpacity: 0.35,
      })
        .bindPopup(
          `<strong>Tu posición</strong><br>
           Precisión: ±${Math.round(accuracy)} m<br>
           ${lat.toFixed(5)}, ${lon.toFixed(5)}`
        )
        .addTo(mapa);

      mapa.setView([lat, lon], 13);
      marcadorYo.openPopup();

      // Aviso deliberado: lo que aparece en el mapa a partir de aquí puede
      // afectar a la privacidad de terceros si se comparte.
      boton.disabled = false;
      boton.textContent = 'Mi ubicación';
    },
    (err) => {
      boton.disabled = false;
      boton.textContent = 'Mi ubicación';
      const motivo = {
        1: 'Permiso de ubicación denegado. Actívalo en los ajustes de la app.',
        2: 'Posición no disponible (¿GPS apagado?).',
        3: 'Se agotó el tiempo de espera.',
      }[err.code] ?? err.message;
      alert(motivo);
    },
    { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 }
  );
}

/* ═══════════════════════════════════════════════════════════════════════════
   13. PANEL DE DETALLE
   ═══════════════════════════════════════════════════════════════════════════ */

function abrirDetalle() {
  $('#panel-detalle').classList.remove('oculto');
}
function cerrarDetalle() {
  $('#panel-detalle').classList.add('oculto');
  clearInterval(temporizadorCamara);
  detalleAbiertoSatelite = null;
}

/* ═══════════════════════════════════════════════════════════════════════════
   14. CINTA DE EVENTOS
   ═══════════════════════════════════════════════════════════════════════════ */

function pintarCinta() {
  const eventos = estado.eventos.sort((a, b) => b.t - a.t).slice(0, 20);

  const activas = Object.values(estado.capas).filter((c) => c.activa);
  const objetos = activas.reduce((s, c) => s + (c._n ?? 0), 0);
  const estado_ = `<b>${horaUTC()}Z</b> ${activas.length} capas activas · ${objetos} objetos en seguimiento`;

  const linea = eventos.length
    ? eventos.map((e) => `<b>${horaUTC(new Date(e.t))}Z</b> ${esc(e.texto)}`).join('   ·   ')
    : `${estado_}   ·   sin sismos significativos en las últimas 24 h`;

  // Se repite para que el desplazamiento continuo no deje huecos.
  $('#cinta-contenido').innerHTML = `${estado_}   ·   ${linea}   ·   `.repeat(2);
}

/* ═══════════════════════════════════════════════════════════════════════════
   15. ARRANQUE
   ═══════════════════════════════════════════════════════════════════════════ */

function arrancarReloj() {
  const tic = () => {
    $('#reloj').textContent = horaUTC();
    if (detalleAbiertoSatelite) refrescarDetalleSatelite();
  };
  tic();
  setInterval(tic, 1000);
}

async function comprobarRed() {
  // Sin servidor Node detrás: se sondea una fuente real desde el propio dispositivo.
  if (!HAY_SERVIDOR) {
    $('#texto-red').textContent = 'modo app · comprobando…';
    try {
      await fetch('https://api.tfl.gov.uk/Place/Type/JamCam', {
        signal: AbortSignal.timeout(12000),
        // Evita descargar el catálogo entero solo para comprobar la conexión.
        headers: { Range: 'bytes=0-64' },
      });
      $('#punto-red').className = 'punto vivo';
      $('#texto-red').textContent = 'modo app · sin conexión al servidor';
    } catch {
      $('#punto-red').className = 'punto muerto';
      $('#texto-red').textContent = 'modo app · sin red';
    }
    return;
  }

  try {
    const r = await fetch('/api/estado', { signal: AbortSignal.timeout(12000) });
    const d = await r.json();
    const vivo = d.salidaInternet;
    $('#punto-red').className = `punto ${vivo ? 'vivo' : 'muerto'}`;
    $('#texto-red').textContent = vivo
      ? `fuentes alcanzables · ${d.sondas.filter((s) => s.alcanzable).length}/${d.sondas.length}`
      : 'sin salida a Internet · modo respaldo';
  } catch {
    $('#punto-red').className = 'punto muerto';
    $('#texto-red').textContent = 'servidor no responde';
  }
}

async function iniciar() {
  pintarPanelCapas();
  arrancarReloj();
  comprobarRed();

  // Cableado de la interfaz
  $('#btn-capas').addEventListener('click', () => $('#panel-capas').classList.toggle('oculto'));
  $('#btn-recentrar').addEventListener('click', () => mapa.setView([35, 5], 3));
  $('#btn-ubicacion').addEventListener('click', irAMiUbicacion);
  $('#btn-legal').addEventListener('click', () => $('#modal-legal').classList.remove('oculto'));
  $('#btn-refrescar').addEventListener('click', async () => {
    for (const c of Object.values(estado.capas)) await c.refrescar();
    pintarPanelCapas();
    pintarResumen();
    comprobarRed();
  });

  document.querySelectorAll('[data-cierra]').forEach((b) =>
    b.addEventListener('click', () => {
      const destino = b.dataset.cierra;
      if (destino === 'panel-detalle') cerrarDetalle();
      else $(`#${destino}`).classList.add('oculto');
    })
  );

  $('#modal-legal').addEventListener('click', (e) => {
    if (e.target.id === 'modal-legal') e.currentTarget.classList.add('oculto');
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      $('#modal-legal').classList.add('oculto');
      cerrarDetalle();
    }
  });

  $('#sel-base').addEventListener('change', (e) => ponerFondo(e.target.value));
  $('#sel-fecha-gibs').addEventListener('change', () => {
    if (estado.capas.gibs.activa) pintarGibs(estado.capas.gibs);
  });

  // Recarga de vuelos al mover el mapa
  let esperaVuelos = null;
  mapa.on('moveend', () => {
    if (!estado.capas.vuelos.activa) return;
    clearTimeout(esperaVuelos);
    esperaVuelos = setTimeout(() => {
      cargarVuelos(estado.capas.vuelos)
        .then(() => pintarResumen())
        .catch(() => {});
    }, 1200);
  });

  // Activa las capas que funcionan sin configuración
  await alternarCapa('camaras');
  await alternarCapa('sismos');
  await alternarCapa('satelites');
  pintarPanelCapas();
  pintarResumen();
  pintarCinta();
  setInterval(pintarCinta, 60_000);
  ajustarDensidad();

  console.log('%cOJO DE DIOS','color:#22d3ee;font-weight:bold', 'listo. Solo fuentes públicas abiertas.');
}

iniciar();
