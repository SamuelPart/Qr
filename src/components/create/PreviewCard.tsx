'use client';

import QrPreview from './QrPreview';
import { getFrameColor } from '@/lib/qrStyle';
import type { StyleConfig } from '@/lib/types';

export default function PreviewCard({ data, style }: { data: string; style: StyleConfig }) {
  const color = getFrameColor(style);
  const text = (style.frameText || '').toUpperCase();

  return (
    <div className="relative">
      <div className={data ? '' : 'opacity-40 blur-[1.5px] transition'}>
        {style.frameStyle === 'none' && (
          <div className="mx-auto w-fit rounded-2xl bg-white p-3 shadow-sm ring-1 ring-slate-200">
            <div className="w-[236px] sm:w-[268px]">
              <QrPreview data={data} style={style} />
            </div>
          </div>
        )}
        {style.frameStyle === 'banner' && (
          <div className="mx-auto w-fit overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
            <div className="p-3">
              <div className="w-[236px] sm:w-[268px]">
                <QrPreview data={data} style={style} />
              </div>
            </div>
            <div
              className="px-6 py-2.5 text-center text-[13px] font-bold tracking-[0.25em] text-white"
              style={{ backgroundColor: color }}
            >
              {text}
            </div>
          </div>
        )}
        {style.frameStyle === 'outline' && (
          <div
            className="mx-auto w-fit rounded-2xl bg-white p-3 shadow-sm"
            style={{ border: `3px solid ${color}` }}
          >
            <div className="w-[236px] sm:w-[268px]">
              <QrPreview data={data} style={style} />
            </div>
            <p
              className="px-4 pb-1 pt-2.5 text-center text-[13px] font-bold tracking-[0.25em]"
              style={{ color }}
            >
              {text}
            </p>
          </div>
        )}
      </div>
      {!data && (
        <div className="pointer-events-none absolute inset-0 grid place-items-center">
          <span className="rounded-full bg-slate-900/85 px-4 py-2 text-xs font-medium text-white shadow-lg backdrop-blur">
            Completa el contenido para ver tu QR
          </span>
        </div>
      )}
    </div>
  );
}
