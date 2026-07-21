import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { BrewerySummary, RegisterBreweryRequest } from '../domain/brewery.model';
import { BreweryApi } from './brewery.api';

/** Estado da tela de cervejarias: listagem e cadastro. */
@Injectable()
export class BreweryStore {
  private readonly api = inject(BreweryApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly itemsState = signal<BrewerySummary[]>([]);

  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
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
  }

  register(request: RegisterBreweryRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.register(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.load();
        },
        error: () => this.actionError.set('Não foi possível cadastrar a cervejaria.'),
      });
  }
}
