import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { FermentationProfile } from '../domain/profile.model';
import {
  BatchOption,
  FermentationReading,
  FgStability,
  ReadingKind,
  RecordReadingRequest,
} from '../domain/reading.model';
import { ReadingsApi } from './readings.api';

/** Estado das leituras e curvas de fermentação de um lote (FER-002). */
@Injectable()
export class ReadingsStore {
  private readonly api = inject(ReadingsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<FermentationReading[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly batches = signal<BatchOption[]>([]);
  readonly batchId = signal<string | null>(null);
  readonly kind = signal<ReadingKind>('DENSITY');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly invalidCount = computed(() => this.items().filter(r => !r.valid).length);

  /** Estabilidade de FG (FER-003): parecer sob demanda, nunca automático. */
  readonly profiles = signal<FermentationProfile[]>([]);
  readonly profileId = signal<string | null>(null);
  readonly stability = signal<FgStability | null>(null);
  readonly evaluating = signal(false);
  readonly stabilityError = signal<string | null>(null);

  loadBatches(): void {
    this.api.batches()
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

  /** Só perfil publicado rege um parecer; rascunho ainda muda debaixo da avaliação. */
  loadProfiles(): void {
    this.api.profiles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: profiles => {
          const published = profiles.filter(p => p.status === 'PUBLISHED');
          this.profiles.set(published);
          if (!this.profileId() && published.length > 0) {
            this.profileId.set(published[0].id);
          }
        },
        error: () => this.profiles.set([]),
      });
  }

  select(batchId: string): void {
    this.batchId.set(batchId);
    this.stability.set(null);
    this.stabilityError.set(null);
    this.load();
  }

  selectProfile(profileId: string): void {
    this.profileId.set(profileId);
    this.stability.set(null);
  }

  evaluateStability(): void {
    const batchId = this.batchId();
    const profileId = this.profileId();
    if (!batchId || !profileId) {
      return;
    }
    this.evaluating.set(true);
    this.stabilityError.set(null);
    this.api.fgStability(batchId, profileId)
      .pipe(finalize(() => this.evaluating.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => this.stability.set(result),
        error: (err: { status?: number }) =>
          this.stabilityError.set(err?.status === 409
            ? 'Perfil em rascunho não pode reger a avaliação; publique-o antes.'
            : 'Não foi possível avaliar a estabilidade de FG.'),
      });
  }

  selectKind(kind: ReadingKind): void {
    this.kind.set(kind);
    this.load();
  }

  load(): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.list(batchId, this.kind())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar as leituras.'),
      });
  }

  record(request: RecordReadingRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.record(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          onSuccess?.();
          // Fora da faixa plausível a leitura é gravada e sinalizada, não recusada.
          if (result.valid) {
            this.toast.success('Leitura registrada.');
          } else {
            this.toast.error(`Leitura registrada e sinalizada: ${result.invalidReason}`);
          }
          this.load();
        },
        error: () => this.actionError.set('Não foi possível registrar a leitura (dados inválidos).'),
      });
  }
}
