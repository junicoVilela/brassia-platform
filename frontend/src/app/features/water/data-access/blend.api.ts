import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  BlendResult,
  RegisterWaterProfileRequest,
  SimulateBlendRequest,
  WaterProfile,
} from '../domain/blend.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class BlendApi {
  private readonly http = inject(HttpClient);

  listProfiles() {
    return this.http.get<PageResponse<WaterProfile>>('/api/v1/water/profiles', {
      params: { page: 0, size: 100 },
    });
  }

  createProfile(request: RegisterWaterProfileRequest) {
    return this.http.post<WaterProfile>('/api/v1/water/profiles', request);
  }

  simulate(request: SimulateBlendRequest) {
    return this.http.post<BlendResult>('/api/v1/water/blends/simulate', request);
  }
}
