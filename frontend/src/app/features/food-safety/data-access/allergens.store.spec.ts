import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { ProceduresApi } from '../../sanitation/data-access/procedures.api';
import { AllergenMatrix } from '../domain/allergen.model';
import { AllergensApi } from './allergens.api';
import { AllergensStore } from './allergens.store';

const GLUTEN = { id: 'a1', code: 'GLUTEN', name: 'Glúten' };

function matrix(over: Partial<AllergenMatrix> = {}): AllergenMatrix {
  return {
    allergens: [GLUTEN],
    ingredients: [
      { ingredientId: 'i1', code: 'M-1', name: 'Malte Pilsen', declared: true, allergens: ['GLUTEN'] },
      { ingredientId: 'i2', code: 'H-1', name: 'Lúpulo', declared: true, allergens: [] },
      { ingredientId: 'i3', code: 'Y-1', name: 'Levedura', declared: false, allergens: [] },
    ],
    dedications: [{ equipmentId: 'e2', allergens: [] }],
    procedures: [{ procedureCode: 'CIP-A', allergens: ['GLUTEN'] }],
    ...over,
  };
}

const EQUIPMENT = [
  { id: 'e1', code: 'EQ-1', name: 'Linha 1' },
  { id: 'e2', code: 'EQ-2', name: 'Linha 2' },
];

const PROCEDURES = [
  { id: 'p1', code: 'CIP-A', name: 'CIP alcalino', version: 1, status: 'PUBLISHED', steps: [] },
  { id: 'p2', code: 'CIP-A', name: 'CIP alcalino', version: 2, status: 'PUBLISHED', steps: [] },
  { id: 'p3', code: 'CIP-B', name: 'CIP ácido', version: 1, status: 'PUBLISHED', steps: [] },
];

function setup(api: Partial<AllergensApi> = {}, procedures = PROCEDURES): AllergensStore {
  TestBed.configureTestingModule({
    providers: [
      AllergensStore,
      { provide: AllergensApi, useValue: { matrix: () => of(matrix()), ...api } },
      { provide: EquipmentApi, useValue: { list: () => of({ content: EQUIPMENT }) } },
      { provide: ProceduresApi, useValue: { list: () => of(procedures) } },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(AllergensStore);
}

describe('AllergensStore', () => {
  it('sem vocabulário cadastrado, a matriz não está em uso', () => {
    const store = setup({ matrix: () => of(matrix({ allergens: [] })) });

    store.load();

    expect(store.inUse()).toBe(false);
  });

  it('separa o ingrediente sem declaração — que não é o mesmo que isento', () => {
    const store = setup();

    store.load();

    expect(store.undeclared().map(row => row.ingredientId)).toEqual(['i3']);
    // O lúpulo declarado isento também tem zero alergênicos, e não conta como lacuna.
    expect(store.ingredients().find(row => row.ingredientId === 'i2')?.declared).toBe(true);
  });

  it('mostra todo equipamento, e não só os dedicados: compartilhado é onde a troca importa', () => {
    const store = setup();

    store.load();

    expect(store.equipmentRows()).toEqual([
      { id: 'e1', code: 'EQ-1', name: 'Linha 1', dedicated: false, allergens: [] },
      { id: 'e2', code: 'EQ-2', name: 'Linha 2', dedicated: true, allergens: [] },
    ]);
  });

  it('agrupa POP por código: a eficácia é do procedimento, não da versão', () => {
    const store = setup();

    store.load();

    expect(store.procedureRows()).toEqual([
      { code: 'CIP-A', name: 'CIP alcalino', allergens: ['GLUTEN'] },
      { code: 'CIP-B', name: 'CIP ácido', allergens: [] },
    ]);
  });

  it('falha ao ler POPs não derruba a matriz — o eixo da limpeza some sozinho', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AllergensStore,
        { provide: AllergensApi, useValue: { matrix: () => of(matrix()) } },
        { provide: EquipmentApi, useValue: { list: () => of({ content: EQUIPMENT }) } },
        { provide: ProceduresApi, useValue: { list: () => throwError(() => ({ status: 403 })) } },
        { provide: ToastService, useValue: { success: vi.fn() } },
      ],
    });
    const store = TestBed.inject(AllergensStore);

    store.load();

    expect(store.error()).toBeNull();
    expect(store.procedureRows()).toEqual([]);
    expect(store.ingredients()).toHaveLength(3);
  });

  it('traduz a recusa de alergênico fora do vocabulário', () => {
    const store = setup({
      declareIngredient: () =>
        // O interceptor desembrulha o Problem Details: `code` chega no primeiro nível.
        throwError(() => ({ status: 400, code: 'unknown_allergen', allergen: 'SOJA' })),
    });
    store.load();

    store.declareIngredient('i1', ['SOJA']);

    expect(store.actionError()).toContain('SOJA');
  });

  it('recarrega a matriz depois de declarar', () => {
    const reload = vi.fn(() => of(matrix()));
    const store = setup({ matrix: reload, declareIngredient: () => of(undefined) });
    store.load();
    expect(reload).toHaveBeenCalledTimes(1);

    store.declareIngredient('i3', []);

    expect(reload).toHaveBeenCalledTimes(2);
    expect(store.saving()).toBeNull();
  });
});
