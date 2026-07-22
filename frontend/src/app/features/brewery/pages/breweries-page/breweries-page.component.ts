import { ChangeDetectionStrategy, Component, OnInit, effect, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BreweryStore } from '../../data-access/brewery.store';

@Component({
  selector: 'app-breweries-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [BreweryStore],
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <h1 class="h4 mb-0 fw-semibold">Cervejarias</h1>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <h2 class="h6 fw-semibold mb-3">Cadastrar cervejaria</h2>
        <form [formGroup]="form" (ngSubmit)="register()" class="row g-3 align-items-end">
          <div class="col-sm-3">
            <label class="form-label" for="code">Código</label>
            <input id="code" type="text" class="form-control" formControlName="code" placeholder="SB40">
          </div>
          <div class="col-sm-4">
            <label class="form-label" for="name">Nome</label>
            <input id="name" type="text" class="form-control" formControlName="name" placeholder="Casa Brew">
          </div>
          <div class="col-sm-3">
            <label class="form-label" for="timezone">Fuso</label>
            <input id="timezone" type="text" class="form-control" formControlName="timezone"
                   placeholder="America/Sao_Paulo">
          </div>
          <div class="col-sm-2 d-grid">
            <button type="submit" class="btn btn-primary" [disabled]="form.invalid || store.submitting()">
              <i class="ri-add-line me-1"></i> Cadastrar
            </button>
          </div>
        </form>
      </div>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <h2 class="h6 fw-semibold mb-3">Preferências operacionais (cervejaria ativa)</h2>
        @if (store.preferencesLoading()) {
          <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
        } @else if (store.preferencesError(); as prefsError) {
          <div class="alert alert-danger mb-0" role="alert">{{ prefsError }}</div>
        } @else {
          <form [formGroup]="prefsForm" (ngSubmit)="savePreferences()" class="row g-3">
            <div class="col-sm-2">
              <label class="form-label" for="volumeUnit">Volume</label>
              <select id="volumeUnit" class="form-select" formControlName="volumeUnit">
                <option value="L">L</option>
                <option value="ML">mL</option>
              </select>
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="massUnit">Massa</label>
              <select id="massUnit" class="form-select" formControlName="massUnit">
                <option value="KG">kg</option>
                <option value="G">g</option>
              </select>
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="temperatureUnit">Temperatura</label>
              <select id="temperatureUnit" class="form-select" formControlName="temperatureUnit">
                <option value="C">°C</option>
                <option value="F">°F</option>
              </select>
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="currencyCode">Moeda</label>
              <input id="currencyCode" type="text" class="form-control" formControlName="currencyCode" maxlength="3">
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="maxBatchVolume">Lote máx.</label>
              <input id="maxBatchVolume" type="number" class="form-control" formControlName="maxBatchVolume" min="0.000001" step="any">
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="stockPolicy">Estoque</label>
              <select id="stockPolicy" class="form-select" formControlName="stockPolicy">
                <option value="FEFO">FEFO</option>
                <option value="FIFO">FIFO</option>
                <option value="NONE">Nenhuma</option>
              </select>
            </div>
            <div class="col-12">
              <div class="form-check">
                <input id="allowNegativeStock" type="checkbox" class="form-check-input" formControlName="allowNegativeStock">
                <label class="form-check-label" for="allowNegativeStock">Permitir estoque negativo</label>
              </div>
            </div>
            <div class="col-12 d-flex justify-content-end">
              <button type="submit" class="btn btn-primary" [disabled]="prefsForm.invalid || store.submitting()">
                Salvar preferências
              </button>
            </div>
          </form>
        }
      </div>
    </div>

    @if (store.actionError(); as actionError) {
      <div class="alert alert-danger" role="alert">{{ actionError }}</div>
    }

    <div class="card border-0 rounded-10">
      <div class="card-body p-4">
        @if (store.loading()) {
          <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
        } @else if (store.error(); as error) {
          <div class="alert alert-danger mb-0" role="alert">{{ error }}</div>
        } @else if (store.empty()) {
          <p class="text-muted mb-0">Nenhuma cervejaria cadastrada ainda.</p>
        } @else {
          <div class="table-responsive">
            <table class="table align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Nome</th>
                  <th scope="col">Fuso</th>
                </tr>
              </thead>
              <tbody>
                @for (brewery of store.items(); track brewery.id) {
                  <tr>
                    <td class="fw-medium">{{ brewery.code }}</td>
                    <td>{{ brewery.name }}</td>
                    <td>{{ brewery.timezone }}</td>
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
export class BreweriesPageComponent implements OnInit {
  protected readonly store = inject(BreweryStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    timezone: ['America/Sao_Paulo', [Validators.required, Validators.maxLength(80)]],
  });

  protected readonly prefsForm = this.fb.nonNullable.group({
    volumeUnit: ['L', Validators.required],
    massUnit: ['KG', Validators.required],
    temperatureUnit: ['C', Validators.required],
    currencyCode: ['BRL', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    maxBatchVolume: [1000, [Validators.required, Validators.min(0.000001)]],
    allowNegativeStock: [false],
    stockPolicy: ['FEFO', Validators.required],
    version: [0, Validators.required],
  });

  constructor() {
    effect(() => {
      const prefs = this.store.preferences();
      if (!prefs) {
        return;
      }
      this.prefsForm.setValue({
        volumeUnit: prefs.volumeUnit,
        massUnit: prefs.massUnit,
        temperatureUnit: prefs.temperatureUnit,
        currencyCode: prefs.currencyCode,
        maxBatchVolume: prefs.maxBatchVolume,
        allowNegativeStock: prefs.allowNegativeStock,
        stockPolicy: prefs.stockPolicy,
        version: prefs.version,
      });
    });
  }

  ngOnInit(): void {
    this.store.load();
  }

  protected register(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.register(this.form.getRawValue(), () => this.form.reset({ timezone: 'America/Sao_Paulo' }));
  }

  protected savePreferences(): void {
    if (this.prefsForm.invalid) {
      return;
    }
    this.store.savePreferences(this.prefsForm.getRawValue());
  }
}
