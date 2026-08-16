import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { LoadsStore } from '../../data-access/loads.store';
import {
  LOAD_STATUS_HELP,
  LOAD_STATUS_LABELS,
  Load,
  LoadStop,
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

  protected filterByDay(value: string): void {
    this.store.filterByDay(value || null);
  }

  private nextSequence(load: Load): number {
    return load.route.reduce((max, s) => Math.max(max, s.sequence), 0) + 1;
  }
}
