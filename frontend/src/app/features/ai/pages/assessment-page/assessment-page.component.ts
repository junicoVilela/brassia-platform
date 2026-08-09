import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { AssessmentStore } from '../../data-access/assessment.store';
import { SEVERITY_BADGES, SEVERITY_LABELS } from '../../domain/assessment.model';

/**
 * Avaliação de risco de um lote (AIA-002).
 *
 * <p>A tela tem uma responsabilidade que decide se ela serve para algo: <strong>deixar claro que os números
 * são do sistema e o texto é do modelo</strong>. Os fatos aparecem numa tabela, cada um com a unidade e com o
 * serviço que o calculou; a análise aparece ao lado, como leitura daqueles números. Quem duvidar de uma frase
 * confere o número na mesma tela, sem precisar confiar.
 *
 * <p>Fato ausente tem bloco próprio, e não some: "ninguém mediu" é risco de desconhecimento, e escondê-lo
 * faria um lote não medido parecer um lote sem problema.
 */
@Component({
  selector: 'app-assessment-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, PageHeaderComponent, LoadingIndicatorComponent, EmptyStateComponent],
  providers: [AssessmentStore],
  templateUrl: './assessment-page.component.html',
})
export class AssessmentPageComponent implements OnInit {
  protected readonly store = inject(AssessmentStore);

  protected readonly severityLabels = SEVERITY_LABELS;
  protected readonly severityBadges = SEVERITY_BADGES;

  ngOnInit(): void {
    this.store.load();
  }
}
