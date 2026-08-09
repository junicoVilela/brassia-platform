import { TestBed } from '@angular/core/testing';
import { OfflineRunbook, OfflineRunbookStore } from './offline-runbook.store';

/**
 * O roteiro guardado para uso sem rede (PWA-001).
 *
 * <p>O que estes testes fixam é o critério da história: **"dados sensíveis seguem protegidos"**. Um tablet
 * de chão de fábrica troca de mão a cada turno, e um roteiro salvo é dado da cervejaria no disco de um
 * aparelho que se perde. As três defesas — dono, cervejaria e validade — são testadas uma a uma, e em
 * todas a resposta é **apagar**, não esconder.
 */
describe('OfflineRunbookStore', () => {
  const ANA = 'user-ana';
  const BRUNO = 'user-bruno';
  const CERVEJARIA_A = 'brewery-a';
  const CERVEJARIA_B = 'brewery-b';
  const AGORA = new Date('2026-08-09T10:00:00Z');

  let store: OfflineRunbookStore;

  const runbook: OfflineRunbook = {
    batchId: 'b1',
    code: 'LOTE-001',
    recipeName: 'IPA da casa',
    recipeVersion: 3,
    volumeLiters: 40,
    status: 'IN_PROGRESS',
    startedAt: '2026-08-09T08:00:00Z',
    steps: [
      { id: 's1', sequence: 1, type: 'MASH', label: 'Mostura', status: 'DONE', completedAt: '2026-08-09T09:00:00Z' },
      { id: 's2', sequence: 2, type: 'BOIL', label: 'Fervura', status: 'ACTIVE', completedAt: null },
    ],
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(OfflineRunbookStore);
  });

  afterEach(() => localStorage.clear());

  it('salva e lê o roteiro do mesmo dono, mesma cervejaria, dentro do prazo', () => {
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    const lido = store.read(ANA, CERVEJARIA_A, 'b1', AGORA);

    expect(lido?.code).toBe('LOTE-001');
    expect(lido?.steps.length).toBe(2);
    expect(store.isAvailable('b1')).toBe(true);
  });

  it('OUTRO USUÁRIO não lê, e o registro é APAGADO na leitura', () => {
    // O tablet que troca de turno. Esconder não bastaria: o dado continuaria no disco.
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    expect(store.read(BRUNO, CERVEJARIA_A, 'b1', AGORA)).toBeNull();
    expect(localStorage.getItem('brassia.offline.runbook.b1')).toBeNull();
    // E nem a dona consegue depois: o registro não existe mais.
    expect(store.read(ANA, CERVEJARIA_A, 'b1', AGORA)).toBeNull();
  });

  it('OUTRA CERVEJARIA não lê, mesmo sendo o mesmo usuário, e apaga', () => {
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    expect(store.read(ANA, CERVEJARIA_B, 'b1', AGORA)).toBeNull();
    expect(localStorage.getItem('brassia.offline.runbook.b1')).toBeNull();
  });

  it('roteiro VENCIDO não é exibido, e é apagado', () => {
    // Um roteiro desatualizado apresentado como atual é pior que nenhum: leva alguém a executar a etapa
    // errada com confiança.
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    const treizeHorasDepois = new Date(AGORA.getTime() + 13 * 3600 * 1000);
    expect(store.read(ANA, CERVEJARIA_A, 'b1', treizeHorasDepois)).toBeNull();
    expect(localStorage.getItem('brassia.offline.runbook.b1')).toBeNull();
  });

  it('dentro do prazo de doze horas ainda vale', () => {
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    const onzeHorasDepois = new Date(AGORA.getTime() + 11 * 3600 * 1000);
    expect(store.read(ANA, CERVEJARIA_A, 'b1', onzeHorasDepois)).not.toBeNull();
  });

  it('conteúdo corrompido é apagado em vez de exibido', () => {
    // Não há como saber de quem ele é, e o que não se sabe de quem é não se mostra.
    localStorage.setItem('brassia.offline.runbook.b1', 'não é json');

    expect(store.read(ANA, CERVEJARIA_A, 'b1', AGORA)).toBeNull();
    expect(localStorage.getItem('brassia.offline.runbook.b1')).toBeNull();
  });

  it('clearAll apaga tudo do disco — é o que o logout precisa fazer', () => {
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);
    store.save(ANA, CERVEJARIA_A, { ...runbook, batchId: 'b2', code: 'LOTE-002' }, AGORA);
    expect(store.count()).toBe(2);

    store.clearAll();

    expect(store.count()).toBe(0);
    expect(localStorage.getItem('brassia.offline.runbook.b1')).toBeNull();
    expect(localStorage.getItem('brassia.offline.runbook.b2')).toBeNull();
  });

  it('clearAll não mexe no que não é nosso', () => {
    localStorage.setItem('outra-coisa', 'preservar');
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    store.clearAll();

    expect(localStorage.getItem('outra-coisa')).toBe('preservar');
  });

  it('o que é gravado contém apenas o roteiro — nem custo, nem fornecedor, nem pessoa', () => {
    // O critério não é "a tela não mostra": é que o dado não está no disco para ser encontrado.
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    const gravado = localStorage.getItem('brassia.offline.runbook.b1') ?? '';
    const proibidos = ['custo', 'cost', 'preco', 'price', 'fornecedor', 'supplier', 'cpf', 'email'];

    for (const termo of proibidos) {
      expect(gravado.toLowerCase()).not.toContain(termo);
    }
  });

  it('o índice do que está disponível acompanha o que foi salvo e descartado', () => {
    expect(store.isAvailable('b1')).toBe(false);

    store.save(ANA, CERVEJARIA_A, runbook, AGORA);
    expect(store.available()).toContain('b1');

    store.discard('b1');
    expect(store.available()).not.toContain('b1');
  });

  it('guarda quando o roteiro foi capturado, para a tela poder dizer', () => {
    // Quem lê sem rede precisa saber que está olhando um retrato, e de quando ele é.
    store.save(ANA, CERVEJARIA_A, runbook, AGORA);

    expect(store.savedAt('b1')?.toISOString()).toBe(AGORA.toISOString());
    expect(store.savedAt('inexistente')).toBeNull();
  });

  it('ler um lote nunca salvo devolve nulo sem estourar', () => {
    expect(store.read(ANA, CERVEJARIA_A, 'nao-existe', AGORA)).toBeNull();
  });
});
