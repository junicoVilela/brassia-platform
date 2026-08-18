import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { OrdersStore } from '../../data-access/orders.store';
import { SalesStore } from '../../data-access/sales.store';
import { ORDER_STATUS_LABELS, SalesOrder } from '../../domain/order.model';
import { Payment } from '../../domain/payment.model';

/**
 * Pedidos (SAL-002).
 *
 * <p>A responsabilidade da tela que não é registrar: <strong>mostrar de quais lotes o pedido é feito</strong>.
 * Um pedido é uma promessa sobre cerveja específica, e quando um recall alcança um lote é aqui que se
 * descobre quem precisa ser avisado — não numa consulta que ninguém sabe fazer.
 *
 * <p><strong>A recusa por crédito fica na tela, e não num toast que some</strong> (SAL-004). Ela é a
 * única recusa que quem vende pode resolver ali mesmo — com a permissão crítica e uma justificativa —, e
 * os três números (teto, comprometido, este pedido) são o que permite decidir sem adivinhar.
 *
 * <p><strong>O recebimento aparece dentro do pedido</strong> (DEB-SAL-002), e não numa tela de
 * financeiro à parte: quem cobra está olhando o pedido, e o número que importa — quanto falta — só faz
 * sentido ao lado do total. O estorno pede motivo na própria linha, porque um estorno sem motivo deixa
 * quem confere seis meses depois sem saber se foi engano de digitação ou cheque devolvido.
 *
 * <p><strong>A promessa de entrega tem limite visível.</strong> A data mais distante que dá para prometer
 * é a validade do lote que vence primeiro, e o servidor recusa o resto. Mostrar a validade ao lado da
 * reserva é o que evita o operador descobrir isso por tentativa e erro.
 */
@Component({
  selector: 'app-orders-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [OrdersStore, SalesStore],
  templateUrl: './orders-page.component.html',
})
export class OrdersPageComponent implements OnInit {
  protected readonly store = inject(OrdersStore);
  protected readonly catalog = inject(SalesStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = ORDER_STATUS_LABELS;
  protected readonly showForm = signal(false);
  protected readonly expanded = signal<string | null>(null);

  protected readonly canManage = this.auth.hasPermission('sales.order.manage');
  protected readonly canPay = this.auth.hasPermission('sales.payment.record');
  protected readonly canOverrideCredit = this.auth.hasPermission('sales.order.credit_override');
  protected readonly canReverse = this.auth.hasPermission('sales.payment.reverse');

  /** Qual recebimento está com o campo de motivo aberto: estornar sem motivo o servidor recusa. */
  protected readonly reversing = signal<string | null>(null);

  protected readonly paymentForm = this.fb.nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0.01)]],
    receivedOn: [''],
    method: ['', [Validators.required, Validators.maxLength(40)]],
    note: [''],
  });

  protected readonly reversalReason = this.fb.nonNullable.control('', Validators.required);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    customerId: ['', Validators.required],
    channelId: ['', Validators.required],
    productId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(1)]],
    promisedFor: [''],
    // Só viaja quando o teto recusou: mandar sempre criaria registro de exceção que não aconteceu.
    creditOverrideReason: [''],
  });

  ngOnInit(): void {
    this.store.load();
    this.catalog.load();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.store.place({
      code: v.code,
      customerId: v.customerId,
      channelId: v.channelId,
      // Vazio vira nulo: "a combinar" é estado legítimo, e mandar string vazia viraria data inválida.
      promisedFor: v.promisedFor || null,
      items: [{ productId: v.productId, quantity: v.quantity }],
      creditOverrideReason: v.creditOverrideReason || null,
    },
    // Só fecha quando o pedido entrou. Fechar no envio esconderia a recusa por crédito antes de ela
    // chegar, e o vendedor repetiria o pedido só para ler os números de novo.
    () => {
      this.showForm.set(false);
      // O motivo morre com o pedido que ele autorizou. O formulário guarda os valores entre um pedido e
      // outro, e um motivo esquecido no campo autorizaria em silêncio a próxima venda acima do teto —
      // com a justificativa de uma venda que não é aquela.
      this.form.controls.creditOverrideReason.reset('');
    });
  }

  protected toggle(order: SalesOrder): void {
    const abrindo = this.expanded() !== order.id;
    this.expanded.set(abrindo ? order.id : null);
    if (abrindo) {
      this.store.loadPayments(order.id);
    }
  }

  protected pay(order: SalesOrder): void {
    if (this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }
    const v = this.paymentForm.getRawValue();
    this.store.pay(order.id, {
      amount: v.amount,
      // A moeda é a do pedido: escolher outra não seria recebimento parcial, seria outra conversa.
      currency: order.currency,
      // Vazio vira nulo, que o servidor lê como hoje.
      receivedOn: v.receivedOn || null,
      method: v.method,
      note: v.note || null,
    });
    this.paymentForm.reset({ amount: 0, receivedOn: '', method: '', note: '' });
  }

  protected startReversal(payment: Payment): void {
    this.reversing.set(payment.id);
    this.reversalReason.reset('');
  }

  protected confirmReversal(order: SalesOrder, payment: Payment): void {
    if (this.reversalReason.invalid) {
      this.reversalReason.markAsTouched();
      return;
    }
    this.store.reversePayment(order.id, payment.id, this.reversalReason.value);
    this.reversing.set(null);
  }

  /** Já estornado: o índice único do banco recusa o segundo, e a tela não oferece o botão. */
  protected isReversed(orderId: string, payment: Payment): boolean {
    return (this.store.payments()[orderId]?.payments ?? []).some(
      p => p.reversesPaymentId === payment.id,
    );
  }

  protected badgeClass(order: SalesOrder): string {
    return order.status === 'PLACED'
      ? 'text-bg-success'
      : order.status === 'CANCELLED'
        ? 'text-bg-secondary'
        : 'text-bg-primary';
  }
}
