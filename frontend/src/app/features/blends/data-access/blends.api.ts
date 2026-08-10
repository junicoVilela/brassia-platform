import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BlendOperation, SimulateBlendRequest } from '../domain/blend.model';

@Injectable({ providedIn: 'root' })
export class BlendsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/blends';

  list(): Observable<BlendOperation[]> {
    return this.http.get<BlendOperation[]>(this.baseUrl);
  }

  simulate(request: SimulateBlendRequest): Observable<BlendOperation> {
    return this.http.post<BlendOperation>(this.baseUrl, request);
  }

  approve(id: string): Observable<BlendOperation> {
    return this.http.post<BlendOperation>(`${this.baseUrl}/${id}/approval`, {});
  }

  execute(id: string): Observable<BlendOperation> {
    return this.http.post<BlendOperation>(`${this.baseUrl}/${id}/execution`, {});
  }

  discard(id: string): Observable<BlendOperation> {
    return this.http.post<BlendOperation>(`${this.baseUrl}/${id}/discard`, {});
  }
}
