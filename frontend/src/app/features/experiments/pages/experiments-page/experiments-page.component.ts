import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { BatchesApi } from '../../../production/data-access/batches.api';
import { Batch } from '../../../production/domain/batch.model';
import { ExperimentsStore } from '../../data-access/experiments.store';
import { Experiment, STATUS_CLASSES, STATUS_LABELS } from '../../domain/experiment.model';

interface FactorRow {
  name: string;
  controlValue: string;
  variantValue: string;
}

const MEASUREMENT_KINDS = ['DENSITY', 'TEMPERATURE', 'VOLUME', 'PH', 'COLOR', 'IBU'];

/**
 * Lote dividido (EXP-001).
 *
 * <p><strong>O formulário mostra quantos fatores diferem enquanto se digita.</strong> Descobrir só ao
 * enviar que o desenho está confundido é tarde: a pessoa já montou o experimento inteiro na cabeça. O
 * contador é o mesmo critério do servidor, dito antes — e o servidor continua sendo quem decide.
 */
@Component({
  selector: 'app-experiments-page',
  standalone: true,
  imports: [DatePipe, FormsModule],
  providers: [ExperimentsStore],
  templateUrl: './experiments-page.component.html',
})
export class ExperimentsPageComponent implements OnInit {
  private readonly batchesApi = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly store = inject(ExperimentsStore);

  readonly batches = signal<Batch[]>([]);
  readonly statusLabels = STATUS_LABELS;
  readonly statusClasses = STATUS_CLASSES;
  readonly kinds = MEASUREMENT_KINDS;

  readonly showForm = signal(false);
  readonly recipeId = signal('');
  readonly hypothesis = signal('');
  readonly controlBatchId = signal('');
  readonly variantBatchId = signal('');
  readonly sensoryPlanned = signal(true);
  readonly sensoryBlind = signal(true);
  readonly measurements = signal<string[]>(['DENSITY']);
  readonly factors = signal<FactorRow[]>([
    { name: '', controlValue: '', variantValue: '' },
    { name: '', controlValue: '', variantValue: '' },
  ]);

  readonly concludeFor = signal<string | null>(null);
  readonly observation = signal('');
  readonly supported = signal(true);

  readonly recipes = computed(() => {
    const seen = new Map<string, string>();
    for (const batch of this.batches()) {
      seen.set(batch.recipeId, batch.recipeName);
    }
    return [...seen].map(([id, name]) => ({ id, name }));
  });

  readonly recipeBatches = computed(() =>
    this.batches().filter(b => b.recipeId === this.recipeId()),
  );

  /** Os fatores preenchidos que diferem. Mesmo critério do servidor, dito antes de enviar. */
  readonly differingFactors = computed(() =>
    this.factors().filter(
      f => f.name.trim() && f.controlValue.trim() && f.variantValue.trim()
        && f.controlValue.trim() !== f.variantValue.trim(),
    ),
  );

  readonly canSubmit = computed(
    () =>
      !!this.recipeId() &&
      !!this.hypothesis().trim() &&
      !!this.controlBatchId() &&
      !!this.variantBatchId() &&
      this.controlBatchId() !== this.variantBatchId() &&
      this.differingFactors().length === 1 &&
      !this.store.planning(),
  );

  ngOnInit(): void {
    this.store.load();
    this.batchesApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: batches => this.batches.set(batches), error: () => undefined });
  }

  addFactor(): void {
    this.factors.update(rows => [...rows, { name: '', controlValue: '', variantValue: '' }]);
  }

  removeFactor(index: number): void {
    this.factors.update(rows => rows.filter((_, i) => i !== index));
  }

  updateFactor(index: number, field: keyof FactorRow, value: string): void {
    this.factors.update(rows =>
      rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)),
    );
  }

  toggleMeasurement(kind: string): void {
    this.measurements.update(kinds =>
      kinds.includes(kind) ? kinds.filter(k => k !== kind) : [...kinds, kind],
    );
  }

  submit(): void {
    const filled = this.factors().filter(f => f.name.trim());
    this.store.plan(
      {
        recipeId: this.recipeId(),
        hypothesis: this.hypothesis().trim(),
        controlBatchId: this.controlBatchId(),
        variantBatchId: this.variantBatchId(),
        factors: filled.map(f => ({
          name: f.name.trim(),
          controlValue: f.controlValue.trim(),
          variantValue: f.variantValue.trim(),
        })),
        plannedMeasurements: this.measurements(),
        sensoryPlanned: this.sensoryPlanned(),
        sensoryBlind: this.sensoryBlind(),
      },
      () => this.resetForm(),
    );
  }

  openConclusion(experiment: Experiment): void {
    this.concludeFor.set(experiment.id);
    this.observation.set('');
    this.supported.set(true);
  }

  submitConclusion(experiment: Experiment): void {
    this.store.conclude(experiment.id, this.supported(), this.observation().trim());
    this.concludeFor.set(null);
  }

  batchLabel(batchId: string): string {
    return this.batches().find(b => b.id === batchId)?.code ?? batchId.slice(0, 8);
  }

  private resetForm(): void {
    this.showForm.set(false);
    this.hypothesis.set('');
    this.controlBatchId.set('');
    this.variantBatchId.set('');
    this.factors.set([
      { name: '', controlValue: '', variantValue: '' },
      { name: '', controlValue: '', variantValue: '' },
    ]);
  }
}
