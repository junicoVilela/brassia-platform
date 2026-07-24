import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  CompareStyleRequest,
  CompareStyleResult,
  CreateStyleSetRequest,
  ReferenceSource,
  StyleSet,
  StyleSetDetail,
} from '../domain/reference.model';
import { ReferenceApi } from './reference.api';

/** Estado da tela de estilos: conjuntos, detalhe selecionado e comparação. */
@Injectable()
export class StylesStore {
  private readonly api = inject(ReferenceApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly setsState = signal<StyleSet[]>([]);
  private readonly sourcesState = signal<ReferenceSource[]>([]);
  private readonly detailState = signal<StyleSetDetail | null>(null);
  private readonly comparisonState = signal<CompareStyleResult | null>(null);

  readonly sets = this.setsState.asReadonly();
  readonly sources = this.sourcesState.asReadonly();
  readonly detail = this.detailState.asReadonly();
  readonly comparison = this.comparisonState.asReadonly();
  readonly loading = signal(false);
  readonly loadingDetail = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.sets().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listStyleSets()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.setsState.set(page.content),
        error: () => this.error.set('Não foi possível carregar os conjuntos de estilos.'),
      });
    this.api.listSources()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.sourcesState.set(page.content), error: () => {} });
  }

  create(request: CreateStyleSetRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.createStyleSet(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Conjunto de estilos criado.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível criar (autoridade/edição/idioma já existe ou dados inválidos).'),
      });
  }

  select(setId: string | null): void {
    this.detailState.set(null);
    this.comparisonState.set(null);
    this.actionError.set(null);
    if (!setId) {
      return;
    }
    this.loadingDetail.set(true);
    this.api.getStyleSet(setId)
      .pipe(finalize(() => this.loadingDetail.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => this.detailState.set(detail),
        error: () => this.error.set('Não foi possível carregar o conjunto.'),
      });
  }

  publish(setId: string): void {
    this.actionError.set(null);
    this.api.publishStyleSet(setId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Conjunto publicado.');
          this.load();
          this.select(setId);
        },
        error: () => this.actionError.set('Não foi possível publicar (permissão da fonte não autoriza).'),
      });
  }

  compare(setId: string, code: string, request: CompareStyleRequest): void {
    this.comparisonState.set(null);
    this.actionError.set(null);
    this.api.compareStyle(setId, code, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => this.comparisonState.set(result),
        error: () => this.actionError.set('Não foi possível comparar com o estilo.'),
      });
  }
}
