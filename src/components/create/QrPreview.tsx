'use client';

import { useEffect, useRef } from 'react';
import type QRCodeStyling from 'qr-code-styling';
import { buildQrOptions } from '@/lib/qrStyle';
import type { StyleConfig } from '@/lib/types';

export default function QrPreview({ data, style }: { data: string; style: StyleConfig }) {
  const holder = useRef<HTMLDivElement>(null);
  const inst = useRef<QRCodeStyling | null>(null);
  const ready = useRef(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      const mod = await import('qr-code-styling');
      if (!alive || !holder.current) return;
      const payload = data || 'qr.studio';
      if (!ready.current) {
        inst.current = new mod.default(buildQrOptions(payload, style, 1024));
        inst.current.append(holder.current);
        ready.current = true;
      } else if (inst.current) {
        inst.current.update(buildQrOptions(payload, style, 1024));
      }
      holder.current.querySelectorAll('canvas, svg').forEach((el) => {
        const e = el as HTMLElement;
        e.style.width = '100%';
        e.style.height = 'auto';
        e.style.display = 'block';
      });
    })();
    return () => {
      alive = false;
    };
  }, [data, style]);

  return <div ref={holder} className="w-full" />;
}
