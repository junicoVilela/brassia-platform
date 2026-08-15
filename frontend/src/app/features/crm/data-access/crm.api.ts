import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Contact, ContactPurpose, Customer } from '../domain/customer.model';

@Injectable({ providedIn: 'root' })
export class CrmApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/crm';

  customers(onlyActive: boolean): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.baseUrl}/customers`, {
      params: new HttpParams().set('onlyActive', onlyActive),
    });
  }

  createCustomer(body: {
    legalName: string;
    tradeName: string | null;
    taxId: string | null;
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/customers`, body);
  }

  setActive(id: string, active: boolean): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/customers/${id}/active`, { active });
  }

  contacts(customerId: string): Observable<Contact[]> {
    return this.http.get<Contact[]>(`${this.baseUrl}/customers/${customerId}/contacts`);
  }

  createContact(
    customerId: string,
    body: { name: string; email: string | null; phone: string | null; role: string | null },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/customers/${customerId}/contacts`, body);
  }

  /**
   * `decidedAt` vai em ISO UTC, e vem de um campo que o operador pode mudar.
   *
   * O padrão da tela é agora, mas quem registra uma decisão tomada por telefone na semana passada
   * precisa poder dizer isso — senão o livro passa a contar a história da digitação.
   */
  recordConsent(
    contactId: string,
    body: { purpose: ContactPurpose; granted: boolean; decidedAt: string; source: string },
  ): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/customers/contacts/${contactId}/consents`, body);
  }

  anonymize(contactId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/customers/contacts/${contactId}/anonymize`, {});
  }

  retentionPolicy(): Observable<{ daysAfterLastInteraction: number | null }> {
    return this.http.get<{ daysAfterLastInteraction: number | null }>(`${this.baseUrl}/retention-policy`);
  }

  saveRetentionPolicy(days: number): Observable<{ daysAfterLastInteraction: number | null }> {
    return this.http.put<{ daysAfterLastInteraction: number | null }>(`${this.baseUrl}/retention-policy`, {
      daysAfterLastInteraction: days,
    });
  }
}
