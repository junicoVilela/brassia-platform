import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  ImportJob,
  ReferenceDataset,
  ReferenceSource,
  RecordReferenceDatasetRequest,
  RegisterReferenceSourceRequest,
  SubmitImportJobRequest,
} from '../domain/reference.model';
import { ReferenceApi } from './reference.api';

/** Estado da curadoria de dados de referência: fontes, datasets da fonte e ações. */
@Injectable()
export class ReferenceStore {
  private readonly api = inject(ReferenceApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly sourcesState = signal<ReferenceSource[]>([]);
  private readonly selectedIdState = signal<string | null>(null);
  private readonly datasetsState = signal<ReferenceDataset[]>([]);
  private readonly jobsState = signal<ImportJob[]>([]);

  readonly sources = this.sourcesState.asReadonly();
  readonly selectedId = this.selectedIdState.asReadonly();
  readonly datasets = this.datasetsState.asReadonly();
  readonly jobs = this.jobsState.asReadonly();
  readonly loading = signal(false);
  readonly loadingDatasets = signal(false);
  readonly loadingJobs = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.sources().length === 0);
  readonly selected = computed(() => this.sources().find(s => s.id === this.selectedId()) ?? null);
  readonly noDatasets = computed(
    () => !!this.selectedIdState() && !this.loadingDatasets() && this.datasets().length === 0,
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listSources()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.sourcesState.set(page.content),
        error: () => this.error.set('Não foi possível carregar as fontes.'),
      });
  }

  registerSource(request: RegisterReferenceSourceRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.registerSource(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Fonte registrada.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível registrar a fonte (nome duplicado ou dados inválidos).'),
      });
  }

  select(sourceId: string | null): void {
    this.selectedIdState.set(sourceId);
    this.datasetsState.set([]);
    this.jobsState.set([]);
    this.actionError.set(null);
    if (sourceId) {
      this.loadDatasets();
      this.loadJobs();
    }
  }

  private loadJobs(): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.loadingJobs.set(true);
    this.api.listJobs(id)
      .pipe(finalize(() => this.loadingJobs.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: jobs => this.jobsState.set(jobs),
        error: () => this.error.set('Não foi possível carregar os jobs de importação.'),
      });
  }

  submitJob(request: SubmitImportJobRequest, onSuccess?: () => void): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.submitJob(id, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: job => {
          onSuccess?.();
          this.toast.success(job.status === 'FAILED' ? 'Job com erros de validação.' : 'Job em revisão.');
          this.loadJobs();
        },
        error: () => this.actionError.set('Não foi possível submeter o job (dados inválidos).'),
      });
  }

  publishJob(jobId: string): void {
    this.actionError.set(null);
    this.api.publishJob(jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Job publicado.');
          this.loadJobs();
          this.loadDatasets();
        },
        error: () =>
          this.actionError.set('Não foi possível publicar o job (permissão da fonte não autoriza ou conteúdo já publicado).'),
      });
  }

  private loadDatasets(): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.loadingDatasets.set(true);
    this.api.listDatasets(id)
      .pipe(finalize(() => this.loadingDatasets.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: datasets => this.datasetsState.set(datasets),
        error: () => this.error.set('Não foi possível carregar os datasets.'),
      });
  }

  recordDataset(request: RecordReferenceDatasetRequest, onSuccess?: () => void): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.recordDataset(id, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: dataset => {
          onSuccess?.();
          this.toast.success(dataset.created ? 'Dataset registrado.' : 'Dataset idêntico já existente (idempotente).');
          this.loadDatasets();
        },
        error: () => this.actionError.set('Não foi possível registrar o dataset (dados inválidos).'),
      });
  }

  publish(datasetId: string): void {
    this.actionError.set(null);
    this.api.publishDataset(datasetId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Dataset publicado.');
          this.loadDatasets();
        },
        error: () =>
          this.actionError.set('Não foi possível publicar (permissão da fonte não autoriza ou dataset alterado).'),
      });
  }
}
