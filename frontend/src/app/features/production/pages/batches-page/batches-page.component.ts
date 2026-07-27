import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { BatchesStore } from '../../data-access/batches.store';
import { ALERT_KINDS } from '../../domain/alert.model';
import { MEASUREMENT_KINDS, MEASUREMENT_SOURCES } from '../../domain/measurement.model';

@Component({
  selector: 'app-batches-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, DecimalPipe, ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent,
  ],
  providers: [BatchesStore],
  templateUrl: './batches-page.component.html',
})
export class BatchesPageComponent implements OnInit {
  protected readonly store = inject(BatchesStore);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly kinds = MEASUREMENT_KINDS;
  protected readonly sources = MEASUREMENT_SOURCES;
  protected readonly alertKinds = ALERT_KINDS;

  protected readonly alertForm = this.fb.nonNullable.group({
    kind: ['DECISION', Validators.required],
    message: ['', [Validators.required, Validators.maxLength(300)]],
  });

  /** Relógio que avança a cada segundo; o decorrido deriva de started_at (server-aware). */
  protected readonly now = signal(Date.now());

  protected readonly transferForm = this.fb.nonNullable.group({
    destinationEquipmentId: ['', Validators.required],
    volumeLiters: [0, [Validators.required, Validators.min(0.01)]],
    ogSg: [1.05, [Validators.required, Validators.min(0.01)]],
    lossesLiters: [0, [Validators.min(0)]],
  });

  protected readonly measurementForm = this.fb.nonNullable.group({
    kind: ['DENSITY', Validators.required],
    unit: ['SG', Validators.required],
    value: [0, [Validators.required]],
    temperatureC: this.fb.control<number | null>(null),
    method: [''],
    source: ['MANUAL', Validators.required],
  });

  private readonly kindSignal = signal('DENSITY');
  /** Unidades válidas para a grandeza selecionada. */
  protected readonly units = computed(() => this.kinds.find(k => k.value === this.kindSignal())?.units ?? []);

  ngOnInit(): void {
    this.store.load();
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
    // Ao trocar a grandeza, ajusta a unidade para a primeira válida.
    this.measurementForm.controls.kind.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(kind => {
        this.kindSignal.set(kind);
        const first = this.kinds.find(k => k.value === kind)?.units[0] ?? '';
        this.measurementForm.controls.unit.setValue(first);
      });
  }

  // --- Correções (PRD-004) ---
  protected readonly selectedCorrectionId = signal<string>('');
  private readonly inputValues = signal<Record<string, number>>({});

  protected readonly selectedCorrection = computed(() =>
    this.store.corrections().find(c => c.id === this.selectedCorrectionId()) ?? null);

  protected startCorrections(batchId: string): void {
    this.selectedCorrectionId.set('');
    this.inputValues.set({});
    this.store.showCorrections(batchId);
  }

  protected selectCorrection(id: string): void {
    this.selectedCorrectionId.set(id);
    this.inputValues.set({});
    this.store.previewResult.set(null);
  }

  protected setInput(name: string, event: Event): void {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    this.inputValues.update(v => ({ ...v, [name]: Number.isNaN(value) ? 0 : value }));
  }

  protected canPreview(): boolean {
    const c = this.selectedCorrection();
    return !!c && c.inputs.every(i => this.inputValues()[i] !== undefined);
  }

  protected runPreview(batchId: string): void {
    const c = this.selectedCorrection();
    if (!c) {
      return;
    }
    this.store.preview(batchId, { calculator: c.id, inputs: this.inputValues() });
  }

  protected readonly realizedValue = signal<number | null>(null);
  protected readonly note = signal('');

  protected setRealized(event: Event): void {
    const v = (event.target as HTMLInputElement).valueAsNumber;
    this.realizedValue.set(Number.isNaN(v) ? null : v);
  }

  protected setNote(event: Event): void {
    this.note.set((event.target as HTMLInputElement).value);
  }

  protected applyCurrent(batchId: string): void {
    const c = this.selectedCorrection();
    if (!c) {
      return;
    }
    this.store.applyCorrection(batchId, {
      calculator: c.id,
      inputs: this.inputValues(),
      realizedValue: this.realizedValue(),
      note: this.note() || null,
    });
  }

  protected startTransfer(batchId: string): void {
    this.transferForm.reset({ destinationEquipmentId: '', volumeLiters: 0, ogSg: 1.05, lossesLiters: 0 });
    this.store.showTransfer(batchId);
  }

  protected doTransfer(batchId: string): void {
    if (this.transferForm.invalid) {
      return;
    }
    const v = this.transferForm.getRawValue();
    this.store.transfer(batchId, {
      destinationEquipmentId: v.destinationEquipmentId,
      volumeLiters: v.volumeLiters,
      ogSg: v.ogSg,
      lossesLiters: v.lossesLiters,
    });
  }

  protected startAlerts(batchId: string): void {
    this.alertForm.reset({ kind: 'DECISION', message: '' });
    this.store.showAlerts(batchId);
  }

  protected createAlert(batchId: string): void {
    if (this.alertForm.invalid) {
      return;
    }
    const v = this.alertForm.getRawValue();
    this.store.createAlert(batchId, { kind: v.kind, message: v.message },
      () => this.alertForm.reset({ kind: v.kind, message: '' }));
  }

  protected startMeasurements(batchId: string): void {
    this.measurementForm.reset({ kind: 'DENSITY', unit: 'SG', value: 0, temperatureC: null, method: '', source: 'MANUAL' });
    this.kindSignal.set('DENSITY');
    this.store.showMeasurements(batchId);
  }

  protected record(batchId: string): void {
    if (this.measurementForm.invalid) {
      return;
    }
    const v = this.measurementForm.getRawValue();
    this.store.recordMeasurement(batchId,
      { kind: v.kind, unit: v.unit, value: v.value, temperatureC: v.temperatureC, method: v.method || null,
        source: v.source },
      () => this.measurementForm.reset({ kind: v.kind, unit: v.unit, value: 0, temperatureC: null, method: '',
        source: v.source }));
  }

  /** Decorrido "mm:ss" desde o início da etapa ativa. */
  protected elapsed(startedAt: string | null): string {
    if (!startedAt) {
      return '—';
    }
    const seconds = Math.max(0, Math.floor((this.now() - new Date(startedAt).getTime()) / 1000));
    const mm = Math.floor(seconds / 60).toString().padStart(2, '0');
    const ss = (seconds % 60).toString().padStart(2, '0');
    return `${mm}:${ss}`;
  }
}
