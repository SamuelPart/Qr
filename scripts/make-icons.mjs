/* Genera los íconos PWA a partir de assets/icon-source.png usando sharp. */
import { mkdirSync } from 'node:fs';
import sharp from 'sharp';

const SRC = 'assets/icon-source.png';

mkdirSync('public/icons', { recursive: true });

async function main() {
  // iconos principales
  await sharp(SRC).resize(512, 512).png().toFile('public/icons/icon-512.png');
  await sharp(SRC).resize(192, 192).png().toFile('public/icons/icon-192.png');

  // maskable: logo centrado al 72% sobre fondo blanco
  const inner = await sharp(SRC).resize(368, 368).png().toBuffer();
  await sharp({
    create: { width: 512, height: 512, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } },
  })
    .composite([{ input: inner, left: 72, top: 72 }])
    .png()
    .toFile('public/icons/icon-maskable-512.png');

  // favicon y apple touch icon (Next los sirve desde public/)
  await sharp(SRC).resize(192, 192).png().toFile('public/icon.png');
  await sharp(SRC).resize(180, 180).png().toFile('public/apple-icon.png');

  console.log('✔ íconos generados en public/');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
