import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { RecipesApi } from '../../recipes/data-access/recipes.api';
import { UsersApi } from '../../security/users/data-access/users.api';
import { ToastService } from '../../../core/notifications/toast.service';
import { PlanningApi } from './planning.api';
import { PlanningStore } from './planning.store';

function emptyPage() {
  return of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
}

function setup(planning: Partial<PlanningApi>) {
  TestBed.configureTestingModule({
    providers: [
      PlanningStore,
      { provide: PlanningApi, useValue: planning },
      { provide: EquipmentApi, useValue: { list: vi.fn(emptyPage) } },
      { provide: RecipesApi, useValue: { list: vi.fn(emptyPage) } },
      { provide: UsersApi, useValue: { list: vi.fn(emptyPage) } },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(PlanningStore);
}

describe('PlanningStore', () => {
  it('carrega a agenda (vazia)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('simula e guarda o resultado de conflito', () => {
    const simulate = vi.fn(() => of({ hasConflict: true, conflicts: [] }));
    const store = setup({ simulate });
    store.simulate({ equipmentId: 'eq', scheduledStart: 's', scheduledEnd: 'e' });
    expect(simulate).toHaveBeenCalledOnce();
    expect(store.simulation()?.hasConflict).toBe(true);
  });

  it('cria uma entrada e recarrega a agenda', () => {
    const create = vi.fn(() => of({ id: 'x', status: 'PLANNED' }));
    const list = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ create, list });
    store.create({
      recipeId: 'r', equipmentId: 'eq', assignedUserId: 'u', plannedVolumeLiters: 40,
      scheduledStart: 's', scheduledEnd: 'e',
    }, onSuccess);
    expect(create).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    // load() foi chamado no sucesso (1ª vez aqui; nenhuma no setup).
    expect(list).toHaveBeenCalled();
  });
});
