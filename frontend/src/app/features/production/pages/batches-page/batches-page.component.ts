import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
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

  ngOnInit(): void {
    this.store.load();
  }
}
