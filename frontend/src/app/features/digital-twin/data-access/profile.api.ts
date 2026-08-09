import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ComputeProfileRequest, LearnedProfile } from '../domain/profile.model';

@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/digital-twin/profiles';

  /** 204 vira `null`: "nunca analisada" é diferente de "analisada e sem resultado". */
  latest(recipeId: string): Observable<LearnedProfile | null> {
    return this.http.get<LearnedProfile | null>(`${this.baseUrl}/${recipeId}`);
  }

  history(recipeId: string): Observable<LearnedProfile[]> {
    return this.http.get<LearnedProfile[]>(`${this.baseUrl}/${recipeId}/history`);
  }

  compute(request: ComputeProfileRequest): Observable<LearnedProfile> {
    return this.http.post<LearnedProfile>(this.baseUrl, request);
  }
}
