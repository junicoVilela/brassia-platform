import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { SalesApi } from '../../sales/data-access/sales.api';
import { Product } from '../../sales/domain/product.model';
import { DemandForecast } from '../domain/forecast.model';
import { ForecastApi } from './forecast.api';

/**
 * Estado da previsão (FCST-001).
 *
 * <p>A previsão é buscada por produto, sob demanda — ela é derivada do histórico no servidor, e não há
 * o que guardar aqui. Guardá-la faria a tela mostrar um número calculado antes do último pedido.
 */
@Injectable()
export class ForecastStore {
  private readonly api = inject(ForecastApi);
  private readonly sales = inject(SalesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly products = signal<Product[]>([]);
  readonly selected = signal<Product | null>(null);
  readonly forecast = signal<DemandForecast | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** A largura da faixa em pontos percentuais da média — o quanto o número ainda balança. */
  readonly spreadPercent = computed(() => {
    const f = this.forecast();
    if (!f?.hasNumbers || !f.expectedUnits || f.expectedUnits === 0) {
      return null;
    }
    return (((f.upperBound ?? 0) - (f.lowerBound ?? 0)) / f.expectedUnits) * 100;
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.sales
      .products(true)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: list => this.products.set(list),
        error: () => this.error.set('Não foi possível carregar os produtos.'),
      });
  }

  select(product: Product): void {
    this.selected.set(product);
    this.forecast.set(null);
    this.api
      .demand(product.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: f => this.forecast.set(f),
        error: () => this.error.set('Não foi possível calcular a previsão.'),
      });
  }
}
