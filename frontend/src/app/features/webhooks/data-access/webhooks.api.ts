import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateSubscriptionRequest,
  CreatedSubscription,
  SubscriptionStatus,
  WebhookDelivery,
  WebhookSubscription,
} from '../domain/webhook.model';

@Injectable({ providedIn: 'root' })
export class WebhooksApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/integration/webhooks';

  /** A allowlist fechada de tipos vem do servidor: a tela não mantém a lista por conta própria. */
  eventTypes(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/event-types`);
  }

  subscriptions(): Observable<WebhookSubscription[]> {
    return this.http.get<WebhookSubscription[]>(this.baseUrl);
  }

  create(request: CreateSubscriptionRequest): Observable<CreatedSubscription> {
    return this.http.post<CreatedSubscription>(this.baseUrl, request);
  }

  changeStatus(
    id: string,
    status: SubscriptionStatus,
    expectedVersion: number,
  ): Observable<WebhookSubscription> {
    return this.http.post<WebhookSubscription>(`${this.baseUrl}/${id}/status`, {
      status,
      expectedVersion,
    });
  }

  deliveries(id: string): Observable<WebhookDelivery[]> {
    return this.http.get<WebhookDelivery[]>(`${this.baseUrl}/${id}/deliveries`);
  }
}
