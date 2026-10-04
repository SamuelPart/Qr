/* Smoke test e2e opcional: requiere `npm i -D puppeteer` y un Chrome disponible.
   Carga la app, interactúa y reporta errores de consola. */
import puppeteer from 'puppeteer';

const BASE = process.env.BASE || 'http://localhost:3000';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true });

const errors = [];
page.on('console', (m) => {
  if (m.type() === 'error') errors.push('console: ' + m.text());
});
page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));

await page.goto(BASE, { waitUntil: 'networkidle0', timeout: 30000 });

// 1. El canvas del QR de vista previa debe existir
await page.waitForSelector('canvas', { timeout: 15000 });
console.log('✔ canvas de vista previa presente');

// 2. Escribir una URL y verificar que el preview siga vivo
await page.type('input[type="url"]', 'https://cafetrujillo.pe/menu');
await sleep(900);
const canvases = await page.$$eval('canvas', (els) => els.length);
console.log('✔ canvases tras escribir URL:', canvases);

// 3. Cambiar tipo de contenido a WiFi y llenar campos
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('button')];
  btns.find((b) => b.textContent?.includes('WiFi'))?.click();
});
await sleep(400);
const inputs = await page.$$('main input[type="text"]');
if (inputs[0]) await inputs[0].type('CafeTrujillo');
if (inputs[1]) await inputs[1].type('clave123');
await sleep(700);
console.log('✔ tipo WiFi con datos');

// 4. Aplicar plantilla
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('button')];
  btns.find((b) => b.textContent?.includes('Restaurante'))?.click();
});
await sleep(700);
console.log('✔ plantilla aplicada');

// 5. Cambiar forma de puntos (la sección "Formas y patrones" abre expandida)
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('button')];
  btns.find((b) => b.textContent?.trim() === 'Puntos')?.click();
});
await sleep(600);
console.log('✔ forma de puntos aplicada');

// 6. Guardar el diseño
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('button')];
  btns.find((b) => b.textContent?.includes('Guardar'))?.click();
});
await sleep(1200);
console.log('✔ acción guardar ejecutada');

// 7. Ir a Mis QR y comprobar la tarjeta guardada
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('nav button')];
  btns.find((b) => b.textContent?.includes('Mis QR'))?.click();
});
await sleep(600);
const savedText = await page.evaluate(() => document.querySelector('main')?.innerText || '');
console.log(savedText.includes('WiFi') ? '✔ diseño guardado visible en Mis QR' : '✗ no se ve el diseño guardado: ' + savedText.slice(0, 200));

// 8. Volver a Crear y abrir Escanear (sin cámara: debe mostrar botones sin crash)
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('nav button')];
  btns.find((b) => b.textContent?.includes('Escanear'))?.click();
});
await sleep(600);
const scanText = await page.evaluate(() => document.querySelector('main')?.innerText || '');
console.log(scanText.includes('Escáner') ? '✔ vista de escáner correcta' : '✗ vista escáner: ' + scanText.slice(0, 200));

// 9. Captura de pantalla para revisión visual
await page.evaluate(() => {
  const btns = [...document.querySelectorAll('nav button')];
  btns.find((b) => b.textContent?.includes('Crear'))?.click();
});
await sleep(800);
await page.screenshot({ path: '/tmp/smoke-mobile.png', fullPage: false });

await page.setViewport({ width: 1280, height: 900 });
await sleep(800);
await page.screenshot({ path: '/tmp/smoke-desktop.png', fullPage: false });

const realErrors = errors.filter((e) => !e.includes('favicon') && !e.includes('camera') && !e.includes('Permissions policy'));
console.log(realErrors.length ? '✗ ERRORES:\n' + realErrors.join('\n') : '✔ sin errores de consola');
await browser.close();
process.exit(realErrors.length ? 1 : 0);
