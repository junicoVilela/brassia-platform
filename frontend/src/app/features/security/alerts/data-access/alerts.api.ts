import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { AlertStatusUpdate, SecurityAlert } from '../domain/alert.model';

@Injectable({ providedIn: 'root' })
export class AlertsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security/alerts';

  list(status?: string) {
    const params: Record<string, string> = status ? { status } : {};
    return this.http.get<SecurityAlert[]>(this.baseUrl, { params });
  }

  updateStatus(id: string, status: AlertStatusUpdate) {
    return this.http.patch<void>(`${this.baseUrl}/${id}`, { status });
  }
}
