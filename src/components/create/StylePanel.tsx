'use client';

import { Frame, Image as ImageIcon, Palette, Settings2, Shapes, Trash2, Upload } from 'lucide-react';
import { useRef } from 'react';
import {
  CheckboxField,
  ColorField,
  Section,
  Segmented,
  SliderField,
  TextField,
} from '@/components/ui';
import { emojiToLogoDataUrl, fileToLogoDataUrl } from '@/lib/imageUtils';
import type { CornerDotType, CornerSquareType, DotType, FrameStyle, StyleConfig } from '@/lib/types';

const DOT_OPTIONS: { value: DotType; label: string; path: React.ReactNode }[] = [
  { value: 'square', label: 'Cuadrado', path: <rect x="7" y="7" width="18" height="18" rx="2" /> },
  { value: 'rounded', label: 'Redondeado', path: <rect x="7" y="7" width="18" height="18" rx="6" /> },
  {
    value: 'extra-rounded',
    label: 'Muy redondo',
    path: <rect x="7" y="7" width="18" height="18" rx="9" />,
  },
  { value: 'dots', label: 'Puntos', path: <circle cx="16" cy="16" r="9.5" /> },
  {
    value: 'classy',
    label: 'Classy',
    path: <path d="M7 25 V16 A9 9 0 0 1 25 16 V25 Z" />,
  },
  {
    value: 'classy-rounded',
    label: 'Classy red.',
    path: <path d="M7 21 V16 A9 9 0 0 1 25 16 V21 A4 4 0 0 1 21 25 H11 A4 4 0 0 1 7 21 Z" />,
  },
];

const EMOJIS = [
  '😀', '😎', '🥳', '❤️', '🍔', '🍕', '🌮', '🍰',
  '🍺', '☕', '🛍️', '🎁', '💈', '💇\u200d♀️', '💪', '⚽',
  '🏨', '🛏️', '🏠', '🏢', '🩺', '💊', '🐶', '🐱',
  '🚗', '✈️', '🎵', '📸', '🎸', '💍', '⭐', '🔥',
];

const FRAME_TEXTS = ['ESCANÉAME', 'MENÚ', 'WIFI GRATIS', 'SÍGUEME', 'RESERVA', 'MÁS INFO', 'GRACIAS', 'OFERTAS'];

