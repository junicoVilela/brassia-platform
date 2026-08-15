import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CatalogItem, CreditSituation, PortalOrder } from '../domain/portal.model';

/**
 * O portal fala com a própria árvore de endpoints.
 *
 * Nenhuma chamada carrega o identificador do cliente: ele vem do vínculo do usuário autenticado, no
 * servidor. Mandá-lo daqui seria oferecer ao navegador a chance de trocá-lo.
 */
@Injectable({ providedIn: 'root' })
export class PortalApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/portal';

  catalog(): Observable<CatalogItem[]> {
    return this.http.get<CatalogItem[]>(`${this.baseUrl}/catalog`);
  }

  orders(): Observable<PortalOrder[]> {
    return this.http.get<PortalOrder[]>(`${this.baseUrl}/orders`);
  }

  credit(): Observable<CreditSituation> {
    return this.http.get<CreditSituation>(`${this.baseUrl}/credit`);
  }

  place(
    body: { code: string; promisedFor: string | null; items: { productId: string; quantity: number }[] },
    idempotencyKey: string,
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/orders`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  reorder(
    orderId: string,
    body: { code: string; promisedFor: string | null },
    idempotencyKey: string,
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/orders/${orderId}/reorder`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
}
