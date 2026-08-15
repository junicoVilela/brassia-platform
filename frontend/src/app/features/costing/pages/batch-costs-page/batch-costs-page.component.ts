import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CostingStore } from '../../data-access/costing.store';
import { CATEGORY_LABELS, CostCategory } from '../../domain/batch-cost.model';

/**
 * Custo realizado do lote (CST-001).
 *
 * <p>A tela tem uma responsabilidade que não é somar: é deixar claro que o número ainda pode mudar.
 * Um custo aberto é a soma de agora e sobe quando o lote for envasado; um custo fechado é a
 * apuração assinada. Mostrar os dois com a mesma cara faria alguém tomar decisão de preço em cima
 * de um total que ainda vai crescer.
 *
 * <p>As lacunas ficam ao lado do total, não no rodapé: sem mão de obra e sem utilidade, o número é
 * menor que a verdade, e quem lê precisa saber disso enquanto lê.
 */
@Component({
  selector: 'app-batch-costs-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [CostingStore],
  templateUrl: './batch-costs-page.component.html',
})
export class BatchCostsPageComponent implements OnInit {
  protected readonly store = inject(CostingStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canClose = this.auth.hasPermission('costing.cost.close');
  protected readonly canManageRate = this.auth.hasPermission('costing.labor-rate.manage');

  /** Campo da taxa da hora (CST-001-A); vazio enquanto a casa não a definiu. */
  protected readonly rateInput = signal<number | null>(null);

  protected saveRate(): void {
    const value = this.rateInput();
    if (value && value > 0) {
      this.store.saveLaborRate(value);
    }
  }
  protected readonly categoryLabels = CATEGORY_LABELS;

  protected readonly closing = signal(false);

  protected readonly closeForm = this.fb.nonNullable.group({
    note: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected select(batchId: string): void {
    this.closing.set(false);
    this.store.select(batchId);
  }

  protected startClose(): void {
    this.closing.set(true);
    this.closeForm.reset({ note: '' });
  }

  protected cancelClose(): void {
    this.closing.set(false);
  }

  protected confirmClose(batchId: string): void {
    if (this.closeForm.invalid) {
      return;
    }
    this.store.close(batchId, this.closeForm.getRawValue().note || null);
    this.closing.set(false);
  }

  protected categoryLabel(category: CostCategory): string {
    return this.categoryLabels[category];
  }

  protected busy(batchId: string): boolean {
    return this.store.saving() === batchId;
  }
}
