import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  LibraryPublication,
  OwnedPublication,
  RecipeLicense,
  Visibility,
} from '../domain/library.model';
import { LibraryApi } from './library.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string };
}

/**
 * Estado da biblioteca (COM-001).
 *
 * <p>Depois de publicar ou mudar visibilidade, tudo é <strong>relido</strong>: a vitrine depende da
 * visibilidade, e uma lista em cache mostraria como pública uma receita que o autor acabou de fechar.
 * Num módulo cujo assunto é o que sai de casa, cache errado não é incômodo — é vazamento na tela.
 */
@Injectable()
export class LibraryStore {
  private readonly api = inject(LibraryApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly feed = signal<LibraryPublication[]>([]);
  readonly mine = signal<OwnedPublication[]>([]);
  readonly selected = signal<LibraryPublication | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);

  /** O que está fora de circulação continua na estante, e a tela separa as duas coisas. */
  readonly live = computed(() => this.mine().filter(p => p.published));
  readonly retired = computed(() => this.mine().filter(p => !p.published));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ feed: this.api.feed(), mine: this.api.mine() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: ({ feed, mine }) => {
          this.feed.set(feed);
          this.mine.set(mine);
        },
        error: (e: ApiError) => this.error.set(this.message(e, 'Não foi possível carregar a biblioteca.')),
      });
  }

  open(publication: LibraryPublication): void {
    this.selected.set(publication);
  }

  close(): void {
    this.selected.set(null);
  }

  publish(recipeId: string, title: string, summary: string | null, license: RecipeLicense,
    visibility: Visibility): void {
    this.saving.set(true);
    this.api
      .publish({ recipeId, title, summary, license, visibility })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Receita publicada.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível publicar.')),
      });
  }

  changeVisibility(publication: OwnedPublication, visibility: Visibility): void {
    this.api
      .changeVisibility(publication.id, visibility)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Visibilidade alterada.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível alterar.')),
      });
  }

  unpublish(publication: OwnedPublication): void {
    this.api
      .unpublish(publication.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "Fora de circulação", e não "excluída": o que já foi lido não se desfaz.
          this.toast.success('Publicação fora de circulação.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível retirar.')),
      });
  }

  private message(e: ApiError, fallback: string): string {
    return e?.error?.detail ?? fallback;
  }
}
