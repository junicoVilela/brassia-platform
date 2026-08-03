import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { CalibrationStandard, Instrument } from '../domain/metrology.model';
import { MetrologyApi } from './metrology.api';
import { MetrologyStore } from './metrology.store';

function instrument(over: Partial<Instrument> = {}): Instrument {
  return {
    id: 'i1',
    code: 'TERM-01',
    name: 'Termômetro',
    type: 'THERMOMETER',
    typeLabel: 'Termômetro',
    rangeMin: -10,
    rangeMax: 110,
    resolution: 0.1,
    accuracy: 0.5,
    unit: '°C',
    location: 'Sala',
    state: 'ACTIVE',
    blockReason: null,
    criticalUse: false,
    fitness: 'FIT',
    fitForCriticalUse: false,
    calibrationDueOn: '2027-08-03',
    lastCalibration: null,
    ...over,
  };
}

function standard(over: Partial<CalibrationStandard> = {}): CalibrationStandard {
  return {
    id: 's1',
    code: 'PAD-01',
    description: 'Banho térmico',
    certificateNumber: 'CERT-1',
    issuer: 'Lab',
    traceability: 'RBC',
    validUntil: '2028-01-01',
    expired: false,
    ...over,
  };
}

function setup(api: Partial<MetrologyApi>): MetrologyStore {
  TestBed.configureTestingModule({
    providers: [
      MetrologyStore,
      { provide: MetrologyApi, useValue: api },
      { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
    ],
  });
  return TestBed.inject(MetrologyStore);
}

describe('MetrologyStore', () => {
  it('carrega instrumentos e padrões', () => {
    const store = setup({
      instruments: () => of([instrument()]),
      standards: () => of([standard()]),
    });

    store.load();

    expect(store.instruments()).toHaveLength(1);
    expect(store.standards()).toHaveLength(1);
    expect(store.empty()).toBe(false);
    expect(store.loading()).toBe(false);
  });

  it('só oferece padrão dentro da validade para calibrar', () => {
    const store = setup({
      instruments: () => of([]),
      standards: () => of([standard(), standard({ id: 's2', code: 'PAD-02', expired: true })]),
    });

    store.load();

    expect(store.validStandards().map(s => s.code)).toEqual(['PAD-01']);
  });

  it('destaca instrumento de ponto crítico que deixou de servir', () => {
    // Continua designado, mas a calibração venceu — é o caso que a tela precisa gritar.
    const store = setup({
      instruments: () =>
        of([
          instrument({ id: 'i1', criticalUse: true, fitForCriticalUse: true }),
          instrument({ id: 'i2', code: 'TERM-02', criticalUse: true, fitForCriticalUse: false, fitness: 'EXPIRED' }),
          instrument({ id: 'i3', code: 'TERM-03', criticalUse: false, fitForCriticalUse: false }),
        ]),
      standards: () => of([]),
    });

    store.load();

    expect(store.criticalAtRisk().map(i => i.code)).toEqual(['TERM-02']);
  });

  it('guarda a recusa de ponto crítico com a aptidão que a barrou', () => {
    const store = setup({
      instruments: () => of([]),
      standards: () => of([]),
      setCriticalUse: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'instrument_not_fit',
            instrument: { code: 'TERM-01', fitness: 'EXPIRED', calibrationDueOn: '2026-01-01' },
          },
        })),
    });

    store.setCriticalUse(instrument(), true);

    expect(store.notFit()?.fitness).toBe('EXPIRED');
    expect(store.notFit()?.calibrationDueOn).toBe('2026-01-01');
    // A recusa explicada não deve virar erro genérico, que esconderia o motivo.
    expect(store.actionError()).toBeNull();
  });

  it('guarda a recusa de padrão vencido separada do erro genérico', () => {
    const store = setup({
      instruments: () => of([]),
      standards: () => of([]),
      calibrate: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'standard_expired',
            standard: { code: 'PAD-01', validUntil: '2026-01-01', performedOn: '2026-08-03' },
          },
        })),
    });

    store.calibrate('i1', {
      standardId: 's1',
      performedOn: '2026-08-03',
      dueOn: '2027-08-03',
      performedBy: 'Metrologista',
      certificateNumber: 'CERT-1',
      result: 'APPROVED',
      maxDeviation: 0.2,
      restriction: null,
      note: null,
      curve: null,
    });

    expect(store.standardExpired()?.code).toBe('PAD-01');
    expect(store.calibrationError()).toBeNull();
  });

  it('guarda a recusa de leitura fora da curva separada do erro genérico', () => {
    const store = setup({
      instruments: () => of([]),
      standards: () => of([]),
      correct: () =>
        throwError(() => ({
          status: 409,
          error: { code: 'outside_curve_range', curve: { value: '150', min: '0.5', max: '100.5' } },
        })),
    });

    store.correct({
      instrumentId: 'i1',
      sourceReadingId: null,
      rawValue: 150,
      unit: '°C',
      sampleTempC: null,
      calibrationTempC: null,
      applyCurve: true,
    });

    expect(store.outsideCurve()?.max).toBe('100.5');
    expect(store.correctionError()).toBeNull();
  });

  it('avisa quando o ponto da curva está em formato inválido', () => {
    const store = setup({ instruments: () => of([]), standards: () => of([]) });

    store.reportCurveFormat('0 e meio');

    expect(store.calibrationError()).toContain('formato inválido');
  });

  it('abre e fecha o histórico do mesmo instrumento', () => {
    const store = setup({
      instruments: () => of([]),
      standards: () => of([]),
      calibrations: () => of([]),
      corrections: () => of([]),
    });

    store.toggleHistory(instrument());
    expect(store.openHistoryOf()).toBe('i1');

    store.toggleHistory(instrument());
    expect(store.openHistoryOf()).toBeNull();
  });

  it('reporta erro de carregamento sem apagar a tela', () => {
    const store = setup({
      instruments: () => throwError(() => ({ status: 500 })),
      standards: () => of([]),
    });

    store.load();

    expect(store.error()).toBe('Não foi possível carregar os instrumentos.');
    expect(store.loading()).toBe(false);
  });
});
