import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { AiStore } from '../../data-access/ai.store';
import { STATUS_BADGES, STATUS_LABELS } from '../../domain/gateway.model';

/**
 * O copiloto de IA visto por quem opera (AIA-001).
 *
 * <p>A tela responde três perguntas, e a ordem delas é a ordem em que aparecem: <em>há copiloto?</em>,
 * <em>quanto já custou este mês?</em> e <em>o que aconteceu nas últimas chamadas?</em> Sem elas, "a IA
 * não respondeu" é um mistério; com elas é um diagnóstico.
 *
 * <p><strong>Sem provedor não é erro.</strong> Uma instalação sem IA vê um aviso neutro, não um alarme
 * vermelho — é o estado padrão do produto, e pintá-lo de vermelho ensinaria a ignorar o vermelho.
 *
 * <p><strong>O botão de verificar avisa que gasta.</strong> Ele faz uma chamada de verdade, cobrada de
 * verdade. Um botão que gasta dinheiro sem dizer isso é uma armadilha.
 */
@Component({
  selector: 'app-ai-gateway-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [AiStore],
  templateUrl: './gateway-page.component.html',
})
export class GatewayPageComponent implements OnInit {
  protected readonly store = inject(AiStore);
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusBadges = STATUS_BADGES;

  protected readonly budgetForm = this.fb.nonNullable.group({
    monthlyLimit: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  /** Preenche o campo com o teto atual: alterar um número começa por vê-lo. */
  protected editBudget(): void {
    const budget = this.store.status()?.budget;
    if (budget) {
      this.budgetForm.setValue({ monthlyLimit: budget.monthlyLimit });
    }
  }

  protected saveBudget(): void {
    if (this.budgetForm.invalid) {
      return;
    }
    this.store.redefineBudget(this.budgetForm.getRawValue().monthlyLimit);
  }
}
