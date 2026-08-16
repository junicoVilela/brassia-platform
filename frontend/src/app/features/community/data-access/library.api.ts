import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AbuseReport,
  Contribution,
  ContributionKind,
  CreatedShareLink,
  ForkedRecipe,
  LibraryPublication,
  OwnedPublication,
  RatingSummary,
  RecipeLicense,
  ReportReason,
  SharePermission,
  ShareLink,
  Visibility,
} from '../domain/library.model';

@Injectable({ providedIn: 'root' })
export class LibraryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/community/library';

  feed(limit = 20): Observable<LibraryPublication[]> {
    return this.http.get<LibraryPublication[]>(this.baseUrl, {
      params: new HttpParams().set('limit', limit),
    });
  }

  mine(): Observable<OwnedPublication[]> {
    return this.http.get<OwnedPublication[]>(`${this.baseUrl}/mine`);
  }

  read(id: string): Observable<LibraryPublication> {
    return this.http.get<LibraryPublication>(`${this.baseUrl}/${id}`);
  }

  publish(body: {
    recipeId: string;
    title: string;
    summary: string | null;
    license: RecipeLicense;
    visibility: Visibility;
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(this.baseUrl, body);
  }

  changeVisibility(id: string, visibility: Visibility): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${id}/visibility`, { visibility });
  }

  unpublish(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/unpublish`, {});
  }

  links(publicationId: string): Observable<ShareLink[]> {
    return this.http.get<ShareLink[]>(`${this.baseUrl}/${publicationId}/links`);
  }

  /** A resposta traz o token — a única vez que ele existe fora de quem compartilha. */
  createLink(
    publicationId: string,
    body: { permission: SharePermission; label: string | null; expiresAt: string | null },
  ): Observable<CreatedShareLink> {
    return this.http.post<CreatedShareLink>(`${this.baseUrl}/${publicationId}/links`, body);
  }

  revokeLink(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/community/links/${id}/revoke`, {});
  }

  contributions(publicationId: string): Observable<Contribution[]> {
    return this.http.get<Contribution[]>(`${this.baseUrl}/${publicationId}/contributions`);
  }

  write(
    publicationId: string,
    body: { kind: ContributionKind; body: string; context: string | null },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${publicationId}/contributions`, body);
  }

  decide(id: string, accept: boolean, note: string | null): Observable<void> {
    const acao = accept ? 'accept' : 'decline';
    return this.http.post<void>(`/api/v1/community/contributions/${id}/${acao}`, { note });
  }

  rating(publicationId: string): Observable<RatingSummary> {
    return this.http.get<RatingSummary>(`${this.baseUrl}/${publicationId}/rating`);
  }

  /** Uma nota por pessoa: repetir troca a anterior, e não acumula. */
  rate(publicationId: string, value: number): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${publicationId}/rating`, { value });
  }

  /** Denunciar abre um caso — não esconde nada. */
  report(
    publicationId: string,
    body: { reason: ReportReason; note: string | null },
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${publicationId}/reports`, body);
  }

  reports(publicationId: string): Observable<AbuseReport[]> {
    return this.http.get<AbuseReport[]>(`${this.baseUrl}/${publicationId}/reports`);
  }

  fork(
    publicationId: string,
    body: { equipmentId: string; name: string | null },
  ): Observable<ForkedRecipe> {
    return this.http.post<ForkedRecipe>(`${this.baseUrl}/${publicationId}/fork`, body);
  }
}
