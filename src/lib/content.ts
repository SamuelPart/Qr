import type { ContentType, FieldDef, FieldValues } from './types';

export const CONTENT_TYPES: { id: ContentType; label: string; emoji: string }[] = [
  { id: 'url', label: 'Enlace', emoji: '🔗' },
  { id: 'text', label: 'Texto', emoji: '📝' },
  { id: 'wifi', label: 'WiFi', emoji: '📶' },
  { id: 'whatsapp', label: 'WhatsApp', emoji: '💬' },
  { id: 'vcard', label: 'Contacto', emoji: '📇' },
  { id: 'social', label: 'Redes', emoji: '📸' },
  { id: 'email', label: 'Email', emoji: '✉️' },
  { id: 'phone', label: 'Llamada', emoji: '📞' },
  { id: 'sms', label: 'SMS', emoji: '💭' },
  { id: 'location', label: 'Ubicación', emoji: '📍' },
  { id: 'event', label: 'Evento', emoji: '📅' },
];

export const SCHEMAS: Record<ContentType, FieldDef[]> = {
  url: [
    {
      key: 'link',
      label: 'Enlace o URL',
      kind: 'url',
      placeholder: 'minnegocio.com o https://…',
      required: true,
      help: 'Si no escribes el protocolo, se agregará https:// automáticamente.',
    },
  ],
  text: [
    {
      key: 'value',
      label: 'Texto',
      kind: 'textarea',
      placeholder: 'Escribe el texto que contendrá el QR…',
      required: true,
    },
  ],
  wifi: [
    { key: 'ssid', label: 'Nombre de la red (SSID)', kind: 'text', placeholder: 'MiWiFi-Negocio', required: true },
    { key: 'password', label: 'Contraseña', kind: 'text', placeholder: '••••••••' },
    {
      key: 'encryption',
      label: 'Seguridad',
      kind: 'select',
      default: 'WPA',
      options: [
        { value: 'WPA', label: 'WPA / WPA2 / WPA3' },
        { value: 'WEP', label: 'WEP' },
        { value: 'nopass', label: 'Red abierta (sin contraseña)' },
      ],
    },
    { key: 'hidden', label: 'Red oculta', kind: 'checkbox', default: false },
  ],
  vcard: [
    { key: 'firstName', label: 'Nombre', kind: 'text', placeholder: 'María' },
    { key: 'lastName', label: 'Apellidos', kind: 'text', placeholder: 'Quispe Ramos' },
    { key: 'org', label: 'Empresa', kind: 'text', placeholder: 'Café Trujillo S.A.C.' },
    { key: 'title', label: 'Cargo', kind: 'text', placeholder: 'Gerente general' },
    { key: 'phone', label: 'Teléfono', kind: 'tel', placeholder: '+51 987 654 321' },
    { key: 'email', label: 'Email', kind: 'email', placeholder: 'maria@cafetrujillo.pe' },
    { key: 'website', label: 'Sitio web', kind: 'url', placeholder: 'cafetrujillo.pe' },
    { key: 'address', label: 'Dirección', kind: 'text', placeholder: 'Jr. Pizarro 456, Trujillo' },
    { key: 'note', label: 'Nota', kind: 'textarea', placeholder: 'Texto adicional opcional' },
  ],
  email: [
    { key: 'to', label: 'Para', kind: 'email', placeholder: 'contacto@minnegocio.pe', required: true },
    { key: 'subject', label: 'Asunto', kind: 'text', placeholder: 'Consulta' },
    { key: 'body', label: 'Mensaje', kind: 'textarea', placeholder: 'Hola, quisiera…' },
  ],
  phone: [
    { key: 'number', label: 'Número de teléfono', kind: 'tel', placeholder: '+51 987 654 321', required: true },
  ],
  sms: [
    { key: 'number', label: 'Número de teléfono', kind: 'tel', placeholder: '+51 987 654 321', required: true },
    { key: 'message', label: 'Mensaje', kind: 'textarea', placeholder: 'Texto prellenado del SMS' },
  ],
  whatsapp: [
    {
      key: 'number',
      label: 'Número de WhatsApp',
      kind: 'tel',
      placeholder: '51987654321',
      required: true,
      help: 'Con código de país, sin "+" ni espacios. Ej.: 51987654321',
    },
    { key: 'message', label: 'Mensaje prellenado', kind: 'textarea', placeholder: 'Hola, quiero más información 😊' },
  ],
  social: [
    {
      key: 'platform',
      label: 'Plataforma',
      kind: 'select',
      default: 'instagram',
      options: [
        { value: 'instagram', label: 'Instagram' },
        { value: 'facebook', label: 'Facebook' },
        { value: 'tiktok', label: 'TikTok' },
        { value: 'x', label: 'X (Twitter)' },
        { value: 'youtube', label: 'YouTube' },
        { value: 'linkedin', label: 'LinkedIn' },
      ],
    },
    {
      key: 'handle',
      label: 'Usuario o página',
      kind: 'text',
      placeholder: 'minnegocio',
      required: true,
      help: 'Sin "@". También puedes pegar una ruta completa, ej.: minnegocio/ofertas',
    },
  ],
  location: [
    { key: 'lat', label: 'Latitud', kind: 'number', placeholder: '-8.11161', required: true },
    { key: 'lng', label: 'Longitud', kind: 'number', placeholder: '-79.02867', required: true },
    { key: 'label', label: 'Nombre del lugar', kind: 'text', placeholder: 'Café Trujillo — Plaza de Armas' },
  ],
  event: [
    { key: 'title', label: 'Título del evento', kind: 'text', placeholder: 'Gran inauguración', required: true },
    { key: 'start', label: 'Inicio', kind: 'datetime', required: true },
    { key: 'end', label: 'Fin (opcional)', kind: 'datetime' },
    { key: 'location', label: 'Lugar', kind: 'text', placeholder: 'Jr. Pizarro 456, Trujillo' },
    { key: 'description', label: 'Descripción', kind: 'textarea', placeholder: 'Detalles del evento…' },
  ],
};

