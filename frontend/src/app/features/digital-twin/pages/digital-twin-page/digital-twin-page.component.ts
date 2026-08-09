import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';
import { Batch } from '../../../production/domain/batch.model';
import { BatchesApi } from '../../../production/data-access/batches.api';
import { ChartStore } from '../../data-access/chart.store';
import { ProfileStore } from '../../data-access/profile.store';
import {
  KIND_LABELS,
  MeasurementKind,
  SIGNAL_HINTS,
  SIGNAL_LABELS,
  SignalKind,
} from '../../domain/chart.model';
import {
  CONFIDENCE_CLASSES,
  CONFIDENCE_HINTS,
  CONFIDENCE_LABELS,
  Confidence,
} from '../../domain/profile.model';

/**
 * Gêmeo digital: o que a receita aprendeu e o que o processo está fazendo (DTW-001 + SPC-001).
 *
 * <p><strong>As duas coisas na mesma tela, e separadas de propósito.</strong> O perfil aprendido responde
 * "quanto esta receita costuma render"; a carta responde "o processo mudou?". São perguntas diferentes com
 * matemática diferente, e um painel único faria parecer que o mesmo número responde as duas.
 */
@Component({
  selector: 'app-digital-twin-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  providers: [ProfileStore, ChartStore],
  templateUrl: './digital-twin-page.component.html',
})
export class DigitalTwinPageComponent implements OnInit {
  private readonly batchesApi = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly profiles = inject(ProfileStore);
  readonly charts = inject(ChartStore);

  readonly batches = signal<Batch[]>([]);
  readonly loadingBatches = signal(false);
  readonly selectedRecipeId = signal<string>('');
  readonly selectedKind = signal<MeasurementKind>('TEMPERATURE');

  readonly kindLabels = KIND_LABELS;
  readonly kinds = Object.keys(KIND_LABELS) as MeasurementKind[];
  readonly signalLabels = SIGNAL_LABELS;
  readonly signalHints = SIGNAL_HINTS;
  readonly confidenceLabels = CONFIDENCE_LABELS;
  readonly confidenceHints = CONFIDENCE_HINTS;
  readonly confidenceClasses = CONFIDENCE_CLASSES;

  /** Receitas que têm lote: analisar uma receita nunca produzida não teria o que ler. */
  readonly recipes = computed(() => {
    const seen = new Map<string, string>();
    for (const batch of this.batches()) {
      seen.set(batch.recipeId, batch.recipeName);
    }
    return [...seen].map(([id, name]) => ({ id, name }));
  });

  readonly recipeBatches = computed(() =>
    this.batches().filter(b => b.recipeId === this.selectedRecipeId()),
  );

  ngOnInit(): void {
    this.loadingBatches.set(true);
    this.batchesApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: batches => {
          this.batches.set(batches);
          this.loadingBatches.set(false);
        },
        error: () => this.loadingBatches.set(false),
      });
  }

  onRecipeChange(recipeId: string): void {
    this.selectedRecipeId.set(recipeId);
    this.charts.clear();
    if (recipeId) {
      this.profiles.load(recipeId);
    }
  }

  analyze(): void {
    const recipeId = this.selectedRecipeId();
    const batchIds = this.recipeBatches().map(b => b.id);
    if (recipeId && batchIds.length > 0) {
      this.charts.analyze(recipeId, this.selectedKind(), batchIds);
    }
  }

  computeProfile(): void {
    const recipeId = this.selectedRecipeId();
    const batchIds = this.recipeBatches().map(b => b.id);
    if (recipeId && batchIds.length > 0) {
      this.profiles.compute(recipeId, batchIds);
    }
  }

  signalClass(kind: SignalKind): string {
    return kind === 'BEYOND_LIMIT'
      ? 'border-danger-subtle bg-danger-subtle'
      : 'border-warning-subtle bg-warning-subtle';
  }

  confidenceClass(confidence: Confidence): string {
    return this.confidenceClasses[confidence];
  }
}
