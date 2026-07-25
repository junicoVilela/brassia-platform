import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { PasswordApi } from './password.api';
import { PasswordStore } from './password.store';

function setup(api: Partial<Record<keyof PasswordApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      PasswordStore,
      { provide: PasswordApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(PasswordStore), toast };
}

describe('PasswordStore', () => {
  it('troca a senha e chama onSuccess', () => {
    const { store, toast } = setup({ change: vi.fn(() => of(undefined)) });
    const onSuccess = vi.fn();

    store.change('atual', 'novaSenha1', onSuccess);

    expect(onSuccess).toHaveBeenCalledOnce();
    expect(toast.success).toHaveBeenCalledOnce();
    expect(store.error()).toBeNull();
    expect(store.submitting()).toBe(false);
  });

  it('marca erro quando a troca falha', () => {
    const { store } = setup({ change: vi.fn(() => throwError(() => ({ status: 400 }))) });

    store.change('errada', 'novaSenha1');

    expect(store.error()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });
});