export function defaultValues(type: ContentType): FieldValues {
  const out: FieldValues = {};
  for (const f of SCHEMAS[type]) {
    out[f.key] = f.default !== undefined ? f.default : '';
  }
  return out;
}

const str = (v: FieldValues[string] | undefined) => (typeof v === 'string' ? v.trim() : '');

function normalizeUrl(raw: string): string {
  const t = raw.trim();
  if (!t) return '';
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(t)) return t; // ya tiene esquema
  return `https://${t}`;
}

function escWifi(s: string): string {
  return s.replace(/([\\;,:"])/g, '\\$1');
}

function toGCalDate(d: Date): string {
  return d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '') + 'Z';
}

export function buildPayload(type: ContentType, values: FieldValues): string {
  switch (type) {
    case 'url':
      return normalizeUrl(str(values.link));
    case 'text':
      return str(values.value);
    case 'wifi': {
      const ssid = str(values.ssid);
      if (!ssid) return '';
      const enc = str(values.encryption) || 'WPA';
      const pass = str(values.password);
      const hidden = values.hidden === true;
      let out = `WIFI:T:${enc};S:${escWifi(ssid)};`;
      if (enc !== 'nopass' && pass) out += `P:${escWifi(pass)};`;
      if (hidden) out += 'H:true;';
      return out + ';';
    }
    case 'vcard': {
      const first = str(values.firstName);
      const last = str(values.lastName);
      const org = str(values.org);
      let fn = [first, last].filter(Boolean).join(' ') || org || str(values.title);
      if (!fn) fn = str(values.phone) || str(values.email);
      if (!fn) return '';
      const lines = ['BEGIN:VCARD', 'VERSION:3.0', `N:${last};${first}`, `FN:${fn}`];
      if (org) lines.push(`ORG:${org}`);
      if (str(values.title)) lines.push(`TITLE:${str(values.title)}`);
      if (str(values.phone)) lines.push(`TEL;TYPE=CELL:${str(values.phone)}`);
      if (str(values.email)) lines.push(`EMAIL:${str(values.email)}`);
      const web = normalizeUrl(str(values.website));
      if (web) lines.push(`URL:${web}`);
      if (str(values.address)) lines.push(`ADR;TYPE=WORK:;;${str(values.address)};;;;`);
      if (str(values.note)) lines.push(`NOTE:${str(values.note)}`);
      lines.push('END:VCARD');
      return lines.join('\r\n');
    }
    case 'email': {
      const to = str(values.to);
      if (!to) return '';
      const params: string[] = [];
      if (str(values.subject)) params.push(`subject=${encodeURIComponent(str(values.subject))}`);
      if (str(values.body)) params.push(`body=${encodeURIComponent(str(values.body))}`);
      return `mailto:${to}${params.length ? `?${params.join('&')}` : ''}`;
    }
    case 'phone': {
      const n = str(values.number);
      return n ? `tel:${n.replace(/\s+/g, '')}` : '';
    }
    case 'sms': {
      const n = str(values.number);
      if (!n) return '';
      const msg = str(values.message);
      return `SMSTO:${n.replace(/\s+/g, '')}:${msg}`;
    }
    case 'whatsapp': {
      const n = str(values.number).replace(/[^\d]/g, '');
      if (!n) return '';
      const msg = str(values.message);
      return `https://wa.me/${n}${msg ? `?text=${encodeURIComponent(msg)}` : ''}`;
    }
    case 'social': {
      const handle = str(values.handle).replace(/^@+/, '');
      if (!handle) return '';
      const platform = str(values.platform) || 'instagram';
      const map: Record<string, (h: string) => string> = {
        instagram: (h) => `https://instagram.com/${h}`,
        facebook: (h) => `https://facebook.com/${h}`,
        tiktok: (h) => `https://tiktok.com/@${h}`,
        x: (h) => `https://x.com/${h}`,
        youtube: (h) => `https://youtube.com/@${h}`,
        linkedin: (h) => (h.includes('/') ? `https://linkedin.com/${h}` : `https://linkedin.com/in/${h}`),
      };
      return (map[platform] ?? map.instagram)(handle);
    }
    case 'location': {
      const lat = str(values.lat);
      const lng = str(values.lng);
      if (!lat || !lng || isNaN(Number(lat)) || isNaN(Number(lng))) return '';
      const label = str(values.label);
      return `geo:${lat},${lng}?q=${lat},${lng}${label ? `(${encodeURIComponent(label)})` : ''}`;
    }
    case 'event': {
      const title = str(values.title);
      const startRaw = str(values.start);
      if (!title || !startRaw) return '';
      const start = new Date(startRaw);
      if (isNaN(start.getTime())) return '';
      const endRaw = str(values.end);
      let end = endRaw ? new Date(endRaw) : new Date(start.getTime() + 60 * 60 * 1000);
      if (isNaN(end.getTime()) || end <= start) end = new Date(start.getTime() + 60 * 60 * 1000);
      const params = new URLSearchParams();
      params.set('action', 'TEMPLATE');
      params.set('text', title);
      params.set('dates', `${toGCalDate(start)}/${toGCalDate(end)}`);
      if (str(values.description)) params.set('details', str(values.description));
      if (str(values.location)) params.set('location', str(values.location));
      return `https://calendar.google.com/calendar/render?${params.toString()}`;
    }
    default:
      return '';
  }
}

export function designName(type: ContentType, values: FieldValues): string {
  const meta = CONTENT_TYPES.find((c) => c.id === type);
  const label = meta ? meta.label : 'QR';
  let detail = '';
  switch (type) {
    case 'url':
      detail = str(values.link).replace(/^https?:\/\//, '').slice(0, 28);
      break;
    case 'text':
      detail = str(values.value).slice(0, 28);
      break;
    case 'wifi':
      detail = str(values.ssid);
      break;
    case 'vcard':
      detail = [str(values.firstName), str(values.lastName)].filter(Boolean).join(' ') || str(values.org);
      break;
    case 'email':
      detail = str(values.to);
      break;
    case 'phone':
    case 'sms':
      detail = str(values.number);
      break;
    case 'whatsapp':
      detail = str(values.number);
      break;
    case 'social':
      detail = `${str(values.platform)} · ${str(values.handle)}`;
      break;
    case 'location':
      detail = str(values.label) || `${str(values.lat)}, ${str(values.lng)}`;
      break;
    case 'event':
      detail = str(values.title);
      break;
  }
  return detail ? `${label} · ${detail}` : label;
}
