import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { SalesOrder } from '../domain/order.model';
import { OrderPayments } from '../domain/payment.model';
import { SalesApi } from './sales.api';

/**
 * O que chega ao `error:` do subscribe é o **Problem Details desembrulhado** pelo
 * `problemDetailsInterceptor`: `code`, `detail` e as extensões vêm no **primeiro nível**.
 *
 * <p>Estava escrito como `error: { code }` aqui, e o efeito era silencioso: `e.error?.code` devolve
 * `undefined` sem erro de compilação, a tela cai calada na mensagem genérica, e o teste de unidade
 * concorda porque simulava o erro na forma errada. É o mesmo engano que a TRC-001 já tinha corrigido em
 * sete stores — e de novo quem pegou foi o E2E, contra a stack real.
 */
interface ApiError {
  status?: number;
  code?: string;
  detail?: string;
  available?: number;
  earliestBestBefore?: string;
  lotCode?: string;
  ceiling?: number;
  committed?: number;
  requested?: number;
  currency?: string;
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

  /**
   * Os recebimentos, por pedido (DEB-SAL-002).
   *
   * <p>Carregados sob demanda, quando o pedido é aberto: buscar de todos na listagem faria uma consulta
   * por linha para uma informação que quase sempre ninguém abre.
   */
  readonly payments = signal<Record<string, OrderPayments>>({});

  /**
   * O pedido recusado por crédito não é um erro qualquer (SAL-004).
   *
   * <p>Ele é o único que o vendedor pode resolver ali mesmo, se tiver a permissão: por isso a recusa fica
   * guardada em vez de virar só um toast que some. Um toast que some faria o vendedor repetir o pedido
   * para ler o número de novo.
   */
  readonly creditRefusal = signal<{
    ceiling: number;
    committed: number;
    requested: number;
    currency: string;
  } | null>(null);

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

  /**
   * @param onPlaced roda só quando o pedido entrou. É o que mantém o formulário aberto na recusa por
   *                 crédito: fechá-lo no envio esconderia os números que explicam a recusa antes mesmo
   *                 de a resposta chegar, e o vendedor repetiria o pedido só para lê-los de novo.
   */
  place(
    body: {
      code: string;
      customerId: string;
      channelId: string;
      promisedFor: string | null;
      items: { productId: string; quantity: number }[];
      creditOverrideReason?: string | null;
    },
    onPlaced?: () => void,
  ): void {
    this.creditRefusal.set(null);
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
          onPlaced?.();
          this.load();
        },
        error: (e: ApiError) => {
          if (e?.code === 'credit_limit_exceeded') {
            this.creditRefusal.set({
              ceiling: e.ceiling ?? 0,
              committed: e.committed ?? 0,
              requested: e.requested ?? 0,
              currency: e.currency ?? '',
            });
          }
          this.toast.error(this.message(e, 'Não foi possível registrar o pedido.'));
        },
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
    return e?.detail ?? fallback;
  }

  loadPayments(orderId: string): void {
    this.api
      .payments(orderId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: p => this.payments.update(atual => ({ ...atual, [orderId]: p })),
        error: (e: ApiError) =>
          this.toast.error(this.message(e, 'Não foi possível carregar os recebimentos.')),
      });
  }

  pay(
    orderId: string,
    body: {
      amount: number;
      currency: string;
      receivedOn: string | null;
      method: string;
      note: string | null;
    },
  ): void {
    this.saving.set(true);
    this.api
      .recordPayment(orderId, body)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Recebimento registrado.');
          this.loadPayments(orderId);
        },
        error: (e: ApiError) =>
          // Em `payment_exceeds_balance` vem o saldo de verdade; é o que corrige o zero a mais sem o
          // operador ficar tentando números.
          this.toast.error(this.message(e, 'Não foi possível registrar o recebimento.')),
      });
  }

  reversePayment(orderId: string, paymentId: string, reason: string): void {
    this.api
      .reversePayment(paymentId, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Estorno registrado. O recebimento original continua no histórico.');
          this.loadPayments(orderId);
        },
        error: (e: ApiError) =>
          this.toast.error(this.message(e, 'Não foi possível estornar o recebimento.')),
      });
  }
}
