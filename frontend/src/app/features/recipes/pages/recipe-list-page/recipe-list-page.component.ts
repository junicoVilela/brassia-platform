import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RecipesStore } from '../../data-access/recipes.store';
import {
  CreateRecipeRequest,
  RECIPE_STAGES,
  RECIPE_UNITS,
  RecipeStage,
  RecipeUnit,
  toCreateRecipeItem,
} from '../../domain/recipe.model';

@Component({
  selector: 'app-recipe-list-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  providers: [RecipesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recipe-list-page.component.html',
})
export class RecipeListPageComponent implements OnInit {
  protected readonly store = inject(RecipesStore);
  private readonly fb = inject(FormBuilder);

  protected readonly stages = RECIPE_STAGES;
  protected readonly units = RECIPE_UNITS;

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    equipmentId: ['', Validators.required],
    batchVolumeLiters: [0, [Validators.required, Validators.min(0.001)]],
    boilTimeMinutes: this.fb.control<number | null>(60),
    targetOgPoints: this.fb.control<number | null>(null),
    targetIbu: this.fb.control<number | null>(null),
    targetColorEbc: this.fb.control<number | null>(null),
    targetAbv: this.fb.control<number | null>(null),
    items: this.fb.array([this.newItem()]),
  });

  get items(): FormArray {
    return this.form.get('items') as FormArray;
  }

  ngOnInit(): void {
    this.store.load();
  }

  private newItem() {
    return this.fb.nonNullable.group({
      ingredientId: ['', Validators.required],
      stage: this.fb.nonNullable.control<RecipeStage>('MASH', Validators.required),
      quantity: [0, [Validators.required, Validators.min(0.0001)]],
      unit: this.fb.nonNullable.control<RecipeUnit>('KG', Validators.required),
      timingMinutes: this.fb.control<number | null>(null),
      percentage: this.fb.control<number | null>(null),
    });
  }

  protected addItem(): void {
    this.items.push(this.newItem());
  }

  protected removeItem(index: number): void {
    if (this.items.length > 1) {
      this.items.removeAt(index);
    }
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateRecipeRequest = {
      name: raw.name,
      equipmentId: raw.equipmentId,
      batchVolumeLiters: raw.batchVolumeLiters,
      boilTimeMinutes: raw.boilTimeMinutes || null,
      targetOgPoints: raw.targetOgPoints,
      targetIbu: raw.targetIbu,
      targetColorEbc: raw.targetColorEbc,
      targetAbv: raw.targetAbv,
      items: raw.items.map(toCreateRecipeItem),
    };
    this.store.create(request, () => {
      this.form.reset({
        name: '',
        equipmentId: '',
        batchVolumeLiters: 0,
        boilTimeMinutes: 60,
        targetOgPoints: null,
        targetIbu: null,
        targetColorEbc: null,
        targetAbv: null,
      });
      this.items.clear();
      this.items.push(this.newItem());
    });
  }
}
