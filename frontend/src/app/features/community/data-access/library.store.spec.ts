import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { LibraryPublication, OwnedPublication, ShareLink } from '../domain/library.model';
import { LibraryApi } from './library.api';
import { LibraryStore } from './library.store';

function owned(over: Partial<OwnedPublication> = {}): OwnedPublication {
  return {
    id: 'p1',
    title: 'IPA da Casa',
    recipeId: 'r1',
    recipeVersion: 3,
    license: 'CC_BY',
    visibility: 'PUBLIC',
    published: true,
    publishedAt: '2026-08-14T10:00:00Z',
    ...over,
  };
}

function published(): LibraryPublication {
  return {
    id: 'x1',
    title: 'Pilsen do Vizinho',
    summary: null,
    author: 'Bruno',
    license: 'CC0',
    licenseLabel: 'CC0 1.0',
    recipeVersion: 1,
    publishedAt: '2026-08-14T10:00:00Z',
    forkable: true,
    recipe: {
      name: 'Pilsen',
      style: null,
      batchVolumeLiters: 400,
      boilTimeMinutes: 60,
      targets: null,
      items: [
        {
          ingredientName: 'Malte Pilsen',
          stage: 'MASH',
          quantity: 20,
          unit: 'KG',
          timingMinutes: null,
          percentage: null,
        },
      ],
    },
  };
}

function link(over: Partial<ShareLink> = {}): ShareLink {
  return {
    id: 'l1',
    label: 'pro Bruno avaliar',
    permission: 'READ',
    createdAt: '2026-08-15T10:00:00Z',
    expiresAt: null,
    revokedAt: null,
    usable: true,
    ...over,
  };
}

