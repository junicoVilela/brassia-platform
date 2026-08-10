import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { OptimizationRun, OptimizeRequest } from '../domain/optimization.model';
import { OptimizationApi } from './optimization.api';

interface OptimizationError {
  status?: number;
  error?: { code?: string; detail?: string };
}

/**
 * Estado da otimização (OPT-001).
 *
 * <p><strong>Inviabilidade não é erro, e o store não a trata como tal.</strong> Ela chega como resultado
 * bem-sucedido com `feasible: false` e vai para `run`, não para `error`. Se caísse no erro, a tela
 * mostraria "algo deu errado" — e a informação que torna a inviabilidade acionável, que é <em>quais</em>
 * restrições se contradizem, se perderia junto.
 */
@Injectable()
export class OptimizationStore {
  private readonly api = inject(OptimizationApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly runs = signal<OptimizationRun[]>([]);
  readonly run = signal<OptimizationRun | null>(null);
  readonly loading = signal(false);
  readonly running = signal(false);
  readonly error = signal<string | null>(null);

  readonly infeasible = computed(() => {
    const current = this.run();
    return current && !current.feasible ? current.infeasible : null;
  });

  readonly best = computed(() => this.run()?.candidates[0] ?? null);

  /** A procedência do número, para a tela sempre poder mostrá-la junto do resultado. */
  readonly provenance = computed(() => {
    const current = this.run();
    if (!current) {
      return null;
    }
    return {
      method: current.method,
      recipeVersion: current.recipeVersion,
      catalogVersion: current.catalogVersion,
      // Distingue "método não usa semente" de "esqueceram de gravar".
      seed: current.usesSeed ? String(current.seed ?? '—') : 'não se aplica (método determinístico)',
    };
  });

  load(recipeId?: string): void {
    this.loading.set(true);
    this.api
      .list(recipeId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: runs => this.runs.set(runs),
        error: () => this.error.set('Não foi possível carregar as otimizações.'),
      });
  }

  optimize(request: OptimizeRequest): void {
    this.running.set(true);
    this.error.set(null);
    this.run.set(null);
    this.api
      .optimize(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.running.set(false)),
      )
      .subscribe({
        next: run => {
          // Inviável também é resultado: vai para `run`, com a explicação de quais restrições brigam.
          this.run.set(run);
          this.load(request.recipeId);
        },
        error: (e: OptimizationError) => this.error.set(this.messageFor(e)),
      });
  }

  explain(id: string, explanation: string): void {
    this.api
      .explain(id, explanation)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: run => this.run.set(run),
        error: (e: OptimizationError) => this.toast.error(this.messageFor(e)),
      });
  }

  apply(id: string, recipeVersionId: string): void {
    this.api
      .apply(id, recipeVersionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: run => {
          this.run.set(run);
          this.toast.success(
            'Registrado. A alternativa só passa a valer pela versão de receita que você criou — ' +
              'o otimizador não escreve na receita.',
          );
        },
        error: (e: OptimizationError) => this.toast.error(this.messageFor(e)),
      });
  }

  private messageFor(e: OptimizationError): string {
    // `e.error` e não `e`: o HttpErrorResponse embrulha o corpo.
    const code = e.error?.code;
    if (code === 'unpublished_recipe') {
      return (
        e.error?.detail ??
        'A receita não tem versão publicada. Otimizar rascunho daria um resultado sobre algo que muda.'
      );
    }
    if (code === 'illegal_optimization_state') {
      return e.error?.detail ?? 'Esta corrida já foi aplicada.';
    }
    if (e.status === 403) {
      return 'Registrar a aplicação é alçada própria.';
    }
    return e.error?.detail ?? 'Não foi possível executar a otimização.';
  }
}
