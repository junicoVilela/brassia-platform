import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { FermentationProfile } from '../domain/profile.model';
import { BatchOption } from '../domain/reading.model';
import {
  AddStepRequest,
  FermentationSchedule,
  PlanScheduleRequest,
  ReschedulePreview,
} from '../domain/schedule.model';
import { ReadingsApi } from './readings.api';
import { ScheduleApi } from './schedule.api';

/** Estado da linha do tempo de fermentação de um lote (FER-004). */
@Injectable()
export class ScheduleStore {
  private readonly api = inject(ScheduleApi);
  private readonly shared = inject(ReadingsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly batches = signal<BatchOption[]>([]);
  readonly profiles = signal<FermentationProfile[]>([]);
  readonly batchId = signal<string | null>(null);
  readonly schedule = signal<FermentationSchedule | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Prévia pendente de confirmação; nada foi gravado enquanto ela existe. */
  readonly preview = signal<ReschedulePreview | null>(null);
  readonly previewStepId = signal<string | null>(null);
  readonly previewNewStart = signal<string | null>(null);

  readonly steps = computed(() => this.schedule()?.steps ?? []);
  readonly planned = computed(() => !!this.schedule());
  readonly pendingSteps = computed(() => this.steps().filter(s => s.status === 'PLANNED'));

  loadBatches(): void {
    this.shared.batches()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: batches => {
          this.batches.set(batches);
          if (!this.batchId() && batches.length > 0) {
            this.select(batches[0].id);
          }
        },
        error: () => this.error.set('Não foi possível carregar os lotes.'),
      });
  }

  /** Só perfil publicado pode reger uma agenda. */
  loadProfiles(): void {
    this.shared.profiles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: profiles => this.profiles.set(profiles.filter(p => p.status === 'PUBLISHED')),
        error: () => this.profiles.set([]),
      });
  }

  select(batchId: string): void {
    this.batchId.set(batchId);
    this.clearPreview();
    this.load();
  }

  load(): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.get(batchId)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // Lote sem agenda não é erro: é o estado inicial, que a tela oferece planejar.
        next: schedule => this.schedule.set(schedule),
        error: () => this.schedule.set(null),
      });
  }

  plan(request: PlanScheduleRequest, onSuccess?: () => void): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.plan(batchId, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          onSuccess?.();
          this.toast.success(`Agenda planejada com ${result.steps} etapa(s).`);
          this.load();
        },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'O lote já tem agenda, ou o perfil escolhido ainda é rascunho.'
            : 'Não foi possível planejar a agenda (dados inválidos).'),
      });
  }

  addStep(request: AddStepRequest, onSuccess?: () => void): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.addStep(batchId, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Etapa acrescentada.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível acrescentar a etapa (dados inválidos).'),
      });
  }

  /** Calcula o impacto sem gravar; a confirmação é um segundo passo. */
  previewReschedule(stepId: string, newStart: string): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    this.api.reschedule(batchId, stepId, newStart, false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: preview => {
          this.preview.set(preview);
          this.previewStepId.set(stepId);
          this.previewNewStart.set(newStart);
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Etapa já executada não é replanejada.'
            : 'Não foi possível calcular a prévia.'),
      });
  }

  confirmReschedule(): void {
    const batchId = this.batchId();
    const stepId = this.previewStepId();
    const newStart = this.previewNewStart();
    if (!batchId || !stepId || !newStart) {
      return;
    }
    this.api.reschedule(batchId, stepId, newStart, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: applied => {
          this.toast.success(`${applied.changes.length} etapa(s) replanejada(s).`);
          this.clearPreview();
          this.load();
        },
        error: () => this.toast.error('Não foi possível aplicar o replanejamento.'),
      });
  }

  clearPreview(): void {
    this.preview.set(null);
    this.previewStepId.set(null);
    this.previewNewStart.set(null);
  }

  execute(stepId: string, executedAt: string, justification: string | null): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    this.api.execute(batchId, stepId, executedAt, justification)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Execução registrada.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Etapa já executada.'
            : 'Não foi possível registrar: fora da tolerância, a justificativa é obrigatória.'),
      });
  }
}
