import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function service(): ToastService {
    TestBed.configureTestingModule({ providers: [ToastService] });
    return TestBed.inject(ToastService);
  }

  it('enfileira toast de sucesso com id único', () => {
    const toast = service();
    toast.success('Salvo.');
    toast.success('Publicado.');
    const list = toast.toasts();
    expect(list.map(t => t.text)).toEqual(['Salvo.', 'Publicado.']);
    expect(list.map(t => t.kind)).toEqual(['success', 'success']);
    expect(list[0].id).not.toBe(list[1].id);
  });

  it('dispensa manualmente por id', () => {
    const toast = service();
    toast.error('Falhou.');
    toast.dismiss(toast.toasts()[0].id);
    expect(toast.toasts()).toHaveLength(0);
  });

  it('auto-dispensa após o TTL', () => {
    const toast = service();
    toast.info('Aviso.');
    expect(toast.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(4000);
    expect(toast.toasts()).toHaveLength(0);
  });
});
