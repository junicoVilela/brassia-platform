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
  templateUrl: './ingredients-page.component.html',
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
