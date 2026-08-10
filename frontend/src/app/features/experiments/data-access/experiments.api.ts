import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ConcludeExperimentRequest,
  Experiment,
  PlanExperimentRequest,
} from '../domain/experiment.model';

@Injectable({ providedIn: 'root' })
export class ExperimentsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/experiments';

  list(recipeId?: string): Observable<Experiment[]> {
    const params = recipeId ? new HttpParams().set('recipeId', recipeId) : undefined;
    return this.http.get<Experiment[]>(this.baseUrl, { params });
  }

  plan(request: PlanExperimentRequest): Observable<Experiment> {
    return this.http.post<Experiment>(this.baseUrl, request);
  }

  start(id: string): Observable<Experiment> {
    return this.http.post<Experiment>(`${this.baseUrl}/${id}/start`, {});
  }

  conclude(id: string, request: ConcludeExperimentRequest): Observable<Experiment> {
    return this.http.post<Experiment>(`${this.baseUrl}/${id}/conclusion`, request);
  }

  abandon(id: string): Observable<Experiment> {
    return this.http.post<Experiment>(`${this.baseUrl}/${id}/abandon`, {});
  }
}
