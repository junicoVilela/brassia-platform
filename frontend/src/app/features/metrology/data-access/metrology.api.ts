import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  Calibration,
  CalibrationStandard,
  CorrectReadingRequest,
  ReadingCorrection,
  Instrument,
  RecordCalibrationRequest,
  RegisterInstrumentRequest,
  RegisterStandardRequest,
} from '../domain/metrology.model';

@Injectable({ providedIn: 'root' })
export class MetrologyApi {
  private readonly http = inject(HttpClient);
  private readonly instrumentsUrl = '/api/v1/metrology/instruments';
  private readonly standardsUrl = '/api/v1/metrology/standards';

  instruments() {
    return this.http.get<Instrument[]>(this.instrumentsUrl);
  }

  register(request: RegisterInstrumentRequest) {
    return this.http.post<Instrument>(this.instrumentsUrl, request);
  }

  block(instrumentId: string, reason: string) {
    return this.http.post<Instrument>(`${this.instrumentsUrl}/${instrumentId}/block`, { reason });
  }

  unblock(instrumentId: string) {
    return this.http.post<Instrument>(`${this.instrumentsUrl}/${instrumentId}/unblock`, {});
  }

  retire(instrumentId: string, reason: string) {
    return this.http.post<Instrument>(`${this.instrumentsUrl}/${instrumentId}/retire`, { reason });
  }

  /** Designar exige instrumento apto; remover a designação é sempre permitido. */
  setCriticalUse(instrumentId: string, criticalUse: boolean) {
    return this.http.put<Instrument>(`${this.instrumentsUrl}/${instrumentId}/critical-use`, { criticalUse });
  }

  calibrations(instrumentId: string) {
    return this.http.get<Calibration[]>(`${this.instrumentsUrl}/${instrumentId}/calibrations`);
  }

  calibrate(instrumentId: string, request: RecordCalibrationRequest) {
    return this.http.post<Instrument>(`${this.instrumentsUrl}/${instrumentId}/calibrations`, request);
  }

  corrections(instrumentId: string) {
    return this.http.get<ReadingCorrection[]>('/api/v1/metrology/corrections', {
      params: { instrumentId },
    });
  }

  correct(request: CorrectReadingRequest) {
    return this.http.post<ReadingCorrection>('/api/v1/metrology/corrections', request);
  }

  standards() {
    return this.http.get<CalibrationStandard[]>(this.standardsUrl);
  }

  registerStandard(request: RegisterStandardRequest) {
    return this.http.post<CalibrationStandard>(this.standardsUrl, request);
  }
}
