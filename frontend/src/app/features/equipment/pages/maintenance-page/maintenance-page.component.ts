import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { MaintenanceStore } from '../../data-access/maintenance.store';
import { MaintenanceKind, toScheduleMaintenanceRequest } from '../../domain/maintenance.model';

@Component({
  selector: 'app-maintenance-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [MaintenanceStore],
  templateUrl: './maintenance-page.component.html',
})
export class MaintenancePageComponent implements OnInit {
  protected readonly store = inject(MaintenanceStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    kind: this.fb.nonNullable.control<MaintenanceKind>('MAINTENANCE', Validators.required),
    instrument: '',
    startAt: ['', Validators.required],
    endAt: ['', Validators.required],
    notes: '',
  });

  ngOnInit(): void {
    this.store.loadEquipment();
  }

  protected onSelect(equipmentId: string): void {
    this.store.select(equipmentId || null);
  }

  protected schedule(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.schedule(toScheduleMaintenanceRequest(this.form.getRawValue()), () =>
      this.form.reset({ kind: 'MAINTENANCE', instrument: '', startAt: '', endAt: '', notes: '' }),
    );
  }

  protected cancel(maintenanceId: string): void {
    this.store.cancel(maintenanceId);
  }
}
