import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ProposalStore } from '../../data-access/proposal.store';
import { CommandProposal, STATUS_BADGES, STATUS_LABELS } from '../../domain/proposal.model';

/**
 * Propostas de comando do copiloto (AIA-003).
 *
 * <p><strong>A tela existe para tornar o consentimento explícito.</strong> Cada proposta mostra a ação em
 * português, os parâmetros exatos, o motivo e a alçada exigida — e o botão de confirmar só aparece habilitado
 * para quem tem essa alçada, com o nome dela ao lado quando não tem. Um botão desabilitado sem explicação
 * deixa quem lê sem o que fazer a respeito.
 *
 * <p><strong>Confirmar e descartar não são simétricos, e a tela mostra isso.</strong> Descartar está sempre
 * disponível para quem pode ver: dizer "não" a uma sugestão não altera nada. Confirmar exige a permissão do
 * comando, porque é o único caminho por onde uma sugestão de IA se aproxima de virar ação.
 *
 * <p>Vencidas têm bloco próprio em vez de sair da lista. Sair esconderia que houve sugestão que ninguém
 * decidiu; misturar com as vigentes faria a lista do que decidir hoje crescer com o que não se decide mais.
 */
@Component({
  selector: 'app-proposals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, PageHeaderComponent, LoadingIndicatorComponent, EmptyStateComponent],
  providers: [ProposalStore],
  templateUrl: './proposals-page.component.html',
})
export class ProposalsPageComponent implements OnInit {
  protected readonly store = inject(ProposalStore);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusBadges = STATUS_BADGES;

  ngOnInit(): void {
    this.store.load();
  }

  /** Os parâmetros do comando, em pares, para a tela poder mostrar exatamente o que será executado. */
  protected parametersOf(proposal: CommandProposal): { key: string; value: string }[] {
    return Object.entries(proposal.parameters).map(([key, value]) => ({ key, value }));
  }
}
