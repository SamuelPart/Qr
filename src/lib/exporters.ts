import type { StyleConfig } from './types';
import { buildQrOptions, getFrameColor } from './qrStyle';

export interface FrameMetrics {
  pad: number;
  barH: number;
  border: number;
  textH: number;
  totalW: number;
  totalH: number;
  radius: number;
}

export function frameMetrics(style: StyleConfig, size: number): FrameMetrics {
  if (style.frameStyle === 'none') {
    return { pad: 0, barH: 0, border: 0, textH: 0, totalW: size, totalH: size, radius: 0 };
  }
  if (style.frameStyle === 'banner') {
    const pad = Math.round(size * 0.05);
    const barH = Math.round(size * 0.13);
    return {
      pad,
      barH,
      border: 0,
      textH: 0,
      totalW: size + pad * 2,
      totalH: size + pad * 2 + barH,
      radius: Math.round(size * 0.06),
    };
  }
  const pad = Math.round(size * 0.06);
  const border = Math.max(3, Math.round(size * 0.026));
  const textH = Math.round(size * 0.13);
  return {
    pad,
    barH: 0,
    border,
    textH,
    totalW: size + pad * 2,
    totalH: size + pad * 2 + textH,
    radius: Math.round(size * 0.07),
  };
}

function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

async function renderQrImage(data: string, style: StyleConfig, size: number): Promise<HTMLImageElement> {
  const { default: QRCodeStyling } = await import('qr-code-styling');
  const qr = new QRCodeStyling(buildQrOptions(data, style, size));
  const raw = await qr.getRawData('png');
  const blob = raw instanceof Blob ? raw : new Blob([raw as BlobPart], { type: 'image/png' });
  const url = URL.createObjectURL(blob);
  try {
    return await loadImage(url);
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
}

function roundedRectPath(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
}

function drawFrameAndText(
  ctx: CanvasRenderingContext2D,
  style: StyleConfig,
  m: FrameMetrics,
  size: number,
): void {
  const color = getFrameColor(style);
  const text = style.frameText.toUpperCase();
  try {
    (ctx as CanvasRenderingContext2D & { letterSpacing?: string }).letterSpacing = `${Math.max(1, Math.round(size * 0.008))}px`;
  } catch {
    /* no soportado */
  }
  if (style.frameStyle === 'banner') {
    ctx.fillStyle = color;
    ctx.fillRect(0, m.totalH - m.barH, m.totalW, m.barH);
    const fs = Math.round(m.barH * 0.4);
    ctx.font = `700 ${fs}px -apple-system, "Segoe UI", Roboto, Arial, sans-serif`;
    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, m.totalW / 2, m.totalH - m.barH / 2 + fs * 0.06, m.totalW * 0.92);
  } else if (style.frameStyle === 'outline') {
    ctx.strokeStyle = color;
    ctx.lineWidth = m.border;
    roundedRectPath(ctx, m.border / 2, m.border / 2, m.totalW - m.border, m.totalH - m.border, m.radius);
    ctx.stroke();
    const fs = Math.round(m.textH * 0.42);
    ctx.font = `700 ${fs}px -apple-system, "Segoe UI", Roboto, Arial, sans-serif`;
    ctx.fillStyle = color;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, m.totalW / 2, m.pad + size + m.textH / 2 + fs * 0.06, m.totalW * 0.9);
  }
}

export async function renderPngBlob(
  data: string,
  style: StyleConfig,
  size: number,
  format: 'png' | 'jpeg',
): Promise<Blob> {
  const qrImg = await renderQrImage(data, style, size);
  const m = frameMetrics(style, size);
  const canvas = document.createElement('canvas');
  canvas.width = m.totalW;
  canvas.height = m.totalH;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas no disponible');

  const hasCard = style.frameStyle !== 'none';
  if (format === 'jpeg' || hasCard || style.bgMode !== 'transparent') {
    ctx.fillStyle = '#ffffff';
    if (hasCard) {
      roundedRectPath(ctx, 0, 0, m.totalW, m.totalH, m.radius);
      ctx.fill();
    } else {
      ctx.fillRect(0, 0, m.totalW, m.totalH);
    }
  }
  ctx.drawImage(qrImg, m.pad, m.pad, size, size);
  if (hasCard) drawFrameAndText(ctx, style, m, size);

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error('No se pudo generar la imagen'))),
      format === 'jpeg' ? 'image/jpeg' : 'image/png',
      0.95,
    );
  });
}

