import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DeliveryOutcome, Load, ProofOfDelivery, SyncResult } from '../domain/load.model';

@Injectable({ providedIn: 'root' })
export class LoadsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/distribution/loads';

  list(day: string | null): Observable<Load[]> {
    const params = day ? new HttpParams().set('day', day) : new HttpParams();
    return this.http.get<Load[]>(this.baseUrl, { params });
  }

  read(id: string): Observable<Load> {
    return this.http.get<Load>(`${this.baseUrl}/${id}`);
  }

  plan(body: {
    code: string;
    scheduledFor: string;
    capacityLiters: number;
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(this.baseUrl, body);
  }

  addStop(
    id: string,
    body: {
      customerId: string;
      customerName: string;
      sequence: number;
      windowFrom: string | null;
      windowTo: string | null;
    },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${id}/stops`, body);
  }

  removeStop(id: string, stopId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/stops/${stopId}`);
  }

  loadContainer(id: string, stopId: string, containerId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/stops/${stopId}/containers`, { containerId });
  }

  unloadContainer(id: string, containerId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/containers/${containerId}`);
  }

  assign(id: string, body: { driverId: string; vehicle: string | null }): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/driver`, body);
  }

  /** Alçada própria: quem montou não confere. */
  release(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/release`, {});
  }

  reopen(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/reopen`, {});
  }

  depart(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/depart`, {});
  }

  close(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/close`, {});
  }

  proofsOfLoad(id: string): Observable<ProofOfDelivery[]> {
    return this.http.get<ProofOfDelivery[]>(`${this.baseUrl}/${id}/proofs`);
  }

  proofsOfStop(stopId: string): Observable<ProofOfDelivery[]> {
    return this.http.get<ProofOfDelivery[]>(`/api/v1/distribution/stops/${stopId}/proof`);
  }

  /** Registrar move os vasilhames: o que desceu vai para o cliente, o que voltou volta sujo. */
  recordProof(
    id: string,
    stopId: string,
    body: {
      outcome: DeliveryOutcome;
      delivered: string[];
      collected: string[];
      note: string | null;
      signatureConsent: {
        kind: 'SIGNATURE';
        storageKey: string;
        consentedByName: string;
        purpose: string;
      } | null;
      latitude: number | null;
      longitude: number | null;
    },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${id}/stops/${stopId}/proof`, body);
  }

  /** Não apaga a anterior: as duas ficam, e a correção aponta para a original. */
  correctProof(
    stopId: string,
    body: {
      outcome: DeliveryOutcome;
      delivered: string[];
      collected: string[];
      reason: string;
    },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(
      `/api/v1/distribution/stops/${stopId}/proof/correction`,
      body,
    );
  }

  /** A fila de conflitos: o aparelho e o escritório registraram a mesma parada. */
  syncConflicts(): Observable<SyncResult[]> {
    return this.http.get<SyncResult[]>('/api/v1/distribution/sync/conflicts');
  }

  syncOfLoad(id: string): Observable<SyncResult[]> {
    return this.http.get<SyncResult[]>(`/api/v1/distribution/sync/loads/${id}`);
  }

  cancel(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
