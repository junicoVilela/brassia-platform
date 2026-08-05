import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { Parameters } from '../domain/parameters.model';
import { ParametersApi } from './parameters.api';
import { ParametersStore } from './parameters.store';

function parameters(over: Partial<Parameters> = {}): Parameters {
  return {
    cleaning: { validityHours: null, expiresByTime: false },
    gas: { requalificationMonths: null, derivesDueDate: false },
    calibration: { monthsByType: {} },
    capa: { bySeverity: {} },
    sensory: { maxScore: 10, appliesToNewSessionsOnly: true },
    ...over,
  };
}

function setup(api: Partial<ParametersApi>, toast = { success: vi.fn(), error: vi.fn() }): ParametersStore {
  TestBed.configureTestingModule({
    providers: [
      ParametersStore,
      { provide: ParametersApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return TestBed.inject(ParametersStore);
}

describe('ParametersStore', () => {
  it('carrega as cinco políticas de uma vez', () => {
    const store = setup({ loadAll: () => of(parameters()) });

    store.load();

    expect(store.parameters()?.sensory.maxScore).toBe(10);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('reporta falha de carga sem deixar a tela pendurada', () => {
    const store = setup({ loadAll: () => throwError(() => new Error('boom')) });

    store.load();

    expect(store.parameters()).toBeNull();
    expect(store.loading()).toBe(false);
    expect(store.error()).toContain('carregar');
  });

  it('conta apenas as derivações ativas', () => {
    const store = setup({
      loadAll: () =>
        of(
          parameters({
            cleaning: { validityHours: 72, expiresByTime: true },
            capa: { bySeverity: { MAJOR: { containmentDays: 2, investigationDays: 10, verificationDays: 30 } } },
          }),
        ),
    });

    store.load();

    // Gás e calibração seguem no padrão; a escala sensorial sempre tem valor e não conta.
    expect(store.configuredCount()).toBe(2);
  });

  it('atualiza só a seção salva e mantém as demais', () => {
    const store = setup({
      loadAll: () => of(parameters()),
      saveCleaning: () => of({ validityHours: 48, expiresByTime: true }),
    });
    store.load();

    store.saveCleaning(48);

    expect(store.parameters()?.cleaning).toEqual({ validityHours: 48, expiresByTime: true });
    expect(store.parameters()?.gas.derivesDueDate).toBe(false);
    expect(store.saving()).toBeNull();
  });

  it('descarta tipo de instrumento sem periodicidade', () => {
    const saveCalibration = vi.fn(() => of({ monthsByType: { THERMOMETER: 12 } }));
    const store = setup({ loadAll: () => of(parameters()), saveCalibration });
    store.load();

    store.saveCalibration({ THERMOMETER: 12, SCALE: undefined, PH_METER: 0 });

    // Tipo sem valor sai do mapa: é assim que o vencimento volta a vir do certificado.
    expect(saveCalibration).toHaveBeenCalledWith({ THERMOMETER: 12 });
  });

  it('descarta severidade sem prazos no CAPA', () => {
    const saveCapa = vi.fn(() => of({ bySeverity: {} }));
    const store = setup({ loadAll: () => of(parameters()), saveCapa });
    store.load();

    store.saveCapa({
      MINOR: null,
      CRITICAL: { containmentDays: 1, investigationDays: 5, verificationDays: 15 },
    });

    expect(saveCapa).toHaveBeenCalledWith({
      CRITICAL: { containmentDays: 1, investigationDays: 5, verificationDays: 15 },
    });
  });

  it('mostra a recusa do backend em vez de mensagem genérica', () => {
    const store = setup({
      loadAll: () => of(parameters()),
      saveCapa: () => throwError(() => ({ status: 400, detail: 'verificação antes da investigação' })),
    });
    store.load();

    store.saveCapa({ MAJOR: { containmentDays: 30, investigationDays: 10, verificationDays: 5 } });

    expect(store.actionError()).toBe('verificação antes da investigação');
    expect(store.saving()).toBeNull();
  });
});
