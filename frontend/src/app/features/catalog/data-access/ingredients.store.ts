import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  CreateTechnicalProfileRequest,
  Ingredient,
  IngredientType,
  RegisterIngredientRequest,
  Substitutions,
  TechnicalProfile,
} from '../domain/ingredient.model';
import { IngredientsApi } from './ingredients.api';

/** Estado da tela de ingredientes: listagem filtrável por tipo e cadastro. */
@Injectable()
export class IngredientsStore {
  private readonly api = inject(IngredientsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly itemsState = signal<Ingredient[]>([]);
  private readonly typeFilterState = signal<IngredientType | null>(null);

  readonly items = this.itemsState.asReadonly();
  readonly typeFilter = this.typeFilterState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  private readonly selectedIdState = signal<string | null>(null);
  private readonly profileState = signal<TechnicalProfile | null>(null);
  readonly selectedId = this.selectedIdState.asReadonly();
  readonly profile = this.profileState.asReadonly();
  readonly loadingProfile = signal(false);
  readonly selected = computed(() => this.items().find(i => i.id === this.selectedIdState()) ?? null);
  readonly noProfile = computed(
    () => !!this.selectedIdState() && !this.loadingProfile() && this.profileState() === null,
  );

  private readonly substitutionsState = signal<Substitutions | null>(null);
  readonly substitutions = this.substitutionsState.asReadonly();
  readonly loadingSubstitutions = signal(false);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.typeFilterState() ?? undefined)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.itemsState.set(page.content),
        error: () => this.error.set('Não foi possível carregar os ingredientes.'),
      });
  }

  filterByType(type: IngredientType | null): void {
    this.typeFilterState.set(type);
    this.load();
  }

  create(request: RegisterIngredientRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Ingrediente cadastrado.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível cadastrar o ingrediente (código duplicado ou valor inválido).'),
      });
  }

  selectIngredient(ingredientId: string | null): void {
    this.selectedIdState.set(ingredientId);
    this.profileState.set(null);
    this.substitutionsState.set(null);
    this.actionError.set(null);
    if (!ingredientId) {
      return;
    }
    this.loadingProfile.set(true);
    this.api.getProfile(ingredientId)
      .pipe(finalize(() => this.loadingProfile.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: profile => this.profileState.set(profile),
        // 404 = ingrediente ainda sem perfil técnico (mostra o formulário de criação).
        error: (e: HttpErrorResponse) => {
          this.profileState.set(null);
          if (e.status !== 404) {
            this.error.set('Não foi possível carregar o perfil técnico.');
          }
        },
      });
  }

  createProfile(request: CreateTechnicalProfileRequest, onSuccess?: () => void): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.createProfile(id, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Perfil técnico criado.');
          this.selectIngredient(id);
        },
        error: () => this.actionError.set('Não foi possível criar o perfil (já existe ou dados inválidos).'),
      });
  }

  publishProfile(): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.actionError.set(null);
    this.api.publishProfile(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Perfil técnico publicado.');
          this.selectIngredient(id);
        },
        error: () => this.actionError.set('Não foi possível publicar o perfil técnico.'),
      });
  }

  loadSubstitutions(): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.loadingSubstitutions.set(true);
    this.substitutionsState.set(null);
    this.api.substitutions(id)
      .pipe(finalize(() => this.loadingSubstitutions.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => this.substitutionsState.set(result),
        error: () => this.actionError.set('Não foi possível buscar substitutos técnicos.'),
      });
  }
}
