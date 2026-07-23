import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EquipmentStore } from '../../data-access/equipment.store';

@Component({
  selector: 'app-equipment-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [EquipmentStore],
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <h1 class="h4 mb-0 fw-semibold">Equipamentos</h1>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <h2 class="h6 fw-semibold mb-3">Cadastrar perfil de equipamento</h2>
        <form [formGroup]="form" (ngSubmit)="create()" class="row g-3 align-items-end">
          <div class="col-sm-2">
            <label class="form-label" for="code">Código</label>
            <input id="code" type="text" class="form-control" formControlName="code" placeholder="BH-500">
          </div>
          <div class="col-sm-3">
            <label class="form-label" for="name">Nome</label>
            <input id="name" type="text" class="form-control" formControlName="name" placeholder="Brewhouse 500L">
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="capacityLiters">Capacidade (L)</label>
            <input id="capacityLiters" type="number" step="0.001" class="form-control" formControlName="capacityLiters">
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="deadSpaceLiters">Perda (L)</label>
            <input id="deadSpaceLiters" type="number" step="0.001" class="form-control" formControlName="deadSpaceLiters">
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="mashEfficiencyPercent">Eficiência (%)</label>
            <input id="mashEfficiencyPercent" type="number" step="0.01" class="form-control"
                   formControlName="mashEfficiencyPercent">
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="boilOffLitersPerHour">Evaporação (L/h)</label>
            <input id="boilOffLitersPerHour" type="number" step="0.001" class="form-control"
                   formControlName="boilOffLitersPerHour">
          </div>
          <div class="col-sm-2 d-grid">
            <button type="submit" class="btn btn-primary" [disabled]="form.invalid || store.submitting()">
              <i class="ri-add-line me-1"></i> Cadastrar
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
        <h2 class="h6 fw-semibold mb-3">Perfis cadastrados</h2>
        @if (store.loading()) {
          <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
        } @else if (store.error(); as error) {
          <div class="alert alert-danger mb-0" role="alert">{{ error }}</div>
        } @else if (store.empty()) {
          <p class="text-muted mb-0">Nenhum equipamento cadastrado.</p>
        } @else {
          <div class="table-responsive">
            <table class="table align-middle mb-0">
              <thead>
                <tr>
                  <th>Código</th><th>Nome</th><th class="text-end">Capacidade (L)</th>
                  <th class="text-end">Perda (L)</th><th class="text-end">Eficiência (%)</th>
                  <th class="text-end">Evaporação (L/h)</th>
                </tr>
              </thead>
              <tbody>
                @for (item of store.items(); track item.id) {
                  <tr>
                    <td class="fw-semibold">{{ item.code }}</td>
                    <td>{{ item.name }}</td>
                    <td class="text-end">{{ item.capacityLiters }}</td>
                    <td class="text-end">{{ item.deadSpaceLiters }}</td>
                    <td class="text-end">{{ item.mashEfficiencyPercent }}</td>
                    <td class="text-end">{{ item.boilOffLitersPerHour }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
})
export class EquipmentPageComponent implements OnInit {
  protected readonly store = inject(EquipmentStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    capacityLiters: [0, [Validators.required, Validators.min(0.001)]],
    deadSpaceLiters: [0, [Validators.required, Validators.min(0)]],
    mashEfficiencyPercent: [70, [Validators.required, Validators.min(0.01), Validators.max(100)]],
    boilOffLitersPerHour: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () =>
      this.form.reset({
        code: '',
        name: '',
        capacityLiters: 0,
        deadSpaceLiters: 0,
        mashEfficiencyPercent: 70,
        boilOffLitersPerHour: 0,
      }),
    );
  }
}
