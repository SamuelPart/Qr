/**
 * Ojo de Dios — servidor de vigilancia de fuentes públicas
 * ---------------------------------------------------------
 * Sirve la interfaz y ofrece un proxy con lista blanca para los feeds
 * que no permiten CORS desde el navegador.
 *
 * El proxy NO es un abrelatas de Internet: solo reenvía hosts declarados
 * en data/sources.json. Cualquier otro destino se rechaza.
 */

const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';
const RAIZ = __dirname;

// ---------------------------------------------------------------------------
// Lista blanca de hosts. Solo fuentes públicas declaradas.
// ---------------------------------------------------------------------------
const HOSTS_PERMITIDOS = new Set([
  'api.tfl.gov.uk',
  's3-eu-west-1.amazonaws.com',
  'tie.digitraffic.fi',
  'weathercam.digitraffic.fi',
  'meri.digitraffic.fi',
  'cwwp2.dot.ca.gov',
  'api.airplanes.live',
  'api.adsb.lol',
  'opendata.adsb.fi',
  'opensky-network.org',
  'earthquake.usgs.gov',
  'api.rainviewer.com',
  'celestrak.org',
  'gibs.earthdata.nasa.gov',
  'api.wheretheiss.at',
]);

const CACHE_TTL_MS = 60_000;
const cache = new Map();

function leerSnapshot(nombre) {
  const ruta = path.join(RAIZ, 'data', `${nombre}.json`);
  if (!fs.existsSync(ruta)) return null;
  try {
    return JSON.parse(fs.readFileSync(ruta, 'utf8'));
  } catch (err) {
    console.error(`[snapshot] no se pudo leer ${nombre}:`, err.message);
    return null;
  }
}

// ---------------------------------------------------------------------------
// Proxy con lista blanca
// ---------------------------------------------------------------------------
app.get('/api/proxy', async (req, res) => {
  const destino = req.query.url;
  if (!destino || typeof destino !== 'string') {
    return res.status(400).json({ error: 'Falta el parámetro url' });
  }

  let parsed;
  try {
    parsed = new URL(destino);
  } catch {
    return res.status(400).json({ error: 'URL inválida' });
  }

  if (!HOSTS_PERMITIDOS.has(parsed.hostname)) {
    return res.status(403).json({
      error: 'Host no autorizado',
      host: parsed.hostname,
      detalle: 'Solo se reenvían feeds públicos declarados en data/sources.json.',
    });
  }

  const enCache = cache.get(destino);
  if (enCache && Date.now() - enCache.t < CACHE_TTL_MS) {
    return res.type(enCache.ct).send(enCache.cuerpo);
  }

  const controlador = new AbortController();
  const temporizador = setTimeout(() => controlador.abort(), 12_000);

  try {
    const respuesta = await fetch(destino, {
      signal: controlador.signal,
      redirect: 'follow',
      headers: {
        // Varios portales públicos (Caltrans, TfL) rechazan clientes sin UA de navegador.
        'User-Agent':
          'Mozilla/5.0 (compatible; OjoDeDios/1.0; +https://github.com/SamuelPart/Qr)',
        Accept: 'application/json, text/plain, image/*, */*',
      },
    });

    const ct = respuesta.headers.get('content-type') || 'application/json';
    const cuerpo = Buffer.from(await respuesta.arrayBuffer());

    if (respuesta.ok) {
      cache.set(destino, { t: Date.now(), cuerpo, ct });
    }

    res.status(respuesta.status).type(ct).send(cuerpo);
  } catch (err) {
    const motivo = err.name === 'AbortError' ? 'agotado el tiempo de espera' : err.message;
    res.status(502).json({ error: 'No se pudo alcanzar la fuente', motivo, url: destino });
  } finally {
    clearTimeout(temporizador);
  }
});

// ---------------------------------------------------------------------------
// Instantáneas locales (respaldo sin red)
// ---------------------------------------------------------------------------
app.get('/api/snapshot/:nombre', (req, res) => {
  const nombre = String(req.params.nombre).replace(/[^a-z0-9_-]/gi, '');
  const datos = leerSnapshot(nombre);
  if (!datos) return res.status(404).json({ error: 'Instantánea no encontrada', nombre });
  res.json(datos);
});

// ---------------------------------------------------------------------------
// Estado del servidor y de la salida a Internet
// ---------------------------------------------------------------------------
app.get('/api/estado', async (_req, res) => {
  const sondas = [
    { id: 'tfl-cams', url: 'https://api.tfl.gov.uk/Place/Type/JamCam' },
    { id: 'usgs-quakes', url: 'https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson' },
    { id: 'rainviewer', url: 'https://api.rainviewer.com/public/weather-maps.json' },
  ];

  const resultados = await Promise.all(
    sondas.map(async (s) => {
      const t0 = Date.now();
      try {
        const r = await fetch(s.url, {
          signal: AbortSignal.timeout(6000),
          headers: { 'User-Agent': 'Mozilla/5.0 (compatible; OjoDeDios/1.0)' },
        });
        return { id: s.id, alcanzable: r.ok, ms: Date.now() - t0, estado: r.status };
      } catch (err) {
        return { id: s.id, alcanzable: false, ms: Date.now() - t0, error: err.name };
      }
    })
  );

  res.json({
    servicio: 'ojo-de-dios',
    version: '1.0.0',
    hora: new Date().toISOString(),
    salidaInternet: resultados.some((r) => r.alcanzable),
    sondas: resultados,
  });
});

app.get('/api/catalogo', (_req, res) => {
  const s = leerSnapshot('sources');
  if (!s) return res.status(404).json({ error: 'Catálogo no disponible' });
  res.json(s);
});

// ---------------------------------------------------------------------------
// Estáticos
// ---------------------------------------------------------------------------
app.use(express.static(path.join(RAIZ, 'public'), { maxAge: '1h' }));
app.use('/data', express.static(path.join(RAIZ, 'data'), { maxAge: '5m' }));

app.get('/', (_req, res) => res.sendFile(path.join(RAIZ, 'public', 'index.html')));

app.use((_req, res) => res.status(404).json({ error: 'Ruta no encontrada' }));

app.listen(PORT, HOST, () => {
  console.log(`\n  OJO DE DIOS  ·  vigilancia de fuentes públicas`);
  console.log(`  ─────────────────────────────────────────────`);
  console.log(`  Escuchando en http://${HOST}:${PORT}`);
  console.log(`  Proxy con lista blanca: /api/proxy?url=…`);
  console.log(`  Hosts autorizados: ${HOSTS_PERMITIDOS.size}\n`);
});
