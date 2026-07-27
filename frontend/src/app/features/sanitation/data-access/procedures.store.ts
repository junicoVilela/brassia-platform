import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { CreateProcedureRequest, Procedure } from '../domain/procedure.model';
import { ProceduresApi } from './procedures.api';

/** Estado dos POPs de limpeza/sanitização (CLN-001). */
@Injectable()
export class ProceduresStore {
  private readonly api = inject(ProceduresApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<Procedure[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly expandedId = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar os POPs.'),
      });
  }

  toggle(id: string): void {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  create(request: CreateProcedureRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('POP criado (rascunho).'); this.load(); },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Já existe um rascunho aberto para este código.'
            : 'Não foi possível criar o POP (dados inválidos).'),
      });
  }

  publish(id: string): void {
    this.api.publish(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success('POP publicado.'); this.load(); },
        error: () => this.toast.error('Não foi possível publicar o POP.'),
      });
  }
}
