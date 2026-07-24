import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import {
  RecordWaterReportRequest,
  RegisterWaterSourceRequest,
  WaterReport,
  WaterSource,
} from '../domain/water.model';
import { ToastService } from '../../../core/notifications/toast.service';
import { WaterApi } from './water.api';

/** Estado da tela de água: fontes, cadastro e laudos (histórico) da fonte escolhida. */
@Injectable()
export class WaterStore {
  private readonly api = inject(WaterApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly sourcesState = signal<WaterSource[]>([]);
  private readonly selectedIdState = signal<string | null>(null);
  private readonly reportsState = signal<WaterReport[]>([]);

  readonly sources = this.sourcesState.asReadonly();
  readonly selectedId = this.selectedIdState.asReadonly();
  readonly reports = this.reportsState.asReadonly();
  readonly loadingReports = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly noReports = computed(() => !!this.selectedIdState() && !this.loadingReports() && this.reports().length === 0);

  loadSources(): void {
    this.api.listSources()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.sourcesState.set(page.content),
        error: () => this.error.set('Não foi possível carregar as fontes.'),
      });
  }

  createSource(request: RegisterWaterSourceRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.createSource(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Fonte cadastrada.');
          this.loadSources();
        },
        error: () => this.actionError.set('Não foi possível cadastrar a fonte (código duplicado?).'),
      });
  }

  select(sourceId: string | null): void {
    this.selectedIdState.set(sourceId);
    this.reportsState.set([]);
    this.actionError.set(null);
    if (sourceId) {
      this.loadReports();
    }
  }

  private loadReports(): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.loadingReports.set(true);
    this.api.listReports(id)
      .pipe(finalize(() => this.loadingReports.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: reports => this.reportsState.set(reports),
        error: () => this.error.set('Não foi possível carregar os laudos.'),
      });
  }

  recordReport(request: RecordWaterReportRequest, onSuccess?: () => void): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.recordReport(id, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Laudo registrado.');
          this.loadReports();
        },
        error: () => this.actionError.set('Não foi possível registrar o laudo (íons/unidades ou data inválidos).'),
      });
  }
}
