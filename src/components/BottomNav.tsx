'use client';

import { Bookmark, QrCode, ScanLine } from 'lucide-react';

export type Tab = 'create' | 'scan' | 'saved';

const ITEMS: { id: Tab; label: string; icon: typeof QrCode }[] = [
  { id: 'create', label: 'Crear', icon: QrCode },
  { id: 'scan', label: 'Escanear', icon: ScanLine },
  { id: 'saved', label: 'Mis QR', icon: Bookmark },
];

export default function BottomNav({ tab, onChange }: { tab: Tab; onChange: (t: Tab) => void }) {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 backdrop-blur md:hidden"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <div className="mx-auto flex h-16 max-w-md">
        {ITEMS.map((item) => {
          const Icon = item.icon;
          const active = tab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onChange(item.id)}
              className={`flex flex-1 flex-col items-center justify-center gap-1 text-[11px] font-medium transition ${
                active ? 'text-indigo-600' : 'text-slate-400 hover:text-slate-600'
              }`}
            >
              <Icon className={`h-5 w-5 ${active ? 'stroke-[2.2]' : ''}`} />
              {item.label}
            </button>
          );
        })}
      </div>
    </nav>
  );
}
