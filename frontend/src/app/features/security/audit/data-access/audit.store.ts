import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuditEvent, AuditFilter } from '../domain/audit-event.model';
import { AuditApi } from './audit.api';

const PAGE_SIZE = 25;
const EMPTY_FILTER: AuditFilter = { action: '', targetType: '', outcome: '', actorId: '', from: '', to: '' };

/** Estado do visualizador de auditoria com filtros e paginação server-side (SEC-B03). */
@Injectable()
export class AuditStore {
  private readonly api = inject(AuditApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly eventsState = signal<AuditEvent[]>([]);
  private readonly userNamesState = signal<Map<string, string>>(new Map());
  private readonly filterState = signal<AuditFilter>({ ...EMPTY_FILTER });

  readonly events = this.eventsState.asReadonly();
  readonly filter = this.filterState.asReadonly();
  readonly users = signal<{ id: string; displayName: string }[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly empty = computed(() => !this.loading() && !this.error() && this.eventsState().length === 0);

  actorName(actorId: string): string {
    return this.userNamesState().get(actorId) ?? actorId.slice(0, 8);
  }

  init(): void {
    this.load(0);
    this.api.listUsers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: users => {
          this.users.set(users);
          this.userNamesState.set(new Map(users.map(u => [u.id, u.displayName])));
        },
        error: () => undefined,
      });
  }

  load(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.search(this.filterState(), page, PAGE_SIZE)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.eventsState.set(result.content);
          this.page.set(result.page);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
        },
        error: () => this.error.set('Não foi possível carregar a auditoria.'),
      });
  }

  /** Aplica os filtros informados e volta para a primeira página. */
  applyFilter(patch: Partial<AuditFilter>): void {
    this.filterState.update(current => ({ ...current, ...patch }));
    this.load(0);
  }

  clearFilters(): void {
    this.filterState.set({ ...EMPTY_FILTER });
    this.load(0);
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }
}
