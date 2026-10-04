/* Genera los íconos PWA y Android a partir de assets/icon-source.png usando sharp. */
import { mkdirSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
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

  // ---------- Android (Capacitor) ----------
  const AND = 'android/app/src/main/res';
  if (existsSync(AND)) {
    const launcher = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
    const foreground = { mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432 };

    for (const [d, size] of Object.entries(launcher)) {
      const dir = join(AND, `mipmap-${d}`);
      mkdirSync(dir, { recursive: true });
      const buf = await sharp(SRC).resize(size, size).png().toBuffer();
      await sharp(buf).toFile(join(dir, 'ic_launcher.png'));
      await sharp(buf).toFile(join(dir, 'ic_launcher_round.png'));
    }

    for (const [d, size] of Object.entries(foreground)) {
      const dir = join(AND, `mipmap-${d}`);
      mkdirSync(dir, { recursive: true });
      const inner = Math.round(size * 0.66);
      const off = Math.round((size - inner) / 2);
      const icon = await sharp(SRC).resize(inner, inner).png().toBuffer();
      await sharp({
        create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
      })
        .composite([{ input: icon, left: off, top: off }])
        .png()
        .toFile(join(dir, 'ic_launcher_foreground.png'));
    }

    // splash screens: mismo tamaño que los del template, fondo blanco + ícono centrado
    const splashDirs = readdirSync(AND).filter((f) => f.startsWith('drawable'));
    for (const dirName of splashDirs) {
      const splashPath = join(AND, dirName, 'splash.png');
      if (!existsSync(splashPath)) continue;
      const meta = await sharp(splashPath).metadata();
      const w = meta.width || 480;
      const h = meta.height || 320;
      const iconSize = Math.round(Math.min(w, h) * 0.42);
      const icon = await sharp(SRC).resize(iconSize, iconSize).png().toBuffer();
      await sharp({
        create: { width: w, height: h, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } },
      })
        .composite([{ input: icon, left: Math.round((w - iconSize) / 2), top: Math.round((h - iconSize) / 2) }])
        .png()
        .toFile(splashPath);
    }
    console.log('✔ íconos y splash de Android actualizados');
  }

  console.log('✔ íconos generados en public/');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
