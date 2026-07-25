import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { MfaApi } from './mfa.api';
import { MfaStore } from './mfa.store';

function setup(api: Partial<Record<keyof MfaApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      MfaStore,
      { provide: MfaApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(MfaStore), toast };
}

describe('MfaStore', () => {
  it('inicia o enroll e entra no passo de configuração', () => {
    const { store } = setup({ enroll: vi.fn(() => of({ secret: 'S', otpauthUri: 'otpauth://x' })) });

    store.startEnroll();

    expect(store.enrolling()).toBe(true);
    expect(store.enrollment()?.secret).toBe('S');
    expect(store.confirmed()).toBe(false);
  });

  it('confirma o código e marca MFA como ativado', () => {
    const { store, toast } = setup({
      enroll: vi.fn(() => of({ secret: 'S', otpauthUri: 'otpauth://x' })),
      confirm: vi.fn(() => of(undefined)),
    });

    store.startEnroll();
    store.confirm('123456');

    expect(store.confirmed()).toBe(true);
    expect(store.enrolling()).toBe(false);
    expect(toast.success).toHaveBeenCalledOnce();
  });

  it('mostra erro quando o código é inválido, sem ativar', () => {
    const { store } = setup({
      enroll: vi.fn(() => of({ secret: 'S', otpauthUri: 'otpauth://x' })),
      confirm: vi.fn(() => throwError(() => ({ status: 400 }))),
    });

    store.startEnroll();
    store.confirm('000000');

    expect(store.confirmed()).toBe(false);
    expect(store.error()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });

  it('expõe códigos de recuperação regenerados uma única vez', () => {
    const { store } = setup({ regenerateRecoveryCodes: vi.fn(() => of({ codes: ['a', 'b'] })) });

    store.regenerateRecoveryCodes();

    expect(store.recoveryCodes()).toEqual(['a', 'b']);
  });
});
