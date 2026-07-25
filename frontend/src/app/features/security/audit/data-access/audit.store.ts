import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuditEvent } from '../domain/audit-event.model';
import { AuditApi } from './audit.api';

/**
 * Estado do visualizador de auditoria. Os filtros (ação/recurso/ator/período)
 * são aplicados no cliente sobre os eventos recentes; filtro/paginação
 * server-side ficam como débito para grandes volumes.
 */
@Injectable()
export class AuditStore {
  private readonly api = inject(AuditApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly eventsState = signal<AuditEvent[]>([]);
  private readonly userNamesState = signal<Map<string, string>>(new Map());

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly term = signal('');
  readonly outcome = signal('');
  readonly from = signal('');
  readonly to = signal('');

  actorName(actorId: string): string {
    return this.userNamesState().get(actorId) ?? actorId.slice(0, 8);
  }

  readonly filtered = computed(() => {
    const term = this.term().trim().toLowerCase();
    const outcome = this.outcome();
    const fromMs = this.from() ? new Date(this.from()).getTime() : null;
    const toMs = this.to() ? new Date(this.to()).getTime() : null;
    const names = this.userNamesState();
    return this.eventsState().filter(event => {
      if (outcome && event.outcome !== outcome) {
        return false;
      }
      const occurred = new Date(event.occurredAt).getTime();
      if (fromMs !== null && occurred < fromMs) {
        return false;
      }
      if (toMs !== null && occurred > toMs) {
        return false;
      }
      if (term) {
        const actor = names.get(event.actorId) ?? event.actorId;
        const haystack = `${event.action} ${event.targetType} ${event.targetId} ${actor}`.toLowerCase();
        if (!haystack.includes(term)) {
          return false;
        }
      }
      return true;
    });
  });

  readonly empty = computed(() => !this.loading() && !this.error() && this.eventsState().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: events => this.eventsState.set(events),
        error: () => this.error.set('Não foi possível carregar a auditoria.'),
      });
    this.api.listUsers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: users => this.userNamesState.set(new Map(users.map(u => [u.id, u.displayName]))),
        error: () => undefined,
      });
  }

  clearFilters(): void {
    this.term.set('');
    this.outcome.set('');
    this.from.set('');
    this.to.set('');
  }
}
