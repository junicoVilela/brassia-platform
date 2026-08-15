import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { SalesOrder } from '../domain/order.model';
import { SalesApi } from './sales.api';

interface ApiError {
  status?: number;
  error?: {
    code?: string;
    detail?: string;
    available?: number;
    earliestBestBefore?: string;
    lotCode?: string;
  };
}

/**
 * Estado dos pedidos (SAL-002).
 *
 * <p><strong>A chave de idempotência é gerada por tentativa de envio, e não por pedido.</strong> Se ela
 * fosse fixa por formulário, o operador que corrige uma quantidade e reenvia receberia de volta o pedido
 * antigo, com o valor errado — e concluiria que o sistema ignorou a correção. Cada clique em "registrar"
 * é uma tentativa nova; o que a chave protege é o duplo clique e o retry de rede da <em>mesma</em>
 * tentativa.
 */
@Injectable()
export class OrdersStore {
  private readonly api = inject(SalesApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly orders = signal<SalesOrder[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);

  readonly open = computed(() => this.orders().filter(o => o.status === 'PLACED'));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .orders()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: list => this.orders.set(list),
        error: (e: ApiError) => this.error.set(this.message(e, 'Não foi possível carregar os pedidos.')),
      });
  }

  place(body: {
    code: string;
    customerId: string;
    channelId: string;
    promisedFor: string | null;
    items: { productId: string; quantity: number }[];
  }): void {
    this.saving.set(true);
    this.api
      .placeOrder(body, crypto.randomUUID())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Pedido registrado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar o pedido.')),
      });
  }

  cancel(order: SalesOrder): void {
    this.api
      .cancelOrder(order.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Pedido cancelado e estoque devolvido.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível cancelar o pedido.')),
      });
  }

  /**
   * A mensagem do servidor vence a genérica, porque ela traz o que resolve.
   *
   * <p>Em `insufficient_lot_stock` vem quanto sobrou; em `promise_after_shelf_life` vem até quando dá
   * para prometer e qual lote limita. Sem isso, o operador fica tentando números e datas até um passar.
   */
  private message(e: ApiError, fallback: string): string {
    return e?.error?.detail ?? fallback;
  }
}
