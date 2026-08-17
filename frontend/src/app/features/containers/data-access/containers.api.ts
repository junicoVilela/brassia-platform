import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Container,
  ContainerFill,
  ContainerIdentifier,
  ContainerLoan,
  ContainerSanitation,
  ContainerKind,
  ContainerLocation,
  ContainerState,
  IdentifierTechnology,
  LocationKind,
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

  /** O histórico do que já esteve dentro — e não só o de agora. */
  fills(id: string): Observable<ContainerFill[]> {
    return this.http.get<ContainerFill[]>(`${this.baseUrl}/${id}/fills`);
  }

  fill(id: string, body: { finishedLotId: string; volumeLiters: number }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${id}/fills`, body);
  }

  /** Fecha o período; não apaga o vínculo. */
  emptyFill(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/fills/empty`, {});
  }

  locations(id: string): Observable<ContainerLocation[]> {
    return this.http.get<ContainerLocation[]>(`${this.baseUrl}/${id}/locations`);
  }

  locate(id: string, body: { kind: LocationKind; place: string | null }): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/locations`, body);
  }

  /** Empréstimos em aberto; com `overdueOn`, só a fila de atrasados. */
  loans(overdueOn: string | null): Observable<ContainerLoan[]> {
    const params = overdueOn ? new HttpParams().set('overdueOn', overdueOn) : new HttpParams();
    return this.http.get<ContainerLoan[]>(`${this.baseUrl}/loans`, { params });
  }

  loansOf(id: string): Observable<ContainerLoan[]> {
    return this.http.get<ContainerLoan[]>(`${this.baseUrl}/${id}/loans`);
  }

  lend(
    id: string,
    body: {
      customerId: string;
      customerName: string;
      dueOn: string;
      depositAmount: number | null;
      depositCurrency: string | null;
    },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${id}/loans`, body);
  }

  returnLoan(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/loans/return`, {});
  }

  /** Perda: encerra o empréstimo, retém a caução e baixa o vasilhame. */
  declareLoss(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/loans/loss`, { reason });
  }

  /** O perdido que reapareceu: a perda fica, e a volta entra ao lado. */
  recoverLoan(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/loans/recovery`, { reason });
  }

  sanitations(id: string): Observable<ContainerSanitation[]> {
    return this.http.get<ContainerSanitation[]>(`${this.baseUrl}/${id}/sanitations`);
  }

  sanitize(id: string, body: { method: string; note: string | null }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${id}/sanitations`, body);
  }

  condition(id: string, condemned: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/condition`, { condemned });
  }

  retire(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/retire`, { reason });
  }
}
