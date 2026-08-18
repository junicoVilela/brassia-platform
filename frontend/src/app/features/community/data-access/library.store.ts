import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  AbuseReport,
  Contribution,
  ContributionKind,
  ForkedRecipe,
  LibraryPublication,
  OwnedPublication,
  RatingSummary,
  RecipeLicense,
  ReportOutcome,
  ReportReason,
  SharePermission,
  ShareLink,
  Visibility,
} from '../domain/library.model';
import { LibraryApi } from './library.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string; missing?: string[] };
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

  readonly links = signal<ShareLink[]>([]);
  readonly linksOf = signal<OwnedPublication | null>(null);

  /**
   * O token recém-criado, para a tela mostrar UMA vez.
   *
   * <p>Fica em memória e some ao fechar: guardá-lo em qualquer lugar — storage, URL, histórico — o
   * transformaria num segredo persistido, que é justamente o que o servidor evitou ao guardar só o
   * hash.
   */
  readonly freshToken = signal<string | null>(null);

  /** O resultado do último fork, para a tela mostrar a atribuição e a obrigação de licença. */
  readonly lastFork = signal<ForkedRecipe | null>(null);

  readonly contributions = signal<Contribution[]>([]);

  /**
   * A nota da publicação aberta.
   *
   * <p>Nasce nula e não zerada: a tela precisa saber a diferença entre "ninguém votou" e "todo mundo
   * deu zero" — e zero nem existe na escala.
   */
  readonly rating = signal<RatingSummary | null>(null);

  /** As denúncias contra a MINHA publicação: direito de resposta, e nunca sobre a dos outros. */
  readonly reports = signal<AbuseReport[]>([]);
  readonly reportsOf = signal<OwnedPublication | null>(null);

  /** Quantas sugestões esperam decisão — comentários não entram, porque não pediram nada. */
  readonly pendingCount = computed(() => this.contributions().filter(c => c.pending).length);

  /** Os ingredientes que faltaram no catálogo — é o que torna a recusa acionável. */
  readonly missingIngredients = signal<string[]>([]);

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
    this.contributions.set([]);
    this.rating.set(null);
    this.loadContributions(publication.id);
    this.loadRating(publication.id);
  }

  close(): void {
    this.selected.set(null);
    this.contributions.set([]);
    this.rating.set(null);
  }

  loadRating(publicationId: string): void {
    this.api
      .rating(publicationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: r => this.rating.set(r) });
  }

  rate(publication: LibraryPublication, value: number): void {
    this.api
      .rate(publication.id, value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Avaliação registrada.');
          this.loadRating(publication.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível avaliar.')),
      });
  }

  report(publication: LibraryPublication, reason: ReportReason, note: string | null): void {
    this.saving.set(true);
    this.api
      .report(publication.id, { reason, note })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          // "Registrada", e não "removida": denunciar abre um caso e não tira nada do ar. Prometer
          // remoção aqui faria a tela mentir sobre o que aconteceu.
          this.toast.success('Denúncia registrada.');
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível denunciar.')),
      });
  }

  reviewReport(publication: OwnedPublication, reportId: string, outcome: ReportOutcome,
    note: string | null): void {
    this.api
      .reviewReport(publication.id, reportId, { outcome, note })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "Decisão registrada", e não "denúncia resolvida": julgar procedente não tira nada do ar, e
          // dizer o contrário faria a tela prometer uma remoção que não houve.
          this.toast.success('Decisão registrada. A publicação continua no ar até você despublicá-la.');
          this.openReports(publication);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível decidir.')),
      });
  }

  openReports(publication: OwnedPublication): void {
    this.reportsOf.set(publication);
    this.reports.set([]);
    this.api
      .reports(publication.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.reports.set(list) });
  }

  closeReports(): void {
    this.reportsOf.set(null);
    this.reports.set([]);
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

  loadContributions(publicationId: string): void {
    this.api
      .contributions(publicationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.contributions.set(list) });
  }

  write(publication: LibraryPublication, kind: ContributionKind, body: string,
    context: string | null): void {
    this.saving.set(true);
    this.api
      .write(publication.id, { kind, body, context })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success(kind === 'SUGGESTION' ? 'Sugestão enviada.' : 'Comentário enviado.');
          this.loadContributions(publication.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível enviar.')),
      });
  }

  decide(publication: LibraryPublication, contribution: Contribution, accept: boolean,
    note: string | null): void {
    this.api
      .decide(contribution.id, accept, note)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "Concordância registrada", e não "sugestão aplicada": aplicar é ato do autor, na receita
          // dele. Dizer o contrário aqui prometeria uma mudança que não aconteceu.
          this.toast.success(accept ? 'Concordância registrada.' : 'Sugestão recusada.');
          this.loadContributions(publication.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível decidir.')),
      });
  }

  fork(publication: LibraryPublication, equipmentId: string, name: string | null): void {
    this.saving.set(true);
    this.lastFork.set(null);
    this.missingIngredients.set([]);
    this.api
      .fork(publication.id, { equipmentId, name })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: forked => {
          this.lastFork.set(forked);
          this.toast.success('Receita copiada para a sua cervejaria.');
        },
        error: (e: ApiError) => {
          // A lista do que falta fica na tela, e não só no toast: o operador precisa dela na mão para
          // cadastrar os ingredientes, e um toast some antes disso.
          this.missingIngredients.set(e?.error?.missing ?? []);
          this.toast.error(this.message(e, 'Não foi possível copiar a receita.'));
        },
      });
  }

  openLinks(publication: OwnedPublication): void {
    this.linksOf.set(publication);
    this.freshToken.set(null);
    this.reloadLinks(publication.id);
  }

  closeLinks(): void {
    this.linksOf.set(null);
    this.links.set([]);
    // O token some junto: ele não sobrevive ao fechamento da tela.
    this.freshToken.set(null);
  }

  createLink(permission: SharePermission, label: string | null, expiresAt: string | null): void {
    const publication = this.linksOf();
    if (!publication) {
      return;
    }
    this.saving.set(true);
    this.api
      .createLink(publication.id, { permission, label, expiresAt })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: created => {
          this.freshToken.set(created.token);
          this.reloadLinks(publication.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível criar o link.')),
      });
  }

  revokeLink(link: ShareLink): void {
    const publication = this.linksOf();
    if (!publication) {
      return;
    }
    this.api
      .revokeLink(link.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Link revogado.');
          this.reloadLinks(publication.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível revogar.')),
      });
  }

  private reloadLinks(publicationId: string): void {
    this.api
      .links(publicationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.links.set(list) });
  }

  private message(e: ApiError, fallback: string): string {
    return e?.error?.detail ?? fallback;
  }
}
