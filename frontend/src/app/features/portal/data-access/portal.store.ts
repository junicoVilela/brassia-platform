import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { CatalogItem, CreditSituation, PortalOrder } from '../domain/portal.model';
import { PortalApi } from './portal.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string; ceiling?: number; committed?: number };
}

/**
 * Estado do portal do cliente (SAL-003).
 *
 * <p>Depois de comprar, o catálogo é <strong>relido</strong>: o pedido consumiu disponibilidade, e uma
 * lista em cache ofereceria unidades que já têm dono. É o mesmo motivo pelo qual a tela interna relê a
 * linha do tempo de preço — a regra vive no servidor, e repeti-la aqui seria mantê-la em dois lugares.
 */
@Injectable()
export class PortalStore {
  private readonly api = inject(PortalApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly catalog = signal<CatalogItem[]>([]);
  readonly orders = signal<PortalOrder[]>([]);
  readonly credit = signal<CreditSituation | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);

  /** Quanto ainda cabe. Nulo quando não há teto — e sem teto, tudo cabe. */
  readonly remaining = computed(() => {
    const c = this.credit();
    return c?.ceiling == null ? null : c.ceiling - c.committed;
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      catalog: this.api.catalog(),
      orders: this.api.orders(),
      credit: this.api.credit(),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: ({ catalog, orders, credit }) => {
          this.catalog.set(catalog);
          this.orders.set(orders);
          this.credit.set(credit);
        },
        error: (e: ApiError) => this.error.set(this.message(e, 'Não foi possível carregar o portal.')),
      });
  }

  place(code: string, productId: string, quantity: number, promisedFor: string | null): void {
    this.saving.set(true);
    this.api
      .place({ code, promisedFor, items: [{ productId, quantity }] }, crypto.randomUUID())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Pedido enviado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível enviar o pedido.')),
      });
  }

  reorder(order: PortalOrder, code: string): void {
    this.saving.set(true);
    this.api
      .reorder(order.id, { code, promisedFor: null }, crypto.randomUUID())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          // Repete a intenção, e não o valor: o preço é o de hoje, e por isso o total pode diferir.
          this.toast.success('Recompra enviada, com o preço de hoje.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível repetir o pedido.')),
      });
  }

  /** A mensagem do servidor traz teto, comprometido e pedido — os três que resolvem a recusa. */
  private message(e: ApiError, fallback: string): string {
    return e?.error?.detail ?? fallback;
  }
}
