import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { GasComponent, GasConnection, GasCylinder } from '../domain/gas.model';
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
