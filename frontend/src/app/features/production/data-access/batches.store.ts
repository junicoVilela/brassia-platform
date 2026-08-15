import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { Equipment } from '../../equipment/domain/equipment.model';
import { BatchAlert, CreateAlertRequest } from '../domain/alert.model';
import { Batch } from '../domain/batch.model';
import {
  ApplyCorrectionRequest,
  AppliedCorrection,
  BrewCorrection,
  CorrectionResult,
  PreviewCorrectionRequest,
} from '../domain/correction.model';
import { Measurement, RecordMeasurementRequest, LaborEntry, RecordLaborRequest } from '../domain/measurement.model';
import { TransferRequest } from '../domain/transfer.model';
import { BatchesApi } from './batches.api';

/** Estado dos lotes de produção (PRD-001/PRD-002). */
@Injectable()
export class BatchesStore {
  private readonly api = inject(BatchesApi);
  private readonly equipmentApi = inject(EquipmentApi);
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
  readonly canRecordLabor = this.auth.hasPermission('production.labor.record');
  readonly completing = signal(false);

  /** Lote expandido para ver o roteiro. */
  readonly expandedId = signal<string | null>(null);

  /** Lote com o painel de medições aberto (PRD-003). */
  readonly measurementsBatchId = signal<string | null>(null);
  readonly measurements = signal<Measurement[]>([]);
  readonly measurementsLoading = signal(false);
  readonly measurementError = signal<string | null>(null);
  readonly recording = signal(false);
  /** Apontamentos de hora do lote aberto no painel (CST-001-A). */
  readonly labor = signal<LaborEntry[]>([]);
  readonly recordingLabor = signal(false);
  readonly laborError = signal<string | null>(null);

  /** Lote com o painel de correções aberto (PRD-004). */
  readonly correctionsBatchId = signal<string | null>(null);
  readonly corrections = signal<BrewCorrection[]>([]);
  readonly previewResult = signal<CorrectionResult | null>(null);
  readonly previewing = signal(false);
  readonly correctionError = signal<string | null>(null);
  readonly applied = signal<AppliedCorrection[]>([]);
  readonly applying = signal(false);

  /** Lote com o painel de transferência aberto (PRD-005). */
  readonly transferBatchId = signal<string | null>(null);
  readonly equipment = signal<Equipment[]>([]);
  readonly transferring = signal(false);
  readonly transferError = signal<string | null>(null);

  /** Lote com a central de alertas aberta (PRD-006). */
  readonly alertsBatchId = signal<string | null>(null);
  readonly alerts = signal<BatchAlert[]>([]);
  readonly alertsLoading = signal(false);
  readonly alertError = signal<string | null>(null);
  readonly pageState = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly savingAlert = signal(false);

  /**
   * Carrega uma página de lotes (REL-002).
   *
   * Esta é a tela de listagem, então aqui a paginação é de verdade — e não o `listForSelection`, que
   * serve aos seletores. `totalElements` alimenta a navegação: sem ele a tela não sabe se existe página
   * seguinte e ofereceria um botão que não leva a lugar nenhum.
   */
  load(page = 0): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(page, this.pageSize)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.itemsState.set(result.content);
          this.pageState.set(result.page);
          this.totalElements.set(result.totalElements);
          this.totalPages.set(result.totalPages);
        },
        error: () => this.error.set('Não foi possível carregar os lotes de produção.'),
      });
  }

  readonly pageSize = 20;

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.load(page);
    }
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
    this.refreshLabor(batchId);
  }

  /**
   * Aponta horas e recarrega a lista.
   *
   * <p>Recarrega em vez de acrescentar localmente: o total de horas-homem é calculado no servidor, e
   * repeti-lo aqui criaria uma segunda conta que diverge da primeira no primeiro arredondamento.
   */
  recordLabor(batchId: string, request: RecordLaborRequest, onSuccess?: () => void): void {
    this.recordingLabor.set(true);
    this.laborError.set(null);
    this.api.recordLabor(batchId, request)
      .pipe(finalize(() => this.recordingLabor.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Horas apontadas.');
          this.refreshLabor(batchId);
        },
        error: (err: { status?: number }) =>
          this.laborError.set(err?.status === 409
            ? 'Lote cancelado não recebe apontamento de hora.'
            : 'Não foi possível apontar as horas. Confira o período e a quantidade de pessoas.'),
      });
  }

  private refreshLabor(batchId: string): void {
    this.api.labor(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: entries => this.labor.set(entries),
        error: () => this.laborError.set('Não foi possível carregar os apontamentos.'),
      });
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
    this.refreshApplied(batchId);
  }

  applyCorrection(batchId: string, request: ApplyCorrectionRequest): void {
    this.applying.set(true);
    this.correctionError.set(null);
    this.api.applyCorrection(batchId, request)
      .pipe(finalize(() => this.applying.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success('Correção aplicada (registrada).'); this.refreshApplied(batchId); },
        error: () => this.correctionError.set('Não foi possível aplicar a correção (dados inválidos).'),
      });
  }

  private refreshApplied(batchId: string): void {
    this.api.appliedCorrections(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: a => this.applied.set(a), error: () => {} });
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

  /** Abre (ou fecha) o painel de transferência de um lote e carrega os equipamentos. */
  showTransfer(batchId: string): void {
    if (this.transferBatchId() === batchId) {
      this.transferBatchId.set(null);
      return;
    }
    this.transferBatchId.set(batchId);
    this.transferError.set(null);
    if (this.equipment().length === 0) {
      this.equipmentApi.list(0, 100)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: page => this.equipment.set(page.content), error: () => {} });
    }
  }

  transfer(batchId: string, request: TransferRequest, onSuccess?: () => void): void {
    this.transferring.set(true);
    this.transferError.set(null);
    this.api.transfer(batchId, request)
      .pipe(finalize(() => this.transferring.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.transferBatchId.set(null);
          this.toast.success('Lote transferido ao fermentador.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.transferError.set(err?.status === 409
            ? 'Capacidade do fermentador ou balanço de massa excedido.'
            : 'Não foi possível transferir (dados inválidos).'),
      });
  }

  /** Abre (ou fecha) a central de alertas de um lote e carrega a timeline. */
  showAlerts(batchId: string): void {
    if (this.alertsBatchId() === batchId) {
      this.alertsBatchId.set(null);
      return;
    }
    this.alertsBatchId.set(batchId);
    this.alertError.set(null);
    this.refreshAlerts(batchId);
  }

  createAlert(batchId: string, request: CreateAlertRequest, onSuccess?: () => void): void {
    this.savingAlert.set(true);
    this.alertError.set(null);
    this.api.createAlert(batchId, request)
      .pipe(finalize(() => this.savingAlert.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Alerta criado.'); this.refreshAlerts(batchId); },
        error: () => this.alertError.set('Não foi possível criar o alerta (dados inválidos).'),
      });
  }

  confirmAlert(batchId: string, alertId: string): void {
    this.api.confirmAlert(batchId, alertId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.alerts.update(list => list.map(a => (a.id === updated.id ? updated : a)));
          this.toast.success('Alerta confirmado.');
        },
        error: () => this.toast.error('Não foi possível confirmar o alerta.'),
      });
  }

  private refreshAlerts(batchId: string): void {
    this.alertsLoading.set(true);
    this.alerts.set([]);
    this.api.alerts(batchId)
      .pipe(finalize(() => this.alertsLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: a => this.alerts.set(a),
        error: () => this.alertError.set('Não foi possível carregar os alertas.'),
      });
  }
}
