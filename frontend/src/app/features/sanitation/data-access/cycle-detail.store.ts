import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { CleaningCycle, RecordStepRequest } from '../domain/cycle.model';
import { CyclesApi } from './cycles.api';

/** Estado da execução de um ciclo (CLN-003): registrar etapa, interromper, retomar, concluir. */
@Injectable()
export class CycleDetailStore {
  private readonly api = inject(CyclesApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly cycle = signal<CleaningCycle | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly canExecute = this.auth.hasPermission('sanitation.cycle.execute');
  readonly canOverride = this.auth.hasPermission('sanitation.cycle.override');
  readonly inProgress = computed(() => this.cycle()?.status === 'IN_PROGRESS');
  readonly interrupted = computed(() => this.cycle()?.status === 'INTERRUPTED');

  load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.get(id)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: c => this.cycle.set(c),
        error: () => this.error.set('Não foi possível carregar o ciclo.'),
      });
  }

  recordStep(request: RecordStepRequest, onSuccess?: () => void): void {
    const id = this.cycle()?.id;
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.recordStep(id, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success('Etapa registrada.'); onSuccess?.(); this.load(id); },
        error: (err: { status?: number; error?: { detail?: string } }) =>
          this.actionError.set(err?.error?.detail ?? this.message(err?.status)),
      });
  }

  interrupt(reason: string): void {
    this.mutate(id => this.api.interrupt(id, reason), 'Ciclo interrompido.');
  }

  resume(): void {
    this.mutate(id => this.api.resume(id), 'Ciclo retomado.');
  }

  complete(): void {
    this.mutate(id => this.api.complete(id), 'Ciclo concluído.');
  }

  private mutate(call: (id: string) => import('rxjs').Observable<void>, ok: string): void {
    const id = this.cycle()?.id;
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    call(id)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success(ok); this.load(id); },
        error: (err: { status?: number; error?: { detail?: string } }) =>
          this.actionError.set(err?.error?.detail ?? this.message(err?.status)),
      });
  }

  private message(status?: number): string {
    if (status === 409) {
      return 'Operação inválida para o estado atual do ciclo.';
    }
    if (status === 403) {
      return 'Sem alçada para esta operação.';
    }
    return 'Parâmetro fora da ficha ou dados inválidos.';
  }
}
