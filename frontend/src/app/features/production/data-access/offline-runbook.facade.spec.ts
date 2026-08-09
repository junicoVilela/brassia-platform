import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../core/auth/auth.service';
import { Batch } from '../domain/batch.model';
import { OfflineRunbookFacade } from './offline-runbook.facade';

/**
 * A ponte entre a tela de lotes e o roteiro offline (PWA-001).
 *
 * <p>O teste central aqui é o da conversão explícita: um campo novo em `Batch` não pode escorregar para o
 * disco só porque a API passou a devolvê-lo.
 */
describe('OfflineRunbookFacade', () => {
  let facade: OfflineRunbookFacade;

  const batch: Batch = {
    id: 'b1',
    orderId: 'o1',
    code: 'LOTE-001',
    recipeId: 'r1',
    recipeVersion: 3,
    recipeName: 'IPA da casa',
    volumeLiters: 40,
    status: 'IN_PROGRESS',
    startedAt: '2026-08-09T08:00:00Z',
    steps: [
      { id: 's1', sequence: 1, type: 'MASH', label: 'Mostura', status: 'DONE', startedAt: null, completedAt: null },
    ],
  };

  function login(userId: string | null, breweryId: string | null): void {
    const auth = TestBed.inject(AuthService);
    const user = userId
      ? {
          userId,
          displayName: 'Tester',
          permissions: [],
          accessibleBreweries: [],
          activeBrewery: breweryId ? { id: breweryId, code: 'CV', name: 'Cervejaria' } : null,
        }
      : null;
    // O signal é privado; o teste ajusta o estado pelo mesmo caminho que o login usa.
    (auth as unknown as { userState: { set: (v: unknown) => void } }).userState.set(user);
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    facade = TestBed.inject(OfflineRunbookFacade);
  });

  afterEach(() => localStorage.clear());

  it('salva e lê o roteiro do usuário logado', () => {
    login('ana', 'cervejaria-a');

    expect(facade.save(batch)).toBe(true);
    expect(facade.read('b1')?.code).toBe('LOTE-001');
    expect(facade.isAvailable('b1')).toBe(true);
  });

  it('sem cervejaria ativa não salva: não haveria como carimbar de quem é', () => {
    login('ana', null);

    expect(facade.save(batch)).toBe(false);
    expect(facade.isAvailable('b1')).toBe(false);
  });

  it('sem sessão não lê nada', () => {
    login('ana', 'cervejaria-a');
    facade.save(batch);

    login(null, null);
    expect(facade.read('b1')).toBeNull();
  });

  it('só os campos do roteiro vão para o disco', () => {
    // `orderId` e `recipeId` existem em Batch e NÃO são gravados: não servem para executar a etapa, e o
    // que não é gravado não vaza de um aparelho perdido.
    login('ana', 'cervejaria-a');
    facade.save(batch);

    const gravado = localStorage.getItem('brassia.offline.runbook.b1') ?? '';

    expect(gravado).toContain('LOTE-001');
    expect(gravado).not.toContain('"orderId"');
    expect(gravado).not.toContain('"recipeId"');
  });

  it('trocar de usuário no mesmo aparelho não expõe o roteiro anterior', () => {
    login('ana', 'cervejaria-a');
    facade.save(batch);

    login('bruno', 'cervejaria-a');
    expect(facade.read('b1')).toBeNull();
  });

  it('descartar remove do disco', () => {
    login('ana', 'cervejaria-a');
    facade.save(batch);

    facade.discard('b1');

    expect(facade.isAvailable('b1')).toBe(false);
    expect(localStorage.getItem('brassia.offline.runbook.b1')).toBeNull();
  });
});
