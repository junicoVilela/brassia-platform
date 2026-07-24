import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
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
  imports: [DecimalPipe, ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [IngredientsStore],
  templateUrl: './ingredients-page.component.html',
})
export class IngredientsPageComponent implements OnInit {
  protected readonly store = inject(IngredientsStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly types = INGREDIENT_TYPES;
  protected readonly units = MEASUREMENT_UNITS;

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const items = this.store.items();
    if (!term) {
      return items;
    }
    return items.filter(i => `${i.code} ${i.name} ${i.type}`.toLowerCase().includes(term));
  });

  protected readonly form = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<IngredientType>('MALT', Validators.required),
    code: ['', Validators.required],
    name: ['', Validators.required],
    useUnit: this.fb.nonNullable.control<MeasurementUnit>('KG', Validators.required),
    purchaseUnit: this.fb.nonNullable.control<MeasurementUnit>('KG', Validators.required),
  });

  protected readonly profileForm = this.fb.nonNullable.group({
    manufacturer: '',
    origin: '',
    form: '',
    purpose: '',
    laboratory: '',
    labCode: '',
    sourceName: '',
    descriptors: '',
    property: '',
    propMin: this.fb.control<number | null>(null),
    propMax: this.fb.control<number | null>(null),
    propUnit: '',
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

  protected selectIngredient(id: string): void {
    this.store.selectIngredient(id || null);
    this.profileForm.reset({});
  }

  protected rangeKeys(): string[] {
    return Object.keys(this.store.profile()?.ranges ?? {});
  }

  protected createProfile(): void {
    const v = this.profileForm.getRawValue();
    const ranges: Record<string, { min: number | null; max: number | null; unit: string | null }> = {};
    if (v.property.trim()) {
      ranges[v.property.trim()] = { min: v.propMin, max: v.propMax, unit: v.propUnit || null };
    }
    this.store.createProfile(
      {
        manufacturer: v.manufacturer || null,
        origin: v.origin || null,
        form: v.form || null,
        purpose: v.purpose || null,
        laboratory: v.laboratory || null,
        labCode: v.labCode || null,
        ranges,
        descriptors: v.descriptors ? v.descriptors.split(',').map(d => d.trim()).filter(d => d) : [],
        sourceId: null,
        sourceName: v.sourceName || null,
      },
      () => this.profileForm.reset({}),
    );
  }

  protected publishProfile(): void {
    this.store.publishProfile();
  }

  protected loadSubstitutions(): void {
    this.store.loadSubstitutions();
  }
}
