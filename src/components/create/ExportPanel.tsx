'use client';

import { Bookmark, Copy, Download, FileImage, Image as ImageIcon, Loader2, Share2 } from 'lucide-react';
import { useState } from 'react';
import { Segmented } from '@/components/ui';
import {
  copyBlobToClipboard,
  downloadBlob,
  downloadText,
  renderPngBlob,
  renderSvgString,
  renderThumbDataUrl,
  shareBlob,
} from '@/lib/exporters';
import type { ContentType, FieldValues, SavedDesign, StyleConfig } from '@/lib/types';

const SIZES = [512, 1024, 2048] as const;

export default function ExportPanel({
  data,
  style,
  name,
  contentType,
  values,
  onToast,
  onSave,
}: {
  data: string;
  style: StyleConfig;
  name: string;
  contentType: ContentType;
  values: FieldValues;
  onToast: (msg: string) => void;
  onSave: (d: SavedDesign) => void;
}) {
  const [size, setSize] = useState<number>(1024);
  const [busy, setBusy] = useState<string | null>(null);

  const slug =
    name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '')
      .slice(0, 40) || 'qr';

  const run = async (id: string, fn: () => Promise<void>) => {
    if (!data) return;
    setBusy(id);
    try {
      await fn();
    } catch {
      onToast('Ocurrió un error al exportar');
    } finally {
      setBusy(null);
    }
  };

  const icon = (id: string, node: React.ReactNode) =>
    busy === id ? <Loader2 className="h-4 w-4 animate-spin" /> : node;

  return (
    <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <p className="mb-2.5 text-xs font-semibold uppercase tracking-wide text-slate-400">Exportar</p>
      <Segmented
        options={SIZES.map((s) => ({ value: String(s), label: `${s} px` }))}
        value={String(size)}
        onChange={(v) => setSize(Number(v))}
        ariaLabel="Tamaño de exportación"
      />
      <div className="mt-3 grid grid-cols-3 gap-2">
        <button
          type="button"
          disabled={!data || busy !== null}
          onClick={() =>
            run('png', async () => {
              const blob = await renderPngBlob(data, style, size, 'png');
              downloadBlob(blob, `${slug}-${size}.png`);
              onToast(`PNG de ${size}px descargado`);
            })
          }
          className="flex items-center justify-center gap-1.5 rounded-xl bg-indigo-600 px-2 py-2.5 text-xs font-semibold text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {icon('png', <Download className="h-4 w-4" />)} PNG
        </button>
        <button
          type="button"
          disabled={!data || busy !== null}
          onClick={() =>
            run('svg', async () => {
              const svg = await renderSvgString(data, style, size);
              downloadText(svg, `${slug}.svg`);
              onToast('SVG vectorial descargado');
            })
          }
          className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-2 py-2.5 text-xs font-semibold text-slate-700 ring-1 ring-slate-200 transition hover:ring-slate-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {icon('svg', <FileImage className="h-4 w-4" />)} SVG
        </button>
        <button
          type="button"
          disabled={!data || busy !== null}
          onClick={() =>
            run('jpg', async () => {
              const blob = await renderPngBlob(data, style, size, 'jpeg');
              downloadBlob(blob, `${slug}-${size}.jpg`);
              onToast(`JPG de ${size}px descargado`);
            })
          }
          className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-2 py-2.5 text-xs font-semibold text-slate-700 ring-1 ring-slate-200 transition hover:ring-slate-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {icon('jpg', <ImageIcon className="h-4 w-4" />)} JPG
        </button>
      </div>
      <div className="mt-2 grid grid-cols-3 gap-2">
        <button
          type="button"
          disabled={!data || busy !== null}
          onClick={() =>
            run('copy', async () => {
              const blob = await renderPngBlob(data, style, size, 'png');
              const ok = await copyBlobToClipboard(blob);
              onToast(ok ? 'Imagen copiada al portapapeles' : 'Tu navegador no permite copiar imágenes');
            })
          }
          className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-2 py-2.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200 transition hover:ring-slate-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {icon('copy', <Copy className="h-4 w-4" />)} Copiar
        </button>
        <button
          type="button"
          disabled={!data || busy !== null}
          onClick={() =>
            run('share', async () => {
              const blob = await renderPngBlob(data, style, size, 'png');
              const ok = await shareBlob(blob, `${slug}-${size}.png`, name);
              if (!ok) onToast('No se pudo compartir en este dispositivo');
            })
          }
          className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-2 py-2.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200 transition hover:ring-slate-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {icon('share', <Share2 className="h-4 w-4" />)} Compartir
        </button>
        <button
          type="button"
          disabled={!data || busy !== null}
          onClick={() =>
            run('save', async () => {
              const thumb = await renderThumbDataUrl(data, style);
              onSave({
                id:
                  typeof crypto !== 'undefined' && 'randomUUID' in crypto
                    ? crypto.randomUUID()
                    : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
                name,
                createdAt: Date.now(),
                contentType,
                values: { ...values },
                style: { ...style },
                thumb,
              });
              onToast('Guardado en “Mis QR”');
            })
          }
          className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-2 py-2.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200 transition hover:ring-slate-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {icon('save', <Bookmark className="h-4 w-4" />)} Guardar
        </button>
      </div>
      <p className="mt-2.5 text-[11px] leading-relaxed text-slate-400">
        Todo se genera y guarda en tu dispositivo. Nada se sube a internet.
      </p>
    </div>
  );
}
