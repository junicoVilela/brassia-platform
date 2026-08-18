import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SalesOrder } from '../domain/order.model';
import { OrderPayments } from '../domain/payment.model';
import { PriceSchedule, Product, SalesChannel, SellableLot } from '../domain/product.model';

@Injectable({ providedIn: 'root' })
export class SalesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sales';

  products(onlyActive: boolean): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.baseUrl}/products`, {
      params: new HttpParams().set('onlyActive', onlyActive),
    });
  }

  createProduct(body: {
    sku: string;
    name: string;
    recipeId: string;
    containerId: string;
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/products`, body);
  }

  setProductActive(id: string, active: boolean): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/products/${id}/active`, { active });
  }

  channels(onlyActive: boolean): Observable<SalesChannel[]> {
    return this.http.get<SalesChannel[]>(`${this.baseUrl}/channels`, {
      params: new HttpParams().set('onlyActive', onlyActive),
    });
  }

  createChannel(body: { code: string; name: string }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/channels`, body);
  }

  /** Só os lotes vendáveis: o backend já aplicou liberação, validade e quarentena. */
  sellableLots(productId: string): Observable<SellableLot[]> {
    return this.http.get<SellableLot[]>(`${this.baseUrl}/products/${productId}/sellable-lots`);
  }

  priceSchedule(productId: string, channelId: string): Observable<PriceSchedule> {
    return this.http.get<PriceSchedule>(`${this.baseUrl}/products/${productId}/prices`, {
      params: new HttpParams().set('channelId', channelId),
    });
  }

  /** `validFrom` é data pura (sem hora), então não há a armadilha de fuso do `datetime-local`. */
  priceFrom(
    productId: string,
    body: {
      channelId: string;
      amount: number;
      currency: string;
      taxIncluded: boolean;
      validFrom: string;
    },
  ): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/products/${productId}/prices`, body);
  }

  orders(): Observable<SalesOrder[]> {
    return this.http.get<SalesOrder[]>(`${this.baseUrl}/orders`);
  }

  /**
   * Registra o pedido.
   *
   * A chave de idempotência vai no cabeçalho e é gerada por **tentativa de envio**, não por pedido: é
   * o que faz um duplo clique ou um retry de rede devolver o mesmo pedido em vez de reservar o estoque
   * duas vezes.
   */
  placeOrder(
    body: {
      code: string;
      customerId: string;
      channelId: string;
      promisedFor: string | null;
      items: { productId: string; quantity: number }[];
    },
    idempotencyKey: string,
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/orders`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  cancelOrder(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/orders/${id}/cancel`, {});
  }

  payments(orderId: string): Observable<OrderPayments> {
    return this.http.get<OrderPayments>(`${this.baseUrl}/orders/${orderId}/payments`);
  }

  /**
   * Registra o recebimento.
   *
   * `receivedOn` é data pura: a data do extrato é a que concilia, e o depósito de sexta costuma ser
   * lançado na segunda.
   */
  recordPayment(
    orderId: string,
    body: {
      amount: number;
      currency: string;
      receivedOn: string | null;
      method: string;
      note: string | null;
    },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/orders/${orderId}/payments`, body);
  }

  /** O estorno é POST, e não DELETE: ele cria um lançamento, e não apaga o original. */
  reversePayment(paymentId: string, reason: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/payments/${paymentId}/reversal`, {
      reason,
    });
  }
}