export default function StylePanel({
  style,
  onChange,
  onToast,
}: {
  style: StyleConfig;
  onChange: (patch: Partial<StyleConfig>) => void;
  onToast: (msg: string) => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);

  const handleLogoFile = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      onToast('El archivo debe ser una imagen');
      return;
    }
    try {
      const dataUrl = await fileToLogoDataUrl(file);
      onChange({ logo: dataUrl });
      onToast('Logo agregado');
    } catch {
      onToast('No se pudo leer la imagen');
    }
  };

  return (
    <div className="space-y-3">
      <Section title="Formas y patrones" icon={<Shapes className="h-4 w-4 text-indigo-500" />} defaultOpen>
        <div>
          <p className="mb-1.5 text-xs font-medium text-slate-500">Estilo de los puntos</p>
          <div className="grid grid-cols-3 gap-2">
            {DOT_OPTIONS.map((o) => (
              <button
                key={o.value}
                type="button"
                onClick={() => onChange({ dotType: o.value })}
                className={`flex flex-col items-center gap-1 rounded-xl p-2 ring-1 transition ${
                  style.dotType === o.value
                    ? 'bg-indigo-50 ring-indigo-400'
                    : 'bg-white ring-slate-200 hover:ring-slate-300'
                }`}
              >
                <svg viewBox="0 0 32 32" className="h-7 w-7 fill-slate-800">
                  {o.path}
                </svg>
                <span className="text-[10px] font-medium text-slate-600">{o.label}</span>
              </button>
            ))}
          </div>
        </div>
        <div>
          <p className="mb-1.5 text-xs font-medium text-slate-500">Esquinas (cuadrados grandes)</p>
          <Segmented<CornerSquareType>
            options={[
              { value: 'square', label: 'Cuadrada' },
              { value: 'dot', label: 'Circular' },
              { value: 'extra-rounded', label: 'Redondeada' },
            ]}
            value={style.cornerSquareType}
            onChange={(v) => onChange({ cornerSquareType: v })}
          />
        </div>
        <div>
          <p className="mb-1.5 text-xs font-medium text-slate-500">Punto central de esquinas</p>
          <Segmented<CornerDotType>
            options={[
              { value: 'square', label: 'Cuadrado' },
              { value: 'dot', label: 'Circular' },
            ]}
            value={style.cornerDotType}
            onChange={(v) => onChange({ cornerDotType: v })}
          />
        </div>
      </Section>

      <Section title="Colores" icon={<Palette className="h-4 w-4 text-indigo-500" />} defaultOpen>
        <div>
          <p className="mb-1.5 text-xs font-medium text-slate-500">Color del código</p>
          <Segmented
            options={[
              { value: 'solid', label: 'Sólido' },
              { value: 'gradient', label: 'Degradado' },
            ]}
            value={style.fgMode}
            onChange={(v) => onChange({ fgMode: v })}
          />
        </div>
        <ColorField label="Color principal" value={style.fgColor} onChange={(v) => onChange({ fgColor: v })} />
        {style.fgMode === 'gradient' && (
          <>
            <ColorField label="Segundo color" value={style.fgColor2} onChange={(v) => onChange({ fgColor2: v })} />
            <div>
              <p className="mb-1.5 text-xs font-medium text-slate-500">Tipo de degradado</p>
              <Segmented
                options={[
                  { value: 'linear', label: 'Lineal' },
                  { value: 'radial', label: 'Radial' },
                ]}
                value={style.gradientType}
                onChange={(v) => onChange({ gradientType: v })}
              />
            </div>
            {style.gradientType === 'linear' && (
              <SliderField
                label="Rotación del degradado"
                value={style.gradientRotation}
                min={0}
                max={360}
                step={5}
                onChange={(v) => onChange({ gradientRotation: v })}
                format={(v) => `${v}°`}
              />
            )}
          </>
        )}
        <div className="border-t border-slate-100 pt-3.5">
          <p className="mb-1.5 text-xs font-medium text-slate-500">Fondo</p>
          <Segmented
            options={[
              { value: 'solid', label: 'Sólido' },
              { value: 'gradient', label: 'Degradado' },
              { value: 'transparent', label: 'Transparente' },
            ]}
            value={style.bgMode}
            onChange={(v) => onChange({ bgMode: v })}
          />
        </div>
        {style.bgMode !== 'transparent' && (
          <ColorField label="Color de fondo" value={style.bgColor} onChange={(v) => onChange({ bgColor: v })} />
        )}
        {style.bgMode === 'gradient' && (
          <ColorField label="Segundo color de fondo" value={style.bgColor2} onChange={(v) => onChange({ bgColor2: v })} />
        )}
        <div className="border-t border-slate-100 pt-3.5">
          <p className="mb-1.5 text-xs font-medium text-slate-500">Ojos (esquinas)</p>
          <Segmented
            options={[
              { value: 'same', label: 'Igual al código' },
              { value: 'custom', label: 'Personalizado' },
            ]}
            value={style.eyeColorMode}
            onChange={(v) => onChange({ eyeColorMode: v })}
          />
          {style.eyeColorMode === 'custom' && (
            <div className="mt-3">
              <ColorField label="Color de ojos" value={style.eyeColor} onChange={(v) => onChange({ eyeColor: v })} />
            </div>
          )}
        </div>
      </Section>

      <Section title="Logo o ícono central" icon={<ImageIcon className="h-4 w-4 text-indigo-500" />}>
        {style.logo && (
          <div className="flex items-center gap-3">
            <img
              src={style.logo}
              alt="Logo actual"
              className="h-14 w-14 rounded-xl bg-white object-contain ring-1 ring-slate-200"
            />
            <button
              type="button"
              onClick={() => onChange({ logo: null })}
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-rose-600 ring-1 ring-rose-200 transition hover:bg-rose-50"
            >
              <Trash2 className="h-3.5 w-3.5" /> Quitar logo
            </button>
          </div>
        )}
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="flex items-center gap-2 rounded-xl bg-slate-900 px-3.5 py-2.5 text-xs font-semibold text-white transition hover:bg-slate-700"
          >
            <Upload className="h-3.5 w-3.5" /> Subir logo (PNG/SVG)
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void handleLogoFile(f);
              e.target.value = '';
            }}
          />
        </div>
        <div>
          <p className="mb-1.5 text-xs font-medium text-slate-500">O elige un ícono</p>
          <div className="grid grid-cols-8 gap-1.5">
            {EMOJIS.map((e) => (
              <button
                key={e}
                type="button"
                onClick={() => {
                  onChange({ logo: emojiToLogoDataUrl(e) });
                  onToast('Ícono agregado como logo');
                }}
                className="grid aspect-square place-items-center rounded-lg text-lg ring-1 ring-slate-200 transition hover:bg-slate-50 hover:ring-indigo-300"
              >
                {e}
              </button>
            ))}
          </div>
        </div>
        {style.logo && (
          <>
            <SliderField
              label="Tamaño del logo"
              value={style.logoSize}
              min={0.15}
              max={0.45}
              step={0.01}
              onChange={(v) => onChange({ logoSize: v })}
              format={(v) => `${Math.round(v * 100)}%`}
            />
            <SliderField
              label="Margen del logo"
              value={style.logoMargin}
              min={0}
              max={48}
              step={2}
              onChange={(v) => onChange({ logoMargin: v })}
            />
            <CheckboxField
              label="Ocultar puntos detrás del logo"
              checked={style.hideLogoBgDots}
              onChange={(v) => onChange({ hideLogoBgDots: v })}
            />
            <p className="text-[11px] leading-relaxed text-slate-400">
              Consejo: con logo, usa corrección de error <strong>Q</strong> o <strong>H</strong> en
              “Avanzado” para que el QR siga escaneando bien.
            </p>
          </>
        )}
      </Section>

      <Section title="Marco y texto" icon={<Frame className="h-4 w-4 text-indigo-500" />}>
        <Segmented<FrameStyle>
          options={[
            { value: 'none', label: 'Sin marco' },
            { value: 'banner', label: 'Banner' },
            { value: 'outline', label: 'Contorno' },
          ]}
          value={style.frameStyle}
          onChange={(v) => onChange({ frameStyle: v })}
        />
        {style.frameStyle !== 'none' && (
          <>
            <TextField
              label="Texto del marco"
              value={style.frameText}
              onChange={(v) => onChange({ frameText: v })}
              placeholder="ESCANÉAME"
            />
            <div className="flex flex-wrap gap-1.5">
              {FRAME_TEXTS.map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => onChange({ frameText: t })}
                  className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1 transition ${
                    style.frameText === t
                      ? 'bg-slate-900 text-white ring-slate-900'
                      : 'bg-white text-slate-500 ring-slate-200 hover:ring-slate-300'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
            <div>
              <p className="mb-1.5 text-xs font-medium text-slate-500">Color del marco</p>
              <Segmented
                options={[
                  { value: 'auto', label: 'Automático' },
                  { value: 'custom', label: 'Personalizado' },
                ]}
                value={style.frameColorMode}
                onChange={(v) => onChange({ frameColorMode: v })}
              />
              {style.frameColorMode === 'custom' && (
                <div className="mt-3">
                  <ColorField
                    label="Color del marco"
                    value={style.frameColor}
                    onChange={(v) => onChange({ frameColor: v })}
                  />
                </div>
              )}
            </div>
          </>
        )}
      </Section>

      <Section title="Avanzado" icon={<Settings2 className="h-4 w-4 text-indigo-500" />}>
        <SliderField
          label="Margen exterior (zona de silencio)"
          value={style.margin}
          min={0}
          max={160}
          step={4}
          onChange={(v) => onChange({ margin: v })}
        />
        <div>
          <p className="mb-1.5 text-xs font-medium text-slate-500">Corrección de errores</p>
          <Segmented
            options={[
              { value: 'L', label: 'L · 7%' },
              { value: 'M', label: 'M · 15%' },
              { value: 'Q', label: 'Q · 25%' },
              { value: 'H', label: 'H · 30%' },
            ]}
            value={style.errorCorrection}
            onChange={(v) => onChange({ errorCorrection: v })}
          />
          <p className="mt-1.5 text-[11px] leading-relaxed text-slate-400">
            Más corrección = más resistente a daños y logos, pero QR más denso. Q o H recomendados
            con logo.
          </p>
        </div>
      </Section>
    </div>
  );
}
