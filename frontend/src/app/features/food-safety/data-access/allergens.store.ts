import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, finalize, forkJoin, map, of } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { ProceduresApi } from '../../sanitation/data-access/procedures.api';
import { AllergenMatrix, IngredientAllergenRow } from '../domain/allergen.model';
import { AllergensApi } from './allergens.api';

interface AllergenError {
  status?: number;
  code?: string;
  detail?: string;
  allergen?: string;
}

/** Equipamento com a dedicação já resolvida — a tela precisa do nome, a API devolve o id. */
export interface EquipmentRow {
  id: string;
  code: string;
  name: string;
  dedicated: boolean;
  allergens: string[];
}

export interface ProcedureRow {
  code: string;
  name: string;
  allergens: string[];
}

/**
 * Estado da matriz de alergênicos (FDS-001).
 *
 * <p>Equipamento e POP vêm dos módulos donos (cadastro e sanitização) e são cruzados aqui com o
 * que a matriz declara. A matriz devolve só os equipamentos <em>dedicados</em>: quem não está lá é
 * compartilhado, que é o estado natural — e é justamente onde a troca de produto precisa ser
 * checada. Uma tela que só listasse os dedicados esconderia exatamente o caso de risco.
 */
@Injectable()
export class AllergensStore {
  private readonly api = inject(AllergensApi);
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly proceduresApi = inject(ProceduresApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly matrix = signal<AllergenMatrix | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** O que está gravando, para o botão certo ficar ocupado. */
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  private readonly equipment = signal<{ id: string; code: string; name: string }[]>([]);
  private readonly procedures = signal<{ code: string; name: string }[]>([]);

  readonly allergens = computed(() => this.matrix()?.allergens ?? []);

  /** Sem vocabulário a matriz não está em uso, e nada mais na tela faz sentido ainda. */
  readonly inUse = computed(() => this.allergens().length > 0);

  readonly ingredients = computed(() => this.matrix()?.ingredients ?? []);

  /** Ingredientes sem declaração: a lacuna que barra o rótulo e a troca de produto. */
  readonly undeclared = computed(() => this.ingredients().filter(row => !row.declared));

  readonly equipmentRows = computed<EquipmentRow[]>(() => {
    const dedications = new Map(
      (this.matrix()?.dedications ?? []).map(row => [row.equipmentId, row.allergens]),
    );
    return this.equipment().map(item => ({
      id: item.id,
      code: item.code,
      name: item.name,
      dedicated: dedications.has(item.id),
      allergens: dedications.get(item.id) ?? [],
    }));
  });

  readonly procedureRows = computed<ProcedureRow[]>(() => {
    const declared = new Map(
      (this.matrix()?.procedures ?? []).map(row => [row.procedureCode, row.allergens]),
    );
    return this.procedures().map(item => ({
      code: item.code,
      name: item.name,
      allergens: declared.get(item.code) ?? [],
    }));
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      matrix: this.api.matrix(),
      equipment: this.equipmentApi.list(0, 200).pipe(map(page => page.content)),
      // POPs são opcionais para a tela: sem permissão de sanitização, o eixo da limpeza some
      // em vez de derrubar a matriz inteira.
      procedures: this.proceduresApi.list().pipe(catchError(() => of([]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ matrix, equipment, procedures }) => {
          this.matrix.set(matrix);
          this.equipment.set(equipment);
          this.procedures.set(
            // Um POP publicado por código basta: a eficácia é do procedimento, não da versão.
            dedupeByCode(procedures.map(p => ({ code: p.code, name: p.name }))),
          );
        },
        error: () => this.error.set('Não foi possível carregar a matriz de alergênicos.'),
      });
  }

  registerAllergen(code: string, name: string): void {
    this.run('allergen', this.api.register(code, name));
  }

  declareIngredient(ingredientId: string, allergens: string[]): void {
    this.run(`ingredient:${ingredientId}`, this.api.declareIngredient(ingredientId, allergens));
  }

  dedicate(equipmentId: string, allergens: string[]): void {
    this.run(`equipment:${equipmentId}`, this.api.dedicate(equipmentId, allergens));
  }

  share(equipmentId: string): void {
    this.run(`equipment:${equipmentId}`, this.api.share(equipmentId));
  }

  declareProcedure(procedureCode: string, allergens: string[]): void {
    this.run(`procedure:${procedureCode}`, this.api.declareProcedure(procedureCode, allergens));
  }

  /** Alergênicos declarados de um ingrediente, para o formulário abrir com o que vale hoje. */
  allergensOf(row: IngredientAllergenRow): Set<string> {
    return new Set(row.allergens);
  }

  private run<T>(key: string, call: Observable<T>): void {
    this.saving.set(key);
    this.actionError.set(null);
    call.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null))).subscribe({
      next: () => {
        this.toast.success('Declaração registrada.');
        // A matriz é a soma de três eixos; recarregar é mais honesto do que remendar o estado.
        this.reload();
      },
      error: (e: AllergenError) => this.actionError.set(this.messageFor(e)),
    });
  }

  private reload(): void {
    this.api
      .matrix()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: matrix => this.matrix.set(matrix) });
  }

  private messageFor(e: AllergenError): string {
    if (e.code === 'unknown_allergen') {
      return `O alergênico ${e.allergen ?? ''} não está no cadastro da cervejaria.`.trim();
    }
    if (e.status === 409) {
      return 'Esse alergênico já está cadastrado.';
    }
    return e.detail ?? 'Não foi possível registrar a declaração.';
  }
}

function dedupeByCode<T extends { code: string }>(items: T[]): T[] {
  const byCode = new Map<string, T>();
  for (const item of items) {
    byCode.set(item.code, item);
  }
  return [...byCode.values()].sort((a, b) => a.code.localeCompare(b.code));
}
