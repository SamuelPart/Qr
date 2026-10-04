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
