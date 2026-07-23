import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import {
  BlendResult,
  RegisterWaterProfileRequest,
  SimulateBlendRequest,
  WaterProfile,
} from '../domain/blend.model';
import { WaterSource } from '../domain/water.model';
import { WaterApi } from './water.api';
import { BlendApi } from './blend.api';

/** Estado da tela de mistura: fontes, perfis-alvo, criação de perfil e simulação. */
@Injectable()
export class BlendStore {
  private readonly waterApi = inject(WaterApi);
  private readonly api = inject(BlendApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly sourcesState = signal<WaterSource[]>([]);
  private readonly profilesState = signal<WaterProfile[]>([]);
  private readonly resultState = signal<BlendResult | null>(null);

  readonly sources = this.sourcesState.asReadonly();
  readonly profiles = this.profilesState.asReadonly();
  readonly result = this.resultState.asReadonly();
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);

  load(): void {
    this.waterApi.listSources()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.sourcesState.set(page.content),
        error: () => this.error.set('Não foi possível carregar as fontes.'),
      });
    this.loadProfiles();
  }

  private loadProfiles(): void {
    this.api.listProfiles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.profilesState.set(page.content),
        error: () => this.error.set('Não foi possível carregar os perfis-alvo.'),
      });
  }

  createProfile(request: RegisterWaterProfileRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.createProfile(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.loadProfiles();
        },
        error: () => this.actionError.set('Não foi possível criar o perfil (código duplicado ou íons inválidos).'),
      });
  }

  simulate(request: SimulateBlendRequest): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.resultState.set(null);
    this.api.simulate(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => this.resultState.set(result),
        error: () => this.actionError.set('Não foi possível simular (fonte sem laudo, volume inválido ou entradas vazias).'),
      });
  }
}
