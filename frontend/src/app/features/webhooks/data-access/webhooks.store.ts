import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  CreateSubscriptionRequest,
  SubscriptionStatus,
  WebhookDelivery,
  WebhookSubscription,
} from '../domain/webhook.model';
import { WebhooksApi } from './webhooks.api';

interface WebhookError {
  status?: number;
  error?: { code?: string; detail?: string };
}

/**
 * Estado dos webhooks (INT-002).
 *
 * <p>O segredo recém-criado vive num signal separado e some ao ser dispensado. Ele existe do lado do
 * cliente pelo tempo de alguém copiá-lo e nada além disso — guardá-lo junto da assinatura o faria
 * sobreviver a cada recarga da lista, que é o oposto de "exibido uma única vez".
 */
@Injectable()
export class WebhooksStore {
  private readonly api = inject(WebhooksApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly subscriptions = signal<WebhookSubscription[]>([]);
  readonly eventTypes = signal<string[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  /** O segredo recém-criado. Nulo em qualquer outro momento. */
  readonly revealedSecret = signal<string | null>(null);

  readonly selectedId = signal<string | null>(null);
  readonly deliveries = signal<WebhookDelivery[]>([]);
  readonly loadingDeliveries = signal(false);
  readonly deliveriesError = signal<string | null>(null);

  readonly selected = computed(
    () => this.subscriptions().find(s => s.id === this.selectedId()) ?? null,
  );

  /**
   * Entregas que precisam de atenção.
   *
   * Só as esgotadas. Uma entrega ainda na fila com duas tentativas é o retry funcionando como projetado —
   * sinalizá-la treinaria quem lê a ignorar a lista inteira, inclusive as que importam.
   */
  readonly failedDeliveries = computed(() =>
    this.deliveries().filter(d => d.status === 'EXHAUSTED'),
  );

  readonly hasFailures = computed(() => this.failedDeliveries().length > 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .eventTypes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: types => this.eventTypes.set(types), error: () => undefined });
    this.api
      .subscriptions()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: subscriptions => this.subscriptions.set(subscriptions),
        error: () => this.error.set('Não foi possível carregar os webhooks.'),
      });
  }

  create(request: CreateSubscriptionRequest): void {
    this.creating.set(true);
    this.createError.set(null);
    this.api
      .create(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.creating.set(false)),
      )
      .subscribe({
        next: created => {
          this.revealedSecret.set(created.secret);
          this.load();
        },
        error: (e: WebhookError) => this.createError.set(this.messageFor(e)),
      });
  }

  dismissSecret(): void {
    this.revealedSecret.set(null);
  }

  changeStatus(subscription: WebhookSubscription, target: SubscriptionStatus): void {
    this.api
      .changeStatus(subscription.id, target, subscription.version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.toast.success(
            updated.status === 'REVOKED'
              ? `${updated.name} revogado. Nada mais será entregue.`
              : updated.status === 'PAUSED'
                ? `${updated.name} pausado. O que já está na fila continua.`
                : `${updated.name} reativado.`,
          );
          this.load();
        },
        error: (e: WebhookError) => this.toast.error(this.messageFor(e)),
      });
  }

  select(id: string): void {
    this.selectedId.set(id);
    this.loadDeliveries(id);
  }

  loadDeliveries(id: string): void {
    this.loadingDeliveries.set(true);
    this.deliveriesError.set(null);
    this.api
      .deliveries(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loadingDeliveries.set(false)),
      )
      .subscribe({
        next: deliveries => this.deliveries.set(deliveries),
        error: (e: WebhookError) => {
          this.deliveries.set([]);
          this.deliveriesError.set(this.messageFor(e));
        },
      });
  }

  private messageFor(e: WebhookError): string {
    if (e.error?.code === 'unknown_webhook_subscription') {
      return 'Este webhook não existe nesta cervejaria.';
    }
    if (e.status === 403) {
      return 'Criar ou reativar um webhook é alçada própria — ele manda dados da cervejaria para fora.';
    }
    if (e.status === 409) {
      return 'O webhook foi alterado por outra pessoa. Recarregue e tente novamente.';
    }
    if (e.status === 400) {
      return 'Confira os campos: o destino precisa ser https e ao menos um evento é obrigatório.';
    }
    return e.error?.detail ?? 'Não foi possível concluir a operação.';
  }
}
