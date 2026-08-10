import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OptimizationRun, OptimizeRequest } from '../domain/optimization.model';

@Injectable({ providedIn: 'root' })
export class OptimizationApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/optimizations';

  list(recipeId?: string): Observable<OptimizationRun[]> {
    const params = recipeId ? new HttpParams().set('recipeId', recipeId) : undefined;
    return this.http.get<OptimizationRun[]>(this.baseUrl, { params });
  }

  optimize(request: OptimizeRequest): Observable<OptimizationRun> {
    return this.http.post<OptimizationRun>(this.baseUrl, request);
  }

  /** Rota separada: a explicação chega depois do resultado e não tem por onde alterá-lo. */
  explain(id: string, explanation: string): Observable<OptimizationRun> {
    return this.http.post<OptimizationRun>(`${this.baseUrl}/${id}/explanation`, { explanation });
  }

  /** Registra o ponteiro para a versão de receita criada por fora, sob revisão. */
  apply(id: string, recipeVersionId: string): Observable<OptimizationRun> {
    return this.http.post<OptimizationRun>(`${this.baseUrl}/${id}/application`, { recipeVersionId });
  }
}
