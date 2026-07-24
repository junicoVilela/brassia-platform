import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'brassia-theme';

/** Lê a preferência salva (ou do SO) sem depender do Angular — usado no bootstrap. */
function resolveInitialMode(): ThemeMode {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'light' || saved === 'dark') {
    return saved;
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/**
 * Aplica o tema Fila no elemento raiz. O tema tem toggles independentes para o
 * conteúdo (`data-theme`), a sidebar (`sidebar-dark-light-data-theme`) e o header
 * (`header-dark-light-data-theme`); no escuro ligamos os três para um resultado
 * coeso; no claro removemos os dois específicos (voltam ao padrão claro).
 */
function applyMode(mode: ThemeMode): void {
  const root = document.documentElement;
  root.setAttribute('data-theme', mode);
  if (mode === 'dark') {
    root.setAttribute('sidebar-dark-light-data-theme', 'sidebar-dark');
    root.setAttribute('header-dark-light-data-theme', 'header-dark');
  } else {
    root.removeAttribute('sidebar-dark-light-data-theme');
    root.removeAttribute('header-dark-light-data-theme');
  }
}

/**
 * Aplica o tema salvo o quanto antes (chamado no `main.ts`, antes do bootstrap),
 * evitando flash de tema claro ao abrir no escuro.
 */
export function initThemeMode(): void {
  applyMode(resolveInitialMode());
}

/** Alterna e persiste o modo claro/escuro (suporte nativo do tema via `[data-theme]`). */
@Injectable({ providedIn: 'root' })
export class ThemeModeService {
  private readonly modeState = signal<ThemeMode>(resolveInitialMode());
  readonly mode = this.modeState.asReadonly();

  constructor() {
    applyMode(this.modeState());
  }

  toggle(): void {
    this.set(this.modeState() === 'dark' ? 'light' : 'dark');
  }

  set(mode: ThemeMode): void {
    this.modeState.set(mode);
    applyMode(mode);
    localStorage.setItem(STORAGE_KEY, mode);
  }
}
