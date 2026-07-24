import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import {
  BrewerySummary,
  OperationalPreferences,
  RegisterBreweryRequest,
  UpdatePreferencesRequest,
} from '../domain/brewery.model';
import { ToastService } from '../../../core/notifications/toast.service';
import { BreweryApi } from './brewery.api';

/** Estado da tela de cervejarias: listagem, cadastro e preferências da ativa. */
@Injectable()
export class BreweryStore {
  private readonly api = inject(BreweryApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly itemsState = signal<BrewerySummary[]>([]);
  private readonly preferencesState = signal<OperationalPreferences | null>(null);

  readonly items = this.itemsState.asReadonly();
  readonly preferences = this.preferencesState.asReadonly();
  readonly loading = signal(false);
  readonly preferencesLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly preferencesError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.itemsState.set(page.content),
        error: () => this.error.set('Não foi possível carregar as cervejarias.'),
      });
    this.loadPreferences();
  }

  loadPreferences(): void {
    this.preferencesLoading.set(true);
    this.preferencesError.set(null);
    this.api.getPreferences()
      .pipe(finalize(() => this.preferencesLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: prefs => this.preferencesState.set(prefs),
        error: () => this.preferencesError.set('Não foi possível carregar as preferências.'),
      });
  }

  register(request: RegisterBreweryRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.register(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Cervejaria cadastrada.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível cadastrar a cervejaria.'),
      });
  }

  savePreferences(request: UpdatePreferencesRequest): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.updatePreferences(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: prefs => {
          this.preferencesState.set(prefs);
          this.toast.success('Preferências salvas.');
        },
        error: () => this.actionError.set('Não foi possível salvar as preferências (conflito ou valor inválido).'),
      });
  }
}
