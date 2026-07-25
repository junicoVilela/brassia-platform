import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import { AlertStatusUpdate, SecurityAlert } from '../domain/alert.model';
import { AlertsApi } from './alerts.api';

/** Estado dos alertas de segurança: listagem filtrável por estado e tratamento. */
@Injectable()
export class AlertsStore {
  private readonly api = inject(AlertsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly alertsState = signal<SecurityAlert[]>([]);
  private readonly statusFilterState = signal<string | null>(null);

  readonly alerts = this.alertsState.asReadonly();
  readonly statusFilter = this.statusFilterState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.alertsState().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.statusFilterState() ?? undefined)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: alerts => this.alertsState.set(alerts),
        error: () => this.error.set('Não foi possível carregar os alertas.'),
      });
  }

  filterByStatus(status: string | null): void {
    this.statusFilterState.set(status);
    this.load();
  }

  updateStatus(id: string, status: AlertStatusUpdate): void {
    this.actionError.set(null);
    this.api.updateStatus(id, status)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(status === 'RESOLVED' ? 'Alerta resolvido.' : 'Alerta reconhecido.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível atualizar o alerta.'),
      });
  }

  /** Evidência serializada como pares curtos para exibição. */
  evidenceEntries(alert: SecurityAlert): { key: string; value: string }[] {
    return Object.entries(alert.evidence ?? {}).map(([key, value]) => ({ key, value: String(value) }));
  }
}
