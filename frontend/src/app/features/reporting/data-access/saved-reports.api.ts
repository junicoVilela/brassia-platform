import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ReportRun, SavedReport, SavedReportRequest } from '../domain/saved-report.model';

@Injectable({ providedIn: 'root' })
export class SavedReportsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/reporting/saved-reports';

  list(): Observable<SavedReport[]> {
    return this.http.get<SavedReport[]>(this.baseUrl);
  }

  define(request: SavedReportRequest): Observable<SavedReport> {
    return this.http.post<SavedReport>(this.baseUrl, request);
  }

  redefine(reportId: string, request: Partial<SavedReportRequest>): Observable<SavedReport> {
    return this.http.put<SavedReport>(`${this.baseUrl}/${reportId}`, request);
  }

  activate(reportId: string, active: boolean): Observable<SavedReport> {
    return this.http.post<SavedReport>(`${this.baseUrl}/${reportId}/active`, { active });
  }

  runs(reportId: string): Observable<ReportRun[]> {
    return this.http.get<ReportRun[]>(`${this.baseUrl}/${reportId}/runs`);
  }

  /** Executa agora — com a alçada do dono, nunca com a de quem pede. */
  run(reportId: string): Observable<ReportRun> {
    return this.http.post<ReportRun>(`${this.baseUrl}/${reportId}/runs`, {});
  }

  /** Emite um link temporário e pessoal para esta execução. */
  link(runId: string): Observable<{ token: string; expiresAt: string }> {
    return this.http.post<{ token: string; expiresAt: string }>(
      `${this.baseUrl}/runs/${runId}/link`,
      {},
    );
  }

  deliver(runId: string, recipientId: string, delivered: boolean, detail: string | null) {
    return this.http.post<ReportRun>(`${this.baseUrl}/runs/${runId}/deliveries`, {
      recipientId,
      delivered,
      detail,
    });
  }

  /** O token diz qual artefato; a sessão diz quem — por isso a chamada continua autenticada. */
  download(token: string): Observable<string> {
    return this.http.get(`/api/v1/reporting/downloads/${token}`, { responseType: 'text' });
  }
}
