export type ContentType =
  | 'url'
  | 'text'
  | 'wifi'
  | 'vcard'
  | 'email'
  | 'phone'
  | 'sms'
  | 'whatsapp'
  | 'social'
  | 'location'
  | 'event';

export type FieldKind =
  | 'text'
  | 'textarea'
  | 'email'
  | 'tel'
  | 'url'
  | 'number'
  | 'select'
  | 'datetime'
  | 'checkbox';

export interface FieldDef {
  key: string;
  label: string;
  kind: FieldKind;
  placeholder?: string;
  required?: boolean;
  options?: { value: string; label: string }[];
  default?: string | boolean;
  help?: string;
}

export type FieldValues = Record<string, string | boolean>;

export type DotType =
  | 'square'
  | 'rounded'
  | 'dots'
  | 'classy'
  | 'classy-rounded'
  | 'extra-rounded';

export type CornerSquareType = 'square' | 'dot' | 'extra-rounded';
export type CornerDotType = 'square' | 'dot';
export type FrameStyle = 'none' | 'banner' | 'outline';

export interface StyleConfig {
  dotType: DotType;
  cornerSquareType: CornerSquareType;
  cornerDotType: CornerDotType;
  fgMode: 'solid' | 'gradient';
  fgColor: string;
  fgColor2: string;
  gradientType: 'linear' | 'radial';
  gradientRotation: number; // grados
  bgMode: 'solid' | 'gradient' | 'transparent';
  bgColor: string;
  bgColor2: string;
  eyeColorMode: 'same' | 'custom';
  eyeColor: string;
  margin: number; // px relativos a tamaño base 1024
  errorCorrection: 'L' | 'M' | 'Q' | 'H';
  logo: string | null; // dataURL
  logoSize: number; // relativo 0.15 - 0.45
  logoMargin: number; // px relativos a 1024
  hideLogoBgDots: boolean;
  frameStyle: FrameStyle;
  frameText: string;
  frameColorMode: 'auto' | 'custom';
  frameColor: string;
}

export interface SavedDesign {
  id: string;
  name: string;
  createdAt: number;
  contentType: ContentType;
  values: FieldValues;
  style: StyleConfig;
  thumb: string; // dataURL png pequeño
}
