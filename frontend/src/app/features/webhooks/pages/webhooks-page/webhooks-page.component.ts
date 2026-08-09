import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { WebhooksStore } from '../../data-access/webhooks.store';
import {
  DELIVERY_STATUS_LABELS,
  DeliveryStatus,
  EVENT_LABELS,
  SUBSCRIPTION_STATUS_LABELS,
  SubscriptionStatus,
  WebhookDelivery,
  WebhookSubscription,
} from '../../domain/webhook.model';

/**
 * Webhooks vistos por quem administra (INT-002).
 *
 * <p>Três coisas que a tela precisa deixar claras:
 *
 * <p><strong>O segredo aparece uma vez e some.</strong> Ele fica num painel destacado, com aviso explícito,
 * e é dispensado por ação de quem leu. Não há caminho para vê-lo de novo — quem o perde cria outro webhook.
 *
 * <p><strong>Entrega esgotada não some da lista.</strong> É o único estado que merece alarme: pendente com
 * duas tentativas é o retry funcionando como projetado, e sinalizá-lo treinaria quem lê a ignorar a lista
 * inteira.
 *
 * <p><strong>Pausar é fácil.</strong> Basta permissão de leitura, e a tela diz o que acontece: para de
 * mandar coisa nova, e o que já está na fila continua. Descobrir que o destino foi comprometido e não
 * conseguir parar seria o pior desenho possível.
 */
@Component({
  selector: 'app-webhooks-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [WebhooksStore],
  templateUrl: './webhooks-page.component.html',
})
export class WebhooksPageComponent implements OnInit {
  protected readonly store = inject(WebhooksStore);
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = SUBSCRIPTION_STATUS_LABELS;
  protected readonly deliveryLabels = DELIVERY_STATUS_LABELS;
  protected readonly eventLabels = EVENT_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    endpoint: ['https://', [Validators.required, Validators.pattern(/^https:\/\/.+/)]],
  });

  /** Os eventos escolhidos. Fora do form porque é um conjunto, não um campo. */
  private readonly chosen = new Set<string>();

  ngOnInit(): void {
    this.store.load();
  }

  protected isChosen(event: string): boolean {
    return this.chosen.has(event);
  }

  protected toggleEvent(event: string): void {
    if (this.chosen.has(event)) {
      this.chosen.delete(event);
    } else {
      this.chosen.add(event);
    }
  }

  protected labelFor(event: string): string {
    return this.eventLabels[event] ?? event;
  }

  protected create(): void {
    if (this.form.invalid || this.chosen.size === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.store.create({
      name: value.name,
      endpoint: value.endpoint,
      events: [...this.chosen],
    });
    this.form.reset({ endpoint: 'https://' });
    this.chosen.clear();
  }

  protected select(subscription: WebhookSubscription): void {
    this.store.select(subscription.id);
  }

  protected pause(subscription: WebhookSubscription): void {
    this.store.changeStatus(subscription, 'PAUSED');
  }

  protected resume(subscription: WebhookSubscription): void {
    this.store.changeStatus(subscription, 'ACTIVE');
  }

  protected revoke(subscription: WebhookSubscription): void {
    const confirmed = window.confirm(
      `Revogar ${subscription.name}?\n\n` +
        'Nada mais será entregue para este destino, inclusive o que já está na fila. ' +
        'A assinatura não volta a operar — para retomar, será preciso criar outra, com novo segredo.',
    );
    if (confirmed) {
      this.store.changeStatus(subscription, 'REVOKED');
    }
  }

  protected statusClass(status: SubscriptionStatus): string {
    return status === 'ACTIVE' ? 'bg-success' : status === 'PAUSED' ? 'bg-warning' : 'bg-secondary';
  }

  protected deliveryClass(status: DeliveryStatus): string {
    if (status === 'DELIVERED') {
      return 'bg-success-subtle text-success-emphasis';
    }
    return status === 'EXHAUSTED'
      ? 'bg-danger-subtle text-danger-emphasis'
      : 'bg-secondary-subtle text-secondary-emphasis';
  }

  /** "3 de 5" é mais legível que "3" quando o teto importa para entender o que vem a seguir. */
  protected attemptsLabel(delivery: WebhookDelivery): string {
    return delivery.status === 'PENDING' ? `${delivery.attempts} de 5` : String(delivery.attempts);
  }
}
