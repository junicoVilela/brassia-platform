import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { GatewayStatus } from '../domain/gateway.model';
import { AiApi } from './ai.api';
import { AiStore } from './ai.store';

function status(over: Partial<GatewayStatus> = {}): GatewayStatus {
  return {
    provider: 'anthropic',
    enabled: true,
    models: ['claude-opus-5', 'claude-sonnet-5'],
    timeoutSeconds: 30,
    budget: {
      monthlyLimit: 50,
      spentThisMonth: 10,
      remaining: 40,
      exhausted: false,
      currency: 'USD',
      version: 3,
    },
    recent: [],
    ...over,
  };
}

function setup(api: Partial<AiApi> = {}): { store: AiStore; toast: { success: ReturnType<typeof vi.fn> } } {
  const toast = { success: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      AiStore,
      {
        provide: AiApi,
        useValue: {
          status: () => of(status()),
          probe: () => of({ ready: true, note: 'consigo responder em JSON' }),
          redefineBudget: () => of(status().budget),
          ...api,
        },
      },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(AiStore), toast };
}

describe('AiStore', () => {
  it('separa o modelo preferido do fallback: fallback só existe se houver primeira escolha', () => {
    const { store } = setup();

    store.load();

    expect(store.primaryModel()).toBe('claude-opus-5');
    expect(store.fallbackModels()).toEqual(['claude-sonnet-5']);
  });

  it('provedor desligado não é erro: carrega normalmente e sem cadeia de modelos', () => {
    const { store } = setup({ status: () => of(status({ enabled: false, models: [] })) });

    store.load();

    expect(store.error()).toBeNull();
    expect(store.status()?.enabled).toBe(false);
    expect(store.primaryModel()).toBeNull();
  });

  it('calcula o consumo do teto sem estourar 100%', () => {
    const { store } = setup({
      status: () =>
        of(
          status({
            budget: {
              monthlyLimit: 10,
              spentThisMonth: 25,
              remaining: 0,
              exhausted: true,
              currency: 'USD',
              version: 1,
            },
          }),
        ),
    });

    store.load();

    // Baixar o teto abaixo do gasto é um freio legítimo; a barra fica cheia, não além.
    expect(store.spentPercent()).toBe(100);
  });

  it('teto zero não vira divisão por zero na barra', () => {
    const { store } = setup({
      status: () =>
        of(
          status({
            budget: {
              monthlyLimit: 0,
              spentThisMonth: 0,
              remaining: 0,
              exhausted: true,
              currency: 'USD',
              version: 1,
            },
          }),
        ),
    });

    store.load();

    expect(store.spentPercent()).toBe(0);
  });

  it('a verificação recarrega o estado: o gasto do mês mudou', () => {
    const statusSpy = vi.fn(() => of(status()));
    const { store, toast } = setup({ status: statusSpy });
    store.load();

    store.probe();

    expect(store.probeResult()?.ready).toBe(true);
    expect(toast.success).toHaveBeenCalled();
    // Uma vez no load, outra depois da verificação: o número que aparece é sempre o do ledger.
    expect(statusSpy).toHaveBeenCalledTimes(2);
  });

  it('verificação recusada também recarrega: a tentativa virou linha no ledger', () => {
    const statusSpy = vi.fn(() => of(status({ enabled: false, models: [] })));
    const { store } = setup({
      status: statusSpy,
      probe: () => throwError(() => ({ status: 501, code: 'ai_provider_disabled' })),
    });
    store.load();

    store.probe();

    expect(store.probeError()).toContain('não tem copiloto de IA habilitado');
    expect(statusSpy).toHaveBeenCalledTimes(2);
  });

  // Cada recusa tem a sua explicação, não uma mensagem genérica: as quatro causas de "a IA não
  // respondeu" pedem providências diferentes, e a mensagem é o que diz de quem é a providência.
  // Um caso por teste porque o TestBed não se reconfigura dentro do mesmo it.
  it.each([
    ['ai_provider_unavailable', 'não respondeu'],
    ['ai_budget_exceeded', 'esgotado'],
    ['ai_response_rejected', 'fora do formato'],
  ])('explica a recusa %s', (code, expected) => {
    const { store } = setup({ probe: () => throwError(() => ({ status: 502, code })) });
    store.load();

    store.probe();

    expect(store.probeError()).toContain(expected);
  });

  it('a alteração do teto envia a versão que foi lida', () => {
    const redefine = vi.fn(() => of(status().budget));
    const { store } = setup({ redefineBudget: redefine });
    store.load();

    store.redefineBudget(120);

    // É o que impede a alteração de uma pessoa de sobrescrever, sem aviso, a de outra.
    expect(redefine).toHaveBeenCalledWith(120, 3);
  });

  it('sem estado carregado não há o que alterar: não chama o backend às cegas', () => {
    const redefine = vi.fn(() => of(status().budget));
    const { store } = setup({ redefineBudget: redefine });

    store.redefineBudget(120);

    expect(redefine).not.toHaveBeenCalled();
  });

  it('conflito de versão recarrega para que a próxima tentativa saia com a versão certa', () => {
    const statusSpy = vi.fn(() => of(status()));
    const { store } = setup({
      status: statusSpy,
      redefineBudget: () => throwError(() => ({ status: 409, code: 'ai_budget_stale' })),
    });
    store.load();

    store.redefineBudget(120);

    expect(store.budgetError()).toContain('alterado por outra pessoa');
    expect(statusSpy).toHaveBeenCalledTimes(2);
  });
});
