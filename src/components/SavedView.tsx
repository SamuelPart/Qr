'use client';

import { Bookmark, Trash2 } from 'lucide-react';
import type { SavedDesign } from '@/lib/types';

export default function SavedView({
  designs,
  onOpen,
  onDelete,
}: {
  designs: SavedDesign[];
  onOpen: (d: SavedDesign) => void;
  onDelete: (id: string) => void;
}) {
  if (designs.length === 0) {
    return (
      <div className="mx-auto max-w-md rounded-2xl bg-white p-8 text-center shadow-sm ring-1 ring-slate-200">
        <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-indigo-50">
          <Bookmark className="h-5 w-5 text-indigo-500" />
        </div>
        <h2 className="text-sm font-semibold text-slate-800">Aún no tienes QR guardados</h2>
        <p className="mt-1.5 text-xs leading-relaxed text-slate-500">
          Crea un diseño en la pestaña <strong>Crear</strong> y pulsa <strong>Guardar</strong> para
          tenerlo siempre a mano en este dispositivo.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between px-1">
        <h2 className="text-sm font-semibold text-slate-800">Mis QR guardados</h2>
        <span className="text-xs text-slate-400">{designs.length} diseño(s)</span>
      </div>
      <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
        {designs.map((d) => (
          <div
            key={d.id}
            className="group flex items-center gap-3 rounded-2xl bg-white p-3 shadow-sm ring-1 ring-slate-200 transition hover:ring-indigo-300"
          >
            <button type="button" onClick={() => onOpen(d)} className="flex min-w-0 flex-1 items-center gap-3 text-left">
              <img
                src={d.thumb}
                alt={`Miniatura de ${d.name}`}
                className="h-16 w-16 shrink-0 rounded-xl bg-white object-contain ring-1 ring-slate-100"
              />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-800">{d.name}</p>
                <p className="mt-0.5 text-[11px] text-slate-400">
                  {new Date(d.createdAt).toLocaleDateString('es-PE', {
                    day: '2-digit',
                    month: 'short',
                    year: 'numeric',
                  })}
                </p>
              </div>
            </button>
            <button
              type="button"
              onClick={() => onDelete(d.id)}
              aria-label={`Eliminar ${d.name}`}
              className="rounded-lg p-2 text-slate-300 transition hover:bg-rose-50 hover:text-rose-500"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
