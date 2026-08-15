import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ForecastStore } from '../../data-access/forecast.store';
import { CONFIDENCE_ADVICE, CONFIDENCE_LABELS } from '../../domain/forecast.model';

/**
 * Previsão de demanda (FCST-001).
 *
 * <p>A responsabilidade da tela que não é mostrar o número: <strong>impedir que ele seja lido como
 * promessa</strong>. Por isso a faixa, o tamanho da amostra, o erro do backtest e a frase sobre o que a
 * confiança autoriza ficam ao lado do valor — e não num rodapé que ninguém lê.
 *
 * <p><strong>Sem histórico, a tela não mostra zero.</strong> Ela diz que não há previsão. Zero pareceria
 * "ninguém quer este produto", que é uma afirmação sobre o mercado que o dado não sustenta.
 */
@Component({
  selector: 'app-forecast-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, PageHeaderComponent, LoadingIndicatorComponent, EmptyStateComponent],
  providers: [ForecastStore],
  templateUrl: './forecast-page.component.html',
})
export class ForecastPageComponent implements OnInit {
  protected readonly store = inject(ForecastStore);
  protected readonly confidenceLabels = CONFIDENCE_LABELS;
  protected readonly confidenceAdvice = CONFIDENCE_ADVICE;

  ngOnInit(): void {
    this.store.load();
  }

  protected badgeClass(confidence: string): string {
    return confidence === 'HIGH'
      ? 'text-bg-success'
      : confidence === 'MODERATE'
        ? 'text-bg-primary'
        : confidence === 'LOW'
          ? 'text-bg-warning'
          : 'text-bg-secondary';
  }
}
