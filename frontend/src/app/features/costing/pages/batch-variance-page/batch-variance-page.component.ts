import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { VarianceStore } from '../../data-access/variance.store';

/**
 * Planejado versus real do lote (CST-002).
 *
 * <p>A tela responde uma pergunta só, e responde na primeira linha: <em>por que este lote custou o
 * que custou?</em> Preço e consumo aparecem lado a lado porque são causas diferentes com donos
 * diferentes — preço é conversa com fornecedor, consumo é conversa com a brassagem.
 *
 * <p>O que não tem base não é apresentado como desvio. Insumo que a ordem não separou fica fora do
 * total e aparece à parte; perda sem esperado cadastrado aparece como fato. Um relatório que chama
 * de desvio o que não tem contra o que medir ensina o brewer a ignorar o relatório.
 */
@Component({
  selector: 'app-batch-variance-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, PageHeaderComponent, LoadingIndicatorComponent, EmptyStateComponent],
  providers: [VarianceStore],
  templateUrl: './batch-variance-page.component.html',
})
export class BatchVariancePageComponent implements OnInit {
  protected readonly store = inject(VarianceStore);

  ngOnInit(): void {
    this.store.load();
  }

  /** Classe do número conforme ele ajuda ou atrapalha; zero fica neutro. */
  protected moneyClass(value: number | null): string {
    if (value === null || value === 0) {
      return '';
    }
    return value > 0 ? 'text-danger' : 'text-success';
  }
}