export async function renderSvgString(data: string, style: StyleConfig, size: number): Promise<string> {
  const { default: QRCodeStyling } = await import('qr-code-styling');
  const qr = new QRCodeStyling(buildQrOptions(data, style, size));
  const raw = await qr.getRawData('svg');
  let inner = typeof raw === 'string' ? raw : await (raw as Blob).text();
  // quita el prólogo XML que añade la librería (inválido dentro de otro SVG)
  inner = inner.replace(/^<\?xml[^>]*\?>\s*/, '');

  if (style.frameStyle === 'none') return inner;

  const m = frameMetrics(style, size);
  const color = getFrameColor(style);
  const text = escapeXml(style.frameText.toUpperCase());
  const parts: string[] = [];

  if (style.frameStyle === 'banner') {
    parts.push(`<rect x="0" y="0" width="${m.totalW}" height="${m.totalH}" rx="${m.radius}" fill="#ffffff"/>`);
    parts.push(`<g transform="translate(${m.pad},${m.pad})">${inner}</g>`);
    parts.push(`<rect x="0" y="${m.totalH - m.barH}" width="${m.totalW}" height="${m.barH}" fill="${color}"/>`);
    const fs = Math.round(m.barH * 0.4);
    parts.push(
      `<text x="${m.totalW / 2}" y="${m.totalH - m.barH / 2 + fs * 0.35}" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-weight="700" font-size="${fs}" letter-spacing="${Math.max(1, Math.round(size * 0.008))}" fill="#ffffff">${text}</text>`,
    );
  } else {
    parts.push(`<rect x="0" y="0" width="${m.totalW}" height="${m.totalH}" rx="${m.radius}" fill="#ffffff"/>`);
    parts.push(
      `<rect x="${m.border / 2}" y="${m.border / 2}" width="${m.totalW - m.border}" height="${m.totalH - m.border}" rx="${m.radius}" fill="none" stroke="${color}" stroke-width="${m.border}"/>`,
    );
    parts.push(`<g transform="translate(${m.pad},${m.pad})">${inner}</g>`);
    const fs = Math.round(m.textH * 0.42);
    parts.push(
      `<text x="${m.totalW / 2}" y="${m.pad + size + m.textH / 2 + fs * 0.35}" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-weight="700" font-size="${fs}" letter-spacing="${Math.max(1, Math.round(size * 0.008))}" fill="${color}">${text}</text>`,
    );
  }

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${m.totalW}" height="${m.totalH}" viewBox="0 0 ${m.totalW} ${m.totalH}">${parts.join('')}</svg>`;
}

export async function renderThumbDataUrl(data: string, style: StyleConfig): Promise<string> {
  const blob = await renderPngBlob(data, style, 240, 'png');
  return blobToDataUrl(blob);
}

export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const fr = new FileReader();
    fr.onload = () => resolve(String(fr.result));
    fr.onerror = reject;
    fr.readAsDataURL(blob);
  });
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

export function downloadText(text: string, filename: string, mime = 'image/svg+xml'): void {
  downloadBlob(new Blob([text], { type: mime }), filename);
}

export async function copyBlobToClipboard(blob: Blob): Promise<boolean> {
  try {
    if (!navigator.clipboard || typeof ClipboardItem === 'undefined') return false;
    await navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]);
    return true;
  } catch {
    return false;
  }
}

export async function shareBlob(blob: Blob, filename: string, title: string): Promise<boolean> {
  try {
    const file = new File([blob], filename, { type: blob.type });
    if (!navigator.canShare || !navigator.canShare({ files: [file] })) return false;
    await navigator.share({ files: [file], title });
    return true;
  } catch {
    return false;
  }
}
