import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { BlendOperation, SimulateBlendRequest } from '../domain/blend.model';
import { BlendsApi } from './blends.api';

interface BlendError {
  status?: number;
  error?: {
    code?: string;
    detail?: string;
    difference?: number;
    inputLiters?: number;
    outputLiters?: number;
    currentStatus?: string;
  };
}

/**
 * Estado das operações de blend (BLD-001).
 *
 * <p><strong>O desequilíbrio é mostrado como aritmética, não como "erro de validação".</strong> Quem
 * simulou uma união não digitou errado um campo: mediu volumes que não fecham. A mensagem precisa dizer
 * quantos litros faltam e de que lado — senão a correção provável é mexer no número até passar, que é
 * exatamente o que destrói o valor do campo de perda declarada.
 */
@Injectable()
export class BlendsStore {
  private readonly api = inject(BlendsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly operations = signal<BlendOperation[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly simulating = signal(false);
  readonly simulateError = signal<string | null>(null);

  readonly pending = computed(() =>
    this.operations().filter(o => o.status === 'SIMULATED' || o.status === 'APPROVED'),
  );

  /** Executadas e descartadas. As executadas são as que pesam no recall. */
  readonly settled = computed(() =>
    this.operations().filter(o => o.status === 'EXECUTED' || o.status === 'DISCARDED'),
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: operations => this.operations.set(operations),
        error: () => this.error.set('Não foi possível carregar as operações.'),
      });
  }

  simulate(request: SimulateBlendRequest, onSuccess: () => void): void {
    this.simulating.set(true);
    this.simulateError.set(null);
    this.api
      .simulate(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.simulating.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Simulação registrada. Nada foi movido ainda — falta aprovar e executar.');
          onSuccess();
          this.load();
        },
        error: (e: BlendError) => this.simulateError.set(this.messageFor(e)),
      });
  }

  approve(id: string): void {
    this.api
      .approve(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(),
        error: (e: BlendError) => this.toast.error(this.messageFor(e)),
      });
  }

  execute(id: string): void {
    this.api
      .execute(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // O aviso diz o que mudou de verdade: a genealogia. É o que faz o recall alcançar os dois lados.
          this.toast.success(
            'Executada. A genealogia passou a valer: um recall que alcançar qualquer um destes lotes ' +
              'alcança os demais.',
          );
          this.load();
        },
        error: (e: BlendError) => this.toast.error(this.messageFor(e)),
      });
  }

  discard(id: string): void {
    this.api
      .discard(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(),
        error: (e: BlendError) => this.toast.error(this.messageFor(e)),
      });
  }

  private messageFor(e: BlendError): string {
    // `e.error` e não `e`: o HttpErrorResponse embrulha o corpo.
    const code = e.error?.code;
    if (code === 'unbalanced_blend') {
      return e.error?.detail ?? 'O balanço não fecha.';
    }
    if (code === 'unknown_blend_batch') {
      return e.error?.detail ?? 'Um dos lotes não existe nesta cervejaria.';
    }
    if (code === 'illegal_blend_transition') {
      return (
        `A operação está em ${e.error?.currentStatus ?? 'outro estado'} — provavelmente outra pessoa ` +
        'já a atualizou. Recarregue antes de tentar de novo: cerveja não se mistura duas vezes.'
      );
    }
    if (e.status === 403) {
      return 'Aprovar e executar são alçadas próprias: depois de misturadas, duas cervejas não se separam.';
    }
    return e.error?.detail ?? 'Não foi possível concluir a operação.';
  }
}
