import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreatedShareLink,
  LibraryPublication,
  OwnedPublication,
  RecipeLicense,
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
}