function setup(api: Partial<LibraryApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  api.feed ??= () => of([published()]);
  api.mine ??= () => of([owned()]);
  api.links ??= () => of([link()]);
  // Abrir uma publicação carrega a conversa; sem o stub, os testes de fork quebrariam por um motivo
  // que nada tem a ver com fork.
  api.contributions ??= () => of([]);
  // Abrir também carrega a nota. Sem votos ela é NULA, e não zero — o padrão do stub é o mesmo estado
  // que o servidor devolve para uma publicação recém-criada.
  api.rating ??= () => of({ average: null, count: 0, meaningful: false, myRating: null });
  TestBed.configureTestingModule({
    providers: [
      LibraryStore,
      { provide: LibraryApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(LibraryStore), toast };
}

describe('LibraryStore', () => {
  it('separa o que está no ar do que saiu de circulação', () => {
    // Fora de circulação não é excluída: continua na estante, e a tela mostra as duas coisas.
    const { store } = setup({
      mine: () => of([owned(), owned({ id: 'p2', published: false })]),
    } as Partial<LibraryApi>);

    store.load();

    expect(store.live()).toHaveLength(1);
    expect(store.retired()).toHaveLength(1);
  });

  it('o retrato da comunidade traz ingrediente pelo nome', () => {
    // O identificador é a chave do catálogo da cervejaria — ele não sai, e o modelo do cliente nem
    // tem campo para ele.
    const { store } = setup({});

    store.load();

    const item = store.feed()[0].recipe.items[0];
    expect(item.ingredientName).toBe('Malte Pilsen');
    expect(Object.keys(item)).not.toContain('ingredientId');
  });

  it('relê tudo depois de mudar a visibilidade', () => {
    // A vitrine depende da visibilidade: uma lista em cache mostraria como pública uma receita que o
    // autor acabou de fechar. Num módulo cujo assunto é o que sai de casa, cache errado é vazamento
    // na tela.
    const feed = vi.fn().mockReturnValue(of([published()]));
    const mine = vi.fn().mockReturnValue(of([owned()]));
    const changeVisibility = vi.fn().mockReturnValue(of(void 0));
    const { store } = setup({ feed, mine, changeVisibility } as Partial<LibraryApi>);
    store.load();
    feed.mockClear();

    store.changeVisibility(owned(), 'BREWERY');

    expect(changeVisibility).toHaveBeenCalledWith('p1', 'BREWERY');
    expect(feed).toHaveBeenCalled();
  });

  it('retirar diz "fora de circulação", e não "excluída"', () => {
    const { store, toast } = setup({ unpublish: () => of(void 0) } as Partial<LibraryApi>);

    store.unpublish(owned());

    expect(toast.success).toHaveBeenCalledWith('Publicação fora de circulação.');
  });

  it('o token recém-criado fica só em memória, e some ao fechar', () => {
    // Guardá-lo em storage, na URL ou no histórico o transformaria num segredo persistido — que é
    // justamente o que o servidor evitou ao guardar só o hash.
    const { store } = setup({
      createLink: () => of({ id: 'l9', token: 'tok-secreto' }),
    } as Partial<LibraryApi>);
    store.openLinks(owned());

    store.createLink('READ', 'pro Bruno', null);
    expect(store.freshToken()).toBe('tok-secreto');

    store.closeLinks();
    expect(store.freshToken()).toBeNull();
    expect(store.links()).toEqual([]);
  });

  it('abrir os links de outra publicação limpa o token anterior', () => {
    // Sem isso, o token de um link ficaria visível na tela de outra publicação — e alguém o copiaria
    // achando que pertence a esta.
    const { store } = setup({
      createLink: () => of({ id: 'l9', token: 'tok-secreto' }),
    } as Partial<LibraryApi>);
    store.openLinks(owned());
    store.createLink('READ', null, null);
    expect(store.freshToken()).toBe('tok-secreto');

    store.openLinks(owned({ id: 'p2' }));

    expect(store.freshToken()).toBeNull();
  });

  it('recarrega os links depois de revogar', () => {
    // O estado de cada link é o que torna a revogação uma decisão informada.
    const links = vi.fn().mockReturnValue(of([link({ revokedAt: '2026-08-15T12:00:00Z' })]));
    const revokeLink = vi.fn().mockReturnValue(of(void 0));
    const { store, toast } = setup({ links, revokeLink } as Partial<LibraryApi>);
    store.openLinks(owned());
    links.mockClear();

    store.revokeLink(link());

    expect(revokeLink).toHaveBeenCalledWith('l1');
    expect(links).toHaveBeenCalledWith('p1');
    expect(toast.success).toHaveBeenCalledWith('Link revogado.');
  });

  it('não cria link sem publicação aberta', () => {
    const createLink = vi.fn();
    const { store } = setup({ createLink } as Partial<LibraryApi>);

    store.createLink('READ', null, null);

    expect(createLink).not.toHaveBeenCalled();
  });

  it('guarda a atribuição e a obrigação de licença do fork', () => {
    // A obrigação vem na resposta para o forkador não descobri-la só na hora de publicar.
    const { store, toast } = setup({
      fork: () =>
        of({
          recipeId: 'r9',
          attribution: 'Pilsen do Vizinho, de Bruno (CC BY-SA 4.0)',
          sourceLicense: 'CC_BY_SA' as const,
          requiredLicense: 'CC_BY_SA' as const,
        }),
    } as Partial<LibraryApi>);

    store.fork(published(), 'eq1', null);

    expect(store.lastFork()?.requiredLicense).toBe('CC_BY_SA');
    expect(toast.success).toHaveBeenCalledWith('Receita copiada para a sua cervejaria.');
  });

  it('a lista de ingredientes faltantes fica na tela, e não só no toast', () => {
    // O operador precisa dela na mão para cadastrar os ingredientes, e um toast some antes disso.
    const { store } = setup({
      fork: () =>
        throwError(() => ({
          status: 409,
          code: 'unmapped_ingredients',
          detail: 'faltam ingredientes no seu catálogo: Lúpulo Citra',
          missing: ['Lúpulo Citra'],
        })),
    } as Partial<LibraryApi>);

    store.fork(published(), 'eq1', null);

    expect(store.missingIngredients()).toEqual(['Lúpulo Citra']);
    expect(store.lastFork()).toBeNull();
  });

  it('um fork novo limpa o resultado do anterior', () => {
    // Sem isso, a atribuição de uma receita ficaria visível sob o formulário de outra.
    const { store } = setup({
      fork: () =>
        of({
          recipeId: 'r9',
          attribution: 'X, de Y (CC0 1.0)',
          sourceLicense: 'CC0' as const,
          requiredLicense: null,
        }),
    } as Partial<LibraryApi>);
    store.fork(published(), 'eq1', null);
    expect(store.lastFork()).not.toBeNull();

    store.open(published());
    store.fork(published(), 'eq1', null);

    expect(store.missingIngredients()).toEqual([]);
  });

  it('conta só as sugestões pendentes, e não os comentários', () => {
    // Contar comentários faria "3 pendentes" incluir elogios — e o autor abriria a tela achando que
    // tem decisão a tomar.
    const { store } = setup({
      contributions: () =>
        of([
          {
            id: 'c1',
            kind: 'SUGGESTION' as const,
            author: 'Bruno',
            body: 'Mais lúpulo',
            context: null,
            status: 'OPEN' as const,
            createdAt: '2026-08-15T10:00:00Z',
            decidedAt: null,
            decisionNote: null,
            pending: true,
          },
          {
            id: 'c2',
            kind: 'COMMENT' as const,
            author: 'Carla',
            body: 'Ficou ótima!',
            context: null,
            status: 'OPEN' as const,
            createdAt: '2026-08-15T11:00:00Z',
            decidedAt: null,
            decisionNote: null,
            pending: false,
          },
        ]),
    } as Partial<LibraryApi>);

    store.open(published());

    expect(store.contributions()).toHaveLength(2);
    expect(store.pendingCount()).toBe(1);
  });

  it('aceitar diz "concordância registrada", e não "aplicada"', () => {
    // Aplicar é ato do autor, na receita dele. Dizer o contrário prometeria uma mudança que não
    // aconteceu.
    const decide = vi.fn().mockReturnValue(of(void 0));
    const { store, toast } = setup({ decide } as Partial<LibraryApi>);

    store.decide(published(), {
      id: 'c1',
      kind: 'SUGGESTION',
      author: 'Bruno',
      body: 'Mais lúpulo',
      context: null,
      status: 'OPEN',
      createdAt: '2026-08-15T10:00:00Z',
      decidedAt: null,
      decisionNote: null,
      pending: true,
    }, true, 'boa ideia');

    expect(decide).toHaveBeenCalledWith('c1', true, 'boa ideia');
    expect(toast.success).toHaveBeenCalledWith('Concordância registrada.');
  });

  it('mostra a mensagem do servidor quando a versão já está publicada', () => {
    const { store, toast } = setup({
      publish: () =>
        throwError(() => ({
          status: 409,
          code: 'version_already_published',
          detail: 'a versão 3 desta receita já está publicada',
        })),
    } as Partial<LibraryApi>);

    store.publish('r1', 'IPA', null, 'CC0', 'PUBLIC');

    expect(toast.error).toHaveBeenCalledWith('a versão 3 desta receita já está publicada');
  });

  it('mostra ninguém-votou como ausência de nota, e não como zero', () => {
    // Zero é a pior nota possível: exibi-lo faria uma receita recém-publicada nascer parecendo péssima.
    const { store } = setup({} as Partial<LibraryApi>);

    store.open(published());

    expect(store.rating()?.average).toBeNull();
    expect(store.rating()?.count).toBe(0);
  });

  it('a média vem com a contagem, e poucos votos não são reputação', () => {
    // "5,0" de uma avaliação e "5,0" de duzentas são o mesmo número e significam coisas opostas.
    const { store } = setup({
      rating: () => of({ average: 5, count: 1, meaningful: false, myRating: null }),
    } as Partial<LibraryApi>);

    store.open(published());

    expect(store.rating()?.average).toBe(5);
    expect(store.rating()?.meaningful).toBe(false);
  });

  it('avaliar relê a nota, porque a média mudou', () => {
    // Sem reler, a tela mostraria a média de antes do voto que a pessoa acabou de dar.
    const rating = vi
      .fn()
      .mockReturnValueOnce(of({ average: null, count: 0, meaningful: false, myRating: null }))
      .mockReturnValueOnce(of({ average: 4, count: 1, meaningful: false, myRating: 4 }));
    const { store, toast } = setup({ rating, rate: () => of(undefined) } as Partial<LibraryApi>);

    store.open(published());
    store.rate(published(), 4);

    expect(store.rating()?.myRating).toBe(4);
    expect(toast.success).toHaveBeenCalled();
  });

  it('denunciar diz "registrada", e nunca "removida"', () => {
    // Denunciar abre um caso e não tira nada do ar. Prometer remoção aqui faria a tela mentir sobre o
    // que aconteceu — e uma denúncia que derrubasse o conteúdo seria uma arma.
    const { store, toast } = setup({
      report: () => of({ id: 'r1' }),
    } as Partial<LibraryApi>);

    store.report(published(), 'SPAM', null);

    expect(toast.success).toHaveBeenCalledWith('Denúncia registrada.');
  });

  it('a decisão do autor é anunciada como registro, e nunca como remoção', () => {
    // Julgar procedente não tira nada do ar: dizer o contrário faria a tela prometer uma remoção que não
    // houve — a ação sobre o conteúdo é ato separado (DUV-COM-001).
    const { store, toast } = setup({
      reviewReport: () => of(undefined),
      reports: () => of([]),
    } as Partial<LibraryApi>);

    store.reviewReport(owned(), 'r1', 'UPHELD', null);

    expect(toast.success).toHaveBeenCalledWith(
      'Decisão registrada. A publicação continua no ar até você despublicá-la.',
    );
  });
});
