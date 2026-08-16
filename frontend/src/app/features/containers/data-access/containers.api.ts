import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Container,
  ContainerIdentifier,
  ContainerKind,
  ContainerState,
  IdentifierTechnology,
  Ownership,
} from '../domain/container.model';

@Injectable({ providedIn: 'root' })
export class ContainersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/containers';

  list(state: ContainerState | null): Observable<Container[]> {
    const params = state ? new HttpParams().set('state', state) : new HttpParams();
    return this.http.get<Container[]>(this.baseUrl, { params });
  }

  register(body: {
    code: string;
    kind: ContainerKind;
    nominalCapacityLiters: number;
    ownership: Ownership;
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(this.baseUrl, body);
  }

  /** Uma leitura de código vira um contêiner — e nada além disso. */
  resolve(value: string): Observable<Container> {
    return this.http.get<Container>(`${this.baseUrl}/by-identifier`, {
      params: new HttpParams().set('value', value),
    });
  }

  identifiers(id: string): Observable<ContainerIdentifier[]> {
    return this.http.get<ContainerIdentifier[]>(`${this.baseUrl}/${id}/identifiers`);
  }

  assign(
    id: string,
    body: { value: string; technology: IdentifierTechnology },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${id}/identifiers`, body);
  }

  retireIdentifier(identifierId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/identifiers/${identifierId}/retire`, {});
  }

  inspect(
    id: string,
    body: { performedAt: string; validUntil: string; note: string | null },
  ): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/inspections`, body);
  }

  /** Só o destino: o estado atual já diz qual transição é. */
  move(id: string, to: ContainerState): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/moves`, { to });
  }

  condition(id: string, condemned: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/condition`, { condemned });
  }

  retire(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/retire`, { reason });
  }
}
