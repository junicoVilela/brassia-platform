import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { CalculationResult, CalculatorSpec } from '../domain/calculator.model';
import { CalculatorsApi } from './calculators.api';

/** Estado do hub de calculadoras: catálogo, seleção e resultado. */
@Injectable()
export class CalculatorsStore {
  private readonly api = inject(CalculatorsApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly specsState = signal<CalculatorSpec[]>([]);
  private readonly selectedIdState = signal<string | null>(null);
  private readonly resultState = signal<CalculationResult | null>(null);

  readonly specs = this.specsState.asReadonly();
  readonly selectedId = this.selectedIdState.asReadonly();
  readonly result = this.resultState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly selected = computed(() => this.specs().find(s => s.id === this.selectedId()) ?? null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: specs => this.specsState.set(specs),
        error: () => this.error.set('Não foi possível carregar as calculadoras.'),
      });
  }

  select(id: string | null): void {
    this.selectedIdState.set(id);
    this.resultState.set(null);
    this.actionError.set(null);
  }

  compute(id: string, inputs: Record<string, number>): void {
    this.actionError.set(null);
    this.resultState.set(null);
    this.api.compute(id, inputs)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => this.resultState.set(result),
        error: () => this.actionError.set('Não foi possível calcular (entradas inválidas ou ausentes).'),
      });
  }
}
