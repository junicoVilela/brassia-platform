import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Genealogy, GenealogyQuery, LineageNode, NodeType } from '../domain/genealogy.model';
import { GenealogyApi } from './genealogy.api';
import { GenealogyStore } from './genealogy.store';

function node(type: NodeType, label: string): LineageNode {
  return { type, id: `${type}-${label}`, label };
}

const LOT = node('STOCK_LOT', 'Malte L-22');
const ORDER = node('BREW_ORDER', 'OP-100');
const BATCH = node('BATCH', 'LOTE-100');
const RUN = node('PACKAGING_RUN', 'ENV-100 — 780 un');

function graph(over: Partial<Genealogy> = {}): Genealogy {
  return {
    root: BATCH,
    direction: 'BOTH',
    depth: 6,
    truncated: false,
    nodes: [LOT, ORDER, BATCH, RUN],
    edges: [
      { from: LOT, to: ORDER, kind: 'reserva de insumo', strength: 'INTENDED', recordedAt: null },
      { from: ORDER, to: BATCH, kind: 'ordem executada', strength: 'CONFIRMED', recordedAt: null },
    ],
    gaps: [],
    ...over,
  };
}

const QUERY: GenealogyQuery = { nodeType: 'BATCH', nodeId: BATCH.id, direction: 'BOTH', depth: 6 };

function setup(api: Partial<GenealogyApi>): GenealogyStore {
  TestBed.configureTestingModule({
    providers: [GenealogyStore, { provide: GenealogyApi, useValue: api }],
  });
  return TestBed.inject(GenealogyStore);
}

describe('GenealogyStore', () => {
  it('organiza os nós em colunas na ordem da produção', () => {
    const store = setup({ genealogy: () => of(graph()) });

    store.load(QUERY);

    expect(store.columns().map(c => c.type)).toEqual([
      'STOCK_LOT',
      'BREW_ORDER',
      'BATCH',
      'PACKAGING_RUN',
    ]);
  });

  it('não cria coluna vazia para tipo ausente', () => {
    const store = setup({ genealogy: () => of(graph({ nodes: [BATCH] })) });

    store.load(QUERY);

    // Uma coluna "Levedura" vazia sugeriria lacuna onde não há; lacuna de verdade vem em gaps.
    expect(store.columns()).toHaveLength(1);
    expect(store.columns()[0].type).toBe('BATCH');
  });

  it('separa as arestas que são intenção', () => {
    const store = setup({ genealogy: () => of(graph()) });

    store.load(QUERY);

    expect(store.intendedEdges()).toHaveLength(1);
    expect(store.intendedEdges()[0].kind).toBe('reserva de insumo');
  });

  it('reconhece o nó isolado, que não é o mesmo que nó inexistente', () => {
    const store = setup({ genealogy: () => of(graph({ nodes: [BATCH], edges: [] })) });

    store.load(QUERY);

    expect(store.isolated()).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('traduz nó inexistente sem falar em erro de carga', () => {
    const store = setup({
      genealogy: () => throwError(() => ({ status: 404, code: 'unknown_node' })),
    });

    store.load(QUERY);

    expect(store.error()).toContain('não existe nesta cervejaria');
    expect(store.genealogy()).toBeNull();
    expect(store.loading()).toBe(false);
  });

  it('traduz profundidade excedida com o teto que o backend informou', () => {
    const store = setup({
      genealogy: () =>
        throwError(() => ({ status: 400, code: 'depth_exceeded', depth: { maximum: 10 } })),
    });

    store.load({ ...QUERY, depth: 50 });

    expect(store.error()).toContain('10 saltos');
  });

  it('mudar o sentido recarrega uma vez só', () => {
    const genealogy = vi.fn(() => of(graph()));
    const store = setup({ genealogy });
    store.load(QUERY);

    store.changeDirection('FORWARD');
    // Pedir o mesmo sentido de novo não gera requisição: o botão ativo não recarrega a tela.
    store.changeDirection('FORWARD');

    expect(genealogy).toHaveBeenCalledTimes(2);
    expect(genealogy).toHaveBeenLastCalledWith({ ...QUERY, direction: 'FORWARD' });
  });

  it('preserva o nó e o sentido ao ampliar o alcance', () => {
    const genealogy = vi.fn(() => of(graph({ truncated: true })));
    const store = setup({ genealogy });
    store.load({ ...QUERY, direction: 'FORWARD' });

    store.changeDepth(9);

    expect(genealogy).toHaveBeenLastCalledWith({ ...QUERY, direction: 'FORWARD', depth: 9 });
  });
});
