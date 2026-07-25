import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import {
  CreateFederationProvider,
  ExternalIdentity,
  FederationProvider,
  GroupOption,
  ScimMapping,
} from '../domain/federation.model';
import { FederationApi } from './federation.api';

/** Estado da administração de provedores de federação (SAML/OIDC). */
@Injectable()
export class FederationStore {
  private readonly api = inject(FederationApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly providersState = signal<FederationProvider[]>([]);
  private readonly selectedState = signal<FederationProvider | null>(null);
  private readonly identitiesState = signal<ExternalIdentity[]>([]);
  private readonly mappingsState = signal<ScimMapping[]>([]);
  private readonly groupsState = signal<GroupOption[]>([]);
  readonly providers = this.providersState.asReadonly();
  readonly selected = this.selectedState.asReadonly();
  readonly identities = this.identitiesState.asReadonly();
  readonly mappings = this.mappingsState.asReadonly();
  readonly groups = this.groupsState.asReadonly();
  readonly loadingIdentities = signal(false);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.providersState().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: providers => this.providersState.set(providers),
        error: () => this.error.set('Não foi possível carregar os provedores.'),
      });
  }

  create(body: CreateFederationProvider, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(body)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Provedor criado.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível criar o provedor (código duplicado ou dados inválidos).'),
      });
  }

  private readonly groupNames = computed(() => new Map(this.groupsState().map(g => [g.id, g.name])));

  groupName(id: string): string {
    return this.groupNames().get(id) ?? id.slice(0, 8);
  }

  /** Seleciona um provedor e carrega identidades vinculadas + mapeamentos SCIM. */
  selectProvider(provider: FederationProvider | null): void {
    this.selectedState.set(provider);
    this.identitiesState.set([]);
    this.mappingsState.set([]);
    if (!provider) {
      return;
    }
    this.loadingIdentities.set(true);
    this.api.listIdentities(provider.id)
      .pipe(finalize(() => this.loadingIdentities.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: identities => this.identitiesState.set(identities),
        error: () => this.actionError.set('Não foi possível carregar as identidades vinculadas.'),
      });
    this.loadMappings(provider.id);
    if (this.groupsState().length === 0) {
      this.api.listGroups()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: groups => this.groupsState.set(groups), error: () => undefined });
    }
  }

  private loadMappings(providerId: string): void {
    this.api.listScimMappings(providerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: mappings => this.mappingsState.set(mappings),
        error: () => this.actionError.set('Não foi possível carregar os mapeamentos SCIM.'),
      });
  }

  upsertMapping(externalGroupId: string, securityGroupId: string, onSuccess?: () => void): void {
    const provider = this.selectedState();
    if (!provider) {
      return;
    }
    this.actionError.set(null);
    this.api.upsertScimMapping(provider.id, externalGroupId, securityGroupId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Mapeamento SCIM salvo.');
          this.loadMappings(provider.id);
        },
        error: () => this.actionError.set('Não foi possível salvar o mapeamento SCIM.'),
      });
  }

  deactivateMapping(externalGroupId: string): void {
    const provider = this.selectedState();
    if (!provider) {
      return;
    }
    this.actionError.set(null);
    this.api.deactivateScimMapping(provider.id, externalGroupId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Mapeamento SCIM desativado.');
          this.loadMappings(provider.id);
        },
        error: () => this.actionError.set('Não foi possível desativar o mapeamento SCIM.'),
      });
  }

  validate(id: string): void {
    this.actionError.set(null);
    this.api.validate(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Metadata validada.');
          this.load();
        },
        error: () => this.actionError.set('Falha ao validar a metadata do provedor.'),
      });
  }
}
