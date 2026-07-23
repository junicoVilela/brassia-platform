import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { WaterStore } from '../../data-access/water.store';
import { WATER_METHODS, WaterMethod } from '../../domain/water.model';

@Component({
  selector: 'app-water-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [WaterStore],
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <h1 class="h4 mb-0 fw-semibold">Água — fontes e laudos</h1>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <h2 class="h6 fw-semibold mb-3">Cadastrar fonte</h2>
        <form [formGroup]="sourceForm" (ngSubmit)="createSource()" class="row g-3 align-items-end">
          <div class="col-sm-3">
            <label class="form-label" for="code">Código</label>
            <input id="code" type="text" class="form-control" formControlName="code" placeholder="POCO-1">
          </div>
          <div class="col-sm-5">
            <label class="form-label" for="name">Nome</label>
            <input id="name" type="text" class="form-control" formControlName="name" placeholder="Poço artesiano">
          </div>
          <div class="col-sm-2 d-grid">
            <button type="submit" class="btn btn-primary" [disabled]="sourceForm.invalid || store.submitting()">
              <i class="ri-add-line me-1"></i> Cadastrar
            </button>
          </div>
        </form>
      </div>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <label class="form-label" for="source">Fonte</label>
        <select id="source" class="form-select" (change)="onSelect($any($event.target).value)">
          <option value="">Selecione uma fonte…</option>
          @for (s of store.sources(); track s.id) {
            <option [value]="s.id">{{ s.code }} — {{ s.name }}</option>
          }
        </select>
      </div>
    </div>

    @if (store.selectedId()) {
      <div class="card border-0 rounded-10 mb-4">
        <div class="card-body p-4">
          <h2 class="h6 fw-semibold mb-3">Registrar laudo (mg/L)</h2>
          <form [formGroup]="reportForm" (ngSubmit)="recordReport()" class="row g-3 align-items-end">
            <div class="col-sm-2">
              <label class="form-label" for="collectedOn">Data</label>
              <input id="collectedOn" type="date" class="form-control" formControlName="collectedOn">
            </div>
            <div class="col-sm-2">
              <label class="form-label" for="method">Método</label>
              <select id="method" class="form-select" formControlName="method">
                @for (m of methods; track m) {
                  <option [value]="m">{{ m }}</option>
                }
              </select>
            </div>
            <div class="col-sm-1">
              <label class="form-label" for="calcium">Ca</label>
              <input id="calcium" type="number" step="0.01" class="form-control" formControlName="calcium">
            </div>
            <div class="col-sm-1">
              <label class="form-label" for="magnesium">Mg</label>
              <input id="magnesium" type="number" step="0.01" class="form-control" formControlName="magnesium">
            </div>
            <div class="col-sm-1">
              <label class="form-label" for="sodium">Na</label>
              <input id="sodium" type="number" step="0.01" class="form-control" formControlName="sodium">
            </div>
            <div class="col-sm-1">
              <label class="form-label" for="sulfate">SO₄</label>
              <input id="sulfate" type="number" step="0.01" class="form-control" formControlName="sulfate">
            </div>
            <div class="col-sm-1">
              <label class="form-label" for="chloride">Cl</label>
              <input id="chloride" type="number" step="0.01" class="form-control" formControlName="chloride">
            </div>
            <div class="col-sm-1">
              <label class="form-label" for="bicarbonate">HCO₃</label>
              <input id="bicarbonate" type="number" step="0.01" class="form-control" formControlName="bicarbonate">
            </div>
            <div class="col-sm-1 d-grid">
              <button type="submit" class="btn btn-primary" [disabled]="reportForm.invalid || store.submitting()">
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
          <h2 class="h6 fw-semibold mb-3">Histórico de laudos</h2>
          @if (store.loadingReports()) {
            <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
          } @else if (store.noReports()) {
            <p class="text-muted mb-0">Nenhum laudo para esta fonte.</p>
          } @else {
            <div class="table-responsive">
              <table class="table align-middle mb-0">
                <thead>
                  <tr>
                    <th>Data</th><th>Método</th><th class="text-end">Ca</th><th class="text-end">Mg</th>
                    <th class="text-end">Na</th><th class="text-end">SO₄</th><th class="text-end">Cl</th>
                    <th class="text-end">HCO₃</th>
                  </tr>
                </thead>
                <tbody>
                  @for (r of store.reports(); track r.id) {
                    <tr>
                      <td class="fw-semibold">{{ r.collectedOn }}</td>
                      <td>{{ r.method }}</td>
                      <td class="text-end">{{ r.calcium }}</td>
                      <td class="text-end">{{ r.magnesium }}</td>
                      <td class="text-end">{{ r.sodium }}</td>
                      <td class="text-end">{{ r.sulfate }}</td>
                      <td class="text-end">{{ r.chloride }}</td>
                      <td class="text-end">{{ r.bicarbonate }}</td>
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
export class WaterPageComponent implements OnInit {
  protected readonly store = inject(WaterStore);
  private readonly fb = inject(FormBuilder);

  protected readonly methods = WATER_METHODS;

  protected readonly sourceForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
  });

  protected readonly reportForm = this.fb.nonNullable.group({
    collectedOn: ['', Validators.required],
    method: this.fb.nonNullable.control<WaterMethod>('LAB', Validators.required),
    calcium: [0, [Validators.required, Validators.min(0)]],
    magnesium: [0, [Validators.required, Validators.min(0)]],
    sodium: [0, [Validators.required, Validators.min(0)]],
    sulfate: [0, [Validators.required, Validators.min(0)]],
    chloride: [0, [Validators.required, Validators.min(0)]],
    bicarbonate: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.store.loadSources();
  }

  protected onSelect(sourceId: string): void {
    this.store.select(sourceId || null);
  }

  protected createSource(): void {
    if (this.sourceForm.invalid) {
      return;
    }
    this.store.createSource(this.sourceForm.getRawValue(), () => this.sourceForm.reset({ code: '', name: '' }));
  }

  protected recordReport(): void {
    if (this.reportForm.invalid) {
      return;
    }
    this.store.recordReport(this.reportForm.getRawValue(), () =>
      this.reportForm.reset({
        collectedOn: '',
        method: 'LAB',
        calcium: 0,
        magnesium: 0,
        sodium: 0,
        sulfate: 0,
        chloride: 0,
        bicarbonate: 0,
      }),
    );
  }
}
