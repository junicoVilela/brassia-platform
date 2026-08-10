import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RecipesApi } from '../../../recipes/data-access/recipes.api';
import { RecipeSummary } from '../../../recipes/domain/recipe.model';
import { OptimizationStore } from '../../data-access/optimization.store';
import {
  CONSTRAINT_LABELS,
  ConstraintKind,
  OBJECTIVE_HINTS,
  OBJECTIVE_LABELS,
  Objective,
  OptimizationConstraint,
} from '../../domain/optimization.model';

/**
 * Otimização assistida (OPT-001).
 *
 * <p><strong>A procedência aparece junto do resultado, não num rodapé.</strong> Método, versão da receita
 * e marca do catálogo são o que permitem reproduzir o número — e um número exibido sem eles convida a ser
 * citado meses depois como se ainda valesse.
 */
@Component({
  selector: 'app-optimization-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  providers: [OptimizationStore],
  templateUrl: './optimization-page.component.html',
})
export class OptimizationPageComponent implements OnInit {
  private readonly recipesApi = inject(RecipesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly store = inject(OptimizationStore);

  readonly recipes = signal<RecipeSummary[]>([]);
  readonly objectiveLabels = OBJECTIVE_LABELS;
  readonly objectiveHints = OBJECTIVE_HINTS;
  readonly constraintLabels = CONSTRAINT_LABELS;
  readonly objectives = Object.keys(OBJECTIVE_LABELS) as Objective[];

  readonly recipeId = signal('');
  readonly objective = signal<Objective>('COST');
  readonly maxCost = signal<number | null>(null);
  readonly ibuMin = signal<number | null>(null);
  readonly ibuMax = signal<number | null>(null);
  readonly stockOnly = signal(false);

  readonly explanation = signal('');
  readonly recipeVersionId = signal('');

  readonly canRun = computed(() => !!this.recipeId() && !this.store.running());

  /** A faixa de IBU só vale inteira: metade de uma faixa não é restrição. */
  readonly ibuIncomplete = computed(
    () => (this.ibuMin() === null) !== (this.ibuMax() === null),
  );

  ngOnInit(): void {
    this.store.load();
    this.recipesApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.recipes.set(page.content), error: () => undefined });
  }

  run(): void {
    const constraints: OptimizationConstraint[] = [];
    if (this.maxCost() !== null) {
      constraints.push({ kind: 'MAX_COST_PER_LITER', maxValue: this.maxCost() });
    }
    if (this.ibuMin() !== null && this.ibuMax() !== null) {
      constraints.push({ kind: 'IBU_RANGE', minValue: this.ibuMin(), maxValue: this.ibuMax() });
    }
    if (this.stockOnly()) {
      constraints.push({ kind: 'STOCK_ONLY' });
    }
    this.store.optimize({
      recipeId: this.recipeId(),
      objective: this.objective(),
      constraints,
    });
  }

  submitExplanation(runId: string): void {
    if (this.explanation().trim()) {
      this.store.explain(runId, this.explanation().trim());
      this.explanation.set('');
    }
  }

  submitApplication(runId: string): void {
    if (this.recipeVersionId().trim()) {
      this.store.apply(runId, this.recipeVersionId().trim());
      this.recipeVersionId.set('');
    }
  }

  constraintLabel(kind: string): string {
    return this.constraintLabels[kind as ConstraintKind] ?? kind;
  }
}
