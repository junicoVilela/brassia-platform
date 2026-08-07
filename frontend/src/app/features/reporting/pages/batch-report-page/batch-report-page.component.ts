import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ReportingStore } from '../../data-access/reporting.store';
import { NODE_TYPE_LABELS } from '../../domain/batch-report.model';

/**
 * Relatório do lote (RPT-001).
 *
 * <p>É o documento que sai da casa, e a tela é montada com isso em mente: as lacunas vêm antes das
 * seções, não depois. Quem imprime precisa ver o que o relatório <em>não</em> prova antes de
 * mandá-lo para um cliente que vai lê-lo como se provasse tudo.
 *
 * <p>A data de geração fica no topo pelo mesmo motivo. O relatório é derivado — o mesmo lote
 * responde diferente amanhã —, e um papel sem data não se defende em auditoria.
 */
@Component({
  selector: 'app-batch-report-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [ReportingStore],
  templateUrl: './batch-report-page.component.html',
})
export class BatchReportPageComponent implements OnInit {
  protected readonly store = inject(ReportingStore);
  private readonly auth = inject(AuthService);

  protected readonly canExport = this.auth.hasPermission('reporting.batch.export');

  ngOnInit(): void {
    this.store.load();
  }

  protected nodeLabel(type: string): string {
    return NODE_TYPE_LABELS[type] ?? type;
  }

  protected severityClass(severity: string): string {
    return severity === 'CRITICAL' ? 'text-bg-danger'
      : severity === 'MAJOR' ? 'text-bg-warning'
      : 'text-bg-secondary';
  }
}
