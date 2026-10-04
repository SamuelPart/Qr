'use client';

import { Bookmark, QrCode, ScanLine, Sparkles } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import BottomNav, { type Tab } from '@/components/BottomNav';
import ContentForm from '@/components/create/ContentForm';
import ExportPanel from '@/components/create/ExportPanel';
import PreviewCard from '@/components/create/PreviewCard';
import StylePanel from '@/components/create/StylePanel';
import TemplateGallery from '@/components/create/TemplateGallery';
import TypePicker from '@/components/create/TypePicker';
import SavedView from '@/components/SavedView';
import ScanView from '@/components/ScanView';
import { buildPayload, CONTENT_TYPES, defaultValues, designName } from '@/lib/content';
import { addSaved, loadDraft, loadSaved, removeSaved, saveDraft } from '@/lib/storage';
import { DEFAULT_STYLE, type Template } from '@/lib/templates';
import type { ContentType, FieldValues, SavedDesign, StyleConfig } from '@/lib/types';

function initialAllValues(): Record<string, FieldValues> {
  const out: Record<string, FieldValues> = {};
  for (const t of CONTENT_TYPES) out[t.id] = defaultValues(t.id);
  return out;
}

export default function Home() {
  const draft = useMemo(() => loadDraft(), []);
  const [tab, setTab] = useState<Tab>('create');
  const [contentType, setContentType] = useState<ContentType>(draft?.contentType ?? 'url');
  const [valuesMap, setValuesMap] = useState<Record<string, FieldValues>>(() => {
    const base = initialAllValues();
    if (draft?.valuesMap) {
      for (const k of Object.keys(base)) {
        if (draft.valuesMap[k]) base[k] = { ...base[k], ...draft.valuesMap[k] };
      }
    }
    return base;
  });
  const [style, setStyle] = useState<StyleConfig>(() => ({ ...DEFAULT_STYLE, ...(draft?.style ?? {}) }));
  const [saved, setSaved] = useState<SavedDesign[]>(() => loadSaved());
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2600);
  }, []);

  // Autoguardado del borrador (debounce)
  useEffect(() => {
    const t = window.setTimeout(() => saveDraft({ contentType, valuesMap, style }), 400);
    return () => window.clearTimeout(t);
  }, [contentType, valuesMap, style]);

  const values = valuesMap[contentType] ?? {};
  const payload = useMemo(() => buildPayload(contentType, values), [contentType, values]);

  const patchStyle = useCallback((p: Partial<StyleConfig>) => setStyle((s) => ({ ...s, ...p })), []);

  const setValue = useCallback(
    (key: string, v: string | boolean) => {
      setValuesMap((m) => ({ ...m, [contentType]: { ...m[contentType], [key]: v } }));
    },
    [contentType],
  );

  const applyTemplate = useCallback(
    (t: Template) => {
      setStyle({ ...DEFAULT_STYLE, ...t.patch });
      showToast(`Plantilla “${t.name}” aplicada`);
    },
    [showToast],
  );

  const openDesign = useCallback(
    (d: SavedDesign) => {
      setContentType(d.contentType);
      setValuesMap((m) => ({
        ...m,
        [d.contentType]: { ...defaultValues(d.contentType), ...d.values },
      }));
      setStyle({ ...DEFAULT_STYLE, ...d.style });
      setTab('create');
      showToast('Diseño cargado en el editor');
    },
    [showToast],
  );

  const useScanContent = useCallback(
    (text: string) => {
      const looksLikeUrl = /^(https?:\/\/|www\.)/i.test(text);
      if (looksLikeUrl) {
        setContentType('url');
        setValuesMap((m) => ({ ...m, url: { ...m.url, link: text } }));
      } else {
        setContentType('text');
        setValuesMap((m) => ({ ...m, text: { ...m.text, value: text } }));
      }
      setTab('create');
      showToast('Contenido cargado en el editor');
    },
    [showToast],
  );

  const navItems: { id: Tab; label: string; icon: typeof QrCode }[] = [
    { id: 'create', label: 'Crear', icon: QrCode },
    { id: 'scan', label: 'Escanear', icon: ScanLine },
    { id: 'saved', label: 'Mis QR', icon: Bookmark },
  ];

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between px-4">
          <div className="flex items-center gap-2.5">
            <div className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm">
              <QrCode className="h-[18px] w-[18px]" strokeWidth={2.2} />
            </div>
            <div>
              <p className="text-sm font-bold leading-none tracking-tight text-slate-900">QR Studio</p>
              <p className="mt-0.5 text-[10px] font-medium leading-none text-slate-400">
                100% en tu dispositivo · gratis
              </p>
            </div>
          </div>
          <nav className="hidden gap-1 md:flex">
            {navItems.map((item) => {
              const Icon = item.icon;
              const active = tab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setTab(item.id)}
                  className={`flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold transition ${
                    active ? 'bg-slate-900 text-white' : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800'
                  }`}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl px-4 pb-28 pt-5 md:pb-14">
        {tab === 'create' && (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:items-start">
            <div className="order-2 space-y-4 lg:order-1">
              <section className="space-y-3.5 rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
                <div className="flex items-center gap-2">
                  <span className="grid h-5 w-5 place-items-center rounded-md bg-slate-900 text-[11px] font-bold text-white">
                    1
                  </span>
                  <h2 className="text-sm font-semibold text-slate-800">Contenido del QR</h2>
                </div>
                <TypePicker value={contentType} onChange={setContentType} />
                <ContentForm type={contentType} values={values} onChange={setValue} />
              </section>

              <section className="space-y-3 rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
                <div className="flex items-center gap-2">
                  <span className="grid h-5 w-5 place-items-center rounded-md bg-slate-900 text-[11px] font-bold text-white">
                    2
                  </span>
                  <h2 className="text-sm font-semibold text-slate-800">Plantillas por negocio</h2>
                  <Sparkles className="h-3.5 w-3.5 text-amber-400" />
                </div>
                <TemplateGallery onApply={applyTemplate} />
              </section>

              <section className="space-y-3">
                <div className="flex items-center gap-2 px-1">
                  <span className="grid h-5 w-5 place-items-center rounded-md bg-slate-900 text-[11px] font-bold text-white">
                    3
                  </span>
                  <h2 className="text-sm font-semibold text-slate-800">Diseño a tu marca</h2>
                </div>
                <StylePanel style={style} onChange={patchStyle} onToast={showToast} />
              </section>
            </div>

            <div className="order-1 space-y-4 lg:order-2 lg:sticky lg:top-20">
              <PreviewCard data={payload} style={style} />
              <ExportPanel
                data={payload}
                style={style}
                name={designName(contentType, values)}
                contentType={contentType}
                values={values}
                onToast={showToast}
                onSave={(d) => setSaved(addSaved(d))}
              />
            </div>
          </div>
        )}

        {tab === 'scan' && <ScanView onUseContent={useScanContent} />}

        {tab === 'saved' && (
          <SavedView
            designs={saved}
            onOpen={openDesign}
            onDelete={(id) => {
              setSaved(removeSaved(id));
              showToast('Diseño eliminado');
            }}
          />
        )}
      </main>

      <BottomNav tab={tab} onChange={setTab} />

      {toast && (
        <div className="fixed left-1/2 top-4 z-50 max-w-[92vw] -translate-x-1/2 truncate rounded-full bg-slate-900 px-4 py-2 text-xs font-medium text-white shadow-lg">
          {toast}
        </div>
      )}
    </div>
  );
}
