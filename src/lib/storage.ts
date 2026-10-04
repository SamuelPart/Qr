import type { ContentType, FieldValues, SavedDesign, StyleConfig } from './types';

const SAVED_KEY = 'qrstudio.saved.v1';
const DRAFT_KEY = 'qrstudio.draft.v1';
const MAX_SAVED = 40;

export function loadSaved(): SavedDesign[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(SAVED_KEY);
    if (!raw) return [];
    const list = JSON.parse(raw);
    return Array.isArray(list) ? (list as SavedDesign[]) : [];
  } catch {
    return [];
  }
}

export function persistSaved(list: SavedDesign[]): void {
  try {
    window.localStorage.setItem(SAVED_KEY, JSON.stringify(list.slice(0, MAX_SAVED)));
  } catch {
    /* almacenamiento lleno: ignora */
  }
}

export function addSaved(design: SavedDesign): SavedDesign[] {
  const list = [design, ...loadSaved().filter((d) => d.id !== design.id)];
  persistSaved(list);
  return list.slice(0, MAX_SAVED);
}

export function removeSaved(id: string): SavedDesign[] {
  const list = loadSaved().filter((d) => d.id !== id);
  persistSaved(list);
  return list;
}

export interface Draft {
  contentType: ContentType;
  valuesMap: Record<string, FieldValues>;
  style: StyleConfig;
}

export function loadDraft(): Draft | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(DRAFT_KEY);
    return raw ? (JSON.parse(raw) as Draft) : null;
  } catch {
    return null;
  }
}

export function saveDraft(draft: Draft): void {
  try {
    window.localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch {
    /* ignora */
  }
}
