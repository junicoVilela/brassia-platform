import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import {
  CalibrationPolicy,
  CapaPolicy,
  CleaningPolicy,
  GasPolicy,
  Parameters,
  SensoryPolicy,
} from '../domain/parameters.model';

/**
 * Cliente da tela de parametrização.
 *
 * <p>Não existe endpoint único de parâmetros: cada política é servida pelo módulo dono do conceito,
 * e é aqui que elas se juntam. É o preço — barato — de manter as fronteiras de módulo intactas.
 */
@Injectable({ providedIn: 'root' })
export class ParametersApi {
  private readonly http = inject(HttpClient);

  /** Uma requisição por política, em paralelo. */
  loadAll(): Observable<Parameters> {
    return forkJoin({
      cleaning: this.http.get<CleaningPolicy>('/api/v1/sanitation/cleaning-policy'),
      gas: this.http.get<GasPolicy>('/api/v1/gas/policy'),
      calibration: this.http.get<CalibrationPolicy>('/api/v1/metrology/calibration-policy'),
      capa: this.http.get<CapaPolicy>('/api/v1/quality/capa-policy'),
      sensory: this.http.get<SensoryPolicy>('/api/v1/sensory/policy'),
    });
  }

  saveCleaning(validityHours: number | null) {
    return this.http.put<CleaningPolicy>('/api/v1/sanitation/cleaning-policy', { validityHours });
  }

  saveGas(requalificationMonths: number | null) {
    return this.http.put<GasPolicy>('/api/v1/gas/policy', { requalificationMonths });
  }

  saveCalibration(monthsByType: Record<string, number>) {
    return this.http.put<CalibrationPolicy>('/api/v1/metrology/calibration-policy', { monthsByType });
  }

  saveCapa(bySeverity: CapaPolicy['bySeverity']) {
    return this.http.put<CapaPolicy>('/api/v1/quality/capa-policy', { bySeverity });
  }

  saveSensory(maxScore: number) {
    return this.http.put<SensoryPolicy>('/api/v1/sensory/policy', { maxScore });
  }
}
