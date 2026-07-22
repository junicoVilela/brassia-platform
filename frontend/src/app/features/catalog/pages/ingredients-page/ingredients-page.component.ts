import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { IngredientsStore } from '../../data-access/ingredients.store';
import {
  INGREDIENT_TYPES,
  IngredientType,
  MEASUREMENT_UNITS,
  MeasurementUnit,
} from '../../domain/ingredient.model';

@Component({
  selector: 'app-ingredients-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [IngredientsStore],
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <h1 class="h4 mb-0 fw-semibold">Ingredientes</h1>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <h2 class="h6 fw-semibold mb-3">Cadastrar ingrediente</h2>
        <form [formGroup]="form" (ngSubmit)="create()" class="row g-3 align-items-end">
          <div class="col-sm-2">
            <label class="form-label" for="type">Tipo</label>
            <select id="type" class="form-select" formControlName="type">
              @for (t of types; track t) {
                <option [value]="t">{{ t }}</option>
              }
            </select>
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="code">Código</label>
            <input id="code" type="text" class="form-control" formControlName="code" placeholder="PILSEN">
          </div>
          <div class="col-sm-3">
            <label class="form-label" for="name">Nome</label>
            <input id="name" type="text" class="form-control" formControlName="name" placeholder="Malte Pilsen">
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="useUnit">Un. uso</label>
            <select id="useUnit" class="form-select" formControlName="useUnit">
              @for (u of units; track u) {
                <option [value]="u">{{ u }}</option>
              }
            </select>
          </div>
          <div class="col-sm-2">
            <label class="form-label" for="purchaseUnit">Un. compra</label>
            <select id="purchaseUnit" class="form-select" formControlName="purchaseUnit">
              @for (u of units; track u) {
                <option [value]="u">{{ u }}</option>
              }
            </select>
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
        <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
          <h2 class="h6 fw-semibold mb-0">Catálogo</h2>
          <select class="form-select w-auto" [value]="store.typeFilter() ?? ''"
                  (change)="onFilter($any($event.target).value)">
            <option value="">Todos os tipos</option>
            @for (t of types; track t) {
              <option [value]="t">{{ t }}</option>
            }
          </select>
        </div>

        @if (store.loading()) {
          <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
        } @else if (store.error(); as error) {
          <div class="alert alert-danger mb-0" role="alert">{{ error }}</div>
        } @else if (store.empty()) {
          <p class="text-muted mb-0">Nenhum ingrediente cadastrado.</p>
        } @else {
          <div class="table-responsive">
            <table class="table align-middle mb-0">
              <thead>
                <tr>
                  <th>Tipo</th><th>Código</th><th>Nome</th><th>Un. uso</th><th>Un. compra</th>
                </tr>
              </thead>
              <tbody>
                @for (item of store.items(); track item.id) {
                  <tr>
                    <td><span class="badge bg-secondary-subtle text-secondary-emphasis">{{ item.type }}</span></td>
                    <td class="fw-semibold">{{ item.code }}</td>
                    <td>{{ item.name }}</td>
                    <td>{{ item.useUnit }}</td>
                    <td>{{ item.purchaseUnit }}</td>
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
export class IngredientsPageComponent implements OnInit {
  protected readonly store = inject(IngredientsStore);
  private readonly fb = inject(FormBuilder);

  protected readonly types = INGREDIENT_TYPES;
  protected readonly units = MEASUREMENT_UNITS;

  protected readonly form = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<IngredientType>('MALT', Validators.required),
    code: ['', Validators.required],
    name: ['', Validators.required],
    useUnit: this.fb.nonNullable.control<MeasurementUnit>('KG', Validators.required),
    purchaseUnit: this.fb.nonNullable.control<MeasurementUnit>('KG', Validators.required),
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected onFilter(value: string): void {
    this.store.filterByType(value ? (value as IngredientType) : null);
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () =>
      this.form.reset({ type: 'MALT', code: '', name: '', useUnit: 'KG', purchaseUnit: 'KG' }),
    );
  }
}
