import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MaintenanceStore } from '../../data-access/maintenance.store';
import { MaintenanceKind, toScheduleMaintenanceRequest } from '../../domain/maintenance.model';

@Component({
  selector: 'app-maintenance-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [MaintenanceStore],
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <h1 class="h4 mb-0 fw-semibold">Manutenção e calibração</h1>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <label class="form-label" for="equipment">Equipamento</label>
        <select id="equipment" class="form-select" (change)="onSelect($any($event.target).value)">
          <option value="">Selecione um equipamento…</option>
          @for (e of store.equipment(); track e.id) {
            <option [value]="e.id">{{ e.code }} — {{ e.name }}</option>
          }
        </select>
      </div>
    </div>

    @if (store.selectedId()) {
      <div class="card border-0 rounded-10 mb-4">
        <div class="card-body p-4">
          <h2 class="h6 fw-semibold mb-3">Agendar janela</h2>
          <form [formGroup]="form" (ngSubmit)="schedule()" class="row g-3 align-items-end">
            <div class="col-sm-2">
              <label class="form-label" for="kind">Tipo</label>
              <select id="kind" class="form-select" formControlName="kind">
                <option value="MAINTENANCE">Manutenção</option>
                <option value="CALIBRATION">Calibração</option>
              </select>
            </div>
            <div class="col-sm-3">
              <label class="form-label" for="instrument">Instrumento</label>
              <input id="instrument" type="text" class="form-control" formControlName="instrument"
                     placeholder="obrigatório p/ calibração">
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="startAt">Início</label>
              <input id="startAt" type="datetime-local" class="form-control" formControlName="startAt">
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="endAt">Fim</label>
              <input id="endAt" type="datetime-local" class="form-control" formControlName="endAt">
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="notes">Observação</label>
              <input id="notes" type="text" class="form-control" formControlName="notes">
            </div>
            <div class="col-sm-1 d-grid">
              <button type="submit" class="btn btn-primary" [disabled]="form.invalid || store.submitting()">
                <i class="ri-add-line"></i>
              </button>
            </div>
          </form>
          @if (store.actionError(); as actionError) {
            <div class="alert alert-danger mt-3 mb-0" role="alert">{{ actionError }}</div>
          }
        </div>
      </div>

      <div class="card border-0 rounded-10">
        <div class="card-body p-4">
          <h2 class="h6 fw-semibold mb-3">Janelas</h2>
          @if (store.loading()) {
            <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
          } @else if (store.error(); as error) {
            <div class="alert alert-danger mb-0" role="alert">{{ error }}</div>
          } @else if (store.empty()) {
            <p class="text-muted mb-0">Nenhuma janela para este equipamento.</p>
          } @else {
            <div class="table-responsive">
              <table class="table align-middle mb-0">
                <thead>
                  <tr><th>Tipo</th><th>Instrumento</th><th>Início</th><th>Fim</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  @for (w of store.windows(); track w.id) {
                    <tr>
                      <td>{{ w.kind }}</td>
                      <td>{{ w.instrument ?? '—' }}</td>
                      <td>{{ w.startAt }}</td>
                      <td>{{ w.endAt }}</td>
                      <td>
                        <span class="badge"
                              [class.bg-success-subtle]="w.status === 'SCHEDULED'"
                              [class.text-success-emphasis]="w.status === 'SCHEDULED'"
                              [class.bg-secondary-subtle]="w.status === 'CANCELLED'"
                              [class.text-secondary-emphasis]="w.status === 'CANCELLED'">{{ w.status }}</span>
                      </td>
                      <td class="text-end">
                        @if (w.status === 'SCHEDULED') {
                          <button type="button" class="btn btn-sm btn-outline-danger" (click)="cancel(w.id)">
                            Cancelar
                          </button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>
    }
  `,
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
