import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { LearnedProfile, ProfileEstimate } from '../domain/profile.model';
import { ProfileApi } from './profile.api';

interface ProfileError {
  status?: number;
  error?: { code?: string; detail?: string };
}

/**
 * Estado do perfil aprendido (DTW-001).
 *
 * <p>`profile` nulo e `loaded` verdadeiro é um estado legítimo e distinto de "ainda carregando": significa
 * que a receita **nunca foi analisada**. Misturar os dois faria a tela dizer "sem dados" enquanto ainda
 * busca, e depois continuar dizendo a mesma coisa — sem que ninguém soubesse qual das duas era.
 */
@Injectable()
export class ProfileStore {
  private readonly api = inject(ProfileApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly profile = signal<LearnedProfile | null>(null);
  readonly history = signal<LearnedProfile[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  readonly computing = signal(false);
  readonly computeError = signal<string | null>(null);

  /** Nunca analisada — diferente de analisada e sem resultado. */
  readonly neverComputed = computed(() => this.loaded() && this.profile() === null);

  /** As estimativas que dá para usar. */
  readonly usable = computed(() => this.profile()?.estimates.filter(e => e.usable) ?? []);

  /**
   * As que não deram.
   *
   * Ficam visíveis de propósito: ausência declarada é informação. Escondê-las faria quem lê concluir que
   * a perda é zero em vez de que ela não foi estimada.
   */
  readonly unusable = computed(() => this.profile()?.estimates.filter(e => !e.usable) ?? []);

  load(recipeId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .latest(recipeId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading.set(false);
          this.loaded.set(true);
        }),
      )
      .subscribe({
        // 204 chega como corpo vazio; normalizar para null aqui evita espalhar a checagem pela tela.
        next: profile => this.profile.set(profile ?? null),
        error: () => this.error.set('Não foi possível carregar o perfil aprendido.'),
      });

    this.api
      .history(recipeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: history => this.history.set(history), error: () => undefined });
  }

  compute(recipeId: string, batchIds: string[]): void {
    this.computing.set(true);
    this.computeError.set(null);
    this.api
      .compute({ recipeId, batchIds })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.computing.set(false)),
      )
      .subscribe({
        next: profile => {
          // A diferença entre lotes pedidos e lotes usados é a informação que evita a pergunta "por que o
          // número não mudou?" — o aviso é mais útil que um "pronto".
          const usados = profile.observedBatchIds.length;
          this.toast.success(
            usados === batchIds.length
              ? `Versão ${profile.version} calculada sobre ${usados} lotes.`
              : `Versão ${profile.version} calculada sobre ${usados} de ${batchIds.length} lotes — ` +
                  'os demais não serviram (outra receita, ou ainda não transferidos).',
          );
          this.load(recipeId);
        },
        error: (e: ProfileError) => this.computeError.set(this.messageFor(e)),
      });
  }

  /** A faixa como texto. Sem ela, a média sozinha parece um fato. */
  rangeOf(estimate: ProfileEstimate): string {
    if (!estimate.usable) {
      return '—';
    }
    return `${estimate.lowerBound} a ${estimate.upperBound}`;
  }

  private messageFor(e: ProfileError): string {
    if (e.error?.code === 'empty_learning_sample') {
      return (
        e.error.detail ??
        'Nenhum dos lotes informados serve para aprender sobre esta receita.'
      );
    }
    if (e.status === 403) {
      return 'Calcular o perfil é alçada própria — quem escolhe a amostra decide o número.';
    }
    if (e.status === 400) {
      return 'Informe a receita e ao menos um lote.';
    }
    return e.error?.detail ?? 'Não foi possível calcular o perfil.';
  }
}
