import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ReadingsStore } from '../../data-access/readings.store';
import {
  FG_VERDICT_LABELS,
  READING_KINDS,
  READING_KIND_LABELS,
  READING_SOURCES,
  READING_UNITS,
  ReadingKind,
  ReadingSource,
} from '../../domain/reading.model';
import { ReadingsChartComponent } from '../../ui/readings-chart.component';

@Component({
  selector: 'app-readings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
    ReadingsChartComponent,
  ],
  providers: [ReadingsStore],
  templateUrl: './readings-page.component.html',
})
export class ReadingsPageComponent implements OnInit {
  protected readonly store = inject(ReadingsStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly canRecord = this.auth.hasPermission('fermentation.reading.record');
  protected readonly kinds = READING_KINDS;
  protected readonly sources = READING_SOURCES;
  protected readonly kindLabels = READING_KIND_LABELS;
  protected readonly verdictLabels = FG_VERDICT_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    kind: this.fb.nonNullable.control<ReadingKind>('DENSITY', Validators.required),
    source: this.fb.nonNullable.control<ReadingSource>('MANUAL', Validators.required),
    value: this.fb.control<number | null>(null, Validators.required),
    unit: ['SG', Validators.required],
    measuredAt: ['', Validators.required],
  });

  ngOnInit(): void {
    this.store.loadBatches();
    this.store.loadProfiles();
    // Trocar a grandeza reposiciona a unidade: unidade incompatível é recusada pelo domínio.
    this.form.controls.kind.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(kind => this.form.controls.unit.setValue(READING_UNITS[kind][0]));
  }

  /** Unidades permitidas para a grandeza escolhida no formulário. */
  protected units(): string[] {
    return READING_UNITS[this.form.controls.kind.value];
  }

  protected record(): void {
    const batchId = this.store.batchId();
    if (this.form.invalid || !batchId) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.record(
      {
        batchId,
        kind: v.kind,
        source: v.source,
        value: v.value!,
        unit: v.unit,
        measuredAt: new Date(v.measuredAt).toISOString(),
      },
      () => this.form.patchValue({ value: null, measuredAt: '' }),
    );
  }
}
