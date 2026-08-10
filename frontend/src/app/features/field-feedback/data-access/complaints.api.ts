import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ComplainantContact,
  Complaint,
  RegisterComplaintRequest,
} from '../domain/complaint.model';

@Injectable({ providedIn: 'root' })
export class ComplaintsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/field-feedback/complaints';

  list(batchId?: string): Observable<Complaint[]> {
    const params = batchId ? new HttpParams().set('batchId', batchId) : undefined;
    return this.http.get<Complaint[]>(this.baseUrl, { params });
  }

  register(request: RegisterComplaintRequest): Observable<Complaint> {
    return this.http.post<Complaint>(this.baseUrl, request);
  }

  startAnalysis(id: string): Observable<Complaint> {
    return this.http.post<Complaint>(`${this.baseUrl}/${id}/analysis`, {});
  }

  fulfill(id: string, action: string, referenceId: string): Observable<Complaint> {
    return this.http.post<Complaint>(`${this.baseUrl}/${id}/actions/${action}/fulfillment`, {
      referenceId,
    });
  }

  waive(id: string, action: string, justification: string): Observable<Complaint> {
    return this.http.post<Complaint>(`${this.baseUrl}/${id}/actions/${action}/waiver`, {
      justification,
    });
  }

  close(id: string, note: string): Observable<Complaint> {
    return this.http.post<Complaint>(`${this.baseUrl}/${id}/closure`, { note });
  }

  /** Chamada explícita e à parte: ler dado pessoal é um ato, e cada um deles é auditado no servidor. */
  contact(id: string): Observable<ComplainantContact> {
    return this.http.get<ComplainantContact>(`${this.baseUrl}/${id}/contact`);
  }

  eraseContact(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/contact`);
  }
}
