import { Injectable, signal } from '@angular/core';

/**
 * Termo de busca global digitado no header. Cada tela de listagem lê este signal
 * e filtra sua própria lista no cliente; ninguém escreve dados aqui além do header.
 * O termo é reiniciado a cada navegação pela própria tela ao assinar/inicializar.
 */
@Injectable({ providedIn: 'root' })
export class UiSearchService {
  private readonly termState = signal('');
  readonly term = this.termState.asReadonly();

  set(term: string): void {
    this.termState.set(term);
  }

  clear(): void {
    this.termState.set('');
  }
}
