# QR Studio 🎨📱

Generador de códigos QR personalizados con diseños variados: colores, degradados,
formas de puntos, estilos de esquinas, logos, íconos y marcos con texto para tu
negocio. **100% local y gratis**: todo se genera y guarda en el dispositivo del
usuario (nada se suba a internet), y funciona offline como app instalable (PWA).

## ✨ Características

- **11 tipos de contenido**: enlace, texto, WiFi, WhatsApp, contacto (vCard),
  redes sociales, email, llamada, SMS, ubicación GPS y evento (Google Calendar).
- **Diseño a tu marca**:
  - 6 formas de puntos (cuadrado, redondeado, puntos, classy…)
  - Estilos de esquinas y punto central independientes
  - Color sólido o degradado (lineal/radial con rotación) para código y fondo
  - Fondo transparente para imprenta
  - Color personalizado para los "ojos" del QR
  - Logo propio (PNG/SVG) o ícono emoji con tamaño y margen ajustables
  - Marcos con texto: banner o contorno ("MENÚ", "WIFI GRATIS", "ESCANÉAME"…)
- **12 plantillas por rubro**: restaurante, cafetería, barbería, tienda,
  hotel/WiFi, gimnasio, boda/evento, inmobiliaria, clínica, creador, tech y clásico.
- **Exportación**: PNG / JPG (512, 1024 o 2048 px) y SVG vectorial con marco incluido;
  copiar al portapapeles y compartir directo desde el celular.
- **Escáner integrado** (cámara o desde imagen) para verificar cualquier QR.
- **Mis QR**: guarda diseños con miniatura en el dispositivo y reedítalos cuando quieras.
- **Borrador automático**: tu trabajo en curso se conserva al cerrar la app.
- **PWA offline**: instálala en el teléfono (Agregar a pantalla de inicio) y úsala sin internet.

## 🚀 Cómo usarla

```bash
npm install
npm run dev        # desarrollo en http://localhost:3000
```

### Producción (estática, gratis en cualquier hosting)

```bash
npm run build      # genera la carpeta out/ (sitio 100% estático)
node scripts/serve.mjs   # sirve out/ en http://localhost:3000 (vista previa)
```

La carpeta `out/` puede subirse tal cual a GitHub Pages, Netlify, Cloudflare
Pages, Vercel o cualquier hosting estático gratuito. No necesita servidor ni
base de datos: al ser todo client-side, el costo de operación es **cero**.

### Íconos de la app

```bash
npm run icons      # regenera public/icons/* desde assets/icon-source.png
```

## 🧰 Stack

- [Next.js](https://nextjs.org) (App Router, export estático) + TypeScript + Tailwind CSS
- [qr-code-styling](https://github.com/kozakdenys/qr-code-styling) para el renderizado del QR
- [html5-qrcode](https://github.com/mebjas/html5-qrcode) para el escáner
- Service worker propio para modo offline

## 🔒 Privacidad

Ningún dato sale del dispositivo: ni el contenido de tus QR, ni tus logos, ni tus
diseños guardados (se usan `localStorage` y canvas local).

## ️ Próximas ideas

- Generación masiva desde CSV (ZIP de QRs)
- Más plantillas y marcos decorativos
- Medidor de "escaneabilidad" (contraste y densidad)
- Exportar hoja de impresión (varios QRs en A4 PDF)
