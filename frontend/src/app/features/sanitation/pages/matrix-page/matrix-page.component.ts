import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { MatrixStore } from '../../data-access/matrix.store';
import { MATERIALS, RISK_LEVELS, SOILING_LEVELS } from '../../domain/matrix.model';

@Component({
  selector: 'app-matrix-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [MatrixStore],
  templateUrl: './matrix-page.component.html',
})
export class MatrixPageComponent implements OnInit {
  protected readonly store = inject(MatrixStore);
  private readonly fb = inject(FormBuilder);

  protected readonly materials = MATERIALS;
  protected readonly soilingLevels = SOILING_LEVELS;
  protected readonly riskLevels = RISK_LEVELS;

  protected readonly form = this.fb.nonNullable.group({
    material: ['INOX', Validators.required],
    soiling: ['LEVE', Validators.required],
    risk: ['BAIXO', Validators.required],
    previousProduct: [''],
    procedureCode: [''],
    method: ['', Validators.required],
    alternative: [''],
    restriction: [''],
  });

  protected readonly recommendForm = this.fb.nonNullable.group({
    material: ['INOX', Validators.required],
    soiling: ['LEVE', Validators.required],
    risk: ['BAIXO', Validators.required],
    previousProduct: [''],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.create({
      material: v.material,
      soiling: v.soiling,
      risk: v.risk,
      previousProduct: v.previousProduct || null,
      procedureCode: v.procedureCode || null,
      method: v.method,
      alternative: v.alternative || null,
      restriction: v.restriction || null,
    }, () => this.form.reset({ material: v.material, soiling: v.soiling, risk: v.risk,
      previousProduct: '', procedureCode: '', method: '', alternative: '', restriction: '' }));
  }

  protected recommend(): void {
    if (this.recommendForm.invalid) {
      return;
    }
    const v = this.recommendForm.getRawValue();
    this.store.recommend({
      material: v.material,
      soiling: v.soiling,
      risk: v.risk,
      previousProduct: v.previousProduct || null,
    });
  }
}
