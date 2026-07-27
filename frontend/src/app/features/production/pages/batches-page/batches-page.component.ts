import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { BatchesStore } from '../../data-access/batches.store';

@Component({
  selector: 'app-batches-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [BatchesStore],
  templateUrl: './batches-page.component.html',
})
export class BatchesPageComponent implements OnInit {
  protected readonly store = inject(BatchesStore);
  private readonly destroyRef = inject(DestroyRef);

  /** Relógio que avança a cada segundo; o decorrido deriva de started_at (server-aware). */
  protected readonly now = signal(Date.now());

  ngOnInit(): void {
    this.store.load();
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  /** Decorrido "mm:ss" desde o início da etapa ativa. */
  protected elapsed(startedAt: string | null): string {
    if (!startedAt) {
      return '—';
    }
    const seconds = Math.max(0, Math.floor((this.now() - new Date(startedAt).getTime()) / 1000));
    const mm = Math.floor(seconds / 60).toString().padStart(2, '0');
    const ss = (seconds % 60).toString().padStart(2, '0');
    return `${mm}:${ss}`;
  }
}
