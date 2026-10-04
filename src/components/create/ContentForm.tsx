'use client';

import { SCHEMAS } from '@/lib/content';
import type { ContentType, FieldValues } from '@/lib/types';
import { CheckboxField, SelectField, TextAreaField, TextField } from '@/components/ui';

export default function ContentForm({
  type,
  values,
  onChange,
}: {
  type: ContentType;
  values: FieldValues;
  onChange: (key: string, v: string | boolean) => void;
}) {
  return (
    <div className="space-y-3.5">
      {SCHEMAS[type].map((f) => {
        const raw = values[f.key];
        const strVal = typeof raw === 'string' ? raw : '';
        const boolVal = raw === true;
        switch (f.kind) {
          case 'checkbox':
            return (
              <CheckboxField
                key={f.key}
                label={f.label}
                checked={boolVal}
                onChange={(v) => onChange(f.key, v)}
              />
            );
          case 'select':
            return (
              <SelectField
                key={f.key}
                label={f.label}
                value={strVal || String(f.default ?? '')}
                onChange={(v) => onChange(f.key, v)}
                options={f.options ?? []}
              />
            );
          case 'textarea':
            return (
              <TextAreaField
                key={f.key}
                label={f.label}
                value={strVal}
                onChange={(v) => onChange(f.key, v)}
                placeholder={f.placeholder}
                required={f.required}
              />
            );
          default:
            return (
              <TextField
                key={f.key}
                label={f.label}
                value={strVal}
                onChange={(v) => onChange(f.key, v)}
                placeholder={f.placeholder}
                kind={f.kind}
                help={f.help}
                required={f.required}
              />
            );
        }
      })}
    </div>
  );
}
