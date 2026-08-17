import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { LoadsStore } from '../../data-access/loads.store';
import {
  DeliveryOutcome,
  LOAD_STATUS_HELP,
  LOAD_STATUS_LABELS,
  Load,
  LoadStop,
  OUTCOME_LABELS,
  SYNC_STATUS_LABELS,
} from '../../domain/load.model';

/**
 * Cargas e roteiros (LOG-001).
 *
 * <p>A responsabilidade da tela que não é listar: <strong>deixar visível que conferir é ato de outra
 * pessoa</strong>. Quem montou não vê o botão de liberar, e quem reabre é avisado de que a conferência
 * anterior cai junto — senão ele reabre achando que só destravou a edição.
 */
@Component({
  selector: 'app-loads-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [LoadsStore],
  templateUrl: './loads-page.component.html',
})
export class LoadsPageComponent implements OnInit {
  protected readonly store = inject(LoadsStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = LOAD_STATUS_LABELS;
  protected readonly statusHelp = LOAD_STATUS_HELP;

  protected readonly canPlan = this.auth.hasPermission('distribution.load.plan');
  protected readonly canRelease = this.auth.hasPermission('distribution.load.release');
  protected readonly canRecord = this.auth.hasPermission('distribution.delivery.record');
  protected readonly canCorrect = this.auth.hasPermission('distribution.delivery.correct');

  protected readonly outcomeLabels = OUTCOME_LABELS;
  protected readonly syncLabels = SYNC_STATUS_LABELS;
  protected readonly outcomes: DeliveryOutcome[] = [
    'DELIVERED',
    'PARTIAL',
    'REFUSED',
    'ABSENT',
    'RESCHEDULED',
  ];

  /** A parada cuja entrega está sendo registrada agora. */
  protected readonly recording = signal<LoadStop | null>(null);
  protected readonly correcting = signal<LoadStop | null>(null);

  protected readonly proofForm = this.fb.nonNullable.group({
    outcome: ['DELIVERED' as DeliveryOutcome, Validators.required],
    note: ['', Validators.maxLength(1000)],
    collected: ['', Validators.maxLength(500)],
    consentedByName: ['', Validators.maxLength(160)],
  });

  protected readonly correctionForm = this.fb.nonNullable.group({
    outcome: ['PARTIAL' as DeliveryOutcome, Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  protected readonly showForm = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    scheduledFor: ['', Validators.required],
    capacityLiters: [1000, [Validators.required, Validators.min(0.001)]],
  });

  protected readonly stopForm = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    customerName: ['', [Validators.required, Validators.maxLength(160)]],
    sequence: [1, [Validators.required, Validators.min(1)]],
    windowFrom: [''],
    windowTo: [''],
  });

  protected readonly containerForm = this.fb.nonNullable.group({
    stopId: ['', Validators.required],
    containerId: ['', Validators.required],
  });

  protected readonly driverForm = this.fb.nonNullable.group({
    driverId: ['', Validators.required],
    vehicle: ['', Validators.maxLength(60)],
  });

  ngOnInit(): void {
    this.store.load();
    this.store.loadConflicts();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.store.plan(v.code, v.scheduledFor, v.capacityLiters);
    this.form.reset({ code: '', scheduledFor: '', capacityLiters: 1000 });
    this.showForm.set(false);
  }

  protected open(load: Load): void {
    this.stopForm.reset({
      customerId: '',
      customerName: '',
      sequence: this.nextSequence(load),
      windowFrom: '',
      windowTo: '',
    });
    this.containerForm.reset({ stopId: '', containerId: '' });
    this.driverForm.reset({ driverId: load.driverId ?? '', vehicle: load.vehicle ?? '' });
    this.store.open(load);
  }

  protected submitStop(load: Load): void {
    if (this.stopForm.invalid) {
      this.stopForm.markAllAsTouched();
      return;
    }
    const v = this.stopForm.getRawValue();
    // Os campos são `datetime-local`; o servidor espera instante — a conversão acontece aqui, e não
    // com a string crua, que viraria uma janela na hora errada.
    const from = v.windowFrom ? new Date(v.windowFrom).toISOString() : null;
    const to = v.windowTo ? new Date(v.windowTo).toISOString() : null;
    this.store.addStop(load, v.customerId, v.customerName, v.sequence, from, to);
    this.stopForm.reset({
      customerId: '',
      customerName: '',
      sequence: v.sequence + 1,
      windowFrom: '',
      windowTo: '',
    });
  }

  protected submitContainer(load: Load): void {
    if (this.containerForm.invalid) {
      this.containerForm.markAllAsTouched();
      return;
    }
    const v = this.containerForm.getRawValue();
    this.store.loadContainer(load, v.stopId, v.containerId);
    this.containerForm.patchValue({ containerId: '' });
  }

  protected submitDriver(load: Load): void {
    if (this.driverForm.invalid) {
      this.driverForm.markAllAsTouched();
      return;
    }
    const v = this.driverForm.getRawValue();
    this.store.assign(load, v.driverId, v.vehicle || null);
  }

  protected removeStop(load: Load, stop: LoadStop): void {
    this.store.removeStop(load, stop.id);
  }

  protected unload(load: Load, containerId: string): void {
    this.store.unloadContainer(load, containerId);
  }

  protected openRecord(stop: LoadStop): void {
    this.proofForm.reset({ outcome: 'DELIVERED', note: '', collected: '', consentedByName: '' });
    this.recording.set(stop);
  }

  protected submitProof(load: Load): void {
    const stop = this.recording();
    if (!stop || this.proofForm.invalid) {
      this.proofForm.markAllAsTouched();
      return;
    }
    const v = this.proofForm.getRawValue();
    // Só desce o que estava na parada. "Entregue" leva tudo; os demais desfechos não entregam nada, e
    // registrar item numa não entrega faria o estoque acreditar em uma das duas metades.
    const delivered = v.outcome === 'DELIVERED' || v.outcome === 'PARTIAL'
      ? stop.items.map(i => i.containerId)
      : [];
    const collected = v.collected
      .split(',')
      .map(c => c.trim())
      .filter(c => c.length > 0);
    this.store.recordProof(load, stop.id, v.outcome, delivered, collected, v.note || null,
      v.consentedByName || null);
    this.recording.set(null);
  }

  protected openCorrection(stop: LoadStop): void {
    this.correctionForm.reset({ outcome: 'PARTIAL', reason: '' });
    this.correcting.set(stop);
  }

  protected submitCorrection(load: Load): void {
    const stop = this.correcting();
    if (!stop || this.correctionForm.invalid) {
      this.correctionForm.markAllAsTouched();
      return;
    }
    const v = this.correctionForm.getRawValue();
    this.store.correctProof(load, stop.id, v.outcome, [], [], v.reason);
    this.correcting.set(null);
  }

  protected proofsOf(stopId: string) {
    return this.store.proofs().filter(p => p.stopId === stopId);
  }

  protected recorded(stopId: string): boolean {
    return this.store.recordedStops().has(stopId);
  }

  protected filterByDay(value: string): void {
    this.store.filterByDay(value || null);
  }

  private nextSequence(load: Load): number {
    return load.route.reduce((max, s) => Math.max(max, s.sequence), 0) + 1;
  }
}
