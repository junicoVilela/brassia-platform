import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CountsStore } from '../../data-access/counts.store';

@Component({
  selector: 'app-counts-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [CountsStore],
  templateUrl: './counts-page.component.html',
})
export class CountsPageComponent implements OnInit {
  protected readonly store = inject(CountsStore);

  /** Valores digitados na folha de contagem, por lote. */
  protected readonly counted = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.store.load();
  }

  protected setCounted(lotId: string, value: string): void {
    this.counted.update(current => ({ ...current, [lotId]: value }));
  }

  protected create(): void {
    const lines = Object.entries(this.counted())
      .filter(([, v]) => v !== '' && v != null && !Number.isNaN(Number(v)))
      .map(([lotId, v]) => ({ lotId, countedQuantity: Number(v) }));
    if (lines.length === 0) {
      return;
    }
    this.store.create({ lines }, () => this.counted.set({}));
  }
}
