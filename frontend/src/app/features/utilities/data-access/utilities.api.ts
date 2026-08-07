import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UtilityReport } from '../domain/utility-indicator.model';

@Injectable({ providedIn: 'root' })
export class UtilitiesApi {
  private readonly http = inject(HttpClient);

  indicators(from: string, to: string): Observable<UtilityReport> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<UtilityReport>('/api/v1/utilities/indicators', { params });
  }
}
