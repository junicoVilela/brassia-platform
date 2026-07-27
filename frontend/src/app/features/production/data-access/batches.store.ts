import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { Batch } from '../domain/batch.model';
import { BrewCorrection, CorrectionResult, PreviewCorrectionRequest } from '../domain/correction.model';
import { Measurement, RecordMeasurementRequest } from '../domain/measurement.model';
import { BatchesApi } from './batches.api';

/** Estado dos lotes de produção (PRD-001/PRD-002). */
@Injectable()
export class BatchesStore {
  private readonly api = inject(BatchesApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<Batch[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly canManage = this.auth.hasPermission('production.batch.manage');
  readonly canRecordMeasurement = this.auth.hasPermission('production.measurement.record');
  readonly completing = signal(false);

  /** Lote expandido para ver o roteiro. */
  readonly expandedId = signal<string | null>(null);

  /** Lote com o painel de medições aberto (PRD-003). */
  readonly measurementsBatchId = signal<string | null>(null);
  readonly measurements = signal<Measurement[]>([]);
  readonly measurementsLoading = signal(false);
  readonly measurementError = signal<string | null>(null);
  readonly recording = signal(false);

  /** Lote com o painel de correções aberto (PRD-004). */
  readonly correctionsBatchId = signal<string | null>(null);
  readonly corrections = signal<BrewCorrection[]>([]);
  readonly previewResult = signal<CorrectionResult | null>(null);
  readonly previewing = signal(false);
  readonly correctionError = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar os lotes de produção.'),
      });
  }

  toggle(batchId: string): void {
    this.expandedId.set(this.expandedId() === batchId ? null : batchId);
  }

  completeStep(batchId: string, stepId: string): void {
    this.completing.set(true);
    this.api.completeStep(batchId, stepId)
      .pipe(finalize(() => this.completing.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.itemsState.update(items => items.map(b => (b.id === updated.id ? updated : b)));
          this.toast.success('Etapa concluída.');
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Apenas a etapa ativa pode ser concluída.'
            : 'Não foi possível concluir a etapa.'),
      });
  }

  /** Abre (ou fecha) o painel de medições de um lote e carrega o histórico. */
  showMeasurements(batchId: string): void {
    if (this.measurementsBatchId() === batchId) {
      this.measurementsBatchId.set(null);
      return;
    }
    this.measurementsBatchId.set(batchId);
    this.measurementError.set(null);
    this.refreshMeasurements(batchId);
  }

  recordMeasurement(batchId: string, request: RecordMeasurementRequest, onSuccess?: () => void): void {
    this.recording.set(true);
    this.measurementError.set(null);
    this.api.recordMeasurement(batchId, request)
      .pipe(finalize(() => this.recording.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Medição registrada.'); this.refreshMeasurements(batchId); },
        error: (err: { status?: number }) =>
          this.measurementError.set(err?.status === 400
            ? 'Unidade incompatível com a grandeza ou dados inválidos.'
            : 'Não foi possível registrar a medição.'),
      });
  }

  private refreshMeasurements(batchId: string): void {
    this.measurementsLoading.set(true);
    this.measurements.set([]);
    this.api.measurements(batchId)
      .pipe(finalize(() => this.measurementsLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: m => this.measurements.set(m),
        error: () => this.measurementError.set('Não foi possível carregar as medições.'),
      });
  }

  /** Abre (ou fecha) o painel de correções de um lote e carrega o catálogo. */
  showCorrections(batchId: string): void {
    if (this.correctionsBatchId() === batchId) {
      this.correctionsBatchId.set(null);
      return;
    }
    this.correctionsBatchId.set(batchId);
    this.previewResult.set(null);
    this.correctionError.set(null);
    if (this.corrections().length === 0) {
      this.api.corrections()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: c => this.corrections.set(c), error: () => {} });
    }
  }

  preview(batchId: string, request: PreviewCorrectionRequest): void {
    this.previewing.set(true);
    this.correctionError.set(null);
    this.previewResult.set(null);
    this.api.previewCorrection(batchId, request)
      .pipe(finalize(() => this.previewing.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: r => this.previewResult.set(r),
        error: () => this.correctionError.set('Não foi possível calcular o impacto (dados inválidos).'),
      });
  }
}
