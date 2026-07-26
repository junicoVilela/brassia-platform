import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { Equipment } from '../../equipment/domain/equipment.model';
import { RecipesApi } from '../../recipes/data-access/recipes.api';
import { RecipeSummary } from '../../recipes/domain/recipe.model';
import { UsersApi } from '../../security/users/data-access/users.api';
import { SecurityUserSummary } from '../../security/users/domain/user.model';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  CreateScheduleEntryRequest,
  ScheduleEntry,
  SimulateScheduleRequest,
  SimulateScheduleResult,
} from '../domain/schedule.model';
import { PlanningApi } from './planning.api';

/** Estado da agenda de produção: listagem por período, catálogos de apoio, simulação e criação. */
@Injectable()
export class PlanningStore {
  private readonly api = inject(PlanningApi);
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly recipesApi = inject(RecipesApi);
  private readonly usersApi = inject(UsersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly entriesState = signal<ScheduleEntry[]>([]);
  private readonly equipmentState = signal<Equipment[]>([]);
  private readonly recipesState = signal<RecipeSummary[]>([]);
  private readonly usersState = signal<SecurityUserSummary[]>([]);

  readonly entries = this.entriesState.asReadonly();
  readonly equipment = this.equipmentState.asReadonly();
  /** Apenas receitas publicadas podem ser agendadas. */
  readonly publishedRecipes = computed(() => this.recipesState().filter(r => r.status === 'PUBLISHED'));
  readonly users = this.usersState.asReadonly();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.entries().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly simulation = signal<SimulateScheduleResult | null>(null);
  readonly simulating = signal(false);

  private range = defaultRange();

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.range.from, this.range.to)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: entries => this.entriesState.set(entries),
        error: () => this.error.set('Não foi possível carregar a agenda de produção.'),
      });
    // Catálogos de apoio (best-effort: falha não bloqueia a agenda).
    this.equipmentApi.list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.equipmentState.set(page.content), error: () => {} });
    this.recipesApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.recipesState.set(page.content), error: () => {} });
    this.usersApi.list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.usersState.set(page.content), error: () => {} });
  }

  simulate(request: SimulateScheduleRequest): void {
    this.simulating.set(true);
    this.simulation.set(null);
    this.actionError.set(null);
    this.api.simulate(request)
      .pipe(finalize(() => this.simulating.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => this.simulation.set(result),
        error: () => this.actionError.set('Não foi possível simular (equipamento ou janela inválidos).'),
      });
  }

  create(request: CreateScheduleEntryRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.simulation.set(null);
          this.toast.success('Brassagem agendada.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Conflito de equipamento na janela selecionada.'
            : 'Não foi possível agendar (receita não publicada, equipamento inválido ou dados incorretos).'),
      });
  }

  recipeName(id: string): string {
    return this.recipesState().find(r => r.id === id)?.name ?? id;
  }

  equipmentName(id: string): string {
    return this.equipmentState().find(e => e.id === id)?.name ?? id;
  }

  userName(id: string): string {
    return this.usersState().find(u => u.id === id)?.displayName ?? id;
  }
}

/** Intervalo padrão da agenda: de ontem até 60 dias à frente. */
function defaultRange(): { from: string; to: string } {
  const from = new Date();
  from.setDate(from.getDate() - 1);
  const to = new Date();
  to.setDate(to.getDate() + 60);
  return { from: from.toISOString(), to: to.toISOString() };
}
