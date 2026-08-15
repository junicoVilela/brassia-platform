import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { LibraryPublication, OwnedPublication } from '../domain/library.model';
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

function setup(api: Partial<LibraryApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  api.feed ??= () => of([published()]);
  api.mine ??= () => of([owned()]);
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
