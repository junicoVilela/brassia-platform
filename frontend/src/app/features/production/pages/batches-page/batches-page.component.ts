import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { BatchesStore } from '../../data-access/batches.store';
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

  /** Relógio que avança a cada segundo; o decorrido deriva de started_at (server-aware). */
  protected readonly now = signal(Date.now());

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
