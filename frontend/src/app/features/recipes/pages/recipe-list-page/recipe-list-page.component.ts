import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  inject,
  viewChild,
} from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { RecipesStore } from '../../data-access/recipes.store';
import {
  CreateRecipeRequest,
  ExchangeFormat,
  RECIPE_STAGES,
  RECIPE_UNITS,
  RecipeStage,
  RecipeUnit,
  toCreateRecipeItem,
} from '../../domain/recipe.model';

@Component({
  selector: 'app-recipe-list-page',
  standalone: true,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [RecipesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recipe-list-page.component.html',
})
export class RecipeListPageComponent implements OnInit {
  protected readonly store = inject(RecipesStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly stages = RECIPE_STAGES;
  protected readonly units = RECIPE_UNITS;

  /** Botão de fechar do offcanvas de criação — acionado após salvar com sucesso. */
  private readonly createDismiss = viewChild<ElementRef<HTMLButtonElement>>('createDismiss');

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const items = this.store.items();
    if (!term) {
      return items;
    }
    return items.filter(r => `${r.name} ${r.status}`.toLowerCase().includes(term));
  });

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

  protected readonly derivationForm = this.fb.nonNullable.group({
    sourceId: ['', Validators.required],
    name: ['', Validators.required],
    batchVolumeLiters: [0, [Validators.min(0.001)]],
  });

  protected readonly compareForm = this.fb.nonNullable.group({
    leftId: ['', Validators.required],
    rightId: ['', Validators.required],
  });

  protected readonly importForm = this.fb.nonNullable.group({
    format: this.fb.nonNullable.control<ExchangeFormat>('beerjson'),
    content: ['', Validators.required],
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

  protected showVolumes(recipeId: string): void {
    this.store.showVolumes(recipeId);
  }

  protected calculateMetrics(recipeId: string): void {
    this.store.calculateMetrics(recipeId);
  }

  protected publish(recipeId: string): void {
    this.store.publish(recipeId);
  }

  protected newVersion(recipeId: string): void {
    this.store.newVersion(recipeId);
  }

  protected clone(): void {
    const v = this.derivationForm.getRawValue();
    if (!v.sourceId || !v.name) {
      return;
    }
    this.store.clone(v.sourceId, v.name, () => this.derivationForm.reset({ sourceId: '', name: '', batchVolumeLiters: 0 }));
  }

  protected scale(): void {
    const v = this.derivationForm.getRawValue();
    if (!v.sourceId || !v.name || v.batchVolumeLiters <= 0) {
      return;
    }
    this.store.scale(v.sourceId, v.name, v.batchVolumeLiters, () =>
      this.derivationForm.reset({ sourceId: '', name: '', batchVolumeLiters: 0 }),
    );
  }

  protected compare(): void {
    const v = this.compareForm.getRawValue();
    if (!v.leftId || !v.rightId) {
      return;
    }
    this.store.compareRecipes(v.leftId, v.rightId);
  }

  protected export(recipeId: string, format: ExchangeFormat): void {
    this.store.export(recipeId, format);
  }

  protected importRecipe(): void {
    const v = this.importForm.getRawValue();
    if (!v.content.trim()) {
      return;
    }
    this.store.importRecipe(v.format, v.content);
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
      this.createDismiss()?.nativeElement.click();
    });
  }
}
