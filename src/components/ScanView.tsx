'use client';

import { Camera, CameraOff, Copy, ExternalLink, FileImage, QrCode, RotateCcw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

type Mode = 'idle' | 'starting' | 'scanning' | 'error' | 'result';

/* eslint-disable @typescript-eslint/no-explicit-any */
type Scanner = any;

export default function ScanView({ onUseContent }: { onUseContent: (text: string) => void }) {
  const [mode, setMode] = useState<Mode>('idle');
  const [error, setError] = useState('');
  const [result, setResult] = useState('');
  const [copied, setCopied] = useState(false);
  const scannerRef = useRef<Scanner>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const stopScanner = async () => {
    const s = scannerRef.current;
    if (!s) return;
    scannerRef.current = null;
    try {
      await s.stop();
      s.clear?.();
    } catch {
      /* ya detenido */
    }
  };

  useEffect(() => {
    return () => {
      void stopScanner();
    };
  }, []);

  const handleResult = (text: string) => {
    setResult(text);
    setCopied(false);
    setMode('result');
  };

  const start = async () => {
    setError('');
    setResult('');
    setMode('starting');
    try {
      const { Html5Qrcode } = await import('html5-qrcode');
      await stopScanner();
      const scanner = new Html5Qrcode('qr-reader', { verbose: false });
      scannerRef.current = scanner;
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText: string) => {
          navigator.vibrate?.(60);
          handleResult(decodedText);
          void stopScanner();
        },
        () => {
          /* sin QR en este frame */
        },
      );
      setMode('scanning');
    } catch {
      setMode('error');
      setError('No se pudo acceder a la cámara. Revisa los permisos del navegador o escanea desde una imagen.');
    }
  };

  const scanFile = async (file: File) => {
    setMode('starting');
    setError('');
    try {
      const { Html5Qrcode } = await import('html5-qrcode');
      await stopScanner();
      const scanner = new Html5Qrcode('qr-reader-file', { verbose: false });
      const text = await scanner.scanFile(file, false);
      try {
        scanner.clear?.();
      } catch {
        /* opcional */
      }
      handleResult(text);
    } catch {
      setMode('error');
      setError('No se detectó ningún código QR en la imagen.');
    }
  };

  const isUrl = /^https?:\/\//i.test(result);

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="rounded-2xl bg-white p-4 text-center shadow-sm ring-1 ring-slate-200">
        <div className="mx-auto mb-2 grid h-11 w-11 place-items-center rounded-xl bg-indigo-50">
          <QrCode className="h-5 w-5 text-indigo-600" />
        </div>
        <h2 className="text-sm font-semibold text-slate-800">Escáner de códigos QR</h2>
        <p className="mt-1 text-xs leading-relaxed text-slate-500">
          Escanea cualquier QR con la cámara o desde una imagen. El procesamiento es 100% local en tu
          dispositivo.
        </p>
      </div>

      <div
        className={`overflow-hidden rounded-2xl bg-slate-900 ring-1 ring-slate-200 ${
          mode === 'scanning' || mode === 'starting' ? '' : 'hidden'
        }`}
      >
        <div id="qr-reader" className="mx-auto w-full" />
        {mode === 'starting' && (
          <p className="py-16 text-center text-xs font-medium text-white/80">Encendiendo cámara…</p>
        )}
      </div>

      {mode === 'error' && (
        <div className="rounded-2xl bg-rose-50 p-4 text-center ring-1 ring-rose-200">
          <p className="text-xs font-medium leading-relaxed text-rose-700">{error}</p>
        </div>
      )}

      {mode === 'result' && (
        <div className="space-y-3 rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="text-xs font-semibold uppercase tracking-wide text-emerald-600">
            ✓ QR detectado
          </p>
          <p className="max-h-36 overflow-auto break-all rounded-xl bg-slate-50 p-3 font-mono text-xs text-slate-700 ring-1 ring-slate-100">
            {result}
          </p>
          <div className="grid grid-cols-2 gap-2">
            {isUrl && (
              <a
                href={result}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-1.5 rounded-xl bg-slate-900 px-3 py-2.5 text-xs font-semibold text-white transition hover:bg-slate-700"
              >
                <ExternalLink className="h-3.5 w-3.5" /> Abrir enlace
              </a>
            )}
            <button
              type="button"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(result);
                  setCopied(true);
                } catch {
                  /* sin permisos */
                }
              }}
              className="flex items-center justify-center gap-1.5 rounded-xl bg-white px-3 py-2.5 text-xs font-semibold text-slate-700 ring-1 ring-slate-200 transition hover:ring-slate-300"
            >
              <Copy className="h-3.5 w-3.5" /> {copied ? '¡Copiado!' : 'Copiar'}
            </button>
            <button
              type="button"
              onClick={() => onUseContent(result)}
              className="col-span-2 flex items-center justify-center gap-1.5 rounded-xl bg-indigo-600 px-3 py-2.5 text-xs font-semibold text-white transition hover:bg-indigo-500"
            >
              <QrCode className="h-3.5 w-3.5" /> Crear un QR con este contenido
            </button>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-2">
        {mode === 'scanning' ? (
          <button
            type="button"
            onClick={() => {
              void stopScanner();
              setMode('idle');
            }}
            className="flex items-center justify-center gap-2 rounded-xl bg-white px-3 py-3 text-xs font-semibold text-slate-700 ring-1 ring-slate-200 transition hover:ring-slate-300"
          >
            <CameraOff className="h-4 w-4" /> Detener cámara
          </button>
        ) : (
          <button
            type="button"
            onClick={() => void start()}
            className="flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-3 py-3 text-xs font-semibold text-white transition hover:bg-indigo-500"
          >
            <Camera className="h-4 w-4" /> {mode === 'result' ? 'Escanear otro' : 'Iniciar cámara'}
          </button>
        )}
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          className="flex items-center justify-center gap-2 rounded-xl bg-white px-3 py-3 text-xs font-semibold text-slate-700 ring-1 ring-slate-200 transition hover:ring-slate-300"
        >
          <FileImage className="h-4 w-4" /> Desde imagen
        </button>
      </div>
      {mode === 'result' && (
        <button
          type="button"
          onClick={() => void start()}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-white px-3 py-2.5 text-xs font-medium text-slate-500 ring-1 ring-slate-200 transition hover:ring-slate-300"
        >
          <RotateCcw className="h-3.5 w-3.5" /> Volver a escanear
        </button>
      )}

      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) void scanFile(f);
          e.target.value = '';
        }}
      />
      <div id="qr-reader-file" className="hidden" />
    </div>
  );
}
