import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../../core/notifications/toast.service';
import { AlertsApi } from './alerts.api';
import { AlertsStore } from './alerts.store';

function setup(api: Partial<Record<keyof AlertsApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [AlertsStore, { provide: AlertsApi, useValue: api }, { provide: ToastService, useValue: toast }],
  });
  return { store: TestBed.inject(AlertsStore), toast };
}

const alert = (over: Partial<Record<string, unknown>> = {}) => ({
  id: 'a1', userId: 'u1', alertType: 'LOGIN_THROTTLE', severity: 'HIGH',
  status: 'OPEN', evidence: { attempts: 5 }, createdAt: 'x', ...over,
});

describe('AlertsStore', () => {
  it('carrega alertas e reflete vazio', () => {
    const { store } = setup({ list: vi.fn(() => of([])) });
    store.load();
    expect(store.empty()).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('filtra por estado repassando ao list', () => {
    const list = vi.fn(() => of([alert()]));
    const { store } = setup({ list });
    store.filterByStatus('OPEN');
    expect(store.statusFilter()).toBe('OPEN');
    expect(list).toHaveBeenCalledWith('OPEN');
    expect(store.alerts()).toHaveLength(1);
  });

  it('atualiza estado e recarrega', () => {
    const list = vi.fn(() => of([alert()]));
    const { store, toast } = setup({ list, updateStatus: vi.fn(() => of(undefined)) });
    store.load();
    store.updateStatus('a1', 'RESOLVED');
    expect(toast.success).toHaveBeenCalled();
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('marca actionError quando a atualização falha', () => {
    const { store } = setup({ list: vi.fn(() => of([])), updateStatus: vi.fn(() => throwError(() => ({ status: 400 }))) });
    store.updateStatus('a1', 'ACKNOWLEDGED');
    expect(store.actionError()).not.toBeNull();
  });

  it('serializa a evidência em pares', () => {
    const { store } = setup({ list: vi.fn(() => of([])) });
    expect(store.evidenceEntries(alert({ evidence: { ip: '1.2.3.4', attempts: 5 } }))).toEqual([
      { key: 'ip', value: '1.2.3.4' },
      { key: 'attempts', value: '5' },
    ]);
  });
});
