import { Injectable, signal } from '@angular/core';
import { Toast, ToastKind } from './toast.model';

/**
 * Feedback transitório (toast) compartilhado por todas as telas. Baseado em
 * signals — funciona com change detection zoneless — e sem dependência de JS do
 * tema: o host renderiza o markup de toast do Bootstrap 5 (embutido no tema).
 * Auto-dispensa após alguns segundos; pode ser fechado manualmente.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private static readonly TTL_MS = 4000;

  private readonly state = signal<Toast[]>([]);
  readonly toasts = this.state.asReadonly();
  private sequence = 0;

  success(text: string): void {
    this.push('success', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  info(text: string): void {
    this.push('info', text);
  }

  dismiss(id: number): void {
    this.state.update(list => list.filter(toast => toast.id !== id));
  }

  private push(kind: ToastKind, text: string): void {
    const id = ++this.sequence;
    this.state.update(list => [...list, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ToastService.TTL_MS);
  }
}
