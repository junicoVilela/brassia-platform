import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { GasComponent, GasConnection, GasCylinder, LineBalance, ServiceLine } from '../domain/gas.model';
import { GasApi } from './gas.api';
import { GasStore } from './gas.store';

function cylinder(overrides: Partial<GasCylinder> = {}): GasCylinder {
  return {
    id: 'c1', code: 'CIL-001', gasType: 'CO2', capacityKg: 10, tareKg: 12.5, contentKg: 10,
    requalificationDueOn: '2029-08-01', expired: false, status: 'AVAILABLE', allocatable: true,
    blockReason: null, location: 'Casa de gases', ...overrides,
  };
}

function component(overrides: Partial<GasComponent> = {}): GasComponent {
  return {
    id: 'r1', kind: 'REGULATOR', code: 'REG-1', name: 'Regulador', maxPressureBar: 10,
    setPressureBar: 3, active: true, ...overrides,
  };
}

function connection(overrides: Partial<GasConnection> = {}): GasConnection {
  return {
    id: 'x1', cylinderId: 'c1', regulatorId: 'r1', manifoldId: null, pointOfUseEquipmentId: 'e1',
    workingPressureBar: 3, networkMaxPressureBar: 6, status: 'PENDING_TEST',
    connectedAt: '2026-08-01T10:00:00Z', leakTest: null, disconnectedAt: null, disconnectReason: null,
    ...overrides,
  };
}

function serviceLine(overrides: Partial<ServiceLine> = {}): ServiceLine {
  return {
    id: 'l1', code: 'LN-01', name: 'Torneira 1', pointOfUseEquipmentId: 'e1', currentRevision: 0,
    everApplied: false, ...overrides,
  };
}

function lineBalance(overrides: Partial<LineBalance> = {}): LineBalance {
  return {
    appliedPressureBar: 0.81, recommendedLengthMeters: 1.05, hydrostaticBar: 0.03,
    effectiveResistanceBarPerMeter: 0.679, targetFlowLpm: 1, servingTempC: 4, targetCo2Volumes: 2.5,
    material: 'vinil', internalDiameterMm: 4.8,
    calculationMethod: 'L = (P − ρ·g·h − P_residual) / (R × vazão/vazão_ref)', calculatorVersion: '1.0',
    feasible: true,
    warnings: [{ code: 'manual_adjustment_only', message: 'Ajuste manual.', safety: true }],
    ...overrides,
  };
}

