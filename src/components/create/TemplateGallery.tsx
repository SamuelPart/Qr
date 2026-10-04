'use client';

import { TEMPLATES, type Template } from '@/lib/templates';

export default function TemplateGallery({ onApply }: { onApply: (t: Template) => void }) {
  return (
    <div className="no-scrollbar -mx-1 flex gap-2.5 overflow-x-auto px-1 pb-1">
      {TEMPLATES.map((t) => {
        const bg =
          t.patch.fgMode === 'gradient'
            ? `linear-gradient(135deg, ${t.patch.fgColor}, ${t.patch.fgColor2 ?? t.patch.fgColor})`
            : (t.patch.fgColor ?? '#111827');
        return (
          <button
            key={t.id}
            type="button"
            onClick={() => onApply(t)}
            className="flex w-[86px] shrink-0 flex-col items-center gap-1.5 rounded-2xl bg-white p-2.5 ring-1 ring-slate-200 transition hover:ring-indigo-300"
          >
            <span
              className="grid h-10 w-10 place-items-center rounded-xl text-lg shadow-inner"
              style={{ background: bg }}
            >
              {t.emoji}
            </span>
            <span className="text-center text-[11px] font-medium leading-tight text-slate-600">
              {t.name}
            </span>
          </button>
        );
      })}
    </div>
  );
}
