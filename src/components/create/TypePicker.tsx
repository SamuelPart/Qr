'use client';

import { CONTENT_TYPES } from '@/lib/content';
import type { ContentType } from '@/lib/types';

export default function TypePicker({
  value,
  onChange,
}: {
  value: ContentType;
  onChange: (t: ContentType) => void;
}) {
  return (
    <div className="no-scrollbar -mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
      {CONTENT_TYPES.map((t) => (
        <button
          key={t.id}
          type="button"
          onClick={() => onChange(t.id)}
          className={`flex shrink-0 items-center gap-1.5 rounded-full px-3.5 py-2 text-xs font-semibold ring-1 transition ${
            value === t.id
              ? 'bg-slate-900 text-white ring-slate-900'
              : 'bg-white text-slate-600 ring-slate-200 hover:ring-slate-300'
          }`}
        >
          <span className="text-sm leading-none">{t.emoji}</span>
          {t.label}
        </button>
      ))}
    </div>
  );
}
