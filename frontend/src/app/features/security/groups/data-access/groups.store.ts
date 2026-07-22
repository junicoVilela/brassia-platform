import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, finalize } from 'rxjs';
import { CreateGroupRequest, GroupSummary, PermissionSummary, UpdateGroupRequest } from '../domain/group.model';
import { GroupsApi } from './groups.api';

/** Estado da tela de grupos: listagem, catálogo de permissões, criar e editar. */
@Injectable()
export class GroupsStore {
  private readonly api = inject(GroupsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly groupsState = signal<GroupSummary[]>([]);
  private readonly permissionsState = signal<PermissionSummary[]>([]);
  private readonly selectedIdState = signal<string | null>(null);

  readonly groups = this.groupsState.asReadonly();
  readonly permissions = this.permissionsState.asReadonly();
  readonly selectedId = this.selectedIdState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.groups().length === 0);
  readonly selected = computed(() => this.groups().find(g => g.id === this.selectedId()) ?? null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      groups: this.api.listGroups(),
      permissions: this.api.listPermissions(),
    })
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ groups, permissions }) => {
          this.groupsState.set(groups);
          this.permissionsState.set(permissions.filter(p => p.active));
        },
        error: () => this.error.set('Não foi possível carregar grupos e permissões.'),
      });
  }

  select(groupId: string | null): void {
    this.selectedIdState.set(groupId);
    this.actionError.set(null);
  }

  create(request: CreateGroupRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: created => {
          onSuccess?.();
          this.load();
          this.select(created.id);
        },
        error: () => this.actionError.set('Não foi possível criar o grupo.'),
      });
  }

  update(groupId: string, request: UpdateGroupRequest): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.update(groupId, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.groupsState.update(items => items.map(g => (g.id === updated.id ? updated : g)));
        },
        error: () => this.actionError.set('Não foi possível atualizar o grupo.'),
      });
  }
}