function setup(api: Partial<GasApi>, toast = { success: vi.fn(), error: vi.fn() }) {
  TestBed.configureTestingModule({
    providers: [
      GasStore,
      { provide: GasApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(GasStore), toast };
}

describe('GasStore', () => {
  it('carrega conexões e cilindros, marcando vazio', () => {
    const { store } = setup({ connections: () => of([]), cylinders: () => of([]) });

    store.load();

    expect(store.empty()).toBe(true);
    expect(store.loading()).toBe(false);
  });

  it('só oferece cilindro apto para montar a linha', () => {
    const { store } = setup({
      connections: () => of([]),
      cylinders: () => of([
        cylinder(),
        cylinder({ id: 'c2', code: 'CIL-002', expired: true, allocatable: false }),
        cylinder({ id: 'c3', code: 'CIL-003', status: 'BLOCKED', allocatable: false, blockReason: 'avaria' }),
      ]),
    });

    store.load();

    expect(store.allocatableCylinders().map(c => c.id)).toEqual(['c1']);
    expect(store.expiredCylinders().map(c => c.id)).toEqual(['c2']);
  });

  it('só oferece componente ativo, separado por papel', () => {
    const { store } = setup({
      components: () => of([
        component(),
        component({ id: 'r2', code: 'REG-2', active: false }),
        component({ id: 'm1', kind: 'MANIFOLD', code: 'MAN-1', setPressureBar: null }),
      ]),
      equipment: () => of([]),
    });

    store.loadReferences();

    expect(store.regulators().map(c => c.id)).toEqual(['r1']);
    expect(store.manifolds().map(c => c.id)).toEqual(['m1']);
  });

  it('mostra todos os impedimentos da conexão recusada de uma vez', () => {
    const blockers = [
      { code: 'cylinder_expired' as const, message: 'A requalificação do cilindro venceu em 2026-07-31.' },
      { code: 'point_of_use_occupied' as const, message: 'O ponto de uso já tem um cilindro conectado.' },
    ];
    const { store, toast } = setup({
      connections: () => of([]),
      cylinders: () => of([]),
      connect: () => throwError(() => ({
        status: 409, error: { code: 'gas_connection_blocked', blockers },
      })),
    });

    store.connect({ cylinderId: 'c1', regulatorId: 'r1', manifoldId: null, pointOfUseEquipmentId: 'e1',
      workingPressureBar: 3 });

    expect(store.connectBlockers()).toEqual(blockers);
    // Impedimento é informação acionável na tela, não um toast que some.
    expect(toast.error).not.toHaveBeenCalled();
    expect(store.submitting()).toBe(false);
  });

  it('avisa a sobrepressão como erro, não como sucesso', () => {
    const { store, toast } = setup({
      connections: () => of([connection({ status: 'SERVING' })]),
      cylinders: () => of([cylinder()]),
      pressure: () => of({ readingId: 'p1', overPressure: true, status: 'BLOCKED' as const }),
    });

    store.pressure('x1', 7, null);

    expect(toast.error).toHaveBeenCalledWith('Sobrepressão: a leitura foi registrada e a linha foi bloqueada.');
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('confirma a leitura dentro do limite', () => {
    const { store, toast } = setup({
      connections: () => of([connection({ status: 'SERVING' })]),
      cylinders: () => of([cylinder()]),
      pressure: () => of({ readingId: 'p1', overPressure: false, status: 'SERVING' as const }),
    });

    store.pressure('x1', 3, 18);

    expect(toast.success).toHaveBeenCalledWith('Leitura registrada.');
  });

  it('explica que desbloquear não requalifica', () => {
    const { store, toast } = setup({
      cylinders: () => of([]),
      setCylinderBlock: () => of(undefined),
    });

    store.setBlock('c1', false, null);

    expect(toast.success).toHaveBeenCalledWith(
      'Cilindro desbloqueado (a requalificação vencida continua impedindo o uso).');
  });

  it('explica a recusa de consumo além do conteúdo', () => {
    const { store, toast } = setup({
      connections: () => of([]),
      cylinders: () => of([]),
      consumption: () => throwError(() => ({ status: 400 })),
    });

    store.consumption('x1', 99, null);

    expect(toast.error).toHaveBeenCalledWith('O consumo informado é maior que o conteúdo do cilindro.');
  });

  // --- linha de serviço (GAS-002) ---

  it('guarda a recomendação com os avisos de segurança', () => {
    const { store } = setup({ balance: () => of(lineBalance()) });

    store.balance('l1', { targetCo2Volumes: 2.5, servingTempC: 4, elevationMeters: 0.305,
      residualPressureBar: 0.069, targetFlowLpm: 1, resistanceId: 't1' });

    expect(store.lineBalance()?.recommendedLengthMeters).toBe(1.05);
    // Todo cálculo carrega o lembrete de que o sistema não ajusta a rede.
    expect(store.lineBalance()?.warnings.map(w => w.code)).toContain('manual_adjustment_only');
    expect(store.balancing()).toBe(false);
  });

  it('montagem impossível vem marcada como inviável', () => {
    const { store } = setup({
      balance: () => of(lineBalance({
        feasible: false, recommendedLengthMeters: 0,
        warnings: [
          { code: 'manual_adjustment_only', message: 'Ajuste manual.', safety: true },
          { code: 'no_balance_possible', message: 'A cerveja não flui.', safety: true },
        ],
      })),
    });

    store.balance('l1', { targetCo2Volumes: 2.5, servingTempC: 4, elevationMeters: 10,
      residualPressureBar: 0.069, targetFlowLpm: 1, resistanceId: 't1' });

    expect(store.lineBalance()?.feasible).toBe(false);
    expect(store.lineBalance()?.warnings.map(w => w.code)).toContain('no_balance_possible');
  });

  it('aplicar avisa que a revisão anterior foi preservada', () => {
    const { store, toast } = setup({
      applyRevision: () => of({ revision: 2, recommendedLengthMeters: 1.05, lengthDeviationMeters: 0.15 }),
      serviceLine: () => of({ line: serviceLine({ currentRevision: 2, everApplied: true }), revisions: [] }),
      serviceLines: () => of([]),
      tubing: () => of([]),
    });

    store.applyRevision('l1', { targetCo2Volumes: 2.5, servingTempC: 4, elevationMeters: 0.305,
      residualPressureBar: 0.069, targetFlowLpm: 1, resistanceId: 't1', appliedLengthMeters: 1.2, note: null });

    expect(toast.success).toHaveBeenCalledWith('Revisão 2 aplicada; a anterior foi preservada.');
  });

  it('calcular não aplica nada', () => {
    const applySpy = vi.fn();
    const { store } = setup({ balance: () => of(lineBalance()), applyRevision: applySpy });

    store.balance('l1', { targetCo2Volumes: 2.5, servingTempC: 4, elevationMeters: 0,
      residualPressureBar: 0.069, targetFlowLpm: 1, resistanceId: 't1' });

    // Calcular é recomendação: nenhuma montagem é registrada por conta disso.
    expect(applySpy).not.toHaveBeenCalled();
    expect(store.lineBalance()).not.toBeNull();
  });

  it('fechar a linha limpa a recomendação e o histórico', () => {
    const { store } = setup({
      serviceLine: () => of({ line: serviceLine(), revisions: [] }),
    });

    store.toggleLine('l1');
    expect(store.openLineOf()).toBe('l1');
    expect(store.lineDetail()).not.toBeNull();

    store.toggleLine('l1');
    expect(store.openLineOf()).toBeNull();
    expect(store.lineDetail()).toBeNull();
    expect(store.lineBalance()).toBeNull();
  });

  it('alterna o histórico da linha sem recarregar quando fecha', () => {
    const connectionSpy = vi.fn().mockReturnValue(of({
      connection: connection(), pressureReadings: [], consumption: [], consumedKg: 0,
    }));
    const { store } = setup({ connection: connectionSpy });

    store.toggleDetail('x1');
    expect(store.openDetailOf()).toBe('x1');
    expect(connectionSpy).toHaveBeenCalledTimes(1);

    store.toggleDetail('x1');
    expect(store.openDetailOf()).toBeNull();
    expect(store.detail()).toBeNull();
    expect(connectionSpy).toHaveBeenCalledTimes(1);
  });
});
