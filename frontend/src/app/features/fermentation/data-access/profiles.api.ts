import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateProfileRequest, FermentationProfile } from '../domain/profile.model';

@Injectable({ providedIn: 'root' })
export class ProfilesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/fermentation/profiles';

  list() {
    return this.http.get<FermentationProfile[]>(this.baseUrl);
  }

  create(request: CreateProfileRequest) {
    return this.http.post<{ id: string; version: number }>(this.baseUrl, request);
  }

  publish(id: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/publish`, {});
  }
}
