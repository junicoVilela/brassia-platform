import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { LoginEvent, UserSession } from '../domain/activity.model';
import { ActivityApi } from './activity.api';

/** Estado das sessões ativas e do histórico de login do próprio usuário. */
@Injectable()
export class ActivityStore {
  private readonly api = inject(ActivityApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly sessionsState = signal<UserSession[]>([]);
  private readonly historyState = signal<LoginEvent[]>([]);

  readonly sessions = this.sessionsState.asReadonly();
  readonly history = this.historyState.asReadonly();
  readonly loadingSessions = signal(false);
  readonly loadingHistory = signal(false);
  readonly error = signal<string | null>(null);
  /** Há outras sessões além da atual? Habilita "encerrar as demais". */
  readonly hasOtherSessions = computed(() => this.sessionsState().some(s => !s.current));

  loadSessions(): void {
    this.loadingSessions.set(true);
    this.error.set(null);
    this.api.listSessions()
      .pipe(finalize(() => this.loadingSessions.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: sessions => this.sessionsState.set(sessions),
        error: () => this.error.set('Não foi possível carregar as sessões.'),
      });
  }

  loadHistory(): void {
    this.loadingHistory.set(true);
    this.api.loginHistory()
      .pipe(finalize(() => this.loadingHistory.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: history => this.historyState.set(history),
        error: () => this.error.set('Não foi possível carregar o histórico de login.'),
      });
  }

  revoke(ref: string): void {
    this.api.revokeSession(ref)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Sessão encerrada.');
          this.loadSessions();
        },
        error: () => this.error.set('Não foi possível encerrar a sessão.'),
      });
  }

  revokeOthers(): void {
    this.api.revokeOtherSessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Outras sessões encerradas.');
          this.loadSessions();
        },
        error: () => this.error.set('Não foi possível encerrar as outras sessões.'),
      });
  }
}
