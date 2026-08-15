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

/**
 * Pedidos (SAL-002).
 *
 * <p>A responsabilidade da tela que não é registrar: <strong>mostrar de quais lotes o pedido é feito</strong>.
 * Um pedido é uma promessa sobre cerveja específica, e quando um recall alcança um lote é aqui que se
 * descobre quem precisa ser avisado — não numa consulta que ninguém sabe fazer.
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

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    customerId: ['', Validators.required],
    channelId: ['', Validators.required],
    productId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(1)]],
    promisedFor: [''],
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
    });
    this.showForm.set(false);
  }

  protected toggle(order: SalesOrder): void {
    this.expanded.set(this.expanded() === order.id ? null : order.id);
  }

  protected badgeClass(order: SalesOrder): string {
    return order.status === 'PLACED'
      ? 'text-bg-success'
      : order.status === 'CANCELLED'
        ? 'text-bg-secondary'
        : 'text-bg-primary';
  }
}
