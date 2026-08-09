import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { Evidence, KnowledgeDocument } from '../domain/knowledge.model';
import { KnowledgeApi } from './knowledge.api';
import { KnowledgeStore } from './knowledge.store';

function doc(over: Partial<KnowledgeDocument> = {}): KnowledgeDocument {
  return {
    id: 'd1',
    type: 'SAFETY_DATA_SHEET',
    code: 'FISPQ-PERAC',
    title: 'FISPQ — Ácido peracético',
    version: 1,
    effectiveFrom: '2026-04-01',
    effectiveTo: null,
    current: true,
    requiredPermission: 'knowledge.document.read',
    equipmentId: null,
    sourceUri: null,
    chunks: 3,
    indexedAt: '2026-04-01T10:00:00Z',
    ...over,
  };
}

function hit(over: Partial<Evidence> = {}): Evidence {
  return {
    documentId: 'd1',
    code: 'FISPQ-PERAC',
    title: 'FISPQ — Ácido peracético',
    type: 'SAFETY_DATA_SHEET',
    version: 1,
    effectiveOnDate: true,
    ordinal: 0,
    text: 'A concentração recomendada é de 0,15% em volume.',
    score: 0.08,
    untrusted: true,
    ...over,
  };
}

function setup(api: Partial<KnowledgeApi> = {}): {
  store: KnowledgeStore;
  toast: { success: ReturnType<typeof vi.fn> };
} {
  const toast = { success: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      KnowledgeStore,
      {
        provide: KnowledgeApi,
        useValue: {
          documents: () => of([doc()]),
          index: () => of(doc()),
          search: () => of([hit()]),
          ...api,
        },
      },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(KnowledgeStore), toast };
}

describe('KnowledgeStore', () => {
  it('separa versões vigentes das substituídas: as duas continuam no acervo', () => {
    // Versão substituída não é apagada — ela responde sobre o período em que valeu, que é o que permite
    // investigar um lote antigo.
    const { store } = setup({
      documents: () =>
        of([
          doc({ id: 'v2', version: 2, current: true }),
          doc({ id: 'v1', version: 1, current: false, effectiveTo: '2026-05-31' }),
        ]),
    });

    store.load();

    expect(store.currentDocuments().map(d => d.version)).toEqual([2]);
    expect(store.supersededDocuments().map(d => d.version)).toEqual([1]);
    expect(store.documents()).toHaveLength(2);
  });

  it('antes da primeira busca não existe "não achei"', () => {
    // A distinção importa: "ainda não perguntei" não pode aparecer como "não há fonte para isto".
    const { store } = setup();

    store.load();

    expect(store.lastQuestion()).toBeNull();
    expect(store.searchedWithoutResult()).toBe(false);
  });

  it('busca sem resultado é resposta legítima, não erro', () => {
    const { store } = setup({ search: () => of([]) });

    store.search('criogenia supercondutora', null, null);

    expect(store.searchedWithoutResult()).toBe(true);
    expect(store.searchError()).toBeNull();
  });

  it('busca com resultado guarda os trechos e o marcador de não confiável', () => {
    const { store } = setup();

    store.search('concentração peracético', null, null);

    expect(store.hits()).toHaveLength(1);
    expect(store.hits()[0].untrusted).toBe(true);
    expect(store.searchedWithoutResult()).toBe(false);
  });

  it('a data de vigência viaja para o servidor: a pergunta sobre o passado é outra pergunta', () => {
    const search = vi.fn(() => of([hit()]));
    const { store } = setup({ search });

    store.search('concentração peracético', '2026-05-01', null);

    expect(search).toHaveBeenCalledWith('concentração peracético', '2026-05-01', null);
  });

  it('falha na busca limpa os trechos: resultado velho ao lado de um erro engana', () => {
    const { store } = setup({
      search: vi.fn().mockReturnValueOnce(of([hit()])).mockReturnValueOnce(throwError(() => ({ status: 500 }))),
    });
    store.search('primeira', null, null);
    expect(store.hits()).toHaveLength(1);

    store.search('segunda', null, null);

    expect(store.hits()).toEqual([]);
    expect(store.searchError()).not.toBeNull();
  });

  it('indexar recarrega o acervo e avisa que a versão anterior foi encerrada', () => {
    const documents = vi.fn(() => of([doc()]));
    const { store, toast } = setup({ documents, index: () => of(doc({ version: 2 })) });
    store.load();

    store.index({
      type: 'SAFETY_DATA_SHEET',
      code: 'FISPQ-PERAC',
      title: 'FISPQ v2',
      effectiveFrom: '2026-06-01',
      requiredPermission: 'knowledge.document.read',
      equipmentId: null,
      sourceUri: null,
      text: 'A concentração passou a ser de 0,20%.',
    });

    expect(toast.success).toHaveBeenCalledWith(
      'Versão 2 indexada; a anterior foi encerrada.',
    );
    expect(documents).toHaveBeenCalledTimes(2);
  });

  it('primeira versão não fala de substituição: não houve nenhuma', () => {
    const { store, toast } = setup({ index: () => of(doc({ version: 1 })) });
    store.load();

    store.index({
      type: 'LAB_REPORT',
      code: 'LAUDO-1',
      title: 'Laudo',
      effectiveFrom: '2026-04-01',
      requiredPermission: 'knowledge.document.read',
      equipmentId: null,
      sourceUri: null,
      text: 'Análise concluída.',
    });

    expect(toast.success).toHaveBeenCalledWith('Documento indexado.');
  });

  it('recusa de alçada é explicada como alçada, não como falha genérica', () => {
    const { store } = setup({ index: () => throwError(() => ({ status: 403 })) });

    store.index({
      type: 'LAB_REPORT',
      code: 'L-1',
      title: 'Laudo',
      effectiveFrom: '2026-04-01',
      requiredPermission: 'knowledge.document.read',
      equipmentId: null,
      sourceUri: null,
      text: 'texto',
    });

    expect(store.indexError()).toContain('alçada própria');
  });

  it('documento sem texto indexável é explicado como tal', () => {
    const { store } = setup({ index: () => throwError(() => ({ status: 400 })) });

    store.index({
      type: 'LAB_REPORT',
      code: 'L-1',
      title: 'Laudo',
      effectiveFrom: '2026-04-01',
      requiredPermission: 'knowledge.document.read',
      equipmentId: null,
      sourceUri: null,
      text: '   ',
    });

    expect(store.indexError()).toContain('sem texto indexável');
  });
});
