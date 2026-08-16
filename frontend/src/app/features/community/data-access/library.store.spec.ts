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
          error: {
            code: 'unmapped_ingredients',
            detail: 'faltam ingredientes no seu catálogo: Lúpulo Citra',
            missing: ['Lúpulo Citra'],
          },
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

  it('mostra a mensagem do servidor quando a versão já está publicada', () => {
    const { store, toast } = setup({
      publish: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'version_already_published',
            detail: 'a versão 3 desta receita já está publicada',
          },
        })),
    } as Partial<LibraryApi>);

    store.publish('r1', 'IPA', null, 'CC0', 'PUBLIC');

    expect(toast.error).toHaveBeenCalledWith('a versão 3 desta receita já está publicada');
  });
});
